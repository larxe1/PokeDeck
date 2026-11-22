package com.pogooverlay.app.core.pokebox

import com.pogooverlay.app.core.data.PokemonSpecies
import com.pogooverlay.app.core.data.PokemonType
import com.pogooverlay.app.core.data.TypeEffectivenessChart
import kotlin.math.floor

object RaidBattleSimulator {

    private data class TierParams(val hp: Int, val timerSec: Int, val bossCpm: Double, val bossDps: Double)

    private fun tierParams(tier: Int): TierParams = when (tier) {
        1 -> TierParams(hp = 600, timerSec = 180, bossCpm = 0.5974, bossDps = 12.0)
        3 -> TierParams(hp = 3600, timerSec = 180, bossCpm = 0.73, bossDps = 22.0)
        4 -> TierParams(hp = 9000, timerSec = 300, bossCpm = 0.79, bossDps = 34.0)
        6 -> TierParams(hp = 22500, timerSec = 300, bossCpm = 0.79, bossDps = 48.0)
        else -> TierParams(hp = 15000, timerSec = 300, bossCpm = 0.79, bossDps = 40.0)
    }

    fun calculateCycleDps(
        attackerAtk: Double,
        defenderDef: Double,
        attackerTypes: List<PokemonType>,
        fast: MoveData,
        charged: MoveData,
        defenderTypes: List<PokemonType>,
        isShadow: Boolean,
        weatherBoostTypes: Set<PokemonType> = emptySet()
    ): Double {
        val shadowAtk = if (isShadow) 1.2 else 1.0
        val fastType = PokemonType.fromApiKey(fast.type)
        val chargedType = PokemonType.fromApiKey(charged.type)

        val fastStab = if (fastType != null && fastType in attackerTypes) 1.2 else 1.0
        val chargedStab = if (chargedType != null && chargedType in attackerTypes) 1.2 else 1.0

        val fastEff = if (fastType != null && defenderTypes.isNotEmpty()) {
            TypeEffectivenessChart.calculate(defenderTypes).let { summary ->
                summary.doubleWeaknesses.firstOrNull { it.attackType == fastType }?.multiplier
                    ?: summary.singleWeaknesses.firstOrNull { it.attackType == fastType }?.multiplier
                    ?: summary.resistances.firstOrNull { it.attackType == fastType }?.multiplier
                    ?: 1.0
            }
        } else 1.0

        val chargedEff = if (chargedType != null && defenderTypes.isNotEmpty()) {
            TypeEffectivenessChart.calculate(defenderTypes).let { summary ->
                summary.doubleWeaknesses.firstOrNull { it.attackType == chargedType }?.multiplier
                    ?: summary.singleWeaknesses.firstOrNull { it.attackType == chargedType }?.multiplier
                    ?: summary.resistances.firstOrNull { it.attackType == chargedType }?.multiplier
                    ?: 1.0
            }
        } else 1.0

        val fastWeather = if (fastType != null && fastType in weatherBoostTypes) 1.2 else 1.0
        val chargedWeather = if (chargedType != null && chargedType in weatherBoostTypes) 1.2 else 1.0

        val fastDmg = floor(0.5 * fast.power * (attackerAtk / defenderDef) * fastStab * fastEff * fastWeather * shadowAtk) + 1.0
        val chargedDmg = floor(0.5 * charged.power * (attackerAtk / defenderDef) * chargedStab * chargedEff * chargedWeather * shadowAtk) + 1.0

        val fastDurationSec = (fast.durationMs / 1000.0).coerceAtLeast(0.4)
        val chargedDurationSec = (charged.durationMs / 1000.0).coerceAtLeast(0.5)
        val energyCost = kotlin.math.abs(charged.energyDelta).coerceAtLeast(33).toDouble()
        val energyPerFast = fast.energyDelta.coerceAtLeast(1).toDouble()

        val fastCountPerCycle = (energyCost / energyPerFast).coerceAtLeast(1.0)
        val cycleDurationSec = fastCountPerCycle * fastDurationSec + chargedDurationSec
        val cycleDamage = fastCountPerCycle * fastDmg + chargedDmg

        return (cycleDamage / cycleDurationSec).coerceAtLeast(0.1)
    }

