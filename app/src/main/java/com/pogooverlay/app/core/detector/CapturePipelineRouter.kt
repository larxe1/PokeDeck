package com.pogooverlay.app.core.detector

import com.pogooverlay.app.core.data.OverlayMode
import com.pogooverlay.app.core.data.SpeciesMatchResult
import com.pogooverlay.app.core.ocr.OcrLine

sealed class CaptureRouteResult<out R, out S> {
    data class Raid<R>(val result: R) : CaptureRouteResult<R, Nothing>()
    data class Scan<S>(val result: S) : CaptureRouteResult<Nothing, S>()
}

object CapturePipelineRouter {
    suspend fun <R, S> dispatch(
        mode: OverlayMode,
        onRaid: suspend () -> R,
        onScan: suspend () -> S
    ): CaptureRouteResult<R, S> = when (mode) {
        OverlayMode.RAID -> CaptureRouteResult.Raid(onRaid())
        OverlayMode.SCAN -> CaptureRouteResult.Scan(onScan())
    }
}

sealed class RaidCaptureAction {
    data class ShowPreviewWeaknesses(val detection: RaidBossDetectionResult) : RaidCaptureAction()
    data class ShowLobbySuggestedParty(val cachedBoss: SpeciesMatchResult) : RaidCaptureAction()
    data class PromptScanPreviewFirst(val message: String) : RaidCaptureAction()
    data class BossNotDetected(val message: String) : RaidCaptureAction()
}

class RaidFlowCoordinator(private val bossDetector: RaidBossDetector) {
    var activeRaidBoss: SpeciesMatchResult? = null
        private set

    fun setManualBoss(match: SpeciesMatchResult) {
        activeRaidBoss = match
    }

    fun clearActiveBoss() {
        activeRaidBoss = null
    }

    fun handleCapturedLines(lines: List<OcrLine>): RaidCaptureAction {
        val screenType = RaidScreenClassifier.classify(lines)
        if (screenType == RaidScreenType.RAID_LOBBY) {
            val cached = activeRaidBoss
            return if (cached != null) {
                RaidCaptureAction.ShowLobbySuggestedParty(cached)
            } else {
                RaidCaptureAction.PromptScanPreviewFirst(
                    "Scan boss on Raid Preview first (or pick boss)"
                )
            }
        }

        val detection = bossDetector.detectWithBox(lines)
        return if (detection != null) {
            activeRaidBoss = detection.match
            RaidCaptureAction.ShowPreviewWeaknesses(detection)
        } else {
            RaidCaptureAction.BossNotDetected("No raid boss found — try ≡ Drawer")
        }
    }
}
