package com.pogooverlay.app.core.detector

import android.graphics.RectF
import com.pogooverlay.app.core.ocr.OcrLine

/**
 * Modular screen-detector interface for manual OCR detection pipelines.
 */
interface ScreenDetector<T> {
    val id: String
    val roiNormalized: RectF
    suspend fun detect(lines: List<OcrLine>): T?
}
