package com.pogooverlay.app.core.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

class PokemonRepository(
    private val context: Context
) {
    private val appContext = context.applicationContext

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val loaded = AtomicBoolean(false)

    private val _species = MutableStateFlow<List<PokemonSpecies>>(emptyList())
    val species: StateFlow<List<PokemonSpecies>> = _species.asStateFlow()

    private val _activeRaids = MutableStateFlow<List<RaidBossEntry>>(emptyList())
    val activeRaids: StateFlow<List<RaidBossEntry>> = _activeRaids.asStateFlow()

    private val _lastRaidsRefreshEpochMs = MutableStateFlow<Long?>(null)
    val lastRaidsRefreshEpochMs: StateFlow<Long?> = _lastRaidsRefreshEpochMs.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    @Volatile
    private var cachedMatcher: PokemonNameMatcher? = null

    fun ensureLoaded() {
        if (!loaded.compareAndSet(false, true)) return

        try {
            val speciesJson = appContext.assets.open(POKEMON_DB_ASSET).bufferedReader().use { it.readText() }
            val parsedSpecies = json.decodeFromString<List<PokemonSpecies>>(speciesJson)
            _species.value = parsedSpecies
            cachedMatcher = PokemonNameMatcher(parsedSpecies)
        } catch (e: Exception) {
            _lastError.value = "Failed to load bundled Pokédex: ${e.message}"
        }

        val cacheFile = File(appContext.filesDir, RAIDS_CACHE_FILENAME)
        val loadedFromCache = if (cacheFile.exists() && cacheFile.length() > 0L) {
            runCatching {
                val cachedText = cacheFile.readText()
                val parsed = parseScrapedDuckRaids(cachedText)
                _activeRaids.value = parsed
                _lastRaidsRefreshEpochMs.value = cacheFile.lastModified().takeIf { it > 0L }
                parsed.isNotEmpty()
            }.getOrDefault(false)
        } else {
            false
        }

        if (!loadedFromCache) {
            runCatching {
                val fallbackText = appContext.assets.open(RAIDS_FALLBACK_ASSET).bufferedReader().use { it.readText() }
                _activeRaids.value = parseScrapedDuckRaids(fallbackText)
            }.onFailure { e ->
                _lastError.value = "Failed to load fallback raids: ${e.message}"
            }
        }
    }

    suspend fun refreshLiveRaids(): Result<Int> = withContext(Dispatchers.IO) {
        ensureLoaded()
        _isRefreshing.value = true
        try {
            val connection = (URL(SCRAPEDDUCK_RAIDS_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = NETWORK_TIMEOUT_MS
                readTimeout = NETWORK_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "PoGoRaidOverlay/1.0")
            }
            val rawBody = try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    throw IllegalStateException("HTTP $code while refreshing live raids")
                }
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }

            val parsed = parseScrapedDuckRaids(rawBody)
            File(appContext.filesDir, RAIDS_CACHE_FILENAME).writeText(rawBody)
            _activeRaids.value = parsed
            _lastRaidsRefreshEpochMs.value = System.currentTimeMillis()
            _lastError.value = null
            Result.success(parsed.size)
        } catch (e: Exception) {
            _lastError.value = e.message ?: "Unable to refresh live raid list"
            Result.failure(e)
        } finally {
            _isRefreshing.value = false
        }
    }

    fun getMatcher(): PokemonNameMatcher {
        ensureLoaded()
        return cachedMatcher ?: PokemonNameMatcher(_species.value).also { cachedMatcher = it }
    }

    private fun parseScrapedDuckRaids(rawJson: String): List<RaidBossEntry> {
        val dtos = json.decodeFromString<List<ScrapedDuckRaidDto>>(rawJson)
        return dtos.mapNotNull { dto ->
            val name = dto.name.trim()
            if (name.isEmpty()) return@mapNotNull null
            RaidBossEntry(
                name = name,
                tier = dto.tier.trim(),
                types = dto.types.mapNotNull { t ->
                    t.name.trim().lowercase().takeIf { it.isNotEmpty() }
                },
                cpMin = dto.combatPower?.normal?.min,
                cpMax = dto.combatPower?.normal?.max,
                boostedCpMin = dto.combatPower?.boosted?.min,
                boostedCpMax = dto.combatPower?.boosted?.max,
                canBeShiny = dto.canBeShiny
            )
        }
    }

    @Serializable
    private data class ScrapedDuckRaidDto(
        val name: String = "",
        val tier: String = "",
        val canBeShiny: Boolean = false,
        val types: List<ScrapedDuckTypeDto> = emptyList(),
        val combatPower: ScrapedDuckCombatPowerDto? = null
    )

    @Serializable
    private data class ScrapedDuckTypeDto(
        val name: String = ""
    )

    @Serializable
    private data class ScrapedDuckCombatPowerDto(
        val normal: ScrapedDuckRangeDto? = null,
        val boosted: ScrapedDuckRangeDto? = null
    )

    @Serializable
    private data class ScrapedDuckRangeDto(
        val min: Int? = null,
        val max: Int? = null
    )

    companion object {
        private const val POKEMON_DB_ASSET = "pokemon_db.json"
        private const val RAIDS_FALLBACK_ASSET = "raids_fallback.json"
        private const val RAIDS_CACHE_FILENAME = "raids_cache.json"
        private const val SCRAPEDDUCK_RAIDS_URL =
            "https://raw.githubusercontent.com/bigfoott/ScrapedDuck/data/raids.min.json"
        private const val NETWORK_TIMEOUT_MS = 10_000
    }
}
