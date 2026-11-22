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

    private val movesById = mapOf(
        "BITE_FAST" to MoveData("BITE_FAST", "Bite", "dark", 6.0, 500, 4, true),
        "SMACK_DOWN_FAST" to MoveData("SMACK_DOWN_FAST", "Smack Down", "rock", 16.0, 1200, 8, true),
        "BRUTAL_SWING" to MoveData("BRUTAL_SWING", "Brutal Swing", "dark", 65.0, 2000, -33, false),
        "STONE_EDGE" to MoveData("STONE_EDGE", "Stone Edge", "rock", 100.0, 2300, -100, false)
    )

    private val detector = MyPokemonDetector(
        matcherProvider = { PokemonNameMatcher(listOf(tyranitar)) },
        movesProvider = { movesById }
    )

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
