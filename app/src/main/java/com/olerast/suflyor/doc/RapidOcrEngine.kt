package com.olerast.suflyor.doc

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.RotatedRect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.Closeable
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** RapidOCR's DB detection → perspective correction → angle classification → CTC recognition.
 * Uses RapidAI's pinned PaddleOCR models. No networking, Android UI or logging of recognized text.
 */
internal class RapidOcrEngine(modelPath: (String) -> String, language: String, keys: String) : Closeable {
    private val env = OrtEnvironment.getEnvironment().apply { setTelemetry(false) }
    private val sessions = mutableListOf<OrtSession>()
    private fun session(path: String): OrtSession = OrtSession.SessionOptions().use { options ->
        options.setIntraOpNumThreads(2)
        options.setInterOpNumThreads(1)
        env.createSession(path, options).also { sessions += it }
    }
    private val det: OrtSession
    private val cls: OrtSession
    private val rec: OrtSession
    private val characters: List<String>

    init {
        try {
            det = session(modelPath("det.onnx"))
            cls = session(modelPath("cls.onnx"))
            rec = session(modelPath(if (language == "en") "rec-en.onnx" else "rec-zh.onnx"))
            // Read the full UTF-8 dictionary from assets: JNI metadata strings truncate non-BMP characters.
            characters = RapidOcrMath.characters(keys)
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    override fun close() { sessions.asReversed().forEach { it.close() }; sessions.clear() }

    private fun <T> run(session: OrtSession, data: FloatArray, width: Int, height: Int, read: (OnnxTensor) -> T): T =
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, height.toLong(), width.toLong())).use { input ->
            session.run(mapOf(session.inputNames.first() to input)).use { output -> read(output[0] as OnnxTensor) }
        }

    private fun tensor(image: Mat, width: Int, height: Int, detection: Boolean, paddedWidth: Int = width): FloatArray {
        val resized = Mat()
        try {
            Imgproc.resize(image, resized, Size(width.toDouble(), height.toDouble()))
            val pixels = ByteArray(width * height * 3)
            resized.get(0, 0, pixels)
            // Recognition's right padding is zero after normalization, matching RapidOCR.
            val floats = FloatArray(paddedWidth * height * 3)
            val mean = if (detection) floatArrayOf(.485f, .456f, .406f) else floatArrayOf(.5f, .5f, .5f)
            val std = if (detection) floatArrayOf(.229f, .224f, .225f) else floatArrayOf(.5f, .5f, .5f)
            for (y in 0 until height) for (x in 0 until width) for (channel in 0..2) {
                floats[channel * paddedWidth * height + y * paddedWidth + x] =
                    ((pixels[(y * width + x) * 3 + channel].toInt() and 255) / 255f - mean[channel]) / std[channel]
            }
            return floats
        } finally { resized.release() }
    }

    private fun ordered(points: Array<Point>): Array<Point> {
        val byX = points.sortedBy { it.x }
        val left = byX.take(2).sortedBy { it.y }
        val right = byX.takeLast(2).sortedBy { it.y }
        return arrayOf(left[0], right[0], right[1], left[1])
    }

