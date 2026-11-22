package com.pogooverlay.app.core.data

import com.pogooverlay.app.core.scan.ActiveCaptureDraft
import com.pogooverlay.app.core.scan.CaptureStepOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class OverlayMode {
    RAID,
    SCAN
}

enum class RaidCardTab {
    WEAKNESSES,
    SUGGESTED_PARTY
}

data class ActiveBossBanner(
    val match: SpeciesMatchResult,
    val matchup: MatchupSummary,
    val detectedAtMs: Long
)

data class OverlayRuntimeState(
    val isRunning: Boolean = false,
    val overlayMode: OverlayMode = OverlayMode.RAID,
    val hasMediaProjection: Boolean = false,
    val isScanningNow: Boolean = false,
    val activeRaidBoss: ActiveBossBanner? = null,
    val isRaidCardVisible: Boolean = false,
    val activeRaidTab: RaidCardTab = RaidCardTab.WEAKNESSES,
    val scanDraft: ActiveCaptureDraft = ActiveCaptureDraft(),
    val scanStatusPrompt: String? = null,
    val lastScanMessage: String? = null,
    val debugSaveAnnotatedCapture: Boolean = false,
    val lastDebugCapturePath: String? = null
)

object OverlayStateHolder {
    private val _state = MutableStateFlow(OverlayRuntimeState())
    val state: StateFlow<OverlayRuntimeState> = _state.asStateFlow()

    fun update(transform: (OverlayRuntimeState) -> OverlayRuntimeState) {
        _state.update(transform)
    }

    fun setOverlayMode(mode: OverlayMode) {
        _state.update { current ->
            val switchingMode = current.overlayMode != mode
            current.copy(
                overlayMode = mode,
                activeRaidBoss = if (switchingMode) null else current.activeRaidBoss,
                isRaidCardVisible = false,
                activeRaidTab = RaidCardTab.WEAKNESSES,
                scanDraft = ActiveCaptureDraft(),
                scanStatusPrompt = null,
                lastScanMessage = null
            )
        }
    }

    fun showRaidPreviewBoss(match: SpeciesMatchResult) {
        val matchup = TypeEffectivenessChart.calculate(match.species.parsedTypes())
        val banner = ActiveBossBanner(
            match = match,
            matchup = matchup,
            detectedAtMs = System.currentTimeMillis()
        )
        _state.update { current ->
            current.copy(
                activeRaidBoss = banner,
                isRaidCardVisible = true,
                activeRaidTab = RaidCardTab.WEAKNESSES,
                lastScanMessage = "Detected ${match.displayTitle}"
            )
        }
    }

    fun showRaidLobbySuggestedParty(match: SpeciesMatchResult) {
        val matchup = TypeEffectivenessChart.calculate(match.species.parsedTypes())
        val banner = ActiveBossBanner(
            match = match,
            matchup = matchup,
            detectedAtMs = System.currentTimeMillis()
        )
        _state.update { current ->
            val resolvedBanner = current.activeRaidBoss
                ?.takeIf { it.match.species.speciesId == match.species.speciesId }
                ?: banner
            current.copy(
                activeRaidBoss = resolvedBanner,
                isRaidCardVisible = true,
                activeRaidTab = RaidCardTab.SUGGESTED_PARTY,
                lastScanMessage = "Suggested Party · ${resolvedBanner.match.displayTitle}"
            )
        }
    }

    fun setRaidCardTab(tab: RaidCardTab) {
        _state.update { it.copy(activeRaidTab = tab) }
    }

    fun selectForm(
        species: PokemonSpecies,
        allForms: List<PokemonSpecies>,
        detectedPrefix: String?
    ): SpeciesMatchResult {
        val displayTitle = PokemonNameMatcher.formatDisplayTitle(species, detectedPrefix)
        val match = SpeciesMatchResult(
            species = species,
            displayTitle = displayTitle,
            detectedPrefix = detectedPrefix,
            alternateForms = allForms,
            confidence = 1.0f
        )
        val matchup = TypeEffectivenessChart.calculate(species.parsedTypes())
        val banner = ActiveBossBanner(
            match = match,
            matchup = matchup,
            detectedAtMs = System.currentTimeMillis()
        )
        _state.update { current ->
            current.copy(
                activeRaidBoss = banner,
                isRaidCardVisible = true,
                lastScanMessage = "Selected $displayTitle"
            )
        }
        return match
    }

    fun showManualSpecies(
        species: PokemonSpecies,
        allForms: List<PokemonSpecies>
    ): SpeciesMatchResult {
        val match = selectForm(species = species, allForms = allForms, detectedPrefix = null)
        _state.update { it.copy(activeRaidTab = RaidCardTab.WEAKNESSES) }
        return match
    }

    fun dismissRaidCard() {
        _state.update { it.copy(isRaidCardVisible = false) }
    }

    fun clearCachedRaidBoss() {
        _state.update {
            it.copy(
                activeRaidBoss = null,
                isRaidCardVisible = false,
                activeRaidTab = RaidCardTab.WEAKNESSES
            )
        }
    }

    fun updateScanOutcome(outcome: CaptureStepOutcome) {
        _state.update { current ->
            when (outcome) {
                is CaptureStepOutcome.Saved -> current.copy(
                    scanDraft = ActiveCaptureDraft(),
                    scanStatusPrompt = null,
                    lastScanMessage = outcome.flashMessage
                )

                is CaptureStepOutcome.InProgress -> current.copy(
                    scanDraft = outcome.draft,
                    scanStatusPrompt = outcome.persistentStatus,
                    lastScanMessage = outcome.transientFlash
                )

                is CaptureStepOutcome.NoData -> current.copy(
                    scanDraft = outcome.draft,
                    scanStatusPrompt = outcome.persistentStatus,
                    lastScanMessage = outcome.transientFlash
                )
            }
        }
    }

    fun clearScanDraft() {
        _state.update {
            it.copy(
                scanDraft = ActiveCaptureDraft(),
                scanStatusPrompt = null,
                lastScanMessage = null
            )
        }
    }

    fun clearScanMessage() {
        _state.update { it.copy(lastScanMessage = null) }
    }

    fun setDebugSaveAnnotatedCapture(enabled: Boolean) {
        _state.update { it.copy(debugSaveAnnotatedCapture = enabled) }
    }

    fun setLastDebugCapturePath(path: String?) {
        _state.update { it.copy(lastDebugCapturePath = path) }
    }

    fun resetStopped() {
        _state.update { current ->
            OverlayRuntimeState(
                debugSaveAnnotatedCapture = current.debugSaveAnnotatedCapture,
                lastDebugCapturePath = current.lastDebugCapturePath
            )
        }
    }
}
