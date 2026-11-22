package com.pogooverlay.app.core.pokebox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class PokebattlerClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val cache = ConcurrentHashMap<String, List<PokebattlerTopCounter>>()

    suspend fun fetchTopCounters(
        pokebattlerId: String,
        raidTier: Int = 5
    ): Result<List<PokebattlerTopCounter>> = withContext(Dispatchers.IO) {
        val cleanId = pokebattlerId.trim().uppercase()
        if (cleanId.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Empty boss ID"))
        val cacheKey = "${cleanId}_T$raidTier"
        cache[cacheKey]?.let { return@withContext Result.success(it) }

        val levelStr = when (raidTier) {
            1 -> "RAID_LEVEL_1"
            3 -> "RAID_LEVEL_3"
            4 -> "RAID_LEVEL_4_5"
            6 -> "RAID_LEVEL_MEGA_5"
            else -> "RAID_LEVEL_5"
        }

        val url = "https://fight.pokebattler.com/raids/defenders/$cleanId/levels/$levelStr" +
            "/attackers/levels/40/strategies/CINEMATIC_ATTACK_WHEN_POSSIBLE/DEFENSE_RANDOM_MC" +
            "?sort=ESTIMATOR&weatherCondition=NO_WEATHER&dodgeStrategy=DODGE_REACTION_TIME&aggregation=AVERAGE"

        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "PoGoRaidOverlay/1.0")
            }
            val body = try {
                if (conn.responseCode !in 200..299) {
                    throw IllegalStateException("Pokébattler HTTP ${conn.responseCode}")
                }
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }

            val parsed = json.decodeFromString<PbRaidResponse>(body)
            val defenders = parsed.attackers.firstOrNull()?.byMove?.firstOrNull()?.defenders.orEmpty()
            val counters = defenders.mapNotNull { def ->
                val bestMove = def.byMove.minByOrNull { it.result?.estimator ?: 999.0 } ?: return@mapNotNull null
                val res = bestMove.result ?: return@mapNotNull null
                PokebattlerTopCounter(
                    pokemonId = def.pokemonId,
                    move1 = bestMove.move1,
                    move2 = bestMove.move2,
                    estimator = res.estimator,
                    timeToWinSec = (res.effectiveCombatTime / 1000.0).toInt()
                )
            }.sortedBy { it.estimator }.take(6)

            cache[cacheKey] = counters
            Result.success(counters)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Serializable
    private data class PbRaidResponse(
        val attackers: List<PbAttackerGroup> = emptyList()
    )

    @Serializable
    private data class PbAttackerGroup(
        val byMove: List<PbAttackerMoveGroup> = emptyList()
    )

    @Serializable
    private data class PbAttackerMoveGroup(
        val defenders: List<PbDefenderEntry> = emptyList()
    )

    @Serializable
    private data class PbDefenderEntry(
        val pokemonId: String = "",
        val byMove: List<PbMoveResultEntry> = emptyList()
    )

    @Serializable
    private data class PbMoveResultEntry(
        val move1: String = "",
        val move2: String = "",
        val result: PbResultMetrics? = null
    )

    @Serializable
    private data class PbResultMetrics(
        val estimator: Double = 99.0,
        val effectiveCombatTime: Double = 0.0
    )
}
