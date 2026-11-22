package com.pogooverlay.app.core.data

import kotlinx.serialization.Serializable

@Serializable
data class PokemonSpecies(
    val speciesId: String,
    val speciesName: String,
    val dex: Int,
    val types: List<String>,
    val tags: List<String> = emptyList(),
    val spriteFile: String? = null,
    val pokebattlerId: String? = null,
    val baseAtk: Int = 0,
    val baseDef: Int = 0,
    val baseSta: Int = 0,
    val quickMoves: List<String> = emptyList(),
    val cinematicMoves: List<String> = emptyList()
) {
    fun parsedTypes(): List<PokemonType> =
        types.mapNotNull { PokemonType.fromApiKey(it) }.distinct()

    fun baseSpeciesName(): String {
        val parenIdx = speciesName.indexOf(" (")
        if (parenIdx >= 0) {
            return speciesName.substring(0, parenIdx).trim()
        }
        val underscoreIdx = speciesName.indexOf('_')
        if (underscoreIdx > 0) {
            return speciesName.substring(0, underscoreIdx).trim()
        }
        return speciesName.trim()
    }

    fun formLabel(): String? {
        val start = speciesName.indexOf(" (")
        val end = speciesName.lastIndexOf(')')
        if (start >= 0 && end > start + 2) {
            return speciesName.substring(start + 2, end).trim().ifEmpty { null }
        }
        val underscoreIdx = speciesName.indexOf('_')
        if (underscoreIdx > 0 && underscoreIdx < speciesName.length - 1) {
            return speciesName.substring(underscoreIdx + 1)
                .split('_')
                .filter { it.isNotBlank() }
                .joinToString(" ") { token ->
                    token.replaceFirstChar { ch -> ch.uppercaseChar() }
                }
                .ifEmpty { null }
        }
        return null
    }
}

@Serializable
data class RaidBossEntry(
    val name: String,
    val tier: String,
    val types: List<String>,
    val cpMin: Int? = null,
    val cpMax: Int? = null,
    val boostedCpMin: Int? = null,
    val boostedCpMax: Int? = null,
    val canBeShiny: Boolean = false
)

data class TypeMatchup(
    val attackType: PokemonType,
    val multiplier: Double,
    val tier: Int
)

data class MatchupSummary(
    val defendingTypes: List<PokemonType>,
    val doubleWeaknesses: List<TypeMatchup>,
    val singleWeaknesses: List<TypeMatchup>,
    val resistances: List<TypeMatchup>
) {
    val orderedWeaknesses: List<TypeMatchup>
        get() = doubleWeaknesses + singleWeaknesses
}

data class SpeciesMatchResult(
    val species: PokemonSpecies,
    val displayTitle: String,
    val detectedPrefix: String?,
    val alternateForms: List<PokemonSpecies>,
    val confidence: Float
)
