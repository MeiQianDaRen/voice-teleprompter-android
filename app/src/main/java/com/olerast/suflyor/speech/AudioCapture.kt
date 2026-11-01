package com.olerast.suflyor.speech

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Process
import android.os.SystemClock
import com.olerast.suflyor.R
import com.olerast.suflyor.diag.DiagLog
import kotlin.math.log10
import kotlin.math.sqrt

/** Why the microphone could not be opened or stopped delivering audio. toString() is the English journal text. */
sealed interface CaptureError {
    data class RateUnsupported(val hz: Int) : CaptureError {
        override fun toString() = "The microphone doesn't support $hz Hz"
    }

    data class CreateFailed(val detail: String) : CaptureError {
        override fun toString() = "AudioRecord not created: $detail"
    }

    data object NotInitialized : CaptureError {
        override fun toString() = "AudioRecord not initialized (no microphone permission?)"
    }

    data class StartFailed(val detail: String) : CaptureError {
        override fun toString() = "Couldn't start recording: $detail"
    }

    data object MicBusy : CaptureError {
        override fun toString() = "Android wouldn't start recording: another app is using the microphone"
    }

    data class ReadFailed(val code: Int) : CaptureError {
        override fun toString() = "Microphone read error: $code"
    }

    /** What the user sees (the window's diagnostics line). */
    fun message(context: Context): String = when (this) {
        is RateUnsupported -> context.getString(R.string.session_error_rate_unsupported, hz)
        is CreateFailed -> context.getString(R.string.session_error_recorder_create, detail)
        NotInitialized -> context.getString(R.string.session_error_recorder_init)
        is StartFailed -> context.getString(R.string.session_error_record_start, detail)
        MicBusy -> context.getString(R.string.session_error_mic_busy)
        is ReadFailed -> context.getString(R.string.session_error_mic_read, code)
    }
}

/**
 * Microphone capture on its own thread. Never uses CAMCORDER / VOICE_COMMUNICATION and never marks the capture
 * as privacy-sensitive: such a capture would take the microphone away from the camera app that is recording.
 */
