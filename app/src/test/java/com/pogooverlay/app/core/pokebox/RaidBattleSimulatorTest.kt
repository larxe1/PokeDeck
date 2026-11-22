package com.pogooverlay.app.core.pokebox

import com.pogooverlay.app.core.data.PokemonSpecies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RaidBattleSimulatorTest {

    private val movesById = mapOf(
        "POWDER_SNOW_FAST" to MoveData("POWDER_SNOW_FAST", "Powder Snow", "ice", 6.0, 1000, 15, true),
        "MUD_SLAP_FAST" to MoveData("MUD_SLAP_FAST", "Mud Slap", "ground", 18.0, 1400, 12, true),
        "AVALANCHE" to MoveData("AVALANCHE", "Avalanche", "ice", 85.0, 2500, -50, false),
        "BULLDOZE" to MoveData("BULLDOZE", "Bulldoze", "ground", 80.0, 3500, -50, false),
        "BULLET_SEED_FAST" to MoveData("BULLET_SEED_FAST", "Bullet Seed", "grass", 8.0, 1100, 14, true),
        "FRENZY_PLANT" to MoveData("FRENZY_PLANT", "Frenzy Plant", "grass", 100.0, 2600, -50, false),
        "TACKLE_FAST" to MoveData("TACKLE_FAST", "Tackle", "normal", 5.0, 500, 5, true),
        "HYPER_BEAM" to MoveData("HYPER_BEAM", "Hyper Beam", "normal", 150.0, 3800, -100, false)
    )

    private val mamoswine = PokemonSpecies(
        speciesId = "mamoswine",
        speciesName = "Mamoswine",
        dex = 473,
        types = listOf("ice", "ground"),
        pokebattlerId = "MAMOSWINE",
        baseAtk = 247, baseDef = 146, baseSta = 242,
        quickMoves = listOf("POWDER_SNOW_FAST", "MUD_SLAP_FAST"),
        cinematicMoves = listOf("AVALANCHE", "BULLDOZE")
    )

    private val sceptile = PokemonSpecies(
        speciesId = "sceptile",
        speciesName = "Sceptile",
        dex = 254,
        types = listOf("grass"),
        pokebattlerId = "SCEPTILE",
        baseAtk = 223, baseDef = 169, baseSta = 172,
        quickMoves = listOf("BULLET_SEED_FAST"),
        cinematicMoves = listOf("FRENZY_PLANT")
    )

    private val snorlax = PokemonSpecies(
        speciesId = "snorlax",
        speciesName = "Snorlax",
        dex = 143,
        types = listOf("normal"),
        pokebattlerId = "SNORLAX",
        baseAtk = 190, baseDef = 169, baseSta = 330,
        quickMoves = listOf("TACKLE_FAST"),
        cinematicMoves = listOf("HYPER_BEAM")
    )

    private val swampert = PokemonSpecies(
        speciesId = "swampert",
        speciesName = "Swampert",
        dex = 260,
        types = listOf("water", "ground"),
        pokebattlerId = "SWAMPERT",
        baseAtk = 208, baseDef = 175, baseSta = 225
    )

    @Test
    fun `evaluateMoveset identifies optimal vs suboptimal TM recommended movesets`() {
        val optimalEval = RaidBattleSimulator.evaluateMoveset(
            mamoswine, movesById, "MUD_SLAP_FAST", "AVALANCHE", null
        )
        assertNotNull(optimalEval)
        assertTrue(optimalEval!!.isCurrentOptimal)

        val suboptimalEval = RaidBattleSimulator.evaluateMoveset(
            mamoswine, movesById, "POWDER_SNOW_FAST", "BULLDOZE", null
        )
        assertNotNull(suboptimalEval)
        assertFalse(suboptimalEval!!.isCurrentOptimal)
        assertTrue(suboptimalEval.recommendationText.contains("TM"))
    }

    @Test
    fun `simulatePersonalTeam ranks 2_56x grass counter above neutral normal attacker and computes verdict`() {
        val mySceptile = SavedPokemon(
            id = "1", speciesId = "sceptile", pokebattlerId = "SCEPTILE",
            cp = 2750, hp = 148, ivAtk = 15, ivDef = 14, ivSta = 15, level = 40.0,
            fastMoveId = "BULLET_SEED_FAST", chargedMove1Id = "FRENZY_PLANT"
        )
        val mySnorlax = SavedPokemon(
            id = "2", speciesId = "snorlax", pokebattlerId = "SNORLAX",
            cp = 3200, hp = 260, ivAtk = 15, ivDef = 15, ivSta = 15, level = 40.0,
            fastMoveId = "TACKLE_FAST", chargedMove1Id = "HYPER_BEAM"
        )
        val speciesMap = listOf(mamoswine, sceptile, snorlax, swampert).associateBy { it.speciesId }

        val verdict = RaidBattleSimulator.simulatePersonalTeam(
            savedBox = listOf(mySnorlax, mySceptile),
            speciesById = speciesMap,
            movesById = movesById,
            bossSpecies = swampert,
            raidTier = 3
        )

        assertEquals(2, verdict.team.size)
        assertEquals("sceptile", verdict.team.first().species.speciesId)
        assertTrue(verdict.team.first().dps > verdict.team.last().dps)
        assertTrue(verdict.teamEstimator < 2.0)
    }
}
