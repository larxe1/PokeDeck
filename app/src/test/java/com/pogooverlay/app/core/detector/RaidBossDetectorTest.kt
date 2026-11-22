package com.pogooverlay.app.core.detector

import com.pogooverlay.app.core.data.OverlayMode
import com.pogooverlay.app.core.data.PokemonNameMatcher
import com.pogooverlay.app.core.data.PokemonSpecies
import com.pogooverlay.app.core.data.RaidBossEntry
import com.pogooverlay.app.core.ocr.OcrLine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            OcrLine(
                text = "RAYQUAZA",
                normalizedTop = 0.16f,
                normalizedLeft = 0.30f,
                normalizedHeight = 0.05f,
                normalizedWidth = 0.40f
            )
        )

        val result = detector.detect(lines)
        assertNotNull(result)
        assertEquals("rayquaza", result?.species?.speciesId)
        assertNull(result?.detectedPrefix)
    }

    @Test
    fun detectWithBox_twoLineSplitMegaCharizardX_joinsLinesAndComputesAccurateUnionBox() {
        val lines = listOf(
            OcrLine(
                text = "CP 52341",
                normalizedTop = 0.09f,
                normalizedLeft = 0.35f,
                normalizedHeight = 0.04f,
                normalizedWidth = 0.30f
            ),
            OcrLine(
                text = "MEGA",
                normalizedTop = 0.15f,
                normalizedLeft = 0.42f,
                normalizedHeight = 0.04f,
                normalizedWidth = 0.16f
            ),
            OcrLine(
                text = "CHARIZARD X",
                normalizedTop = 0.20f,
                normalizedLeft = 0.28f,
                normalizedHeight = 0.05f,
                normalizedWidth = 0.44f
            ),
            OcrLine(
                text = "01:42",
                normalizedTop = 0.30f,
                normalizedLeft = 0.45f,
                normalizedHeight = 0.03f,
                normalizedWidth = 0.10f
            )
        )

        val result = detector.detectWithBox(lines)
        assertNotNull(result)
        assertEquals("charizard_mega_x", result!!.match.species.speciesId)
        assertEquals(listOf("fire", "dragon"), result.match.species.types)
        assertTrue(result.match.alternateForms.size >= 3)
        assertEquals(0.15f, result.matchedLine.normalizedTop, 0.001f)
        assertEquals(0.28f, result.matchedLine.normalizedLeft, 0.001f)
        assertEquals(0.10f, result.matchedLine.normalizedHeight, 0.001f)
        assertEquals(0.44f, result.matchedLine.normalizedWidth, 0.001f)
    }

    @Test
    fun detect_shadowAndGigantamaxPrefixOcr_stripsPrefixAndMapsBaseWeaknesses() = runTest {
        val shadowLines = listOf(
            OcrLine(text = "SHADOW", normalizedTop = 0.14f, normalizedLeft = 0.38f, normalizedHeight = 0.04f),
            OcrLine(text = "MEWTWO", normalizedTop = 0.19f, normalizedLeft = 0.32f, normalizedHeight = 0.05f)
        )

        val shadowResult = detector.detect(shadowLines)
        assertNotNull(shadowResult)
        assertEquals("mewtwo", shadowResult?.species?.speciesId)
        assertEquals("Shadow", shadowResult?.detectedPrefix)

        val gigantamaxLines = listOf(
            OcrLine(text = "GIGANTAMAX GENGAR", normalizedTop = 0.16f, normalizedLeft = 0.22f, normalizedHeight = 0.05f)
        )

        val gmaxResult = detector.detect(gigantamaxLines)
        assertNotNull(gmaxResult)
        assertEquals("gengar", gmaxResult?.species?.speciesId)
        assertEquals("Gigantamax", gmaxResult?.detectedPrefix)
    }

    @Test
    fun detect_ignoresTopGymNameAboveTopBandAndAboveCpLine() = runTest {
        val linesWithTopGym = listOf(
            OcrLine(text = "GENGAR STATUE GYM", normalizedTop = 0.07f, normalizedLeft = 0.25f, normalizedHeight = 0.03f),
            OcrLine(text = "MEWTWO PARK", normalizedTop = 0.13f, normalizedLeft = 0.30f, normalizedHeight = 0.03f),
            OcrLine(text = "CP 45468", normalizedTop = 0.15f, normalizedLeft = 0.35f, normalizedHeight = 0.04f),
            OcrLine(text = "RAYQUAZA", normalizedTop = 0.22f, normalizedLeft = 0.28f, normalizedHeight = 0.06f),
            OcrLine(text = "BATTLE", normalizedTop = 0.72f, normalizedLeft = 0.40f, normalizedHeight = 0.05f)
        )

        val result = detector.detect(linesWithTopGym)
        assertNotNull(result)
        assertEquals("rayquaza", result?.species?.speciesId)

        val onlyTopGymLines = listOf(
            OcrLine(text = "GENGAR STATUE GYM", normalizedTop = 0.07f, normalizedLeft = 0.25f, normalizedHeight = 0.03f),
            OcrLine(text = "MEWTWO PARK", normalizedTop = 0.13f, normalizedLeft = 0.30f, normalizedHeight = 0.03f),
            OcrLine(text = "CP 45468", normalizedTop = 0.15f, normalizedLeft = 0.35f, normalizedHeight = 0.04f),
            OcrLine(text = "BATTLE", normalizedTop = 0.72f, normalizedLeft = 0.40f, normalizedHeight = 0.05f)
        )
        assertNull(detector.detect(onlyTopGymLines))
    }

    @Test
    fun detect_noiseAndButtonLinesOnly_returnsNull() = runTest {
        val noiseLines = listOf(
            OcrLine(text = "CP 52341", normalizedTop = 0.10f, normalizedLeft = 0.30f, normalizedHeight = 0.04f),
            OcrLine(text = "01:42", normalizedTop = 0.18f, normalizedLeft = 0.45f, normalizedHeight = 0.03f),
            OcrLine(text = "BATTLE", normalizedTop = 0.50f, normalizedLeft = 0.40f, normalizedHeight = 0.05f),
            OcrLine(text = "READY", normalizedTop = 0.44f, normalizedLeft = 0.42f, normalizedHeight = 0.04f),
            OcrLine(text = "REJOIN", normalizedTop = 0.44f, normalizedLeft = 0.42f, normalizedHeight = 0.04f),
            OcrLine(text = "PRIVATE GROUP", normalizedTop = 0.56f, normalizedLeft = 0.35f, normalizedHeight = 0.03f),
            OcrLine(text = "RECOMMENDED PARTY", normalizedTop = 0.62f, normalizedLeft = 0.25f, normalizedHeight = 0.03f),
            OcrLine(text = "BATTLE PARTY", normalizedTop = 0.66f, normalizedLeft = 0.30f, normalizedHeight = 0.03f),
            OcrLine(text = "INVITE", normalizedTop = 0.38f, normalizedLeft = 0.70f, normalizedHeight = 0.03f)
        )

        for (line in noiseLines) {
            assertTrue("Expected noise line to be filtered: ${line.text}", detector.isNoiseLine(line.text))
        }

        val result = detector.detect(noiseLines)
        assertNull(result)
    }

    @Test
    fun raidScreenClassifier_distinguishesLobbyPreviewAndUnknown() {
        val lobbyLines = listOf(
            OcrLine(text = "01:45", normalizedTop = 0.12f, normalizedLeft = 0.45f, normalizedHeight = 0.03f),
            OcrLine(text = "RECOMMENDED PARTY", normalizedTop = 0.55f, normalizedLeft = 0.25f, normalizedHeight = 0.03f),
            OcrLine(text = "READY", normalizedTop = 0.75f, normalizedLeft = 0.38f, normalizedHeight = 0.04f),
            OcrLine(text = "INVITE", normalizedTop = 0.45f, normalizedLeft = 0.72f, normalizedHeight = 0.03f)
        )
        assertEquals(RaidScreenType.RAID_LOBBY, RaidScreenClassifier.classify(lobbyLines))

        val previewLines = listOf(
            OcrLine(text = "CP 45468", normalizedTop = 0.15f, normalizedLeft = 0.35f, normalizedHeight = 0.04f),
            OcrLine(text = "RAYQUAZA", normalizedTop = 0.22f, normalizedLeft = 0.30f, normalizedHeight = 0.05f),
            OcrLine(text = "BATTLE", normalizedTop = 0.75f, normalizedLeft = 0.40f, normalizedHeight = 0.05f),
            OcrLine(text = "PRIVATE GROUP", normalizedTop = 0.85f, normalizedLeft = 0.35f, normalizedHeight = 0.03f)
        )
        assertEquals(RaidScreenType.RAID_PREVIEW, RaidScreenClassifier.classify(previewLines))

        val unknownLines = listOf(
            OcrLine(text = "SETTINGS", normalizedTop = 0.20f, normalizedLeft = 0.40f, normalizedHeight = 0.04f)
        )
        assertEquals(RaidScreenType.UNKNOWN, RaidScreenClassifier.classify(unknownLines))
    }

    @Test
    fun capturePipelineRouter_strictlyIsolatesRaidAndScanModes() = runTest {
        var raidCalled = false
        var scanCalled = false

        val scanResult = CapturePipelineRouter.dispatch(
            mode = OverlayMode.SCAN,
            onRaid = {
                raidCalled = true
                "raid"
            },
            onScan = {
                scanCalled = true
                "scan"
            }
        )
        assertFalse(raidCalled)
        assertTrue(scanCalled)
        assertEquals(CaptureRouteResult.Scan("scan"), scanResult)

        raidCalled = false
        scanCalled = false

        val raidResult = CapturePipelineRouter.dispatch(
            mode = OverlayMode.RAID,
            onRaid = {
                raidCalled = true
                "raid"
            },
            onScan = {
                scanCalled = true
                "scan"
            }
        )
        assertTrue(raidCalled)
        assertFalse(scanCalled)
        assertEquals(CaptureRouteResult.Raid("raid"), raidResult)
    }

    @Test
    fun raidFlowCoordinator_cachesPreviewBossForLobbyAndPromptsWhenEmpty() {
        val coordinator = RaidFlowCoordinator(detector)

        val lobbyLines = listOf(
            OcrLine(text = "MEWTWO", normalizedTop = 0.20f, normalizedLeft = 0.30f, normalizedHeight = 0.03f),
            OcrLine(text = "RECOMMENDED PARTY", normalizedTop = 0.55f, normalizedLeft = 0.25f, normalizedHeight = 0.03f),
            OcrLine(text = "READY", normalizedTop = 0.75f, normalizedLeft = 0.38f, normalizedHeight = 0.04f)
        )

        // Lobby without activeRaidBoss prompts user to scan Preview first
        val promptAction = coordinator.handleCapturedLines(lobbyLines)
        assertTrue(promptAction is RaidCaptureAction.PromptScanPreviewFirst)
        assertNull(coordinator.activeRaidBoss)

        // Preview scan detects Rayquaza and caches activeRaidBoss
        val previewLines = listOf(
            OcrLine(text = "CP 45468", normalizedTop = 0.14f, normalizedLeft = 0.35f, normalizedHeight = 0.04f),
            OcrLine(text = "RAYQUAZA", normalizedTop = 0.22f, normalizedLeft = 0.28f, normalizedHeight = 0.05f),
            OcrLine(text = "BATTLE", normalizedTop = 0.75f, normalizedLeft = 0.40f, normalizedHeight = 0.05f)
        )
        val previewAction = coordinator.handleCapturedLines(previewLines)
        assertTrue(previewAction is RaidCaptureAction.ShowPreviewWeaknesses)
        val detected = (previewAction as RaidCaptureAction.ShowPreviewWeaknesses).detection
        assertEquals("rayquaza", detected.match.species.speciesId)
        assertEquals("rayquaza", coordinator.activeRaidBoss?.species?.speciesId)

        // Subsequent Lobby scan reuses cached Rayquaza instead of re-parsing lobby text ("MEWTWO")
        val lobbyAction = coordinator.handleCapturedLines(lobbyLines)
        assertTrue(lobbyAction is RaidCaptureAction.ShowLobbySuggestedParty)
        val cachedBoss = (lobbyAction as RaidCaptureAction.ShowLobbySuggestedParty).cachedBoss
        assertEquals("rayquaza", cachedBoss.species.speciesId)

        // Clearing active boss resets to prompt on Lobby
        coordinator.clearActiveBoss()
        assertNull(coordinator.activeRaidBoss)
        assertTrue(coordinator.handleCapturedLines(lobbyLines) is RaidCaptureAction.PromptScanPreviewFirst)
    }
}
