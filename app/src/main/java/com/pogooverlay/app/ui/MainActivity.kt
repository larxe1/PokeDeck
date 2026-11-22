package com.pogooverlay.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.pogooverlay.app.PoGoOverlayApp
import com.pogooverlay.app.core.data.OverlayStateHolder
import com.pogooverlay.app.overlay.OverlayService
import com.pogooverlay.app.ui.home.HomeScreen
import com.pogooverlay.app.ui.theme.PoGoOverlayTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        private const val PREFS_NAME = "pogo_overlay_prefs"
        private const val KEY_DEBUG_ANNOTATED_CAPTURE = "debug_annotated_capture"
    }

    private var hasOverlayPermission by mutableStateOf(false)
    private var hasNotificationPermission by mutableStateOf(true)

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        hasOverlayPermission = Settings.canDrawOverlays(this)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasNotificationPermission = granted
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            OverlayService.start(this, result.resultCode, result.data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshPermissionFlags()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        OverlayStateHolder.setDebugSaveAnnotatedCapture(
            prefs.getBoolean(KEY_DEBUG_ANNOTATED_CAPTURE, false)
        )

        val repository = PoGoOverlayApp.instance.repository
        val pokeboxRepository = PoGoOverlayApp.instance.pokeboxRepository
        repository.ensureLoaded()
        pokeboxRepository.ensureLoaded()
        lifecycleScope.launch {
            repository.refreshLiveRaids()
        }

        setContent {
            PoGoOverlayTheme {
                val species by repository.species.collectAsStateWithLifecycle()
                val activeRaids by repository.activeRaids.collectAsStateWithLifecycle()
                val lastRefresh by repository.lastRaidsRefreshEpochMs.collectAsStateWithLifecycle()
                val isRefreshing by repository.isRefreshing.collectAsStateWithLifecycle()
                val lastError by repository.lastError.collectAsStateWithLifecycle()
                val savedPokemon by pokeboxRepository.savedPokemon.collectAsStateWithLifecycle()
                val movesById by pokeboxRepository.movesById.collectAsStateWithLifecycle()
                val overlayState by OverlayStateHolder.state.collectAsStateWithLifecycle()
                val matcher = remember(species) { repository.getMatcher() }

                HomeScreen(
                    species = species,
                    activeRaids = activeRaids,
                    lastRaidsRefreshEpochMs = lastRefresh,
                    isRefreshing = isRefreshing,
                    lastError = lastError,
                    savedPokemon = savedPokemon,
                    movesById = movesById,
                    overlayState = overlayState,
                    hasOverlayPermission = hasOverlayPermission,
                    hasNotificationPermission = hasNotificationPermission,
                    needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                    matcher = matcher,
                    onRefreshRaids = {
                        lifecycleScope.launch {
                            repository.refreshLiveRaids()
                        }
                    },
                    onSavePokemon = { pokeboxRepository.savePokemon(it) },
                    onDeletePokemon = { pokeboxRepository.deletePokemon(it) },
                    onRequestOverlayPermission = ::launchOverlayPermissionSettings,
                    onRequestNotificationPermission = ::requestNotificationPermissionIfNeeded,
                    onToggleDebugAnnotatedCapture = { enabled ->
                        prefs.edit().putBoolean(KEY_DEBUG_ANNOTATED_CAPTURE, enabled).apply()
                        OverlayStateHolder.setDebugSaveAnnotatedCapture(enabled)
                    },
                    onActivateOverlay = ::activateOverlayWithCapture,
                    onStartManualOnly = ::startManualOnlyOverlay,
                    onStopOverlay = { OverlayService.stop(this) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionFlags()
    }

    private fun refreshPermissionFlags() {
        hasOverlayPermission = Settings.canDrawOverlays(this)
        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun launchOverlayPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun activateOverlayWithCapture() {
        if (!Settings.canDrawOverlays(this)) {
            launchOverlayPermissionSettings()
            return
        }
        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun startManualOnlyOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            launchOverlayPermissionSettings()
            return
        }
        OverlayService.start(this, null, null)
    }
}
