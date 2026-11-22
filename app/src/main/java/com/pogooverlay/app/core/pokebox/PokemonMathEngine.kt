package com.pogooverlay.app.core.pokebox

import com.pogooverlay.app.core.data.PokemonSpecies
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

object PokemonMathEngine {

    // Exact Pokémon GO CPM values for levels 1.0 .. 51.0 (0.5 increments, 101 entries)
    private val CPM_TABLE = doubleArrayOf(
        0.094, 0.1351374318, 0.16639787, 0.192650919, 0.21573247,
        0.2365726613, 0.25572005, 0.2735303812, 0.29024988, 0.3060573775,
        0.3210876, 0.3354450362, 0.34921268, 0.3624577511, 0.3752356,
        0.387592416, 0.39956728, 0.4111935514, 0.4225, 0.4329264091,
        0.44310755, 0.4530599591, 0.4627984, 0.472336093, 0.48168495,
        0.4908558003, 0.49985844, 0.508701765, 0.51739395, 0.5259425113,
        0.5343543, 0.5426357375, 0.5507927, 0.5588305862, 0.5667545,
        0.5745691333, 0.5822789, 0.5898879072, 0.5974, 0.6048236651,
        0.6121573, 0.6194041216, 0.6265671, 0.6336491432, 0.64065295,
        0.6475809666, 0.65443563, 0.6612192524, 0.667934, 0.6745818959,
        0.6811649, 0.6876849038, 0.69414365, 0.70054287, 0.7068842,
        0.7131691091, 0.7193991, 0.7255756136, 0.7317, 0.7347410093,
        0.7377695, 0.7407855938, 0.74378943, 0.7467812109, 0.74976104,
        0.7527290867, 0.7556855, 0.7586303683, 0.76156384, 0.7644860647,
        0.76739717, 0.7702972656, 0.7731865, 0.7760649616, 0.77893275,
        0.7817900548, 0.784637, 0.7874736075, 0.7903, 0.7928,
        0.7953, 0.7978, 0.8003, 0.8028, 0.8053,
        0.8078, 0.8103, 0.8128, 0.81529999, 0.8178,
        0.82029999, 0.8228, 0.82529999, 0.8278, 0.83029999,
        0.8328, 0.83529999, 0.8378, 0.84029999, 0.8428,
        0.84529999
    )

    private data class StepCost(val dust: Int, val candy: Int, val xlCandy: Int)

    // Base cost for powering up FROM level (index * 0.5 + 1.0) -> +0.5
    private fun baseStepCost(fromLevel: Double): StepCost {
        val lv = ((fromLevel * 2.0).roundToInt()) / 2.0
        return when {
            lv < 3.0 -> StepCost(200, 1, 0)
            lv < 5.0 -> StepCost(400, 1, 0)
            lv < 7.0 -> StepCost(600, 1, 0)
            lv < 9.0 -> StepCost(800, 1, 0)
            lv < 11.0 -> StepCost(1000, 1, 0)
            lv < 13.0 -> StepCost(1300, 2, 0)
            lv < 15.0 -> StepCost(1600, 2, 0)
            lv < 17.0 -> StepCost(1900, 2, 0)
            lv < 19.0 -> StepCost(2200, 2, 0)
            lv < 21.0 -> StepCost(2500, 2, 0)
            lv < 23.0 -> StepCost(3000, 3, 0)
            lv < 25.0 -> StepCost(3500, 3, 0)
            lv < 26.0 -> StepCost(4000, 3, 0)
            lv < 27.0 -> StepCost(4000, 4, 0)
            lv < 29.0 -> StepCost(4500, 4, 0)
            lv < 31.0 -> StepCost(5000, 4, 0)
            lv < 33.0 -> StepCost(6000, 6, 0)
            lv < 35.0 -> StepCost(7000, 8, 0)
            lv < 37.0 -> StepCost(8000, 10, 0)
            lv < 39.0 -> StepCost(9000, 12, 0)
            lv < 40.0 -> StepCost(10000, 15, 0)
            lv < 41.0 -> StepCost(10000, 0, 10)
            lv < 42.0 -> StepCost(11000, 0, 10)
            lv < 43.0 -> StepCost(11000, 0, 12)
            lv < 44.0 -> StepCost(12000, 0, 12)
            lv < 45.0 -> StepCost(12000, 0, 15)
            lv < 46.0 -> StepCost(13000, 0, 15)
            lv < 47.0 -> StepCost(13000, 0, 17)
            lv < 48.0 -> StepCost(14000, 0, 17)
            lv < 49.0 -> StepCost(14000, 0, 20)
            else -> StepCost(15000, 0, 20)
        }
    }

