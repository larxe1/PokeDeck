package com.pogooverlay.app.core.pokebox

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class PokeboxRepository(
    private val context: Context
) {
    private val appContext = context.applicationContext

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val loaded = AtomicBoolean(false)

    private val _movesById = MutableStateFlow<Map<String, MoveData>>(emptyMap())
    val movesById: StateFlow<Map<String, MoveData>> = _movesById.asStateFlow()

    private val _savedPokemon = MutableStateFlow<List<SavedPokemon>>(emptyList())
    val savedPokemon: StateFlow<List<SavedPokemon>> = _savedPokemon.asStateFlow()

    fun ensureLoaded() {
        if (!loaded.compareAndSet(false, true)) return

        runCatching {
            val movesText = appContext.assets.open(MOVES_DB_ASSET).bufferedReader().use { it.readText() }
            val movesList = json.decodeFromString<List<MoveData>>(movesText)
            _movesById.value = movesList.associateBy { it.moveId }
        }

        val boxFile = File(appContext.filesDir, POKEBOX_FILENAME)
        if (boxFile.exists() && boxFile.length() > 0L) {
            runCatching {
                val raw = boxFile.readText()
                _savedPokemon.value = json.decodeFromString<List<SavedPokemon>>(raw)
                    .sortedByDescending { it.cp }
            }
        }
    }

    @Synchronized
    fun savePokemon(pokemon: SavedPokemon) {
        ensureLoaded()
        val updated = (_savedPokemon.value.filterNot { it.id == pokemon.id } + pokemon)
            .sortedByDescending { it.cp }
        _savedPokemon.value = updated
        persist(updated)
    }

    @Synchronized
    fun deletePokemon(id: String) {
        ensureLoaded()
        val updated = _savedPokemon.value.filterNot { it.id == id }
        _savedPokemon.value = updated
        persist(updated)
    }

    private fun persist(list: List<SavedPokemon>) {
        runCatching {
            File(appContext.filesDir, POKEBOX_FILENAME).writeText(json.encodeToString(list))
        }
    }

    companion object {
        private const val MOVES_DB_ASSET = "moves_db.json"
        private const val POKEBOX_FILENAME = "pokebox.json"
    }
}
