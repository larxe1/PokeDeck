package com.pogooverlay.app.core.detector

import com.pogooverlay.app.core.data.PokemonNameMatcher
import com.pogooverlay.app.core.data.PokemonSpecies
import com.pogooverlay.app.core.ocr.OcrLine
import com.pogooverlay.app.core.pokebox.MoveData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MyPokemonDetectorTest {

    private val tyranitar = PokemonSpecies(
        speciesId = "tyranitar",
        speciesName = "Tyranitar",
        dex = 248,
        types = listOf("rock", "dark"),
        pokebattlerId = "TYRANITAR",
        baseAtk = 251, baseDef = 207, baseSta = 225,
        quickMoves = listOf("BITE_FAST", "SMACK_DOWN_FAST"),
        cinematicMoves = listOf("BRUTAL_SWING", "STONE_EDGE")
    )

    private val kyurem = PokemonSpecies(
        speciesId = "kyurem",
        speciesName = "Kyurem",
        dex = 646,
        types = listOf("dragon", "ice"),
        pokebattlerId = "KYUREM",
        baseAtk = 246, baseDef = 170, baseSta = 245,
        quickMoves = listOf("DRAGON_BREATH_FAST"),
        cinematicMoves = listOf("ICE_BURN", "GLACIATE")
    )

    private val zacianHero = PokemonSpecies(
        speciesId = "zacian_hero",
        speciesName = "Zacian (Hero)",
        dex = 888,
        types = listOf("fairy"),
        pokebattlerId = "ZACIAN",
        baseAtk = 254, baseDef = 236, baseSta = 192,
        quickMoves = listOf("METAL_CLAW_FAST", "SNARL_FAST"),
        cinematicMoves = listOf("PLAY_ROUGH", "CLOSE_COMBAT")
    )

    private val zacianCrowned = PokemonSpecies(
        speciesId = "zacian_crowned_sword",
        speciesName = "Zacian (Crowned Sword)",
        dex = 888,
        types = listOf("fairy", "steel"),
        pokebattlerId = "ZACIAN_CROWNED_SWORD_FORM",
        baseAtk = 332, baseDef = 240, baseSta = 192,
        quickMoves = listOf("METAL_CLAW_FAST", "AIR_SLASH_FAST"),
        cinematicMoves = listOf("BEHEMOTH_BLADE", "PLAY_ROUGH")
    )

    private val movesById = mapOf(
        "BITE_FAST" to MoveData("BITE_FAST", "Bite", "dark", 6.0, 500, 4, true),
        "SMACK_DOWN_FAST" to MoveData("SMACK_DOWN_FAST", "Smack Down", "rock", 16.0, 1200, 8, true),
        "BRUTAL_SWING" to MoveData("BRUTAL_SWING", "Brutal Swing", "dark", 65.0, 2000, -33, false),
        "STONE_EDGE" to MoveData("STONE_EDGE", "Stone Edge", "rock", 100.0, 2300, -100, false),
        "DRAGON_BREATH_FAST" to MoveData("DRAGON_BREATH_FAST", "Dragon Breath", "dragon", 6.0, 500, 4, true),
        "ICE_BURN" to MoveData("ICE_BURN", "Ice Burn", "ice", 90.0, 2500, -50, false),
        "METAL_CLAW_FAST" to MoveData("METAL_CLAW_FAST", "Metal Claw", "steel", 6.0, 600, 5, true),
        "BEHEMOTH_BLADE" to MoveData("BEHEMOTH_BLADE", "Behemoth Blade", "steel", 130.0, 2500, -50, false)
    )

    private val detector = MyPokemonDetector(
        matcherProvider = { PokemonNameMatcher(listOf(tyranitar, kyurem, zacianHero, zacianCrowned)) },
        movesProvider = { movesById }
    )

    @Test
    fun `ScreenClassifier distinguishes Raid Lobby from My Pokemon details and Appraisal`() {
        val raidLines = listOf(
            OcrLine("CP 12730", 0.14f, 0.35f, 0.04f),
            OcrLine("Prinplup", 0.24f, 0.30f, 0.05f),
            OcrLine("0:35:41", 0.58f, 0.72f, 0.03f),
            OcrLine("BATTLE", 0.75f, 0.40f, 0.04f),
            OcrLine("PRIVATE GROUP", 0.86f, 0.35f, 0.03f)
        )
        assertEquals(ScreenKind.RAID_BOSS, ScreenClassifier.classify(raidLines))

        val ownLines = listOf(
            OcrLine("Sword Doggo", 0.24f, 0.30f, 0.05f),
            OcrLine("160 / 160 HP", 0.29f, 0.40f, 0.02f),
            OcrLine("ZACIAN CANDY", 0.53f, 0.42f, 0.02f),
            OcrLine("GYMS & RAIDS", 0.72f, 0.22f, 0.02f)
        )
        assertEquals(ScreenKind.MY_POKEMON, ScreenClassifier.classify(ownLines))
    }

    @Test
    fun `resolves renamed Sword Doggo via Zacian Candy types and Behemoth Blade even when CP is hidden`() {
        val lines = listOf(
            OcrLine("Sword Doggo", 0.24f, 0.30f, 0.05f),
            OcrLine("160 / 160 HP", 0.29f, 0.40f, 0.02f),
            OcrLine("FAIRY / STEEL", 0.37f, 0.44f, 0.02f),
            OcrLine("85,507", 0.50f, 0.14f, 0.02f),
            OcrLine("STARDUST", 0.53f, 0.14f, 0.02f),
            OcrLine("ZACIAN CANDY", 0.53f, 0.42f, 0.02f),
            OcrLine("POWER UP", 0.61f, 0.20f, 0.03f),
            OcrLine("9,000", 0.61f, 0.56f, 0.02f),
            OcrLine("Metal Claw", 0.78f, 0.16f, 0.03f),
            OcrLine("Behemoth Blade", 0.83f, 0.16f, 0.03f)
        )

        val result = detector.detectFromLinesAndBitmap(lines, bitmap = null)
        assertNotNull(result)
        assertEquals("zacian_crowned_sword", result!!.species.speciesId)
        assertEquals("Sword Doggo", result.nickname)
        assertNull(result.cp)
        assertEquals(160, result.hp)
        assertEquals(9000, result.powerUpDust)
        assertEquals("METAL_CLAW_FAST", result.fastMove?.moveId)
        assertEquals("BEHEMOTH_BLADE", result.chargedMove1?.moveId)
    }

    @Test
    fun `computeIvFromBarSpan recovers exact 11 14 15 IVs on Kyurem pixel bar bounds`() {
        val barLeft = 58
        val barRight = 219

        val atk = MyPokemonDetector.computeIvFromBarSpan(
            barLeft = barLeft,
            barRight = barRight,
            lastFilledX = 172,
            filledCount = 54,
            pinkCount = 0,
            grayCount = 22
        )
        val def = MyPokemonDetector.computeIvFromBarSpan(
            barLeft = barLeft,
            barRight = barRight,
            lastFilledX = 204,
            filledCount = 67,
            pinkCount = 0,
            grayCount = 5
        )
        val sta = MyPokemonDetector.computeIvFromBarSpan(
            barLeft = barLeft,
            barRight = barRight,
            lastFilledX = 216,
            filledCount = 77,
            pinkCount = 77,
            grayCount = 0
        )

        assertEquals(11, atk)
        assertEquals(14, def)
        assertEquals(15, sta)
    }

    @Test
    fun `mergeWith combines top Appraisal scan with scrolled-down Moves scan`() {
        val appraisalPass = DetectedOwnPokemon(
            species = kyurem,
            cp = 4213,
            hp = 198,
            powerUpDust = null,
            ivAtk = 11,
            ivDef = 14,
            ivSta = 15,
            isShadow = false,
            isPurified = false,
            isLucky = false,
            fastMove = null,
            chargedMove1 = null,
            chargedMove2 = null,
            estimatedLevel = 39.0
        )
        val movesPass = DetectedOwnPokemon(
            species = kyurem,
            cp = null,
            hp = 198,
            powerUpDust = 10000,
            ivAtk = null,
            ivDef = null,
            ivSta = null,
            isShadow = false,
            isPurified = false,
            isLucky = false,
            fastMove = movesById["DRAGON_BREATH_FAST"],
            chargedMove1 = movesById["ICE_BURN"],
            chargedMove2 = null,
            estimatedLevel = 20.0
        )

        val merged = appraisalPass.mergeWith(movesPass)
        assertEquals(4213, merged.cp)
        assertEquals(11, merged.ivAtk)
        assertEquals(14, merged.ivDef)
        assertEquals(15, merged.ivSta)
        assertEquals("DRAGON_BREATH_FAST", merged.fastMove?.moveId)
        assertEquals("ICE_BURN", merged.chargedMove1?.moveId)
        assertEquals(10000, merged.powerUpDust)
    }

    @Test
    fun `extracts CP HP shadow flag species and moveset from Pokemon details OCR lines`() {
        val lines = listOf(
            OcrLine("CP 3834", 0.08f, 0.35f, 0.05f),
            OcrLine("Tyranitar", 0.38f, 0.35f, 0.05f),
            OcrLine("189 / 189 HP", 0.44f, 0.32f, 0.03f),
            OcrLine("PURIFY", 0.56f, 0.25f, 0.04f),
            OcrLine("POWER UP", 0.62f, 0.22f, 0.04f),
            OcrLine("6000", 0.62f, 0.55f, 0.04f),
            OcrLine("Bite 6", 0.72f, 0.15f, 0.04f),
            OcrLine("Brutal Swing 65", 0.78f, 0.15f, 0.04f)
        )

        val result = detector.detectFromLinesAndBitmap(lines, bitmap = null)
        assertNotNull(result)
        assertEquals("tyranitar", result!!.species.speciesId)
        assertEquals(3834, result.cp)
        assertEquals(189, result.hp)
        assertEquals(6000, result.powerUpDust)
        assertTrue(result.isShadow)
        assertEquals("BITE_FAST", result.fastMove?.moveId)
        assertEquals("BRUTAL_SWING", result.chargedMove1?.moveId)
    }

    @Test
    fun `returns null on Raid Lobby screen without Pokemon details cues`() {
        val lobbyLines = listOf(
            OcrLine("CP 54148", 0.12f, 0.35f, 0.05f),
            OcrLine("Tyranitar", 0.22f, 0.35f, 0.05f),
            OcrLine("01:42", 0.32f, 0.40f, 0.04f),
            OcrLine("BATTLE", 0.80f, 0.40f, 0.05f)
        )
        val result = detector.detectFromLinesAndBitmap(lobbyLines, bitmap = null)
        assertNull(result)
    }
}
