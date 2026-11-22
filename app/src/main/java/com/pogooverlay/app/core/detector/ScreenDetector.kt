package com.pogooverlay.app.core.detector

import android.graphics.RectF
import com.pogooverlay.app.core.ocr.OcrLine

/**
 * Modular screen-detector interface so Phase 1 (RaidBossDetector) and future Phase 2+
 * detectors (e.g. MyPokemonDetector, RocketHintDetector) share a uniform pipeline.
 */
interface ScreenDetector<T> {
    val id: String
    val roiNormalized: RectF
    suspend fun detect(lines: List<OcrLine>, isManualTrigger: Boolean = false): T?
}
