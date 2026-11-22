package com.pogooverlay.app.core.scan

import com.pogooverlay.app.core.data.PokemonNameMatcher
import com.pogooverlay.app.core.data.PokemonSpecies
import com.pogooverlay.app.core.ocr.OcrLine
import com.pogooverlay.app.core.pokebox.MoveData
import com.pogooverlay.app.core.pokebox.PokemonMathEngine
import com.pogooverlay.app.core.pokebox.SavedPokemon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanCapturePipelineTest {

    private val tyranitar = PokemonSpecies(
        speciesId = "tyranitar",
        speciesName = "Tyranitar",
        dex = 248,
        types = listOf("rock", "dark"),
        pokebattlerId = "TYRANITAR",
        baseAtk = 251,
        baseDef = 207,
        baseSta = 225,
        quickMoves = listOf("BITE_FAST", "SMACK_DOWN_FAST"),
        cinematicMoves = listOf("BRUTAL_SWING", "STONE_EDGE")
    )

    private val kyurem = PokemonSpecies(
        speciesId = "kyurem",
        speciesName = "Kyurem",
        dex = 646,
        types = listOf("dragon", "ice"),
        pokebattlerId = "KYUREM",
        baseAtk = 246,
        baseDef = 170,
        baseSta = 245,
        quickMoves = listOf("DRAGON_BREATH_FAST"),
        cinematicMoves = listOf("ICE_BURN", "GLACIATE")
    )

    private val lucario = PokemonSpecies(
        speciesId = "lucario",
        speciesName = "Lucario",
        dex = 448,
        types = listOf("fighting", "steel"),
        tags = listOf("mega"),
        pokebattlerId = "LUCARIO",
        baseAtk = 236,
        baseDef = 144,
        baseSta = 172,
        quickMoves = listOf("COUNTER_FAST", "FORCE_PALM_FAST"),
        cinematicMoves = listOf("AURA_SPHERE", "SHADOW_BALL")
    )

    private val zacianHero = PokemonSpecies(
        speciesId = "zacian_hero",
        speciesName = "Zacian (Hero)",
        dex = 888,
        types = listOf("fairy"),
        pokebattlerId = "ZACIAN",
        baseAtk = 254,
        baseDef = 236,
        baseSta = 192,
        quickMoves = listOf("METAL_CLAW_FAST", "SNARL_FAST"),
        cinematicMoves = listOf("PLAY_ROUGH", "CLOSE_COMBAT")
    )

    private val zacianCrowned = PokemonSpecies(
        speciesId = "zacian_crowned_sword",
        speciesName = "Zacian (Crowned Sword)",
        dex = 888,
        types = listOf("fairy", "steel"),
        pokebattlerId = "ZACIAN_CROWNED_SWORD_FORM",
        baseAtk = 332,
        baseDef = 240,
        baseSta = 192,
        quickMoves = listOf("METAL_CLAW_FAST", "AIR_SLASH_FAST"),
        cinematicMoves = listOf("BEHEMOTH_BLADE", "PLAY_ROUGH")
    )

    private val movesById = mapOf(
        "BITE_FAST" to MoveData("BITE_FAST", "Bite", "dark", 6.0, 500, 4, true),
        "SMACK_DOWN_FAST" to MoveData("SMACK_DOWN_FAST", "Smack Down", "rock", 13.0, 1000, 7, true),
        "BRUTAL_SWING" to MoveData("BRUTAL_SWING", "Brutal Swing", "dark", 65.0, 2000, -33, false),
        "STONE_EDGE" to MoveData("STONE_EDGE", "Stone Edge", "rock", 105.0, 2500, -100, false),
        "DRAGON_BREATH_FAST" to MoveData("DRAGON_BREATH_FAST", "Dragon Breath", "dragon", 6.0, 500, 4, true),
        "ICE_BURN" to MoveData("ICE_BURN", "Ice Burn", "ice", 90.0, 2000, -50, false),
        "GLACIATE" to MoveData("GLACIATE", "Glaciate", "ice", 160.0, 2500, -100, false),
        "COUNTER_FAST" to MoveData("COUNTER_FAST", "Counter", "fighting", 13.0, 1000, 9, true),
        "FORCE_PALM_FAST" to MoveData("FORCE_PALM_FAST", "Force Palm", "fighting", 10.0, 1000, 16, true),
        "AURA_SPHERE" to MoveData("AURA_SPHERE", "Aura Sphere", "fighting", 100.0, 2000, -50, false),
        "AVALANCHE" to MoveData("AVALANCHE", "Avalanche", "ice", 85.0, 2500, -50, false),
        "SHADOW_BALL" to MoveData("SHADOW_BALL", "Shadow Ball", "ghost", 100.0, 3000, -50, false),
        "METAL_CLAW_FAST" to MoveData("METAL_CLAW_FAST", "Metal Claw", "steel", 6.0, 500, 5, true),
        "BEHEMOTH_BLADE" to MoveData("BEHEMOTH_BLADE", "Behemoth Blade", "steel", 200.0, 3500, -100, false),
        "WING_ATTACK_FAST" to MoveData("WING_ATTACK_FAST", "Wing Attack", "flying", 10.0, 1000, 11, true),
        "SKY_ATTACK" to MoveData("SKY_ATTACK", "Sky Attack", "flying", 80.0, 2000, -50, false),
        "POWER_UP_PUNCH" to MoveData("POWER_UP_PUNCH", "Power Up Punch", "fighting", 50.0, 2000, -33, false),
        "FRUSTRATION" to MoveData("FRUSTRATION", "Frustration", "normal", 10.0, 2000, -33, false),
        "RETURN" to MoveData("RETURN", "Return", "normal", 25.0, 500, -33, false)
    )

    private val matcher = PokemonNameMatcher(listOf(tyranitar, kyurem, lucario, zacianHero, zacianCrowned))
    private val ivParser = AppraisalIvParser()
    private val movesetParser = MovesetParser()
    private val screenParser = ScanScreenParser(
        matcherProvider = { matcher },
        movesProvider = { movesById },
        appraisalIvParser = ivParser,
        movesetParser = movesetParser
    )

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    private val white = argb(255, 255, 255)
    private val orange = argb(244, 164, 66)
    private val pink = argb(235, 95, 110)
    private val gray = argb(220, 220, 220)

    /**
     * Builds a synthetic bar row with 15 equal segments of 5px (75 effective pixels)
     * plus two 3px white divider gaps after segment 5 and segment 10 (total width 81px).
     */
    private fun buildSyntheticBarRow(iv: Int, maxedPink: Boolean = iv == 15): IntArray {
        val pixels = mutableListOf<Int>()
        for (seg in 1..15) {
            val color = when {
                seg <= iv && maxedPink -> pink
                seg <= iv -> orange
                else -> gray
            }
            repeat(5) { pixels.add(color) }
            if (seg == 5 || seg == 10) {
                repeat(3) { pixels.add(white) }
            }
        }
        return pixels.toIntArray()
    }

    @Test
    fun `computeIvFromTrackPixels excludes white divider gaps across all IVs 0 to 15`() {
        for (iv in 0..14) {
            val row = buildSyntheticBarRow(iv = iv, maxedPink = false)
            val (computed, isPink) = ivParser.computeIvFromTrackPixels(row, 0, row.lastIndex)
            assertEquals("Expected exact IV $iv", iv, computed)
            assertFalse(isPink)
        }

        val pinkRow = buildSyntheticBarRow(iv = 15, maxedPink = true)
        val (computed15, isPink15) = ivParser.computeIvFromTrackPixels(pinkRow, 0, pinkRow.lastIndex)
        assertEquals(15, computed15)
        assertTrue(isPink15)
    }

    @Test
    fun `AppraisalIvParser parseFromPixels extracts 11 14 15 while ignoring right leader artwork`() {
        val width = 300
        val height = 600
        val grid = Array(height) { IntArray(width) { white } }

        // Draw colorful team leader jacket on the right (> 0.55 * width) that could mimic orange/pink if scanned.
        for (y in 300 until 550) {
            for (x in 175 until 280) {
                grid[y][x] = if (y % 2 == 0) orange else pink
            }
        }

        fun drawBar(barTopY: Int, startX: Int, iv: Int) {
            val barRow = buildSyntheticBarRow(iv)
            for (dy in 0..5) {
                for (dx in barRow.indices) {
                    grid[barTopY + dy][startX + dx] = barRow[dx]
                }
            }
        }

        val atkLine = OcrLine("Attack", normalizedTop = 0.56f, normalizedLeft = 0.08f, normalizedHeight = 0.025f, normalizedWidth = 0.14f)
        val defLine = OcrLine("Defense", normalizedTop = 0.64f, normalizedLeft = 0.085f, normalizedHeight = 0.025f, normalizedWidth = 0.16f)
        val hpLine = OcrLine("HP", normalizedTop = 0.72f, normalizedLeft = 0.08f, normalizedHeight = 0.025f, normalizedWidth = 0.08f)

        // Place bars just beneath each label's bottom (0.585*600=351, 0.665*600=399, 0.745*600=447)
        drawBar(barTopY = 356, startX = 24, iv = 11)
        drawBar(barTopY = 404, startX = 24, iv = 14)
        drawBar(barTopY = 452, startX = 24, iv = 15)

        val result = ivParser.parseFromPixels(
            width = width,
            height = height,
            lines = listOf(
                OcrLine("198 / 198 HP", 0.30f, 0.35f, 0.025f, 0.25f),
                atkLine,
                defLine,
                hpLine,
                OcrLine("NEW ATTACK", 0.85f, 0.10f, 0.03f, 0.25f)
            )
        ) { x, y -> grid[y][x] }

        assertNotNull(result)
        assertEquals(IvSpread(11, 14, 15), result!!.ivSpread)
        assertEquals(3, result.barScanlines.size)
        assertFalse(result.barScanlines[0].isMaxedPink)
        assertFalse(result.barScanlines[1].isMaxedPink)
        assertTrue(result.barScanlines[2].isMaxedPink)
        assertTrue(result.appraisalCardBoundsNormalized.containsCenter(atkLine))
        assertTrue(result.appraisalCardBoundsNormalized.containsCenter(hpLine))
    }

    @Test
    fun `AppraisalIvParser rejects NEW ATTACK and slash HP and misaligned labels`() {
        val width = 300
        val height = 600
        val grid = Array(height) { IntArray(width) { white } }

        // Only NEW ATTACK + Defense + slash HP (not real Appraisal)
        val fakeLines = listOf(
            OcrLine("NEW ATTACK", 0.56f, 0.08f, 0.025f, 0.20f),
            OcrLine("Defense", 0.64f, 0.08f, 0.025f, 0.15f),
            OcrLine("160 / 160 HP", 0.72f, 0.08f, 0.025f, 0.22f)
        )
        assertNull(ivParser.parseFromPixels(width, height, fakeLines) { x, y -> grid[y][x] })

        // Misaligned left coordinates (> 0.10f difference)
        val misalignedLines = listOf(
            OcrLine("Attack", 0.56f, 0.08f, 0.025f, 0.14f),
            OcrLine("Defense", 0.64f, 0.35f, 0.025f, 0.16f),
            OcrLine("HP", 0.72f, 0.08f, 0.025f, 0.08f)
        )
        assertNull(ivParser.parseFromPixels(width, height, misalignedLines) { x, y -> grid[y][x] })
    }

    @Test
    fun `MovesetParser strips numbers and boosts uses Levenshtein and legal species movepool`() {
        val lines = listOf(
            OcrLine("Counter 13", 0.70f, 0.15f, 0.03f, 0.28f),
            OcrLine("Aura Sphere 100 +", 0.76f, 0.15f, 0.03f, 0.36f),
            OcrLine("Shadow Ball 100", 0.82f, 0.15f, 0.03f, 0.34f)
        )
        val result = movesetParser.parse(lines, lucario, movesById)
        assertEquals(MovesetParseStatus.COMPLETE, result.status)
        assertNotNull(result.moveset)
        assertEquals("COUNTER_FAST", result.moveset!!.fastMove.moveId)
        assertEquals("AURA_SPHERE", result.moveset!!.chargedMove1.moveId)
        assertEquals("SHADOW_BALL", result.moveset!!.chargedMove2?.moveId)

        // Noisy OCR typo still >= 0.80f similarity + Frustration allowed for shadow species
        val noisyLines = listOf(
            OcrLine("Dragon Breath 6", 0.68f, 0.15f, 0.03f, 0.32f),
            OcrLine("lce Burn 90", 0.74f, 0.15f, 0.03f, 0.26f),
            OcrLine("Frustration 10", 0.80f, 0.15f, 0.03f, 0.28f)
        )
        val noisyResult = movesetParser.parse(noisyLines, kyurem, movesById)
        assertEquals(MovesetParseStatus.COMPLETE, noisyResult.status)
        assertEquals("ICE_BURN", noisyResult.moveset!!.chargedMove1.moveId)
        assertEquals("FRUSTRATION", noisyResult.moveset!!.chargedMove2?.moveId)

        // Regression: moves containing "Attack" or "Power Up" must NOT be dropped by UI ignore rules
        val attackAndPowerUpMoveLines = listOf(
            OcrLine("POWER UP", 0.60f, 0.15f, 0.03f, 0.20f),
            OcrLine("Wing Attack 10", 0.68f, 0.15f, 0.03f, 0.28f),
            OcrLine("Sky Attack 80", 0.74f, 0.15f, 0.03f, 0.26f),
            OcrLine("Power-Up Punch 50", 0.80f, 0.15f, 0.03f, 0.32f)
        )
        val regressionMoves = movesetParser.parse(attackAndPowerUpMoveLines, species = null, movesById = movesById)
        assertEquals(MovesetParseStatus.COMPLETE, regressionMoves.status)
        assertEquals("WING_ATTACK_FAST", regressionMoves.moveset!!.fastMove.moveId)
        assertEquals("SKY_ATTACK", regressionMoves.moveset!!.chargedMove1.moveId)
        assertEquals("POWER_UP_PUNCH", regressionMoves.moveset!!.chargedMove2?.moveId)
    }

    @Test
    fun `MovesetParser detects INCOMPLETE_CUTOFF when 1 charged move sits at bottom without anchor`() {
        val cutoffLines = listOf(
            OcrLine("Dragon Breath 6", 0.78f, 0.15f, 0.03f, 0.32f),
            OcrLine("Ice Burn 90", 0.84f, 0.15f, 0.03f, 0.26f) // normalizedBottom = 0.87f > 0.85f
        )
        val cutoffResult = movesetParser.parse(cutoffLines, kyurem, movesById)
        assertEquals(MovesetParseStatus.INCOMPLETE_CUTOFF, cutoffResult.status)
        assertNull(cutoffResult.moveset)

        // Adding NEW ATTACK below confirms single charged move -> COMPLETE
        val anchoredLines = cutoffLines + OcrLine("NEW ATTACK", 0.90f, 0.15f, 0.03f, 0.25f)
        val anchoredResult = movesetParser.parse(anchoredLines, kyurem, movesById)
        assertEquals(MovesetParseStatus.COMPLETE, anchoredResult.status)
        assertEquals("ICE_BURN", anchoredResult.moveset!!.chargedMove1.moveId)
        assertNull(anchoredResult.moveset!!.chargedMove2)

        // Single charged move higher up (<= 0.85f) with no anchor -> COMPLETE
        val safeHeightLines = listOf(
            OcrLine("Dragon Breath 6", 0.68f, 0.15f, 0.03f, 0.32f),
            OcrLine("Ice Burn 90", 0.74f, 0.15f, 0.03f, 0.26f) // normalizedBottom = 0.77f <= 0.85f
        )
        val safeResult = movesetParser.parse(safeHeightLines, kyurem, movesById)
        assertEquals(MovesetParseStatus.COMPLETE, safeResult.status)
    }

    @Test
    fun `MovesetParser excludes lines covered by Appraisal card bounds`() {
        val appraisalBounds = NormalizedRect(left = 0.04f, top = 0.50f, right = 0.52f, bottom = 0.86f)
        val lines = listOf(
            OcrLine("Bite 6", 0.60f, 0.15f, 0.03f, 0.18f), // inside appraisal bounds -> ignored
            OcrLine("Brutal Swing 65", 0.70f, 0.15f, 0.03f, 0.28f) // inside appraisal bounds -> ignored
        )
        val result = movesetParser.parse(lines, tyranitar, movesById, appraisalBounds)
        assertEquals(MovesetParseStatus.NONE_FOUND, result.status)
    }

    @Test
    fun `ScanScreenParser resolves renamed Zacian Crowned Sword and ignores Shadow Ball as shadow flag`() {
        val zacianLines = listOf(
            OcrLine("Sword Doggo", 0.24f, 0.30f, 0.04f, 0.30f),
            OcrLine("160 / 160 HP", 0.29f, 0.38f, 0.025f, 0.22f),
            OcrLine("FAIRY / STEEL", 0.37f, 0.35f, 0.025f, 0.25f),
            OcrLine("ZACIAN CANDY", 0.53f, 0.42f, 0.025f, 0.25f),
            OcrLine("Metal Claw 6", 0.72f, 0.15f, 0.03f, 0.25f),
            OcrLine("Behemoth Blade 200", 0.78f, 0.15f, 0.03f, 0.35f),
            OcrLine("NEW ATTACK", 0.86f, 0.15f, 0.03f, 0.25f)
        )
        val zacianExtraction = screenParser.parse(zacianLines, bitmap = null)
        assertEquals("zacian_crowned_sword", zacianExtraction.species?.speciesId)
        assertEquals("Sword Doggo", zacianExtraction.nickname)
        assertEquals(160, zacianExtraction.hp)
        assertNull(zacianExtraction.ivData) // Never estimates IVs
        assertEquals(MovesetParseStatus.COMPLETE, zacianExtraction.movesetResult.status)

        // Lucario with Shadow Ball should NOT be flagged isShadow = true
        val lucarioLines = listOf(
            OcrLine("CP 2703", 0.10f, 0.35f, 0.04f, 0.25f),
            OcrLine("Lucario", 0.28f, 0.35f, 0.04f, 0.22f),
            OcrLine("Counter 13", 0.68f, 0.15f, 0.03f, 0.22f),
            OcrLine("Aura Sphere 100", 0.74f, 0.15f, 0.03f, 0.30f),
            OcrLine("Shadow Ball 100", 0.80f, 0.15f, 0.03f, 0.30f)
        )
        val lucarioExtraction = screenParser.parse(lucarioLines, bitmap = null)
        assertEquals("lucario", lucarioExtraction.species?.speciesId)
        assertFalse(lucarioExtraction.isShadow)

        // Regression: activeSpeciesHint helps moveset parsing without leaking stale species onto a CP-changed unidentified Pokemon
        val manager = CaptureSessionManager {}
        manager.processFrame(
            ScanFrameExtraction(
                species = kyurem,
                cp = 4213,
                ivData = IvSpread(11, 14, 15)
            )
        )
        val unidentifiedNewPokemonLines = listOf(
            OcrLine("CP 2150", 0.10f, 0.35f, 0.04f, 0.22f),
            OcrLine("Wing Attack 10", 0.68f, 0.15f, 0.03f, 0.28f),
            OcrLine("Sky Attack 80", 0.74f, 0.15f, 0.03f, 0.26f)
        )
        val unidentifiedExtraction = screenParser.parse(
            lines = unidentifiedNewPokemonLines,
            bitmap = null,
            activeSpeciesHint = manager.currentDraft.species
        )
        assertNull(unidentifiedExtraction.species)
        assertEquals(2150, unidentifiedExtraction.cp)
        val resetOutcome = manager.processFrame(unidentifiedExtraction)
        assertTrue(resetOutcome is CaptureStepOutcome.InProgress)
        val resetDraft = (resetOutcome as CaptureStepOutcome.InProgress).draft
        assertTrue(resetOutcome.wasResetForNewPokemon)
        assertNull(resetDraft.species)
        assertNull(resetDraft.ivData)
        assertEquals(2150, resetDraft.cp)
    }

    @Test
    fun `CaptureSessionManager supports order-agnostic 2-tap and single-tap saving with exact level`() {
        val savedList = mutableListOf<SavedPokemon>()
        val manager = CaptureSessionManager { savedList.add(it) }

        val targetCp = PokemonMathEngine.calculateCp(
            kyurem.baseAtk, kyurem.baseDef, kyurem.baseSta,
            11, 14, 15, 40.0
        )

        // Flow 1: Appraisal first -> Moves second
        val appraisalStep = ScanFrameExtraction(
            species = kyurem,
            cp = targetCp,
            hp = 198,
            ivData = IvSpread(11, 14, 15),
            movesetResult = MovesetParseResult(MovesetParseStatus.NONE_FOUND)
        )
        val out1 = manager.processFrame(appraisalStep)
        assertTrue(out1 is CaptureStepOutcome.InProgress)
        assertEquals("IVs ✓ · Scroll to moves & tap", (out1 as CaptureStepOutcome.InProgress).persistentStatus)
        assertTrue(savedList.isEmpty())

        val movesStep = ScanFrameExtraction(
            species = kyurem, // from activeSpeciesHint
            movesetResult = MovesetParseResult(
                status = MovesetParseStatus.COMPLETE,
                moveset = CompleteMoveset(
                    fastMove = movesById.getValue("DRAGON_BREATH_FAST"),
                    chargedMoves = listOf(movesById.getValue("ICE_BURN"))
                )
            )
        )
        val out2 = manager.processFrame(movesStep)
        assertTrue(out2 is CaptureStepOutcome.Saved)
        assertEquals(1, savedList.size)
        val savedKyurem = savedList[0]
        assertEquals("kyurem", savedKyurem.speciesId)
        assertEquals(targetCp, savedKyurem.cp)
        assertEquals(11, savedKyurem.ivAtk)
        assertEquals(14, savedKyurem.ivDef)
        assertEquals(15, savedKyurem.ivSta)
        assertEquals(40.0, savedKyurem.level, 0.01)
        assertEquals("DRAGON_BREATH_FAST", savedKyurem.fastMoveId)
        assertEquals("ICE_BURN", savedKyurem.chargedMove1Id)
        assertNull(savedKyurem.chargedMove2Id)
        assertTrue(manager.currentDraft.isIdle)

        // Flow 2 (Reverse order): Moves first -> Appraisal second
        val tyranitarCp = PokemonMathEngine.calculateCp(
            tyranitar.baseAtk, tyranitar.baseDef, tyranitar.baseSta,
            15, 14, 13, 35.0
        )
        val rev1 = manager.processFrame(
            ScanFrameExtraction(
                species = tyranitar,
                cp = tyranitarCp,
                isShadow = true,
                movesetResult = MovesetParseResult(
                    status = MovesetParseStatus.COMPLETE,
                    moveset = CompleteMoveset(
                        fastMove = movesById.getValue("BITE_FAST"),
                        chargedMoves = listOf(
                            movesById.getValue("BRUTAL_SWING"),
                            movesById.getValue("STONE_EDGE")
                        )
                    )
                )
            )
        )
        assertTrue(rev1 is CaptureStepOutcome.InProgress)
        assertEquals("Moves ✓ · Open Appraisal & tap", (rev1 as CaptureStepOutcome.InProgress).persistentStatus)

        val rev2 = manager.processFrame(
            ScanFrameExtraction(
                species = tyranitar,
                cp = tyranitarCp,
                ivData = IvSpread(15, 14, 13)
            )
        )
        assertTrue(rev2 is CaptureStepOutcome.Saved)
        assertEquals(2, savedList.size)
        assertTrue(savedList[1].isShadow)
        assertEquals(35.0, savedList[1].level, 0.01)
        assertEquals("STONE_EDGE", savedList[1].chargedMove2Id)

        // Flow 3: Single-tap (both IVs + Complete moveset on same frame)
        val singleTap = manager.processFrame(
            ScanFrameExtraction(
                species = lucario,
                cp = 2703,
                ivData = IvSpread(15, 15, 15),
                movesetResult = MovesetParseResult(
                    status = MovesetParseStatus.COMPLETE,
                    moveset = CompleteMoveset(
                        fastMove = movesById.getValue("COUNTER_FAST"),
                        chargedMoves = listOf(movesById.getValue("AURA_SPHERE"))
                    )
                )
            )
        )
        assertTrue(singleTap is CaptureStepOutcome.Saved)
        assertEquals(3, savedList.size)
        assertTrue(savedList[2].isMegaCapable)
    }

    @Test
    fun `CaptureSessionManager blocks INCOMPLETE_CUTOFF and resets on cross-Pokemon CP or dex change`() {
        val savedList = mutableListOf<SavedPokemon>()
        val manager = CaptureSessionManager { savedList.add(it) }

        // 1. Scan Kyurem Appraisal
        manager.processFrame(
            ScanFrameExtraction(
                species = kyurem,
                cp = 4213,
                ivData = IvSpread(11, 14, 15)
            )
        )

        // 2. Cut-off moveset must NOT save
        val cutoffOut = manager.processFrame(
            ScanFrameExtraction(
                species = kyurem,
                movesetResult = MovesetParseResult(MovesetParseStatus.INCOMPLETE_CUTOFF)
            )
        )
        assertTrue(cutoffOut is CaptureStepOutcome.InProgress)
        assertEquals(
            "Scroll slightly lower to confirm 2nd move & tap",
            (cutoffOut as CaptureStepOutcome.InProgress).transientFlash
        )
        assertTrue(savedList.isEmpty())

        // 3. Switch to Tyranitar (different dex & CP) -> discards Kyurem IVs!
        val switchOut = manager.processFrame(
            ScanFrameExtraction(
                species = tyranitar,
                cp = 3834,
                movesetResult = MovesetParseResult(
                    status = MovesetParseStatus.COMPLETE,
                    moveset = CompleteMoveset(
                        fastMove = movesById.getValue("BITE_FAST"),
                        chargedMoves = listOf(movesById.getValue("BRUTAL_SWING"))
                    )
                )
            )
        )
        assertTrue(switchOut is CaptureStepOutcome.InProgress)
        val inProg = switchOut as CaptureStepOutcome.InProgress
        assertTrue(inProg.wasResetForNewPokemon)
        assertNull(inProg.draft.ivData)
        assertEquals("tyranitar", inProg.draft.speciesId)
        assertTrue(savedList.isEmpty())

        // 4. Same-dex form upgrade (Zacian Hero -> Zacian Crowned Sword) preserves draft
        manager.reset()
        manager.processFrame(
            ScanFrameExtraction(
                species = zacianHero,
                nickname = "Sword Doggo",
                ivData = IvSpread(15, 15, 14)
            )
        )
        val upgradeOut = manager.processFrame(
            ScanFrameExtraction(
                species = zacianCrowned,
                movesetResult = MovesetParseResult(
                    status = MovesetParseStatus.COMPLETE,
                    moveset = CompleteMoveset(
                        fastMove = movesById.getValue("METAL_CLAW_FAST"),
                        chargedMoves = listOf(movesById.getValue("BEHEMOTH_BLADE"))
                    )
                )
            )
        )
        assertTrue(upgradeOut is CaptureStepOutcome.Saved)
        assertEquals("zacian_crowned_sword", savedList[0].speciesId)
        assertEquals("Sword Doggo", savedList[0].nickname)
    }

    @Test
    fun `CaptureSessionManager emits expected NoData prompts across draft states`() {
        val manager = CaptureSessionManager {}

        val idleNoData = manager.processFrame(ScanFrameExtraction())
        assertTrue(idleNoData is CaptureStepOutcome.NoData)
        assertNull((idleNoData as CaptureStepOutcome.NoData).persistentStatus)
        assertEquals("Open Appraisal (or scroll to moves) & tap", idleNoData.transientFlash)

        manager.processFrame(ScanFrameExtraction(species = kyurem, ivData = IvSpread(15, 15, 15)))
        val awaitMovesNoData = manager.processFrame(ScanFrameExtraction(species = kyurem))
        assertTrue(awaitMovesNoData is CaptureStepOutcome.NoData)
        assertEquals("IVs ✓ · Scroll to moves & tap", (awaitMovesNoData as CaptureStepOutcome.NoData).persistentStatus)
        assertEquals("Moves not detected — scroll down & tap", awaitMovesNoData.transientFlash)

        manager.reset()
        manager.processFrame(
            ScanFrameExtraction(
                species = kyurem,
                movesetResult = MovesetParseResult(
                    status = MovesetParseStatus.COMPLETE,
                    moveset = CompleteMoveset(
                        fastMove = movesById.getValue("DRAGON_BREATH_FAST"),
                        chargedMoves = listOf(movesById.getValue("ICE_BURN"))
                    )
                )
            )
        )
        val awaitIvNoData = manager.processFrame(ScanFrameExtraction(species = kyurem))
        assertTrue(awaitIvNoData is CaptureStepOutcome.NoData)
        assertEquals("Moves ✓ · Open Appraisal & tap", (awaitIvNoData as CaptureStepOutcome.NoData).persistentStatus)
        assertEquals("Open Appraisal screen & tap", awaitIvNoData.transientFlash)
    }
}
