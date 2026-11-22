package com.pogooverlay.app

import android.app.Application
import com.pogooverlay.app.core.data.PokemonRepository
import com.pogooverlay.app.core.pokebox.PokeboxRepository

class PoGoOverlayApp : Application() {

    lateinit var repository: PokemonRepository
        private set

    lateinit var pokeboxRepository: PokeboxRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = PokemonRepository(this)
        repository.ensureLoaded()
        pokeboxRepository = PokeboxRepository(this)
        pokeboxRepository.ensureLoaded()
    }

    companion object {
        lateinit var instance: PoGoOverlayApp
            private set
    }
}