    fun evaluateMoveset(
        species: PokemonSpecies,
        movesById: Map<String, MoveData>,
        currentFastId: String?,
        currentCharged1Id: String?,
        currentCharged2Id: String?
    ): MovesetRecommendation? {
        val fastPool = species.quickMoves.mapNotNull { movesById[it] }.ifEmpty {
            currentFastId?.let { listOfNotNull(movesById[it]) } ?: emptyList()
        }
        val chargedPool = species.cinematicMoves.mapNotNull { movesById[it] }.ifEmpty {
            listOfNotNull(currentCharged1Id?.let { movesById[it] }, currentCharged2Id?.let { movesById[it] })
        }
        if (fastPool.isEmpty() || chargedPool.isEmpty()) return null

        val atk = (species.baseAtk.coerceAtLeast(100) + 15).toDouble() * 0.7903
        val neutralDef = 180.0 * 0.7903
        val ownTypes = species.parsedTypes()

        var bestPair: Pair<MoveData, MoveData>? = null
        var bestDps = -1.0

        for (f in fastPool) {
            for (c in chargedPool) {
                val dps = calculateCycleDps(atk, neutralDef, ownTypes, f, c, emptyList(), isShadow = false)
                if (dps > bestDps) {
                    bestDps = dps
                    bestPair = f to c
                }
            }
        }
        val (bestFast, bestCharged) = bestPair ?: return null

        val savedFast = currentFastId?.let { movesById[it] }
        val savedChargeds = listOfNotNull(
            currentCharged1Id?.let { movesById[it] },
            currentCharged2Id?.let { movesById[it] }
        )

        val currentDps = if (savedFast != null && savedChargeds.isNotEmpty()) {
            savedChargeds.maxOf { c ->
                calculateCycleDps(atk, neutralDef, ownTypes, savedFast, c, emptyList(), isShadow = false)
            }
        } else null

        val isOptimal = currentDps != null && currentDps >= bestDps * 0.96
        val text = when {
            currentDps == null -> "Best PvE: ${bestFast.displayName} + ${bestCharged.displayName}"
            isOptimal -> "Optimal Moveset"
            else -> "TM → ${bestFast.displayName} + ${bestCharged.displayName}"
        }

        return MovesetRecommendation(
            bestFast = bestFast,
            bestCharged = bestCharged,
            bestNeutralDps = bestDps,
            currentNeutralDps = currentDps,
            isCurrentOptimal = isOptimal,
            recommendationText = text
        )
    }

