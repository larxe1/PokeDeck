package com.pogooverlay.app.core.pokebox

import com.pogooverlay.app.core.data.PokemonSpecies
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class MoveData(
    val moveId: String,
    val displayName: String,
    val type: String,
    val power: Double,
    val durationMs: Int,
    val energyDelta: Int,
    val isFast: Boolean
)

@Serializable
data class SavedPokemon(
    val id: String,
    val speciesId: String,
    val pokebattlerId: String,
    val nickname: String? = null,
    val cp: Int,
    val hp: Int? = null,
    val ivAtk: Int,
    val ivDef: Int,
    val ivSta: Int,
    val level: Double,
    val isShadow: Boolean = false,
    val isPurified: Boolean = false,
    val isLucky: Boolean = false,
    val isMegaCapable: Boolean = false,
    val fastMoveId: String? = null,
    val chargedMove1Id: String? = null,
    val chargedMove2Id: String? = null,
    val savedAtMs: Long = 0L
) {
    fun ivPercent(): Int = ((ivAtk + ivDef + ivSta) * 100.0 / 45.0).roundToInt()
}

data class PowerUpCost(
    val currentLevel: Double,
    val targetLevel: Double,
    val stardust: Int,
    val candy: Int,
    val xlCandy: Int,
    val targetCp: Int,
    val targetHp: Int
)

data class MovesetRecommendation(
    val bestFast: MoveData,
    val bestCharged: MoveData,
    val bestNeutralDps: Double,
    val currentNeutralDps: Double?,
    val isCurrentOptimal: Boolean,
    val recommendationText: String
)

data class PersonalCounterResult(
    val pokemon: SavedPokemon,
    val species: PokemonSpecies,
    val fastMove: MoveData?,
    val chargedMove: MoveData?,
    val dps: Double,
    val tdo: Double,
    val estimator: Double,
    val damagePercentOfBoss: Double,
    val usingSuboptimalMoves: Boolean,
    val recommendedMoveHint: String?
)

data class RaidWinnabilityVerdict(
    val bossName: String,
    val raidTier: Int,
    val bossHp: Int,
    val timerSec: Int,
    val team: List<PersonalCounterResult>,
    val teamEstimator: Double,
    val totalDamagePercent: Double,
    val verdictLabel: String,
    val verdictDetail: String
)

data class PokebattlerTopCounter(
    val pokemonId: String,
    val move1: String,
    val move2: String,
    val estimator: Double,
    val timeToWinSec: Int
)
