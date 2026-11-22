package com.pogooverlay.app.core.scan

import com.pogooverlay.app.core.data.PokemonSpecies
import com.pogooverlay.app.core.ocr.OcrLine
import com.pogooverlay.app.core.pokebox.MoveData
import com.pogooverlay.app.core.pokebox.SavedPokemon

data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun containsCenter(line: OcrLine): Boolean {
        val centerX = if (line.normalizedWidth > 0f) {
            (line.normalizedLeft + line.normalizedRight) * 0.5f
        } else {
            line.normalizedLeft
        }
        val centerY = (line.normalizedTop + line.normalizedBottom) * 0.5f
        return centerX in left..right && centerY in top..bottom
    }
}

data class IvSpread(
    val atk: Int,
    val def: Int,
    val sta: Int
)

data class CompleteMoveset(
    val fastMove: MoveData,
    val chargedMoves: List<MoveData>
) {
    init {
        require(chargedMoves.isNotEmpty())
    }

    val chargedMove1: MoveData get() = chargedMoves[0]
    val chargedMove2: MoveData? get() = chargedMoves.getOrNull(1)
}

enum class MovesetParseStatus {
    NONE_FOUND,
    COMPLETE,
    INCOMPLETE_CUTOFF
}

data class MovesetParseResult(
    val status: MovesetParseStatus,
    val moveset: CompleteMoveset? = null,
    val matchedLines: List<OcrLine> = emptyList()
)

data class IvBarScanDebugInfo(
    val label: String,
    val anchorLine: OcrLine,
    val scanY: Int,
    val trackStartX: Int,
    val trackEndX: Int,
    val computedIv: Int,
    val isMaxedPink: Boolean
)

data class AppraisalIvParseResult(
    val ivSpread: IvSpread,
    val attackAnchor: OcrLine,
    val defenseAnchor: OcrLine,
    val hpAnchor: OcrLine,
    val barScanlines: List<IvBarScanDebugInfo>,
    val appraisalCardBoundsNormalized: NormalizedRect
)

data class ScanFrameExtraction(
    val species: PokemonSpecies? = null,
    val speciesId: String? = species?.speciesId,
    val cp: Int? = null,
    val hp: Int? = null,
    val nickname: String? = null,
    val isShadow: Boolean = false,
    val isPurified: Boolean = false,
    val isLucky: Boolean = false,
    val ivData: IvSpread? = null,
    val appraisalResult: AppraisalIvParseResult? = null,
    val movesetResult: MovesetParseResult = MovesetParseResult(MovesetParseStatus.NONE_FOUND)
)

data class ActiveCaptureDraft(
    val speciesId: String? = null,
    val species: PokemonSpecies? = null,
    val cp: Int? = null,
    val hp: Int? = null,
    val nickname: String? = null,
    val isShadow: Boolean = false,
    val isPurified: Boolean = false,
    val isLucky: Boolean = false,
    val ivData: IvSpread? = null,
    val movesetData: CompleteMoveset? = null
) {
    val isIdle: Boolean
        get() = speciesId == null && cp == null && ivData == null && movesetData == null

    val isComplete: Boolean
        get() = species != null && ivData != null && movesetData != null
}

sealed class CaptureStepOutcome {
    data class Saved(
        val pokemon: SavedPokemon,
        val flashMessage: String
    ) : CaptureStepOutcome()

    data class InProgress(
        val draft: ActiveCaptureDraft,
        val persistentStatus: String,
        val transientFlash: String? = null,
        val wasResetForNewPokemon: Boolean = false
    ) : CaptureStepOutcome()

    data class NoData(
        val draft: ActiveCaptureDraft,
        val persistentStatus: String?,
        val transientFlash: String
    ) : CaptureStepOutcome()
}
