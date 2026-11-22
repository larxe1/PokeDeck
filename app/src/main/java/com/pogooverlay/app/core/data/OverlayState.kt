package com.pogooverlay.app.core.data

import com.pogooverlay.app.core.detector.DetectedOwnPokemon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ScanMode {
    AUTO_WATCH,
    BATTERY_SAVER_ONE_TAP
}

data class ActiveBossBanner(
    val match: SpeciesMatchResult,
    val matchup: MatchupSummary,
    val detectedAtMs: Long
)

data class OverlayRuntimeState(
    val isRunning: Boolean = false,
    val scanMode: ScanMode = ScanMode.AUTO_WATCH,
    val hasMediaProjection: Boolean = false,
    val isScanningNow: Boolean = false,
    val activeBanner: ActiveBossBanner? = null,
    val pendingPokemonSave: DetectedOwnPokemon? = null,
    val awaitingFollowUpScan: Boolean = false,
    val lastScanMessage: String? = null
)

object OverlayStateHolder {
    private val _state = MutableStateFlow(OverlayRuntimeState())
    val state: StateFlow<OverlayRuntimeState> = _state.asStateFlow()

    fun update(transform: (OverlayRuntimeState) -> OverlayRuntimeState) {
        _state.update(transform)
    }

    fun showMatchedBoss(match: SpeciesMatchResult) {
        val matchup = TypeEffectivenessChart.calculate(match.species.parsedTypes())
        val banner = ActiveBossBanner(
            match = match,
            matchup = matchup,
            detectedAtMs = System.currentTimeMillis()
        )
        _state.update { current ->
            current.copy(
                activeBanner = banner,
                lastScanMessage = "Detected ${match.displayTitle}"
            )
        }
    }

    fun selectForm(
        species: PokemonSpecies,
        allForms: List<PokemonSpecies>,
        detectedPrefix: String?
    ) {
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
                activeBanner = banner,
                lastScanMessage = "Selected $displayTitle"
            )
        }
    }

    fun showManualSpecies(
        species: PokemonSpecies,
        allForms: List<PokemonSpecies>
    ) {
        selectForm(species = species, allForms = allForms, detectedPrefix = null)
    }

    fun dismissBanner() {
        _state.update { it.copy(activeBanner = null) }
    }

    fun showPendingPokemonSave(detected: DetectedOwnPokemon) {
        _state.update {
            it.copy(
                pendingPokemonSave = detected,
                awaitingFollowUpScan = false,
                lastScanMessage = "Scanned ${detected.species.speciesName}${detected.cp?.let { c -> " (CP $c)" } ?: ""}"
            )
        }
    }

    fun beginFollowUpPokemonScan() {
        _state.update {
            it.copy(
                awaitingFollowUpScan = true,
                lastScanMessage = "Scroll & tap 📷 to scan more info"
            )
        }
    }

    fun mergePendingPokemonSave(incoming: DetectedOwnPokemon) {
        _state.update { current ->
            val merged = current.pendingPokemonSave?.mergeWith(incoming) ?: incoming
            current.copy(
                pendingPokemonSave = merged,
                awaitingFollowUpScan = false,
                lastScanMessage = "Updated ${merged.species.speciesName}"
            )
        }
    }

    fun dismissPendingPokemonSave() {
        _state.update { it.copy(pendingPokemonSave = null, awaitingFollowUpScan = false) }
    }

    fun clearScanMessage() {
        _state.update { it.copy(lastScanMessage = null, awaitingFollowUpScan = false) }
    }

    fun resetStopped() {
        _state.value = OverlayRuntimeState()
    }
}
