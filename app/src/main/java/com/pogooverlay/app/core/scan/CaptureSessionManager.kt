package com.pogooverlay.app.core.scan

import com.pogooverlay.app.core.data.PokemonSpecies
import com.pogooverlay.app.core.pokebox.PokemonMathEngine
import com.pogooverlay.app.core.pokebox.SavedPokemon
import java.util.UUID

class CaptureSessionManager(
    private val onSavePokemon: (SavedPokemon) -> Unit
) {
    var currentDraft: ActiveCaptureDraft = ActiveCaptureDraft()
        private set

    fun reset() {
        currentDraft = ActiveCaptureDraft()
    }

    fun processFrame(extraction: ScanFrameExtraction): CaptureStepOutcome {
        val hasExistingIdentity = currentDraft.speciesId != null || currentDraft.cp != null
        val isDifferentCp = extraction.cp != null &&
            currentDraft.cp != null &&
            extraction.cp != currentDraft.cp
        val isDifferentDex = extraction.species != null &&
            currentDraft.species != null &&
            extraction.species.dex != currentDraft.species!!.dex

        val wasResetForNewPokemon = hasExistingIdentity && (isDifferentCp || isDifferentDex)
        val baseDraft = if (wasResetForNewPokemon) ActiveCaptureDraft() else currentDraft

        val mergedSpecies = resolveMergedSpecies(baseDraft.species, extraction)
        val mergedMoveset = if (extraction.movesetResult.status == MovesetParseStatus.COMPLETE) {
            extraction.movesetResult.moveset
        } else {
            baseDraft.movesetData
        }

        val mergedDraft = ActiveCaptureDraft(
            speciesId = mergedSpecies?.speciesId ?: extraction.speciesId ?: baseDraft.speciesId,
            species = mergedSpecies,
            cp = extraction.cp ?: baseDraft.cp,
            hp = extraction.hp ?: baseDraft.hp,
            nickname = baseDraft.nickname ?: extraction.nickname,
            isShadow = baseDraft.isShadow || extraction.isShadow,
            isPurified = baseDraft.isPurified || extraction.isPurified,
            isLucky = baseDraft.isLucky || extraction.isLucky,
            ivData = extraction.ivData ?: baseDraft.ivData,
            movesetData = mergedMoveset
        )

        currentDraft = mergedDraft

        if (extraction.movesetResult.status == MovesetParseStatus.INCOMPLETE_CUTOFF &&
            mergedDraft.movesetData == null
        ) {
            val persistent = persistentStatusFor(mergedDraft) ?: "Scroll to moves & tap"
            return CaptureStepOutcome.InProgress(
                draft = mergedDraft,
                persistentStatus = persistent,
                transientFlash = "Scroll slightly lower to confirm 2nd move & tap",
                wasResetForNewPokemon = wasResetForNewPokemon
            )
        }

        if (mergedDraft.isComplete) {
            val species = mergedDraft.species!!
            val iv = mergedDraft.ivData!!
            val moveset = mergedDraft.movesetData!!

            val resolvedCp = mergedDraft.cp ?: PokemonMathEngine.calculateCp(
                baseAtk = species.baseAtk,
                baseDef = species.baseDef,
                baseSta = species.baseSta,
                ivAtk = iv.atk,
                ivDef = iv.def,
                ivSta = iv.sta,
                level = 20.0
            )
            val solvedLevel = if (mergedDraft.cp != null && mergedDraft.cp > 0) {
                PokemonMathEngine.solveLevel(
                    baseAtk = species.baseAtk,
                    baseDef = species.baseDef,
                    baseSta = species.baseSta,
                    ivAtk = iv.atk,
                    ivDef = iv.def,
                    ivSta = iv.sta,
                    cp = mergedDraft.cp,
                    hp = mergedDraft.hp,
                    isShadow = mergedDraft.isShadow,
                    isPurified = mergedDraft.isPurified,
                    isLucky = mergedDraft.isLucky
                )
            } else {
                20.0
            }

            val saved = SavedPokemon(
                id = UUID.randomUUID().toString(),
                speciesId = species.speciesId,
                pokebattlerId = species.pokebattlerId ?: species.speciesId.uppercase(),
                nickname = mergedDraft.nickname,
                cp = resolvedCp,
                hp = mergedDraft.hp,
                ivAtk = iv.atk,
                ivDef = iv.def,
                ivSta = iv.sta,
                level = solvedLevel,
                isShadow = mergedDraft.isShadow,
                isPurified = mergedDraft.isPurified,
                isLucky = mergedDraft.isLucky,
                isMegaCapable = species.tags.any { it.equals("mega", ignoreCase = true) },
                fastMoveId = moveset.fastMove.moveId,
                chargedMove1Id = moveset.chargedMove1.moveId,
                chargedMove2Id = moveset.chargedMove2?.moveId,
                savedAtMs = System.currentTimeMillis()
            )

            onSavePokemon(saved)
            currentDraft = ActiveCaptureDraft()

            return CaptureStepOutcome.Saved(
                pokemon = saved,
                flashMessage = "Saved ${species.speciesName} (${iv.atk}/${iv.def}/${iv.sta})"
            )
        }

        val capturedDataThisTap = extraction.ivData != null ||
            extraction.movesetResult.status != MovesetParseStatus.NONE_FOUND

        if (!capturedDataThisTap) {
            return when {
                mergedDraft.ivData != null -> CaptureStepOutcome.NoData(
                    draft = mergedDraft,
                    persistentStatus = "IVs ✓ · Scroll to moves & tap",
                    transientFlash = "Moves not detected — scroll down & tap"
                )
                mergedDraft.movesetData != null -> CaptureStepOutcome.NoData(
                    draft = mergedDraft,
                    persistentStatus = "Moves ✓ · Open Appraisal & tap",
                    transientFlash = "Open Appraisal screen & tap"
                )
                else -> CaptureStepOutcome.NoData(
                    draft = mergedDraft,
                    persistentStatus = null,
                    transientFlash = "Open Appraisal (or scroll to moves) & tap"
                )
            }
        }

        return when {
            mergedDraft.ivData != null && mergedDraft.movesetData == null -> {
                CaptureStepOutcome.InProgress(
                    draft = mergedDraft,
                    persistentStatus = "IVs ✓ · Scroll to moves & tap",
                    transientFlash = if (wasResetForNewPokemon) "New Pokémon detected" else null,
                    wasResetForNewPokemon = wasResetForNewPokemon
                )
            }
            mergedDraft.movesetData != null && mergedDraft.ivData == null -> {
                CaptureStepOutcome.InProgress(
                    draft = mergedDraft,
                    persistentStatus = "Moves ✓ · Open Appraisal & tap",
                    transientFlash = if (wasResetForNewPokemon) "New Pokémon detected" else null,
                    wasResetForNewPokemon = wasResetForNewPokemon
                )
            }
            else -> {
                // Both IVs and moveset present, but species is still missing
                CaptureStepOutcome.InProgress(
                    draft = mergedDraft,
                    persistentStatus = "Species not detected — scroll up & tap",
                    transientFlash = if (wasResetForNewPokemon) "New Pokémon detected" else null,
                    wasResetForNewPokemon = wasResetForNewPokemon
                )
            }
        }
    }

    private fun resolveMergedSpecies(
        existing: PokemonSpecies?,
        extraction: ScanFrameExtraction
    ): PokemonSpecies? {
        val incoming = extraction.species ?: return existing
        if (existing == null) return incoming
        if (incoming.dex != existing.dex) return existing
        if (incoming.formLabel() != null && existing.formLabel() == null) return incoming
        if (incoming.speciesId != existing.speciesId &&
            extraction.movesetResult.status == MovesetParseStatus.COMPLETE
        ) {
            return incoming
        }
        return existing
    }

    private fun persistentStatusFor(draft: ActiveCaptureDraft): String? {
        return when {
            draft.ivData != null && draft.movesetData == null -> "IVs ✓ · Scroll to moves & tap"
            draft.movesetData != null && draft.ivData == null -> "Moves ✓ · Open Appraisal & tap"
            else -> null
        }
    }
}
