package com.pogooverlay.app.core.data

import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

object TypeEffectivenessChart {

    private const val SE = 1
    private const val NVE = -1
    private const val IMM = -2
    private const val NEUTRAL = 0

    /**
     * Exact Pokémon GO 18x18 effectiveness exponent table:
     * - Super Effective = +1 (1.6x)
     * - Not Very Effective = -1 (0.625x)
     * - Immunity = -2 (0.390625x)
     * - Neutral = 0 (1.0x)
     *
     * Because 1.6 = 8/5 and 0.625 = 5/8 = 1.6^-1, dual-type matchups are exact
     * integer sums of exponents (e.g. +1 + -1 = 0 -> exact 1.0x cancellation).
     */
    private val chart: Map<PokemonType, Map<PokemonType, Int>> = mapOf(
        PokemonType.NORMAL to mapOf(
            PokemonType.ROCK to NVE,
            PokemonType.STEEL to NVE,
            PokemonType.GHOST to IMM
        ),
        PokemonType.FIRE to mapOf(
            PokemonType.GRASS to SE,
            PokemonType.ICE to SE,
            PokemonType.BUG to SE,
            PokemonType.STEEL to SE,
            PokemonType.FIRE to NVE,
            PokemonType.WATER to NVE,
            PokemonType.ROCK to NVE,
            PokemonType.DRAGON to NVE
        ),
        PokemonType.WATER to mapOf(
            PokemonType.FIRE to SE,
            PokemonType.GROUND to SE,
            PokemonType.ROCK to SE,
            PokemonType.WATER to NVE,
            PokemonType.GRASS to NVE,
            PokemonType.DRAGON to NVE
        ),
        PokemonType.ELECTRIC to mapOf(
            PokemonType.WATER to SE,
            PokemonType.FLYING to SE,
            PokemonType.ELECTRIC to NVE,
            PokemonType.GRASS to NVE,
            PokemonType.DRAGON to NVE,
            PokemonType.GROUND to IMM
        ),
        PokemonType.GRASS to mapOf(
            PokemonType.WATER to SE,
            PokemonType.GROUND to SE,
            PokemonType.ROCK to SE,
            PokemonType.FIRE to NVE,
            PokemonType.GRASS to NVE,
            PokemonType.POISON to NVE,
            PokemonType.FLYING to NVE,
            PokemonType.BUG to NVE,
            PokemonType.DRAGON to NVE,
            PokemonType.STEEL to NVE
        ),
        PokemonType.ICE to mapOf(
            PokemonType.GRASS to SE,
            PokemonType.GROUND to SE,
            PokemonType.FLYING to SE,
            PokemonType.DRAGON to SE,
            PokemonType.FIRE to NVE,
            PokemonType.WATER to NVE,
            PokemonType.ICE to NVE,
            PokemonType.STEEL to NVE
        ),
        PokemonType.FIGHTING to mapOf(
            PokemonType.NORMAL to SE,
            PokemonType.ICE to SE,
            PokemonType.ROCK to SE,
            PokemonType.DARK to SE,
            PokemonType.STEEL to SE,
            PokemonType.POISON to NVE,
            PokemonType.FLYING to NVE,
            PokemonType.PSYCHIC to NVE,
            PokemonType.BUG to NVE,
            PokemonType.FAIRY to NVE,
            PokemonType.GHOST to IMM
        ),
        PokemonType.POISON to mapOf(
            PokemonType.GRASS to SE,
            PokemonType.FAIRY to SE,
            PokemonType.POISON to NVE,
            PokemonType.GROUND to NVE,
            PokemonType.ROCK to NVE,
            PokemonType.GHOST to NVE,
            PokemonType.STEEL to IMM
        ),
        PokemonType.GROUND to mapOf(
            PokemonType.FIRE to SE,
            PokemonType.ELECTRIC to SE,
            PokemonType.POISON to SE,
            PokemonType.ROCK to SE,
            PokemonType.STEEL to SE,
            PokemonType.GRASS to NVE,
            PokemonType.BUG to NVE,
            PokemonType.FLYING to IMM
        ),
        PokemonType.FLYING to mapOf(
            PokemonType.GRASS to SE,
            PokemonType.FIGHTING to SE,
            PokemonType.BUG to SE,
            PokemonType.ELECTRIC to NVE,
            PokemonType.ROCK to NVE,
            PokemonType.STEEL to NVE
        ),
        PokemonType.PSYCHIC to mapOf(
            PokemonType.FIGHTING to SE,
            PokemonType.POISON to SE,
            PokemonType.PSYCHIC to NVE,
            PokemonType.STEEL to NVE,
            PokemonType.DARK to IMM
        ),
        PokemonType.BUG to mapOf(
            PokemonType.GRASS to SE,
            PokemonType.PSYCHIC to SE,
            PokemonType.DARK to SE,
            PokemonType.FIRE to NVE,
            PokemonType.FIGHTING to NVE,
            PokemonType.POISON to NVE,
            PokemonType.FLYING to NVE,
            PokemonType.GHOST to NVE,
            PokemonType.STEEL to NVE,
            PokemonType.FAIRY to NVE
        ),
        PokemonType.ROCK to mapOf(
            PokemonType.FIRE to SE,
            PokemonType.ICE to SE,
            PokemonType.FLYING to SE,
            PokemonType.BUG to SE,
            PokemonType.FIGHTING to NVE,
            PokemonType.GROUND to NVE,
            PokemonType.STEEL to NVE
        ),
        PokemonType.GHOST to mapOf(
            PokemonType.PSYCHIC to SE,
            PokemonType.GHOST to SE,
            PokemonType.DARK to NVE,
            PokemonType.NORMAL to IMM
        ),
        PokemonType.DRAGON to mapOf(
            PokemonType.DRAGON to SE,
            PokemonType.STEEL to NVE,
            PokemonType.FAIRY to IMM
        ),
        PokemonType.DARK to mapOf(
            PokemonType.PSYCHIC to SE,
            PokemonType.GHOST to SE,
            PokemonType.FIGHTING to NVE,
            PokemonType.DARK to NVE,
            PokemonType.FAIRY to NVE
        ),
        PokemonType.STEEL to mapOf(
            PokemonType.ICE to SE,
            PokemonType.ROCK to SE,
            PokemonType.FAIRY to SE,
            PokemonType.FIRE to NVE,
            PokemonType.WATER to NVE,
            PokemonType.ELECTRIC to NVE,
            PokemonType.STEEL to NVE
        ),
        PokemonType.FAIRY to mapOf(
            PokemonType.FIGHTING to SE,
            PokemonType.DRAGON to SE,
            PokemonType.DARK to SE,
            PokemonType.FIRE to NVE,
            PokemonType.POISON to NVE,
            PokemonType.STEEL to NVE
        )
    )

