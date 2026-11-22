package com.pogooverlay.app.core.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.pogooverlay.app.core.data.OverlayMode
import com.pogooverlay.app.core.ocr.OcrLine
import com.pogooverlay.app.core.scan.AppraisalIvParseResult
import java.io.File
import java.io.FileOutputStream

data class CaptureDebugOverlayData(
    val mode: OverlayMode,
    val allOcrLines: List<OcrLine>,
    val raidBossLine: OcrLine? = null,
    val raidBossLabel: String? = null,
    val appraisalResult: AppraisalIvParseResult? = null,
    val matchedMoveLines: List<OcrLine> = emptyList()
)

object CaptureDebugAnnotator {

    fun saveAnnotatedCapture(
        context: Context,
        bitmap: Bitmap,
        data: CaptureDebugOverlayData
    ): String? {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return null
        }

        var annotated: Bitmap? = null
        return try {
            annotated = bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return null
            val canvas = Canvas(annotated)
            val width = annotated.width.toFloat()
            val height = annotated.height.toFloat()

            val ocrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = Color.argb(185, 96, 165, 250) // blue/gray for raw OCR lines
            }

            val raidBossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 5f
                color = Color.rgb(245, 197, 24) // gold for raid boss box
            }

            val anchorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 4f
                color = Color.rgb(249, 115, 22) // orange for Attack/Defense/HP anchors
            }

            val cardBoundsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                color = Color.argb(180, 251, 146, 60)
            }

            val scanlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 4f
                color = Color.rgb(239, 68, 68) // red for IV bar scanlines
            }

            val maxedScanlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 4f
                color = Color.rgb(236, 72, 153) // pink/red for 15-IV maxed bar
            }

            val movePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 4f
                color = Color.rgb(16, 185, 129) // emerald for matched moves
            }

            val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.argb(210, 17, 24, 39)
            }

            val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = (width * 0.028f).coerceIn(18f, 30f)
                isFakeBoldText = true
            }

            // 1. All OCR line boxes (blue/gray)
            for (line in data.allOcrLines) {
                canvas.drawRect(lineToRectF(line, width, height), ocrPaint)
            }

            // 2. Raid boss box + label (gold/green)
            val bossLine = data.raidBossLine
            if (bossLine != null) {
                val rect = lineToRectF(bossLine, width, height)
                canvas.drawRect(rect, raidBossPaint)
                val label = data.raidBossLabel ?: bossLine.text
                drawLabelBadge(
                    canvas = canvas,
                    text = "BOSS: $label",
                    x = rect.left,
                    y = (rect.top - 8f).coerceAtLeast(36f),
                    bgPaint = labelBgPaint,
                    textPaint = labelTextPaint,
                    accentColor = Color.rgb(34, 197, 94)
                )
            } else if (!data.raidBossLabel.isNullOrBlank()) {
                drawLabelBadge(
                    canvas = canvas,
                    text = data.raidBossLabel,
                    x = 16f,
                    y = 88f,
                    bgPaint = labelBgPaint,
                    textPaint = labelTextPaint,
                    accentColor = Color.rgb(245, 197, 24)
                )
            }

            // 3. Appraisal anchors, card bounds, IV bar scanlines, track bounds & computed IVs (orange/red)
            val appraisal = data.appraisalResult
            if (appraisal != null) {
                val bounds = appraisal.appraisalCardBoundsNormalized
                canvas.drawRect(
                    RectF(
                        bounds.left * width,
                        bounds.top * height,
                        bounds.right * width,
                        bounds.bottom * height
                    ),
                    cardBoundsPaint
                )

                listOf(
                    appraisal.attackAnchor,
                    appraisal.defenseAnchor,
                    appraisal.hpAnchor
                ).forEach { anchor ->
                    canvas.drawRect(lineToRectF(anchor, width, height), anchorPaint)
                }

                for (bar in appraisal.barScanlines) {
                    val y = bar.scanY.toFloat().coerceIn(0f, height)
                    val startX = bar.trackStartX.toFloat().coerceIn(0f, width)
                    val endX = bar.trackEndX.toFloat().coerceIn(0f, width)
                    val linePaint = if (bar.isMaxedPink) maxedScanlinePaint else scanlinePaint

                    canvas.drawLine(startX, y, endX, y, linePaint)
                    canvas.drawLine(startX, y - 10f, startX, y + 10f, linePaint)
                    canvas.drawLine(endX, y - 10f, endX, y + 10f, linePaint)

                    val ivTag = "${bar.label}=${bar.computedIv}${if (bar.isMaxedPink) "*" else ""}"
                    drawLabelBadge(
                        canvas = canvas,
                        text = ivTag,
                        x = (endX + 8f).coerceAtMost((width - 140f).coerceAtLeast(8f)),
                        y = (y + 8f).coerceIn(32f, height - 8f),
                        bgPaint = labelBgPaint,
                        textPaint = labelTextPaint,
                        accentColor = if (bar.isMaxedPink) Color.rgb(236, 72, 153) else Color.rgb(249, 115, 22)
                    )
                }

                val iv = appraisal.ivSpread
                drawLabelBadge(
                    canvas = canvas,
                    text = "IV ${iv.atk}/${iv.def}/${iv.sta}",
                    x = (bounds.left * width).coerceAtLeast(12f),
                    y = ((bounds.top * height) - 8f).coerceAtLeast(40f),
                    bgPaint = labelBgPaint,
                    textPaint = labelTextPaint,
                    accentColor = Color.rgb(249, 115, 22)
                )
            }

            // 4. Matched move boxes (emerald)
            for (moveLine in data.matchedMoveLines) {
                val rect = lineToRectF(moveLine, width, height)
                canvas.drawRect(rect, movePaint)
                drawLabelBadge(
                    canvas = canvas,
                    text = moveLine.text,
                    x = rect.left,
                    y = (rect.top - 6f).coerceAtLeast(32f),
                    bgPaint = labelBgPaint,
                    textPaint = labelTextPaint,
                    accentColor = Color.rgb(16, 185, 129)
                )
            }

            // 5. Top-left mode HUD header
            drawLabelBadge(
                canvas = canvas,
                text = "MODE: ${data.mode.name} | OCR: ${data.allOcrLines.size}",
                x = 16f,
                y = 44f,
                bgPaint = labelBgPaint,
                textPaint = labelTextPaint,
                accentColor = Color.rgb(96, 165, 250)
            )

            val dir = context.getExternalFilesDir("debug_captures")
                ?: File(context.filesDir, "debug_captures")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val outputFile = File(dir, "last_capture.png")
            FileOutputStream(outputFile).use { stream ->
                annotated.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.flush()
            }
            outputFile.absolutePath
        } catch (_: Throwable) {
            null
        } finally {
            try {
                if (annotated != null && !annotated.isRecycled) {
                    annotated.recycle()
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun lineToRectF(line: OcrLine, width: Float, height: Float): RectF {
        val left = (line.normalizedLeft * width).coerceIn(0f, width)
        val top = (line.normalizedTop * height).coerceIn(0f, height)
        val rightNorm = if (line.normalizedWidth > 0f) {
            line.normalizedRight
        } else {
            (line.normalizedLeft + 0.16f).coerceAtMost(1f)
        }
        val right = (rightNorm * width).coerceIn(left + 2f, width)
        val bottom = (line.normalizedBottom * height).coerceIn(top + 2f, height)
        return RectF(left, top, right, bottom)
    }

    private fun drawLabelBadge(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        bgPaint: Paint,
        textPaint: Paint,
        accentColor: Int
    ) {
        val textWidth = textPaint.measureText(text)
        val fm = textPaint.fontMetrics
        val padH = 8f
        val padV = 4f
        val left = x.coerceIn(4f, (canvas.width - textWidth - padH * 2f - 4f).coerceAtLeast(4f))
        val baselineY = y.coerceIn(-fm.top + padV + 4f, canvas.height - fm.bottom - padV - 4f)
        val bgRect = RectF(
            left,
            baselineY + fm.top - padV,
            left + textWidth + padH * 2f,
            baselineY + fm.bottom + padV
        )
        canvas.drawRoundRect(bgRect, 6f, 6f, bgPaint)
        val previousColor = textPaint.color
        textPaint.color = accentColor
        canvas.drawText(text, left + padH, baselineY, textPaint)
        textPaint.color = previousColor
    }
}
