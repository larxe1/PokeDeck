package com.pogooverlay.app.core.ocr

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

data class OcrLine(
    val text: String,
    val normalizedTop: Float,
    val normalizedLeft: Float,
    val normalizedHeight: Float,
    val normalizedWidth: Float = 0f
) {
    val normalizedBottom: Float
        get() = (normalizedTop + normalizedHeight).coerceIn(0f, 1f)

    val normalizedRight: Float
        get() = (normalizedLeft + normalizedWidth).coerceIn(0f, 1f)
}

class OcrPipeline {

    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @Volatile
    private var isClosed: Boolean = false

    suspend fun recognizeLines(
        bitmap: Bitmap,
        cropNormalized: RectF? = null
    ): List<OcrLine> = withContext(Dispatchers.Default) {
        if (isClosed || bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return@withContext emptyList()
        }

        val fullWidth = bitmap.width
        val fullHeight = bitmap.height

        var offsetX = 0
        var offsetY = 0
        var croppedBitmap: Bitmap? = null

        try {
            val targetBitmap = if (cropNormalized != null) {
                val leftRatio = cropNormalized.left.coerceIn(0f, 1f)
                val topRatio = cropNormalized.top.coerceIn(0f, 1f)
                val rightRatio = cropNormalized.right.coerceIn(leftRatio, 1f)
                val bottomRatio = cropNormalized.bottom.coerceIn(topRatio, 1f)

                val leftPx = (leftRatio * fullWidth).roundToInt().coerceIn(0, (fullWidth - 1).coerceAtLeast(0))
                val topPx = (topRatio * fullHeight).roundToInt().coerceIn(0, (fullHeight - 1).coerceAtLeast(0))
                val rightPx = (rightRatio * fullWidth).roundToInt().coerceIn(leftPx + 1, fullWidth)
                val bottomPx = (bottomRatio * fullHeight).roundToInt().coerceIn(topPx + 1, fullHeight)

                val cropWidth = (rightPx - leftPx).coerceAtLeast(1)
                val cropHeight = (bottomPx - topPx).coerceAtLeast(1)

                if (leftPx == 0 && topPx == 0 && cropWidth == fullWidth && cropHeight == fullHeight) {
                    bitmap
                } else {
                    offsetX = leftPx
                    offsetY = topPx
                    Bitmap.createBitmap(bitmap, leftPx, topPx, cropWidth, cropHeight).also {
                        croppedBitmap = it
                    }
                }
            } else {
                bitmap
            }

            val inputImage = InputImage.fromBitmap(targetBitmap, 0)
            val result: Text = awaitTask(recognizer.process(inputImage))

            val lines = mutableListOf<OcrLine>()
            for (block in result.textBlocks) {
                for (line in block.lines) {
                    val trimmed = line.text.trim()
                    if (trimmed.isEmpty()) continue
                    val box = line.boundingBox
                    val absTop = ((box?.top ?: 0) + offsetY).coerceIn(0, fullHeight)
                    val absLeft = ((box?.left ?: 0) + offsetX).coerceIn(0, fullWidth)
                    val heightPx = (box?.height() ?: 0).coerceAtLeast(1)

                    lines.add(
                        OcrLine(
                            text = trimmed,
                            normalizedTop = (absTop.toFloat() / fullHeight.toFloat()).coerceIn(0f, 1f),
                            normalizedLeft = (absLeft.toFloat() / fullWidth.toFloat()).coerceIn(0f, 1f),
                            normalizedHeight = (heightPx.toFloat() / fullHeight.toFloat()).coerceIn(0f, 1f),
                            normalizedWidth = ((box?.width() ?: 0).coerceAtLeast(1).toFloat() / fullWidth.toFloat()).coerceIn(0f, 1f)
                        )
                    )
                }
            }

            lines.sortedWith(
                compareBy<OcrLine> { it.normalizedTop }
                    .thenBy { it.normalizedLeft }
            )
        } catch (_: Throwable) {
            emptyList()
        } finally {
            val temp = croppedBitmap
            if (temp != null && temp !== bitmap && !temp.isRecycled) {
                try {
                    temp.recycle()
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun close() {
        isClosed = true
        try {
            recognizer.close()
        } catch (_: Throwable) {
        }
    }

    private suspend fun <T> awaitTask(task: Task<T>): T =
        suspendCancellableCoroutine { continuation ->
            task.addOnSuccessListener { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
            task.addOnFailureListener { exception ->
                if (continuation.isActive) {
                    continuation.resumeWithException(exception)
                }
            }
            task.addOnCanceledListener {
                if (continuation.isActive) {
                    continuation.cancel()
                }
            }
        }
}