    fun singleTier(attackType: PokemonType, defendingType: PokemonType): Int =
        chart[attackType]?.get(defendingType) ?: NEUTRAL

    fun tierToMultiplier(tier: Int): Double = when (tier) {
        2 -> 2.56
        1 -> 1.6
        0 -> 1.0
        -1 -> 0.625
        -2 -> 0.390625
        -3 -> 0.244140625
        -4 -> 0.152587890625
        else -> if (tier > 0) 1.6.pow(tier) else 0.625.pow(-tier)
    }

    fun formatMultiplier(multiplier: Double): String = when {
        abs(multiplier - 2.56) < 1e-6 -> "2.56×"
        abs(multiplier - 1.6) < 1e-6 -> "1.6×"
        abs(multiplier - 1.0) < 1e-6 -> "1×"
        abs(multiplier - 0.625) < 1e-6 -> "0.625×"
        abs(multiplier - 0.390625) < 1e-6 -> "0.39×"
        abs(multiplier - 0.244140625) < 1e-6 -> "0.244×"
        abs(multiplier - 0.152587890625) < 1e-6 -> "0.153×"
        else -> {
            val formatted = String.format(Locale.US, "%.3f", multiplier)
                .trimEnd('0')
                .trimEnd('.')
            "${formatted}×"
        }
    }

    fun calculate(defendingTypes: List<PokemonType>): MatchupSummary {
        val uniqueDefending = defendingTypes.distinct()
        if (uniqueDefending.isEmpty()) {
            return MatchupSummary(
                defendingTypes = emptyList(),
                doubleWeaknesses = emptyList(),
                singleWeaknesses = emptyList(),
                resistances = emptyList()
            )
        }

        val doubleWeaknesses = mutableListOf<TypeMatchup>()
        val singleWeaknesses = mutableListOf<TypeMatchup>()
        val resistances = mutableListOf<TypeMatchup>()

        for (attackType in PokemonType.entries) {
            val totalTier = uniqueDefending.sumOf { singleTier(attackType, it) }
            if (totalTier == 0) continue

            val matchup = TypeMatchup(
                attackType = attackType,
                multiplier = tierToMultiplier(totalTier),
                tier = totalTier
            )
            when {
                totalTier >= 2 -> doubleWeaknesses.add(matchup)
                totalTier == 1 -> singleWeaknesses.add(matchup)
                totalTier <= -1 -> resistances.add(matchup)
            }
        }

        doubleWeaknesses.sortWith(
            compareByDescending<TypeMatchup> { it.tier }
                .thenBy { it.attackType.displayName }
        )
        singleWeaknesses.sortBy { it.attackType.displayName }
        resistances.sortWith(
            compareBy<TypeMatchup> { it.tier }
                .thenBy { it.attackType.displayName }
        )

        return MatchupSummary(
            defendingTypes = uniqueDefending,
            doubleWeaknesses = doubleWeaknesses,
            singleWeaknesses = singleWeaknesses,
            resistances = resistances
        )
    }
}
