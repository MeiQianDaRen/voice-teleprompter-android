package com.olerast.suflyor

import com.olerast.suflyor.speech.*
import com.olerast.suflyor.script.SpeechLang
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AudioPipelineTest {
    private fun frame(at: Long, gap: Boolean = false) = AudioFrame(FloatArray(640), 16000, -20f, false, at, at, gap)

    @Test fun queuePreservesContinuousAudioOrder() {
        val q = AudioFrameQueue()
        for (i in 1..10) assertTrue(q.offer(frame(i * 40L)))
        for (i in 1..10) {
            val f = q.take()!!
            assertEquals(i * 40L, f.capturedAtMs)
            assertFalse(f.discontinuity)
        }
        assertEquals(0L, q.droppedMs)
        q.close()
        assertNull(q.take())
        assertFalse(q.offer(frame(1000)))
    }

    @Test fun slowInferenceCannotBlockCaptureAndOverflowIsAnExplicitGap() {
        val q = AudioFrameQueue(120)
        val consuming = CountDownLatch(1)
        val release = CountDownLatch(1)
        q.offer(frame(40))
        val worker = Thread {
            q.take()
            consuming.countDown()
            release.await(2, TimeUnit.SECONDS)
        }.apply { start() }
        assertTrue(consuming.await(1, TimeUnit.SECONDS))
        try {
            for (i in 2..5) assertTrue(q.offer(frame(i * 40L)))
            val fresh = q.take()!!
            assertEquals(200L, fresh.capturedAtMs)
            assertTrue(fresh.discontinuity)
            assertEquals(120L, q.droppedMs)
        } finally { release.countDown(); worker.join(1000); q.close() }
    }

    @Test fun reopeningTheMicDiscardsPendingFramesFromTheOldHardwareClock() {
        val q = AudioFrameQueue()
        q.offer(frame(40)); q.offer(frame(80)); q.offer(frame(1000, true))
        assertEquals(1000L, q.take()!!.capturedAtMs)
        assertEquals(80L, q.droppedMs)
        q.close()
    }

    @Test fun closeWakesAWaitingDecoder() {
        val q = AudioFrameQueue()
        val done = CountDownLatch(1)
        val worker = Thread { assertNull(q.take()); done.countDown() }.apply { start() }
        q.close()
        assertTrue(done.await(1, TimeUnit.SECONDS))
        worker.join(1000)
    }

    @Test fun hardwareFramesMapBufferedAudioToItsCaptureTime() {
        val c = AudioSampleClock(16000)
        assertEquals(1040L, c.endTime(640, 1140, 1600, 1100.0))
        assertEquals(1080L, c.endTime(1280, 1150, 1600, 1100.0))
    }

    @Test fun fallbackClockDoesNotRelabelQueuedAudioAsFresh() {
        val c = AudioSampleClock(16000)
        assertEquals(1040L, c.endTime(640, 1040))
        assertEquals(1080L, c.endTime(1280, 1600))
        assertTrue(c.endTime(1920, 1640, -1, Double.NaN) <= 1640)
    }

    @Test fun forcedEndpointsKeepThePendingAudioAndInvalidateUnknownOffsets() {
        val c = AsrInputClock()
        repeat(300) { c.accept(640, 16000, true) }
        assertEquals(12000L, c.audioDurationMs)
        assertFalse(c.mayRecreateAtEndpoint)
        c.decoderReset()
        c.accept(640, 16000, true)
        assertNull(c.audioDurationMs)
        assertFalse(c.mayRecreateAtEndpoint)
        repeat(20) { c.accept(640, 16000, false) }
        c.textChanged()
        assertFalse(c.mayRecreateAtEndpoint)
        repeat(20) { c.accept(640, 16000, false) }
        assertTrue(c.mayRecreateAtEndpoint)
        c.reset()
        c.accept(640, 16000, true)
        assertEquals(40L, c.audioDurationMs)
    }

    @Test fun naturalSilenceAllowsAFreshKnownClockWithoutCuttingSpeech() {
        val c = AsrInputClock()
        repeat(310) { c.accept(640, 16000, true) }
        repeat(20) { c.accept(640, 16000, false) }
        assertTrue(c.mayRecreateAtEndpoint)
    }

    @Test fun staleCaptureLeaseDoesNotFeedTheDecoder() {
        var calls = 0
        val engine = object : AsrEngine {
            override val description = "test"
            override val lang = SpeechLang.ZH
            override val hotwordsSetting = false
            override fun accept(samples: FloatArray, sampleRate: Int): AsrUpdate { calls++; return AsrUpdate("今天", false) }
            override fun setBiasWords(words: List<String>) {}
            override fun reset() {}
            override fun release() {}
        }
        assertNull(engine.accept(FloatArray(640), 16000) { false })
        assertEquals(0, calls)
        assertNotNull(engine.accept(FloatArray(640), 16000) { true })
        assertEquals(1, calls)
    }
}
