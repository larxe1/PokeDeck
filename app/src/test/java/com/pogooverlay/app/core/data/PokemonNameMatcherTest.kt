package com.pogooverlay.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PokemonNameMatcherTest {

    private lateinit var matcher: PokemonNameMatcher

    private val sampleSpecies = listOf(
        PokemonSpecies("pidgey", "Pidgey", 16, listOf("normal", "flying")),
        PokemonSpecies("pidgeotto", "Pidgeotto", 17, listOf("normal", "flying")),
        PokemonSpecies("pidgeot", "Pidgeot", 18, listOf("normal", "flying")),
        PokemonSpecies("charizard", "Charizard", 6, listOf("fire", "flying")),
        PokemonSpecies("charizard_mega_x", "Charizard (Mega X)", 6, listOf("fire", "dragon")),
        PokemonSpecies("charizard_mega_y", "Charizard (Mega Y)", 6, listOf("fire", "flying")),
        PokemonSpecies("meowth", "Meowth", 52, listOf("normal")),
        PokemonSpecies("meowth_alolan", "Meowth (Alolan)", 52, listOf("dark")),
        PokemonSpecies("meowth_galarian", "Meowth (Galarian)", 52, listOf("steel")),
        PokemonSpecies("rhyhorn", "Rhyhorn", 111, listOf("ground", "rock")),
        PokemonSpecies("rhydon", "Rhydon", 112, listOf("ground", "rock")),
        PokemonSpecies("gyarados", "Gyarados", 130, listOf("water", "flying")),
        PokemonSpecies("gyarados_mega", "Gyarados (Mega)", 130, listOf("water", "dark")),
        PokemonSpecies("mewtwo", "Mewtwo", 150, listOf("psychic")),
        PokemonSpecies("mewtwo_armored", "Mewtwo (Armored)", 150, listOf("psychic")),
        PokemonSpecies("mewtwo_mega_x", "Mewtwo (Mega X)", 150, listOf("psychic", "fighting")),
        PokemonSpecies("mewtwo_mega_y", "Mewtwo (Mega Y)", 150, listOf("psychic")),
        PokemonSpecies("tyranitar", "Tyranitar", 248, listOf("rock", "dark")),
        PokemonSpecies("tyranitar_mega", "Tyranitar (Mega)", 248, listOf("rock", "dark")),
        PokemonSpecies("swampert", "Swampert", 260, listOf("water", "ground")),
        PokemonSpecies("swampert_mega", "Swampert (Mega)", 260, listOf("water", "ground")),
        PokemonSpecies("kirlia", "Kirlia", 281, listOf("psychic", "fairy")),
        PokemonSpecies("gardevoir", "Gardevoir", 282, listOf("psychic", "fairy")),
        PokemonSpecies("piplup", "Piplup", 393, listOf("water")),
        PokemonSpecies("prinplup", "Prinplup", 394, listOf("water")),
        PokemonSpecies("giratina_altered", "Giratina (Altered)", 487, listOf("ghost", "dragon")),
        PokemonSpecies("giratina_origin", "Giratina (Origin)", 487, listOf("ghost", "dragon")),
        PokemonSpecies("necrozma", "Necrozma", 800, listOf("psychic")),
        PokemonSpecies("necrozma_dusk_mane", "Necrozma (Dusk Mane)", 800, listOf("psychic", "steel")),
        PokemonSpecies("necrozma_dawn_wings", "Necrozma (Dawn Wings)", 800, listOf("psychic", "ghost")),
        PokemonSpecies("calyrex", "Calyrex", 898, listOf("psychic", "grass")),
        PokemonSpecies("calyrex_ice_rider", "Calyrex (Ice Rider)", 898, listOf("psychic", "ice")),
        PokemonSpecies("calyrex_shadow_rider", "Calyrex (Shadow Rider)", 898, listOf("psychic", "ghost"))
    )

    @Before
    fun setUp() {
        matcher = PokemonNameMatcher(sampleSpecies)
    }

    @Test
    fun matchesShadowMewtwo_andRecordsShadowPrefix() {
        val result = matcher.matchText("Shadow Mewtwo")
        assertNotNull(result)
        assertEquals("mewtwo", result!!.species.speciesId)
        assertEquals("Shadow", result.detectedPrefix)
        assertEquals("Shadow Mewtwo", result.displayTitle)
        assertEquals(4, result.alternateForms.size)
    }

    @Test
    fun matchesGigantamaxCharizard_andStripsPrefixVariants() {
        val gmaxFull = matcher.matchText("Gigantamax Charizard")
        assertNotNull(gmaxFull)
        assertEquals("charizard", gmaxFull!!.species.speciesId)
        assertEquals("Gigantamax", gmaxFull.detectedPrefix)
        assertEquals("Gigantamax Charizard", gmaxFull.displayTitle)

        val gmaxShort = matcher.matchText("G-Max Charizard")
        assertNotNull(gmaxShort)
        assertEquals("charizard", gmaxShort!!.species.speciesId)
        assertEquals("Gigantamax", gmaxShort.detectedPrefix)
    }

    @Test
    fun matchesMegaForms_preservingTypeChangingModifiers() {
        val megaCharX = matcher.matchText("Mega Charizard X")
        assertNotNull(megaCharX)
        assertEquals("charizard_mega_x", megaCharX!!.species.speciesId)
        assertNull(megaCharX.detectedPrefix)
        assertEquals("Mega Charizard X", megaCharX.displayTitle)
        assertEquals(listOf(PokemonType.FIRE, PokemonType.DRAGON), megaCharX.species.parsedTypes())

        val megaTyranitar = matcher.matchText("Mega Tyranitar")
        assertNotNull(megaTyranitar)
        assertEquals("tyranitar_mega", megaTyranitar!!.species.speciesId)
        assertEquals("Mega Tyranitar", megaTyranitar.displayTitle)
    }

    @Test
    fun matchesDuskManeNecrozma_inBothWordOrders() {
        val prefixOrder = matcher.matchText("Dusk Mane Necrozma")
        assertNotNull(prefixOrder)
        assertEquals("necrozma_dusk_mane", prefixOrder!!.species.speciesId)
        assertEquals("Dusk Mane Necrozma", prefixOrder.displayTitle)
        assertEquals(listOf(PokemonType.PSYCHIC, PokemonType.STEEL), prefixOrder.species.parsedTypes())

        val suffixOrder = matcher.matchText("Necrozma (Dusk Mane)")
        assertNotNull(suffixOrder)
        assertEquals("necrozma_dusk_mane", suffixOrder!!.species.speciesId)
    }

    @Test
    fun doesNotStripShadowFromShadowRiderCalyrex() {
        val result = matcher.matchText("Shadow Rider Calyrex")
        assertNotNull(result)
        assertEquals("calyrex_shadow_rider", result!!.species.speciesId)
        assertNull(result.detectedPrefix)
    }

    @Test
    fun filtersOcrCpHpTimerNoise_andHandlesOcrTypos() {
        val noisyLobby = """
            02:45
            CP 54148
            Shadow Mewtw0
            HP 15000
            READY
        """.trimIndent()
        val mewtwoResult = matcher.matchText(noisyLobby)
        assertNotNull(mewtwoResult)
        assertEquals("mewtwo", mewtwoResult!!.species.speciesId)
        assertEquals("Shadow", mewtwoResult.detectedPrefix)

        val typoResult = matcher.matchText("CP 41250\nTyrantar\n01:30")
        assertNotNull(typoResult)
        assertEquals("tyranitar", typoResult!!.species.speciesId)
        assertTrue(typoResult.confidence >= 0.70f)
    }

    @Test
    fun disambiguatesAmbiguousBaseNameUsingActiveRaids() {
        val activeRaids = listOf(
            RaidBossEntry(
                name = "Alolan Meowth",
                tier = "1-Star Raids",
                types = listOf("dark")
            ),
            RaidBossEntry(
                name = "Origin Forme Giratina",
                tier = "5-Star Raids",
                types = listOf("ghost", "dragon")
            )
        )

        val meowthMatch = matcher.matchText("CP 3981\nMeowth", activeRaids = activeRaids)
        assertNotNull(meowthMatch)
        assertEquals("meowth_alolan", meowthMatch!!.species.speciesId)
        assertEquals("Alolan Meowth", meowthMatch.displayTitle)
        assertEquals(3, meowthMatch.alternateForms.size)

        val giratinaMatch = matcher.matchText("Giratina", activeRaids = activeRaids)
        assertNotNull(giratinaMatch)
        assertEquals("giratina_origin", giratinaMatch!!.species.speciesId)

        // Explicit form in OCR text must NOT be overridden by active raids
        val explicitGalarian = matcher.matchText("Galarian Meowth", activeRaids = activeRaids)
        assertNotNull(explicitGalarian)
        assertEquals("meowth_galarian", explicitGalarian!!.species.speciesId)
    }

    @Test
    fun search_matchesByNameDexAndFormPrefix() {
        val byDex = matcher.search("#248")
        assertTrue(byDex.isNotEmpty())
        assertEquals(248, byDex.first().dex)

        val byFormPrefix = matcher.search("Mega Char")
        assertTrue(byFormPrefix.any { it.speciesId == "charizard_mega_x" })
        assertTrue(byFormPrefix.any { it.speciesId == "charizard_mega_y" })

        val byName = matcher.search("Swampert")
        assertEquals("swampert", byName.first().speciesId)
    }

    @Test
    fun prinplupNeverMatchesPiplup_exactAndOneCharOcrTypo() {
        val exactPrinplup = matcher.matchText("Prinplup")
        assertNotNull(exactPrinplup)
        assertEquals("prinplup", exactPrinplup!!.species.speciesId)
        assertEquals(394, exactPrinplup.species.dex)
        assertEquals(1.0f, exactPrinplup.confidence, 0.0001f)

        val noisyPrinplup = matcher.matchText("Prlnplup")
        assertNotNull(noisyPrinplup)
        assertEquals("prinplup", noisyPrinplup!!.species.speciesId)
        assertEquals(394, noisyPrinplup.species.dex)

        val exactPiplup = matcher.matchText("Piplup")
        assertNotNull(exactPiplup)
        assertEquals("piplup", exactPiplup!!.species.speciesId)
        assertEquals(393, exactPiplup.species.dex)
    }

    @Test
    fun similarEvolutionPairs_matchAccurately() {
        val pidgey = matcher.matchText("Pidgey")
        assertEquals("pidgey", pidgey?.species?.speciesId)

        val pidgeyTypo = matcher.matchText("Pldgey")
        assertEquals("pidgey", pidgeyTypo?.species?.speciesId)

        val pidgeotto = matcher.matchText("Pidgeotto")
        assertEquals("pidgeotto", pidgeotto?.species?.speciesId)

        val pidgeottoTypo = matcher.matchText("Pidgeott0")
        assertEquals("pidgeotto", pidgeottoTypo?.species?.speciesId)

        val kirlia = matcher.matchText("Kirlia")
        assertEquals("kirlia", kirlia?.species?.speciesId)

        val gardevoir = matcher.matchText("Gardevoir")
        assertEquals("gardevoir", gardevoir?.species?.speciesId)

        val gardevoirTypo = matcher.matchText("Gardev0ir")
        assertEquals("gardevoir", gardevoirTypo?.species?.speciesId)

        val rhyhorn = matcher.matchText("Rhyhorn")
        assertEquals("rhyhorn", rhyhorn?.species?.speciesId)

        val rhyhornTypo = matcher.matchText("Rhyh0rn")
        assertEquals("rhyhorn", rhyhornTypo?.species?.speciesId)

        val rhydon = matcher.matchText("Rhydon")
        assertEquals("rhydon", rhydon?.species?.speciesId)

        val rhydonTypo = matcher.matchText("Rhyd0n")
        assertEquals("rhydon", rhydonTypo?.species?.speciesId)
    }
}
