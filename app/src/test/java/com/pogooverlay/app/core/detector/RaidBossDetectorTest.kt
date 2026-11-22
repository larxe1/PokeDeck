package com.pogooverlay.app.core.detector

import com.pogooverlay.app.core.data.PokemonNameMatcher
import com.pogooverlay.app.core.data.PokemonSpecies
import com.pogooverlay.app.core.data.RaidBossEntry
import com.pogooverlay.app.core.ocr.OcrLine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RaidBossDetectorTest {

    private lateinit var sampleSpecies: List<PokemonSpecies>
    private lateinit var matcher: PokemonNameMatcher
    private lateinit var detector: RaidBossDetector

    @Before
    fun setUp() {
        sampleSpecies = listOf(
            PokemonSpecies(
                speciesId = "charizard",
                speciesName = "Charizard",
                dex = 6,
                types = listOf("fire", "flying"),
                spriteFile = "charizard.png"
            ),
            PokemonSpecies(
                speciesId = "charizard_mega_x",
                speciesName = "Charizard (Mega X)",
                dex = 6,
                types = listOf("fire", "dragon"),
                tags = listOf("mega"),
                spriteFile = "charizard_mega_x.png"
            ),
            PokemonSpecies(
                speciesId = "charizard_mega_y",
                speciesName = "Charizard (Mega Y)",
                dex = 6,
                types = listOf("fire", "flying"),
                tags = listOf("mega"),
                spriteFile = "charizard_mega_y.png"
            ),
            PokemonSpecies(
                speciesId = "gengar",
                speciesName = "Gengar",
                dex = 94,
                types = listOf("ghost", "poison"),
                spriteFile = "gengar.png"
            ),
            PokemonSpecies(
                speciesId = "mewtwo",
                speciesName = "Mewtwo",
                dex = 150,
                types = listOf("psychic"),
                tags = listOf("legendary"),
                spriteFile = "mewtwo.png"
            ),
            PokemonSpecies(
                speciesId = "tyranitar",
                speciesName = "Tyranitar",
                dex = 248,
                types = listOf("rock", "dark"),
                spriteFile = "tyranitar.png"
            ),
            PokemonSpecies(
                speciesId = "tyranitar_mega",
                speciesName = "Tyranitar (Mega)",
                dex = 248,
                types = listOf("rock", "dark"),
                tags = listOf("mega"),
                spriteFile = "tyranitar_mega.png"
            ),
            PokemonSpecies(
                speciesId = "rayquaza",
                speciesName = "Rayquaza",
                dex = 384,
                types = listOf("dragon", "flying"),
                tags = listOf("legendary"),
                spriteFile = "rayquaza.png"
            )
        )

        matcher = PokemonNameMatcher(sampleSpecies)
        detector = RaidBossDetector(
            matcherProvider = { matcher },
            activeRaidsProvider = {
                listOf(
                    RaidBossEntry(
                        name = "Rayquaza",
                        tier = "Tier 5",
                        types = listOf("dragon", "flying")
                    ),
                    RaidBossEntry(
                        name = "Mega Charizard X",
                        tier = "Mega Raid",
                        types = listOf("fire", "dragon")
                    )
                )
            }
        )
    }

    @Test
    fun detect_singleLineBossMatch_returnsExpectedSpecies() = runTest {
        val lines = listOf(
            OcrLine(text = "RAYQUAZA", normalizedTop = 0.16f, normalizedLeft = 0.30f, normalizedHeight = 0.05f)
        )

        val result = detector.detect(lines, isManualTrigger = false)
        assertNotNull(result)
        assertEquals("rayquaza", result?.species?.speciesId)
        assertNull(result?.detectedPrefix)
    }

    @Test
    fun detect_twoLineSplitMegaCharizardX_joinsLinesAndPreservesMegaForm() = runTest {
        val lines = listOf(
            OcrLine(text = "CP 52341", normalizedTop = 0.09f, normalizedLeft = 0.35f, normalizedHeight = 0.04f),
            OcrLine(text = "MEGA", normalizedTop = 0.15f, normalizedLeft = 0.42f, normalizedHeight = 0.04f),
            OcrLine(text = "CHARIZARD X", normalizedTop = 0.20f, normalizedLeft = 0.28f, normalizedHeight = 0.05f),
            OcrLine(text = "01:42", normalizedTop = 0.30f, normalizedLeft = 0.45f, normalizedHeight = 0.03f)
        )

        val result = detector.detect(lines, isManualTrigger = false)
        assertNotNull(result)
        assertEquals("charizard_mega_x", result?.species?.speciesId)
        assertEquals(listOf("fire", "dragon"), result?.species?.types)
        assertTrue((result?.alternateForms?.size ?: 0) >= 3)
    }

    @Test
    fun detect_shadowAndGigantamaxPrefixOcr_stripsPrefixAndMapsBaseWeaknesses() = runTest {
        val shadowLines = listOf(
            OcrLine(text = "SHADOW", normalizedTop = 0.14f, normalizedLeft = 0.38f, normalizedHeight = 0.04f),
            OcrLine(text = "MEWTWO", normalizedTop = 0.19f, normalizedLeft = 0.32f, normalizedHeight = 0.05f)
        )

        val shadowResult = detector.detect(shadowLines, isManualTrigger = false)
        assertNotNull(shadowResult)
        assertEquals("mewtwo", shadowResult?.species?.speciesId)
        assertEquals("Shadow", shadowResult?.detectedPrefix)

        val gigantamaxLines = listOf(
            OcrLine(text = "GIGANTAMAX GENGAR", normalizedTop = 0.16f, normalizedLeft = 0.22f, normalizedHeight = 0.05f)
        )

        val gmaxResult = detector.detect(gigantamaxLines, isManualTrigger = false)
        assertNotNull(gmaxResult)
        assertEquals("gengar", gmaxResult?.species?.speciesId)
        assertEquals("Gigantamax", gmaxResult?.detectedPrefix)
    }

    @Test
    fun detect_noiseLinesOnly_returnsNull() = runTest {
        val noiseLines = listOf(
            OcrLine(text = "CP 52341", normalizedTop = 0.10f, normalizedLeft = 0.30f, normalizedHeight = 0.04f),
            OcrLine(text = "01:42", normalizedTop = 0.18f, normalizedLeft = 0.45f, normalizedHeight = 0.03f),
            OcrLine(text = "BATTLE", normalizedTop = 0.50f, normalizedLeft = 0.40f, normalizedHeight = 0.05f),
            OcrLine(text = "READY", normalizedTop = 0.44f, normalizedLeft = 0.42f, normalizedHeight = 0.04f),
            OcrLine(text = "PRIVATE GROUP", normalizedTop = 0.56f, normalizedLeft = 0.35f, normalizedHeight = 0.03f),
            OcrLine(text = "INVITE", normalizedTop = 0.38f, normalizedLeft = 0.70f, normalizedHeight = 0.03f)
        )

        for (line in noiseLines) {
            assertTrue("Expected noise line to be filtered: ${line.text}", detector.isNoiseLine(line.text))
        }

        val result = detector.detect(noiseLines, isManualTrigger = false)
        assertNull(result)
    }

    @Test
    fun detect_autoWatchDeduplicationAndResetAndManualOverride() = runTest {
        val rayquazaLines = listOf(
            OcrLine(text = "CP 45468", normalizedTop = 0.10f, normalizedLeft = 0.35f, normalizedHeight = 0.04f),
            OcrLine(text = "RAYQUAZA", normalizedTop = 0.16f, normalizedLeft = 0.30f, normalizedHeight = 0.05f),
            OcrLine(text = "BATTLE", normalizedTop = 0.52f, normalizedLeft = 0.40f, normalizedHeight = 0.05f)
        )

        // 1. First auto-watch pass detects Rayquaza
        val firstAuto = detector.detect(rayquazaLines, isManualTrigger = false)
        assertNotNull(firstAuto)
        assertEquals("rayquaza", firstAuto?.species?.speciesId)

        // 2. Second auto-watch pass with same boss is deduplicated (returns null)
        val secondAuto = detector.detect(rayquazaLines, isManualTrigger = false)
        assertNull(secondAuto)

        // 3. Manual 1-tap scan override always returns the match even when deduplicated
        val manualOverride = detector.detect(rayquazaLines, isManualTrigger = true)
        assertNotNull(manualOverride)
        assertEquals("rayquaza", manualOverride?.species?.speciesId)

        // 4. Subsequent auto-watch pass is still deduplicated until reset or boss changes
        val thirdAuto = detector.detect(rayquazaLines, isManualTrigger = false)
        assertNull(thirdAuto)

        // 5. Resetting deduplication (e.g. on banner dismiss) allows auto-watch to emit again
        detector.resetDeduplication()
        val afterResetAuto = detector.detect(rayquazaLines, isManualTrigger = false)
        assertNotNull(afterResetAuto)
        assertEquals("rayquaza", afterResetAuto?.species?.speciesId)

        // 6. Changing boss in auto-watch immediately emits the new boss
        val mewtwoLines = listOf(
            OcrLine(text = "SHADOW MEWTWO", normalizedTop = 0.16f, normalizedLeft = 0.28f, normalizedHeight = 0.05f)
        )
        val bossChangedAuto = detector.detect(mewtwoLines, isManualTrigger = false)
        assertNotNull(bossChangedAuto)
        assertEquals("mewtwo", bossChangedAuto?.species?.speciesId)
        assertEquals("Shadow", bossChangedAuto?.detectedPrefix)
    }
}
