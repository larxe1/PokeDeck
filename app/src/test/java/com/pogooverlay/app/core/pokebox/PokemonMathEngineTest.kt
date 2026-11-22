package com.pogooverlay.app.core.pokebox

import com.pogooverlay.app.core.data.PokemonSpecies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PokemonMathEngineTest {

    private val mewtwo = PokemonSpecies(
        speciesId = "mewtwo",
        speciesName = "Mewtwo",
        dex = 150,
        types = listOf("psychic"),
        pokebattlerId = "MEWTWO",
        baseAtk = 300,
        baseDef = 182,
        baseSta = 214
    )

    @Test
    fun `calculateCp and calculateHp match exact Pokemon GO formulas at Lv 40 and Lv 50`() {
        val cp40 = PokemonMathEngine.calculateCp(300, 182, 214, 15, 15, 15, 40.0)
        val hp40 = PokemonMathEngine.calculateHp(214, 15, 40.0)
        val cp50 = PokemonMathEngine.calculateCp(300, 182, 214, 15, 15, 15, 50.0)

        assertEquals(4178, cp40)
        assertEquals(180, hp40)
        assertEquals(4724, cp50)
    }

    @Test
    fun `solveLevel recovers exact level from CP HP and power up stardust`() {
        val cp325 = PokemonMathEngine.calculateCp(300, 182, 214, 15, 14, 15, 32.5)
        val hp325 = PokemonMathEngine.calculateHp(214, 15, 32.5)

        val solved = PokemonMathEngine.solveLevel(
            baseAtk = 300,
            baseDef = 182,
            baseSta = 214,
            ivAtk = 15,
            ivDef = 14,
            ivSta = 15,
            cp = cp325,
            hp = hp325,
            powerUpDust = 6000
        )

        assertEquals(32.5, solved, 0.01)
    }

    @Test
    fun `calculateUpgradeCost applies Shadow 1_2x Purified 0_9x and Lucky 0_5x modifiers`() {
        val normalCost = PokemonMathEngine.calculateUpgradeCost(
            species = mewtwo,
            ivAtk = 15, ivDef = 15, ivSta = 15,
            currentLevel = 20.0, targetLevel = 40.0,
            isShadow = false, isPurified = false, isLucky = false
        )
        val shadowCost = PokemonMathEngine.calculateUpgradeCost(
            species = mewtwo,
            ivAtk = 15, ivDef = 15, ivSta = 15,
            currentLevel = 20.0, targetLevel = 40.0,
            isShadow = true, isPurified = false, isLucky = false
        )
        val purifiedCost = PokemonMathEngine.calculateUpgradeCost(
            species = mewtwo,
            ivAtk = 15, ivDef = 15, ivSta = 15,
            currentLevel = 20.0, targetLevel = 40.0,
            isShadow = false, isPurified = true, isLucky = false
        )
        val luckyCost = PokemonMathEngine.calculateUpgradeCost(
            species = mewtwo,
            ivAtk = 15, ivDef = 15, ivSta = 15,
            currentLevel = 20.0, targetLevel = 40.0,
            isShadow = false, isPurified = false, isLucky = true
        )

        assertEquals(225000, normalCost.stardust)
        assertEquals(248, normalCost.candy)
        assertEquals(0, normalCost.xlCandy)
        assertEquals(4178, normalCost.targetCp)

        assertEquals(270000, shadowCost.stardust)
        assertTrue(shadowCost.candy > normalCost.candy)
        assertEquals(202500, purifiedCost.stardust)
        assertEquals(112500, luckyCost.stardust)
        assertEquals(248, luckyCost.candy)

        val lv50Cost = PokemonMathEngine.calculateUpgradeCost(
            species = mewtwo,
            ivAtk = 15, ivDef = 15, ivSta = 15,
            currentLevel = 40.0, targetLevel = 50.0,
            isShadow = false, isPurified = false, isLucky = false
        )
        assertEquals(250000, lv50Cost.stardust)
        assertEquals(0, lv50Cost.candy)
        assertEquals(296, lv50Cost.xlCandy)
    }
}
