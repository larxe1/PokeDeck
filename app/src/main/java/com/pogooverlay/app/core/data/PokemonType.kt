package com.pogooverlay.app.core.data

enum class PokemonType(
    val displayName: String,
    val apiKey: String
) {
    NORMAL("Normal", "normal"),
    FIRE("Fire", "fire"),
    WATER("Water", "water"),
    ELECTRIC("Electric", "electric"),
    GRASS("Grass", "grass"),
    ICE("Ice", "ice"),
    FIGHTING("Fighting", "fighting"),
    POISON("Poison", "poison"),
    GROUND("Ground", "ground"),
    FLYING("Flying", "flying"),
    PSYCHIC("Psychic", "psychic"),
    BUG("Bug", "bug"),
    ROCK("Rock", "rock"),
    GHOST("Ghost", "ghost"),
    DRAGON("Dragon", "dragon"),
    DARK("Dark", "dark"),
    STEEL("Steel", "steel"),
    FAIRY("Fairy", "fairy");

    companion object {
        private val byKey: Map<String, PokemonType> = entries.associateBy { it.apiKey }

        fun fromApiKey(key: String): PokemonType? {
            val normalized = key.trim().lowercase()
            return byKey[normalized]
        }
    }
}