    fun simulatePersonalTeam(
        savedBox: List<SavedPokemon>,
        speciesById: Map<String, PokemonSpecies>,
        movesById: Map<String, MoveData>,
        bossSpecies: PokemonSpecies,
        raidTier: Int = 5,
        weatherBoostTypes: Set<PokemonType> = emptySet()
    ): RaidWinnabilityVerdict {
        val params = tierParams(raidTier)
        if (savedBox.isEmpty()) {
            return RaidWinnabilityVerdict(
                bossName = bossSpecies.speciesName,
                raidTier = raidTier,
                bossHp = params.hp,
                timerSec = params.timerSec,
                team = emptyList(),
                teamEstimator = 99.0,
                totalDamagePercent = 0.0,
                verdictLabel = "No Saved Pokémon",
                verdictDetail = "Save Pokémon in My Pokébox to simulate your personal team."
            )
        }

        val bossTypes = bossSpecies.parsedTypes()
        val bossEffDef = ((bossSpecies.baseDef.coerceAtLeast(120) + 15) * params.bossCpm).coerceAtLeast(50.0)
        val bossStatScale = ((bossSpecies.baseAtk.coerceAtLeast(150) + 15) * params.bossCpm / 190.0).coerceIn(0.65, 1.85)
        val adjustedBossDps = params.bossDps * bossStatScale

        val evaluated = savedBox.mapNotNull { saved ->
            val sp = speciesById[saved.speciesId] ?: return@mapNotNull null
            val spTypes = sp.parsedTypes()
            val cpm = PokemonMathEngine.cpmForLevel(saved.level)
            val effAtk = ((sp.baseAtk.coerceAtLeast(100) + saved.ivAtk) * cpm).coerceAtLeast(20.0)
            val effDef = ((sp.baseDef.coerceAtLeast(100) + saved.ivDef) * cpm * if (saved.isShadow) 0.833333 else 1.0).coerceAtLeast(20.0)
            val hp = saved.hp?.takeIf { it > 0 } ?: PokemonMathEngine.calculateHp(sp.baseSta, saved.ivSta, saved.level)

            val fastPool = sp.quickMoves.mapNotNull { movesById[it] }
            val chargedPool = sp.cinematicMoves.mapNotNull { movesById[it] }

            // Find theoretical optimal moveset for this species vs this specific raid boss
            var optFast: MoveData? = null
            var optCharged: MoveData? = null
            var optDps = 0.0
            for (f in fastPool) {
                for (c in chargedPool) {
                    val d = calculateCycleDps(effAtk, bossEffDef, spTypes, f, c, bossTypes, saved.isShadow, weatherBoostTypes)
                    if (d > optDps) {
                        optDps = d
                        optFast = f
                        optCharged = c
                    }
                }
            }

            // Evaluate user's actual saved moves (or fall back to optimal if user hasn't picked moves yet)
            val userFast = saved.fastMoveId?.let { movesById[it] }
            val userChargeds = listOfNotNull(
                saved.chargedMove1Id?.let { movesById[it] },
                saved.chargedMove2Id?.let { movesById[it] }
            )

            var usedFast = userFast ?: optFast
            var usedCharged = optCharged
            var actualDps = optDps

            if (userFast != null && userChargeds.isNotEmpty()) {
                var bestSavedCharged = userChargeds.first()
                var bestSavedDps = -1.0
                for (c in userChargeds) {
                    val d = calculateCycleDps(effAtk, bossEffDef, spTypes, userFast, c, bossTypes, saved.isShadow, weatherBoostTypes)
                    if (d > bestSavedDps) {
                        bestSavedDps = d
                        bestSavedCharged = c
                    }
                }
                usedFast = userFast
                usedCharged = bestSavedCharged
                actualDps = bestSavedDps
            } else if (usedFast != null && usedCharged != null) {
                actualDps = calculateCycleDps(effAtk, bossEffDef, spTypes, usedFast, usedCharged, bossTypes, saved.isShadow, weatherBoostTypes)
            } else {
                // Species has no move data at all -> baseline neutral DPS estimate
                actualDps = (effAtk / bossEffDef) * 14.0
            }

            // Estimate attacker survival time & TDO
            val incomingDps = (adjustedBossDps * (120.0 / effDef)).coerceAtLeast(3.0)
            val survivalSec = (hp / incomingDps).coerceIn(6.0, params.timerSec.toDouble())
            val tdo = actualDps * survivalSec

            // Pokébattler-like Estimator: time to deal bossHp including faint/relobby overhead
            val faintsNeeded = (params.hp / tdo.coerceAtLeast(1.0))
            val relobbySec = floor(faintsNeeded / 6.0) * 12.0 + faintsNeeded * 0.6
            val timeToWin = (params.hp / actualDps.coerceAtLeast(0.1)) + relobbySec
            val estimator = (timeToWin / params.timerSec.toDouble()).coerceIn(0.25, 99.0)
            val dmgPct = (100.0 / estimator).coerceIn(0.5, 200.0)

            val suboptimal = (userFast != null && userChargeds.isNotEmpty() && optDps > actualDps * 1.08 && optFast != null && optCharged != null)
            val hint = if (suboptimal && optFast != null && optCharged != null) {
                "TM to ${optFast.displayName} + ${optCharged.displayName}"
            } else null

            PersonalCounterResult(
                pokemon = saved,
                species = sp,
                fastMove = usedFast,
                chargedMove = usedCharged,
                dps = actualDps,
                tdo = tdo,
                estimator = estimator,
                damagePercentOfBoss = dmgPct,
                usingSuboptimalMoves = suboptimal,
                recommendedMoveHint = hint
            )
        }.sortedWith(compareBy<PersonalCounterResult> { it.estimator }.thenByDescending { it.dps })

        val topTeam = evaluated.take(6)
        if (topTeam.isEmpty()) {
            return RaidWinnabilityVerdict(
                bossName = bossSpecies.speciesName,
                raidTier = raidTier,
                bossHp = params.hp,
                timerSec = params.timerSec,
                team = emptyList(),
                teamEstimator = 99.0,
                totalDamagePercent = 0.0,
                verdictLabel = "No Match",
                verdictDetail = "Could not match saved Pokémon to database."
            )
        }

        val avgDps = topTeam.map { it.dps }.average()
        val teamTdo = topTeam.sumOf { it.tdo }
        val teamRelobbies = floor(params.hp / teamTdo.coerceAtLeast(1.0))
        val teamFightTime = (params.hp / avgDps.coerceAtLeast(0.1)) + teamRelobbies * 12.0
        val teamEstimator = (teamFightTime / params.timerSec.toDouble()).coerceIn(0.2, 99.0)
        val totalDamagePct = (100.0 / teamEstimator).coerceIn(1.0, 200.0)

        val label = when {
            teamEstimator <= 1.0 -> "Soloable"
            teamEstimator <= 1.5 -> "Easy Duo"
            teamEstimator <= 2.0 -> "Duo / Easy Trio"
            teamEstimator <= 3.0 -> "Winnable (3 Trainers)"
            teamEstimator <= 5.0 -> "Hard (4–5 Trainers)"
            else -> "Large Group (6+)"
        }
        val detail = String.format(
            "Est. %.2f • Your team deals ~%.0f%% of T%d boss HP",
            teamEstimator,
            totalDamagePct,
            raidTier
        )

        return RaidWinnabilityVerdict(
            bossName = bossSpecies.speciesName,
            raidTier = raidTier,
            bossHp = params.hp,
            timerSec = params.timerSec,
            team = topTeam,
            teamEstimator = teamEstimator,
            totalDamagePercent = totalDamagePct,
            verdictLabel = label,
            verdictDetail = detail
        )
    }
}