class AudioCapture(
    private val source: Int,
    private val sampleRate: Int,
    private val listener: Listener,
) {
    interface Listener {
        /** Called on the processing thread; capture remains independent of model inference. */
        fun onAudio(samples: FloatArray, sampleRate: Int, rmsDb: Float, digitalSilence: Boolean)
        fun onAudioFrame(source: AudioCapture, frame: AudioFrame) {
            onAudio(frame.samples, frame.sampleRate, frame.rmsDb, frame.digitalSilence)
        }
        /** The microphone stopped for good; [source] tells an old capture's late report from the current one. */
        fun onCaptureError(source: AudioCapture, error: CaptureError)
    }

    @Volatile
    private var running = false
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private var processor: Thread? = null
    private var queue = AudioFrameQueue()

    @Volatile var lastAudibleAtMs = 0L
        private set

    val audioSessionId: Int get() = record?.audioSessionId ?: 0

    /** Whether Android currently feeds this capture with silence because another app has priority (API 29+). */
    val isSilencedBySystem: Boolean?
        get() = runCatching { record?.activeRecordingConfiguration?.isClientSilenced }.getOrNull()

    /** @return null when recording has started, otherwise why it could not. */
    fun start(): CaptureError? {
        require(source != MediaRecorder.AudioSource.CAMCORDER && source != MediaRecorder.AudioSource.VOICE_COMMUNICATION)
        val (rec, error) = open()
        if (rec == null) return error
        record = rec
        running = true
        queue = AudioFrameQueue()
        val input = queue
        processor = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT)
            while (running) {
                val frame = input.take() ?: break
                if (!running) break
                if (frame.discontinuity) DiagLog.i("Audio stream gap; pending audio cleared, total ${input.droppedMs} ms")
                listener.onAudioFrame(this, frame)
            }
        }, "asr-decode").also { it.start() }
        thread = Thread({ loop(rec) }, "audio-capture").also { it.start() }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun open(): Pair<AudioRecord?, CaptureError?> {
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return null to CaptureError.RateUnsupported(sampleRate)
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val builder = AudioRecord.Builder()
            .setAudioSource(source)
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minBuf * 4, sampleRate * 2))
        if (Build.VERSION.SDK_INT >= 30) builder.setPrivacySensitive(false)
        val rec = try {
            builder.build()
        } catch (e: Exception) {
            return null to CaptureError.CreateFailed(e.message ?: e.javaClass.simpleName)
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return null to CaptureError.NotInitialized
        }
        try {
            rec.startRecording()
        } catch (e: IllegalStateException) {
            rec.release()
            return null to CaptureError.StartFailed(e.message ?: e.javaClass.simpleName)
        }
        if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            rec.release()
            return null to CaptureError.MicBusy
        }
        return rec to null
    }

    private fun loop(first: AudioRecord) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        var rec = first
        var reopens = 0
        val chunk = sampleRate / 25 // 40 ms; model readiness still determines decoding cadence.
        var readFrames = 0L
        var clock = AudioSampleClock(sampleRate)
        val timestamp = AudioTimestamp()
        var gap = false
        val shorts = ShortArray(chunk)
        while (running) {
            val n = rec.read(shorts, 0, chunk)
            if (n < 0) {
                if (!running) break
                // A dead AudioRecord (audio server restart, route change) can often be replaced by a new one. A
                // microphone that keeps failing is given up after a few reopens, not retried forever.
                val reopened = if (reopens < MAX_REOPENS_PER_SESSION) reopen(rec) else null
                if (reopened == null) {
                    // Stopped while reopening: that is not a failure worth reporting.
                    if (running) listener.onCaptureError(this, CaptureError.ReadFailed(n))
                    break
                }
                reopens++
                rec = reopened
                readFrames = 0L
                clock = AudioSampleClock(sampleRate)
                gap = true
                continue
            }
            if (n == 0) continue
            val samples = FloatArray(n)
            var sum = 0.0
            var nonZero = false
            for (i in 0 until n) {
                val s = shorts[i]
                if (s.toInt() != 0) nonZero = true
                val f = s / 32768f
                samples[i] = f
                sum += f * f
            }
            val rms = sqrt(sum / n)
            val db = if (rms > 1e-7) (20 * log10(rms)).toFloat() else -120f
            readFrames += n
            val now = SystemClock.elapsedRealtime()
            val stamped = runCatching {
                rec.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_BOOTTIME) == AudioRecord.SUCCESS
            }.getOrDefault(false)
            val capturedAt = clock.endTime(readFrames, now,
                if (stamped) timestamp.framePosition else null,
                if (stamped) timestamp.nanoTime / 1_000_000.0 else null)
            if (nonZero && db > -50f) lastAudibleAtMs = capturedAt
            queue.offer(AudioFrame(samples, sampleRate, db, !nonZero, capturedAt, now, gap))
            gap = false
        }
        queue.close()
    }

    /**
     * Releases the failed recorder and tries to open a new one, three times with growing pauses: an audio server
     * that restarts needs a second or two. @return the new recorder, or null when every try failed or [stop] came.
     */
    private fun reopen(old: AudioRecord): AudioRecord? {
        runCatching { old.stop() }
        runCatching { old.release() }
        for ((attempt, delay) in REOPEN_DELAYS_MS.withIndex()) {
            try {
                Thread.sleep(delay)
            } catch (_: InterruptedException) {
                return null
            }
            if (!running) return null
            val (rec, _) = open()
            if (rec == null) continue
            synchronized(this) {
                if (!running) {
                    rec.release()
                    return null
                }
                record = rec
            }
            DiagLog.i("Microphone read failed; reopened it on try ${attempt + 1}")
            return rec
        }
        return null
    }

    fun stop() {
        running = false
        queue.close()
        runCatching { record?.stop() }
        thread?.join(700)
        thread = null
        processor?.join(700)
        processor = null
        runCatching { record?.release() }
        record = null
    }

    companion object {
        private val REOPEN_DELAYS_MS = longArrayOf(400L, 800L, 1600L)
        private const val MAX_REOPENS_PER_SESSION = 5

        fun sourceName(source: Int): String = when (source) {
            MediaRecorder.AudioSource.DEFAULT -> "DEFAULT"
            MediaRecorder.AudioSource.MIC -> "MIC"
            MediaRecorder.AudioSource.VOICE_UPLINK -> "VOICE_UPLINK"
            MediaRecorder.AudioSource.VOICE_DOWNLINK -> "VOICE_DOWNLINK"
            MediaRecorder.AudioSource.VOICE_CALL -> "VOICE_CALL"
            MediaRecorder.AudioSource.CAMCORDER -> "CAMCORDER"
            MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
            MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
            MediaRecorder.AudioSource.REMOTE_SUBMIX -> "REMOTE_SUBMIX"
            MediaRecorder.AudioSource.UNPROCESSED -> "UNPROCESSED"
            MediaRecorder.AudioSource.VOICE_PERFORMANCE -> "VOICE_PERFORMANCE"
            1997 -> "RADIO_TUNER"
            1998 -> "HOTWORD"
            1999 -> "ECHO_REFERENCE"
            else -> "source $source"
        }
    }
}
