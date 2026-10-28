package com.olerast.suflyor.doc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** No network APIs: the engine and all three language models ship in the APK. */
object OfflineOcr {
    private val lock = Mutex()
    private val languages = listOf("chi_sim", "chi_tra", "eng")

    fun decode(context: Context, uri: Uri, rotation: Int): Bitmap {
        require(uri.scheme == "content")
        // ImageDecoder applies EXIF orientation before our optional manual quarter turns.
        val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val (width, height) = ScanText.imageSize(info.size.width, info.size.height)
            decoder.setTargetSize(width, height)
        }
        var bitmap = decoded
        if (decoded.hasAlpha()) {
            bitmap = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply { drawColor(Color.WHITE); drawBitmap(decoded, 0f, 0f, null) }
            decoded.recycle()
        }
        if (rotation % 360 == 0) return bitmap
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height,
                Matrix().apply { postRotate(rotation.toFloat()) }, true)
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun recognize(context: Context, uri: Uri, rotation: Int, language: String): String = lock.withLock {
        require(language in languages)
        withContext(Dispatchers.IO) {
            val job = currentCoroutineContext()
            job.ensureActive()
            val data = File(context.noBackupFilesDir, "ocr-4.9.0")
            val models = File(data, "tessdata").apply { mkdirs() }
            for (language in languages) {
                job.ensureActive()
                val target = File(models, "$language.traineddata")
                context.assets.open("ocr/$language.traineddata").use { input ->
                    // Replace interrupted copies atomically; asset length also detects a partial older copy.
                    if (!target.exists() || target.length() != input.available().toLong()) {
                        val part = File(models, "$language.part")
                        part.outputStream().use { input.copyTo(it) }
                        check(part.renameTo(target)) { "Cannot install OCR model" }
                    }
                }
            }
            val bitmap = decode(context, uri, rotation)
            var engine: TessBaseAPI? = null
            try {
                job.ensureActive()
                val tess = TessBaseAPI { if (!job.isActive) engine?.stop() }
                engine = tess
                val selected = if (language == "eng") "eng" else "$language+eng"
                check(tess.init(data.absolutePath + "/", selected, TessBaseAPI.OEM_LSTM_ONLY))
                tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
                tess.setImage(bitmap)
                val text = ScanText.clean(tess.getUTF8Text().orEmpty())
                job.ensureActive()
                require(text.length <= DocumentImporter.MAX_CHARS) { "OCR text too long" }
                text
            } finally {
                engine?.recycle()
                bitmap.recycle()
            }
        }
    }
}