    private fun boxes(image: Mat): List<Array<Point>> {
        val ratio = min(1.0, 1600.0 / max(image.cols(), image.rows()))
        val width = max(32, (image.cols() * ratio / 32).roundToInt() * 32)
        val height = max(32, (image.rows() * ratio / 32).roundToInt() * 32)
        return run(det, tensor(image, width, height, true), width, height) { output ->
            val shape = (output.info as TensorInfo).shape
            val rows = shape[shape.lastIndex - 1].toInt()
            val cols = shape.last().toInt()
            val probabilities = FloatArray(rows * cols)
            output.floatBuffer.get(probabilities)
            val probability = Mat(rows, cols, CvType.CV_32FC1)
            val binary = Mat(rows, cols, CvType.CV_8UC1)
            val hierarchy = Mat()
            val contours = mutableListOf<MatOfPoint>()
            try {
                probability.put(0, 0, probabilities)
                binary.put(0, 0, ByteArray(probabilities.size) { if (probabilities[it] > .3f) 255.toByte() else 0 })
                Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
                val found = mutableListOf<Array<Point>>()
                for (contour in contours.take(1000)) {
                    val pts = MatOfPoint2f(*contour.toArray())
                    val rect = try { Imgproc.minAreaRect(pts) } finally { pts.release() }
                    if (min(rect.size.width, rect.size.height) < 3) continue
                    val corners = Array(4) { Point() }.also { rect.points(it) }
                    val polygon = MatOfPoint(*corners)
                    val bounds = Imgproc.boundingRect(polygon)
                    bounds.x = bounds.x.coerceIn(0, cols - 1); bounds.y = bounds.y.coerceIn(0, rows - 1)
                    bounds.width = min(bounds.width, cols - bounds.x); bounds.height = min(bounds.height, rows - bounds.y)
                    val mask = Mat.zeros(bounds.height, bounds.width, CvType.CV_8UC1)
                    val relative = MatOfPoint(*corners.map { Point(it.x - bounds.x, it.y - bounds.y) }.toTypedArray())
                    val region = probability.submat(bounds)
                    val score = try {
                        Imgproc.fillPoly(mask, listOf(relative), Scalar(255.0))
                        Core.mean(region, mask).`val`[0]
                    } finally { mask.release(); relative.release(); region.release(); polygon.release() }
                    if (score < .5) continue
                    // For the rectangular DB box, offsetting its polygon and taking minAreaRect is this expansion.
                    val distance = rect.size.width * rect.size.height * 1.6 / (2 * (rect.size.width + rect.size.height))
                    val expanded = RotatedRect(rect.center, Size(rect.size.width + 2 * distance, rect.size.height + 2 * distance), rect.angle)
                    val box = Array(4) { Point() }.also { expanded.points(it) }
                    found += ordered(box.map { Point((it.x * image.cols() / cols).coerceIn(0.0, image.cols() - 1.0),
                        (it.y * image.rows() / rows).coerceIn(0.0, image.rows() - 1.0)) }.toTypedArray())
                }
                // Same top-to-bottom order with left-to-right correction for boxes on the same line.
                val sorted = found.sortedWith(compareBy<Array<Point>> { it[0].y }.thenBy { it[0].x }).toMutableList()
                for (i in 1 until sorted.size) {
                    var j = i
                    while (j > 0 && kotlin.math.abs(sorted[j][0].y - sorted[j - 1][0].y) < 10 && sorted[j][0].x < sorted[j - 1][0].x) {
                        val swap = sorted[j]; sorted[j] = sorted[j - 1]; sorted[j - 1] = swap; j--
                    }
                }
                sorted
            } finally { contours.forEach { it.release() }; probability.release(); binary.release(); hierarchy.release() }
        }
    }

    private fun crop(image: Mat, box: Array<Point>): Mat {
        fun length(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)
        val width = max(2, max(length(box[0], box[1]), length(box[2], box[3])).roundToInt())
        val height = max(2, max(length(box[0], box[3]), length(box[1], box[2])).roundToInt())
        val from = MatOfPoint2f(*box)
        val to = MatOfPoint2f(Point(0.0, 0.0), Point(width - 1.0, 0.0), Point(width - 1.0, height - 1.0), Point(0.0, height - 1.0))
        val transform = Imgproc.getPerspectiveTransform(from, to)
        val result = Mat()
        try {
            Imgproc.warpPerspective(image, result, transform, Size(width.toDouble(), height.toDouble()), Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE)
            if (height.toDouble() / width >= 1.5) Core.rotate(result, result, Core.ROTATE_90_COUNTERCLOCKWISE)
            return result
        } catch (e: Exception) { result.release(); throw e }
        finally { from.release(); to.release(); transform.release() }
    }

    private fun upsideDown(line: Mat): Boolean {
        val width = min(192, ceil(48.0 * line.cols() / line.rows()).toInt()).coerceAtLeast(1)
        return run(cls, tensor(line, width, 48, false, 192), 192, 48) { output ->
            val p = output.floatBuffer
            p.get(1) > .9f && p.get(1) > p.get(0)
        }
    }

    fun recognize(bgr: Mat, checkCancelled: () -> Unit = {}, onProgress: (Int, Int) -> Unit = { _, _ -> }): String {
        checkCancelled()
        var detected = boxes(bgr)
        val upright = Mat()
        try {
            val sample = detected.take(3)
            var inverted = 0
            for (box in sample) {
                checkCancelled()
                val line = crop(bgr, box)
                try { if (upsideDown(line)) inverted++ } finally { line.release() }
            }
            val source = if (inverted > sample.size / 2) {
                // Correct the entire page before sorting: per-line rotation alone reverses paragraph order.
                Core.rotate(bgr, upright, Core.ROTATE_180)
                detected = boxes(upright)
                upright
            } else bgr
            checkCancelled()
            onProgress(0, detected.size)
            val lines = mutableListOf<String>()
            for ((index, box) in detected.withIndex()) {
                checkCancelled()
                val line = crop(source, box)
                try {
                    if (upsideDown(line)) Core.rotate(line, line, Core.ROTATE_180)
                    checkCancelled()
                    val width = ceil(48.0 * line.cols() / line.rows()).toInt().coerceIn(8, 4096)
                    val padded = max(320, width)
                    val result = run(rec, tensor(line, width, 48, false, padded), padded, 48) { output ->
                        val shape = (output.info as TensorInfo).shape
                        RapidOcrMath.decode(output.floatBuffer, shape[1].toInt(), shape[2].toInt(), characters)
                    }
                    if (result.confidence >= .45f && result.text.isNotBlank()) lines += result.text
                } finally { line.release() }
                onProgress(index + 1, detected.size)
            }
            return lines.joinToString("\n")
        } finally { upright.release() }
    }
}
