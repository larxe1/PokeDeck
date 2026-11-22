package com.pogooverlay.app

import android.app.Application
import com.pogooverlay.app.core.data.PokemonRepository

class PoGoOverlayApp : Application() {

    lateinit var repository: PokemonRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = PokemonRepository(this)
        repository.ensureLoaded()
    }

    companion object {
        lateinit var instance: PoGoOverlayApp
            private set
    }
}
