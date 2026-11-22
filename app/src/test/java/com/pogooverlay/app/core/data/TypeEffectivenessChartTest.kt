package com.pogooverlay.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeEffectivenessChartTest {

    @Test
    fun swampert_hasDoubleGrassWeaknessOnly() {
        val summary = TypeEffectivenessChart.calculate(listOf(PokemonType.WATER, PokemonType.GROUND))

        assertEquals(listOf(PokemonType.GRASS), summary.doubleWeaknesses.map { it.attackType })
        assertEquals(2.56, summary.doubleWeaknesses.first().multiplier, 1e-9)
        assertEquals(2, summary.doubleWeaknesses.first().tier)
        assertTrue(summary.singleWeaknesses.isEmpty())
        assertEquals(summary.doubleWeaknesses, summary.orderedWeaknesses)

        // Electric is SE (+1) vs Water and IMM (-2) vs Ground => net tier -1 (0.625x)
        val electric = summary.resistances.first { it.attackType == PokemonType.ELECTRIC }
        assertEquals(-1, electric.tier)
        assertEquals(0.625, electric.multiplier, 1e-9)
    }

    @Test
    fun tyranitar_hasDoubleFightingWeaknessAndCorrectOrdering() {
        val summary = TypeEffectivenessChart.calculate(listOf(PokemonType.ROCK, PokemonType.DARK))

        assertEquals(listOf(PokemonType.FIGHTING), summary.doubleWeaknesses.map { it.attackType })
        assertEquals(2.56, summary.doubleWeaknesses.first().multiplier, 1e-9)

        assertEquals(
            listOf(
                PokemonType.BUG,
                PokemonType.FAIRY,
                PokemonType.GRASS,
                PokemonType.GROUND,
                PokemonType.STEEL,
                PokemonType.WATER
            ),
            summary.singleWeaknesses.map { it.attackType }
        )

        // Double weaknesses always precede single weaknesses in orderedWeaknesses
        assertEquals(PokemonType.FIGHTING, summary.orderedWeaknesses.first().attackType)
        assertEquals(7, summary.orderedWeaknesses.size)

        // Psychic is IMM (-2) vs Dark => 0.390625x and sorted first among resistances
        val psychic = summary.resistances.first()
        assertEquals(PokemonType.PSYCHIC, psychic.attackType)
        assertEquals(-2, psychic.tier)
        assertEquals(0.390625, psychic.multiplier, 1e-9)
    }

    @Test
    fun charizard_hasDoubleRockWeaknessAndGroundSingleResistance() {
        val summary = TypeEffectivenessChart.calculate(listOf(PokemonType.FIRE, PokemonType.FLYING))

        assertEquals(listOf(PokemonType.ROCK), summary.doubleWeaknesses.map { it.attackType })
        assertEquals(2.56, summary.doubleWeaknesses.first().multiplier, 1e-9)
        assertEquals(
            listOf(PokemonType.ELECTRIC, PokemonType.WATER),
            summary.singleWeaknesses.map { it.attackType }
        )

        // Ground is SE (+1) vs Fire and IMM (-2) vs Flying => tier -1 (0.625x)
        val ground = summary.resistances.first { it.attackType == PokemonType.GROUND }
        assertEquals(-1, ground.tier)
        assertEquals(0.625, ground.multiplier, 1e-9)

        // Grass and Bug are double resisted (-2)
        val doubleResisted = summary.resistances.filter { it.tier == -2 }.map { it.attackType }
        assertEquals(listOf(PokemonType.BUG, PokemonType.GRASS), doubleResisted)
    }

    @Test
    fun megaGyarados_vsRegularGyarados_changesWeaknessProfile() {
        val regular = TypeEffectivenessChart.calculate(listOf(PokemonType.WATER, PokemonType.FLYING))
        val mega = TypeEffectivenessChart.calculate(listOf(PokemonType.WATER, PokemonType.DARK))

        // Regular Gyarados: 2.56x Electric, 1.6x Rock
        assertEquals(listOf(PokemonType.ELECTRIC), regular.doubleWeaknesses.map { it.attackType })
        assertEquals(listOf(PokemonType.ROCK), regular.singleWeaknesses.map { it.attackType })

        // Mega Gyarados: no double weakness; 1.6x Bug, Electric, Fairy, Fighting, Grass
        assertTrue(mega.doubleWeaknesses.isEmpty())
        assertEquals(
            listOf(
                PokemonType.BUG,
                PokemonType.ELECTRIC,
                PokemonType.FAIRY,
                PokemonType.FIGHTING,
                PokemonType.GRASS
            ),
            mega.singleWeaknesses.map { it.attackType }
        )
    }

    @Test
    fun tripleResistance_isExact0244() {
        // Ghost vs Normal/Dark: IMM (-2) + NVE (-1) = -3 => 0.244140625x ("0.244×")
        val normalDark = TypeEffectivenessChart.calculate(listOf(PokemonType.NORMAL, PokemonType.DARK))
        val ghostMatchup = normalDark.resistances.first { it.attackType == PokemonType.GHOST }
        assertEquals(-3, ghostMatchup.tier)
        assertEquals(0.244140625, ghostMatchup.multiplier, 1e-12)
        assertEquals("0.244×", TypeEffectivenessChart.formatMultiplier(ghostMatchup.multiplier))
        // Triple resistance must appear first in sorted resistances
        assertEquals(PokemonType.GHOST, normalDark.resistances.first().attackType)

        // Normal vs Ghost/Rock: IMM (-2) + NVE (-1) = -3 => 0.244140625x
        val ghostRock = TypeEffectivenessChart.calculate(listOf(PokemonType.GHOST, PokemonType.ROCK))
        val normalMatchup = ghostRock.resistances.first { it.attackType == PokemonType.NORMAL }
        assertEquals(-3, normalMatchup.tier)
        assertEquals(0.244140625, normalMatchup.multiplier, 1e-12)
    }

    @Test
    fun superEffectiveAndNotVeryEffective_cancelExactlyToNeutral() {
        // Fire vs Water/Grass: Fire is NVE (-1) vs Water and SE (+1) vs Grass => tier 0 (1.0x)
        val ludicolo = TypeEffectivenessChart.calculate(listOf(PokemonType.WATER, PokemonType.GRASS))
        val allNonNeutralTypes = (ludicolo.orderedWeaknesses + ludicolo.resistances).map { it.attackType }
        assertTrue(PokemonType.FIRE !in allNonNeutralTypes)

        // Ice vs Water/Dragon: Ice is NVE (-1) vs Water and SE (+1) vs Dragon => tier 0 (1.0x)
        val palkia = TypeEffectivenessChart.calculate(listOf(PokemonType.WATER, PokemonType.DRAGON))
        val palkiaNonNeutral = (palkia.orderedWeaknesses + palkia.resistances).map { it.attackType }
        assertTrue(PokemonType.ICE !in palkiaNonNeutral)
        assertEquals(1.0, TypeEffectivenessChart.tierToMultiplier(0), 1e-12)
    }

    @Test
    fun formatMultiplier_matchesExactPoGoLabels() {
        assertEquals("2.56×", TypeEffectivenessChart.formatMultiplier(TypeEffectivenessChart.tierToMultiplier(2)))
        assertEquals("1.6×", TypeEffectivenessChart.formatMultiplier(TypeEffectivenessChart.tierToMultiplier(1)))
        assertEquals("1×", TypeEffectivenessChart.formatMultiplier(TypeEffectivenessChart.tierToMultiplier(0)))
        assertEquals("0.625×", TypeEffectivenessChart.formatMultiplier(TypeEffectivenessChart.tierToMultiplier(-1)))
        assertEquals("0.39×", TypeEffectivenessChart.formatMultiplier(TypeEffectivenessChart.tierToMultiplier(-2)))
        assertEquals("0.244×", TypeEffectivenessChart.formatMultiplier(TypeEffectivenessChart.tierToMultiplier(-3)))
        assertEquals("0.153×", TypeEffectivenessChart.formatMultiplier(TypeEffectivenessChart.tierToMultiplier(-4)))
    }
}
