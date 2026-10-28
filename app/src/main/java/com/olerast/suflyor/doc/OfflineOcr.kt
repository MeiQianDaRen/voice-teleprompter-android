package com.olerast.suflyor.doc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** No network APIs: RapidOCR detection, orientation and recognition models ship in the APK. */
object OfflineOcr {
    private val lock = Mutex()
    private val models = listOf("det.onnx", "cls.onnx", "rec-zh.onnx", "rec-en.onnx")

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

    suspend fun recognize(context: Context, uri: Uri, rotation: Int, language: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> }): String = lock.withLock {
        withContext(Dispatchers.IO) {
            val job = currentCoroutineContext()
            job.ensureActive()
            check(OpenCVLoader.initLocal()) { "Cannot load OpenCV" }
            File(context.noBackupFilesDir, "ocr-4.9.0").takeIf { it.exists() }?.deleteRecursively()
            val data = File(context.noBackupFilesDir, "rapidocr-v3.10.0").apply { mkdirs() }
            for (name in models) {
                job.ensureActive()
                val target = File(data, name)
                context.assets.open("rapidocr/$name").use { input ->
                    if (!target.exists() || target.length() != input.available().toLong()) {
                        val part = File(data, "$name.part")
                        part.outputStream().use { input.copyTo(it) }
                        check(part.renameTo(target)) { "Cannot install OCR model" }
                    }
                }
            }
            val bitmap = decode(context, uri, rotation)
            val rgba = Mat()
            val bgr = Mat()
            try {
                Utils.bitmapToMat(bitmap, rgba)
                Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
                rgba.release()
                bitmap.recycle()
                val keys = context.assets.open("rapidocr/keys-${if (language == "en") "en" else "zh"}.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
                RapidOcrEngine({ File(data, it).absolutePath }, language, keys).use { engine ->
                    val text = engine.recognize(bgr, { job.ensureActive() }, onProgress)
                    job.ensureActive()
                    require(text.length <= DocumentImporter.MAX_CHARS) { "OCR text too long" }
                    text.trim()
                }
            } finally {
                rgba.release()
                bgr.release()
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }
}