    private fun applyModifiers(
        base: StepCost,
        isShadow: Boolean,
        isPurified: Boolean,
        isLucky: Boolean
    ): StepCost {
        var dustMult = 1.0
        var candyMult = 1.0
        if (isShadow) {
            dustMult *= 1.2
            candyMult *= 1.2
        } else if (isPurified) {
            dustMult *= 0.9
            candyMult *= 0.9
        }
        if (isLucky) {
            dustMult *= 0.5
        }
        return StepCost(
            dust = ceil(base.dust * dustMult).toInt(),
            candy = ceil(base.candy * candyMult).toInt(),
            xlCandy = ceil(base.xlCandy * candyMult).toInt()
        )
    }

    fun cpmForLevel(level: Double): Double {
        val clamped = level.coerceIn(1.0, 51.0)
        val idx = ((clamped - 1.0) * 2.0).roundToInt().coerceIn(0, CPM_TABLE.lastIndex)
        return CPM_TABLE[idx]
    }

    fun calculateCp(
        baseAtk: Int,
        baseDef: Int,
        baseSta: Int,
        ivAtk: Int,
        ivDef: Int,
        ivSta: Int,
        level: Double
    ): Int {
        if (baseAtk <= 0 || baseDef <= 0 || baseSta <= 0) return 10
        val cpm = cpmForLevel(level)
        val atk = (baseAtk + ivAtk).toDouble()
        val def = (baseDef + ivDef).toDouble()
        val sta = (baseSta + ivSta).toDouble()
        val raw = floor((atk * sqrt(def) * sqrt(sta) * cpm * cpm) / 10.0).toInt()
        return max(10, raw)
    }

    fun calculateHp(
        baseSta: Int,
        ivSta: Int,
        level: Double
    ): Int {
        if (baseSta <= 0) return 10
        val cpm = cpmForLevel(level)
        return max(10, floor((baseSta + ivSta) * cpm).toInt())
    }

    fun solveLevel(
        baseAtk: Int,
        baseDef: Int,
        baseSta: Int,
        ivAtk: Int,
        ivDef: Int,
        ivSta: Int,
        cp: Int,
        hp: Int? = null,
        powerUpDust: Int? = null,
        isShadow: Boolean = false,
        isPurified: Boolean = false,
        isLucky: Boolean = false
    ): Double {
        if (baseAtk <= 0 || baseDef <= 0 || baseSta <= 0 || cp <= 0) {
            return 20.0
        }
        var bestLevel = 20.0
        var bestScore = Double.MAX_VALUE

        for (step in 0..98) { // Levels 1.0 .. 50.0
            val lv = 1.0 + step * 0.5
            val predCp = calculateCp(baseAtk, baseDef, baseSta, ivAtk, ivDef, ivSta, lv)
            val cpDiff = abs(predCp - cp).toDouble()
            var score = cpDiff * 100.0

            if (hp != null && hp > 0) {
                val predHp = calculateHp(baseSta, ivSta, lv)
                score += abs(predHp - hp) * 20.0
            }

            if (powerUpDust != null && powerUpDust > 0 && lv < 50.0) {
                val expectedDust = applyModifiers(baseStepCost(lv), isShadow, isPurified, isLucky).dust
                if (expectedDust != powerUpDust) {
                    score += 50.0
                }
            }

            // Slight preference for whole levels on exact tie
            if ((lv * 2.0).roundToInt() % 2 != 0) {
                score += 0.01
            }

            if (score < bestScore) {
                bestScore = score
                bestLevel = lv
            }
        }
        return bestLevel
    }

    fun calculateUpgradeCost(
        species: PokemonSpecies,
        ivAtk: Int,
        ivDef: Int,
        ivSta: Int,
        currentLevel: Double,
        targetLevel: Double,
        isShadow: Boolean,
        isPurified: Boolean,
        isLucky: Boolean
    ): PowerUpCost {
        val fromStep = ((currentLevel.coerceIn(1.0, 50.0) - 1.0) * 2.0).roundToInt()
        val toStep = ((targetLevel.coerceIn(1.0, 50.0) - 1.0) * 2.0).roundToInt()

        var totalDust = 0
        var totalCandy = 0
        var totalXl = 0

        if (toStep > fromStep) {
            for (step in fromStep until toStep) {
                val lv = 1.0 + step * 0.5
                val modified = applyModifiers(baseStepCost(lv), isShadow, isPurified, isLucky)
                totalDust += modified.dust
                totalCandy += modified.candy
                totalXl += modified.xlCandy
            }
        }

        val normalizedTarget = 1.0 + max(fromStep, toStep) * 0.5
        return PowerUpCost(
            currentLevel = 1.0 + fromStep * 0.5,
            targetLevel = normalizedTarget,
            stardust = totalDust,
            candy = totalCandy,
            xlCandy = totalXl,
            targetCp = calculateCp(
                species.baseAtk, species.baseDef, species.baseSta,
                ivAtk, ivDef, ivSta, normalizedTarget
            ),
            targetHp = calculateHp(species.baseSta, ivSta, normalizedTarget)
        )
    }
}
