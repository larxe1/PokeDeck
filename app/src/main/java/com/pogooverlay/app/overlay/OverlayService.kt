package com.pogooverlay.app.overlay

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import java.util.UUID
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.pogooverlay.app.PoGoOverlayApp
import com.pogooverlay.app.R
import com.pogooverlay.app.core.capture.ScreenCaptureEngine
import com.pogooverlay.app.core.data.ActiveBossBanner
import com.pogooverlay.app.core.data.OverlayRuntimeState
import com.pogooverlay.app.core.data.OverlayStateHolder
import com.pogooverlay.app.core.data.PokemonRepository
import com.pogooverlay.app.core.data.PokemonSpecies
import com.pogooverlay.app.core.data.PokemonType
import com.pogooverlay.app.core.data.RaidBossEntry
import com.pogooverlay.app.core.data.ScanMode
import com.pogooverlay.app.core.data.TypeEffectivenessChart
import com.pogooverlay.app.core.detector.DetectedOwnPokemon
import com.pogooverlay.app.core.detector.MyPokemonDetector
import com.pogooverlay.app.core.detector.RaidBossDetector
import com.pogooverlay.app.core.ocr.OcrPipeline
import com.pogooverlay.app.core.pokebox.MoveData
import com.pogooverlay.app.core.pokebox.PokeboxRepository
import com.pogooverlay.app.core.pokebox.PokemonMathEngine
import com.pogooverlay.app.core.pokebox.RaidBattleSimulator
import com.pogooverlay.app.core.pokebox.SavedPokemon
import com.pogooverlay.app.ui.MainActivity
import com.pogooverlay.app.ui.components.PokemonSprite
import com.pogooverlay.app.ui.components.TypeChip
import com.pogooverlay.app.ui.components.TypeDot
import com.pogooverlay.app.ui.components.WeaknessPriorityStrip
import com.pogooverlay.app.ui.components.badgeColor
import com.pogooverlay.app.ui.components.contentColor
import com.pogooverlay.app.ui.pokebox.formatLevel
import com.pogooverlay.app.ui.theme.PoGoOverlayTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlin.math.roundToInt

class OverlayService : Service() {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "pogo_raid_overlay_channel"
        private const val NOTIFICATION_ID = 4101
        private const val AUTO_WATCH_INTERVAL_MS = 1500L

        const val ACTION_START = "com.pogooverlay.app.action.START_OVERLAY"
        const val ACTION_STOP = "com.pogooverlay.app.action.STOP_OVERLAY"
        private const val EXTRA_HAS_PROJECTION = "extra_has_projection"
        private const val EXTRA_RESULT_CODE = "extra_result_code"
        private const val EXTRA_RESULT_DATA = "extra_result_data"

        fun start(
            context: Context,
            resultCode: Int? = null,
            resultData: Intent? = null
        ) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_START
                if (resultCode != null && resultData != null) {
                    putExtra(EXTRA_HAS_PROJECTION, true)
                    putExtra(EXTRA_RESULT_CODE, resultCode)
                    putExtra(EXTRA_RESULT_DATA, resultData)
                }
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            OverlayStateHolder.resetStopped()
            val intent = Intent(context, OverlayService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val scanMutex = Mutex()

    private lateinit var windowManager: WindowManager
    private lateinit var repository: PokemonRepository
    private lateinit var pokeboxRepository: PokeboxRepository
    private lateinit var captureEngine: ScreenCaptureEngine
    private lateinit var ocrPipeline: OcrPipeline
    private lateinit var raidBossDetector: RaidBossDetector
    private lateinit var myPokemonDetector: MyPokemonDetector

    private var pillLifecycleOwner: OverlayLifecycleOwner? = null
    private var bannerLifecycleOwner: OverlayLifecycleOwner? = null
    private var drawerLifecycleOwner: OverlayLifecycleOwner? = null

    private var edgePillView: ComposeView? = null
    private var bannerView: ComposeView? = null
    private var drawerView: ComposeView? = null

    private var edgePillParams: WindowManager.LayoutParams? = null
    private var bannerParams: WindowManager.LayoutParams? = null
    private var drawerParams: WindowManager.LayoutParams? = null

    private var autoWatchJob: Job? = null
    private var messageClearJob: Job? = null

    private val _isDrawerOpen = MutableStateFlow(false)
    private val isDrawerOpen: StateFlow<Boolean> = _isDrawerOpen.asStateFlow()

    @Volatile
    private var isScreenOn: Boolean = true

    private var isScreenReceiverRegistered: Boolean = false

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    if (::captureEngine.isInitialized) {
                        captureEngine.isScreenOn = false
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    if (::captureEngine.isInitialized) {
                        captureEngine.isScreenOn = true
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        repository = try {
            PoGoOverlayApp.instance.repository
        } catch (_: Throwable) {
            PokemonRepository(applicationContext)
        }
        repository.ensureLoaded()
        pokeboxRepository = try {
            PoGoOverlayApp.instance.pokeboxRepository
        } catch (_: Throwable) {
            PokeboxRepository(applicationContext)
        }
        pokeboxRepository.ensureLoaded()

        isScreenOn = queryIsScreenInteractive()

        captureEngine = ScreenCaptureEngine(
            context = applicationContext,
            onProjectionStopped = {
                OverlayStateHolder.update { state ->
                    state.copy(
                        hasMediaProjection = false,
                        scanMode = ScanMode.BATTERY_SAVER_ONE_TAP,
                        lastScanMessage = "Capture ended — Saver mode"
                    )
                }
            }
        )
        captureEngine.isScreenOn = isScreenOn

        ocrPipeline = OcrPipeline()
        raidBossDetector = RaidBossDetector(
            matcherProvider = { repository.getMatcher() },
            activeRaidsProvider = { repository.activeRaids.value }
        )
        myPokemonDetector = MyPokemonDetector(
            matcherProvider = { repository.getMatcher() },
            movesProvider = { pokeboxRepository.movesById.value },
            activeRaidsProvider = { repository.activeRaids.value }
        )

        registerScreenStateReceiver()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopOverlayService()
            return START_NOT_STICKY
        }

        val hasProjectionExtra = intent?.getBooleanExtra(EXTRA_HAS_PROJECTION, false) == true
        val resultCode: Int? = if (hasProjectionExtra) {
            intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        } else {
            null
        }
        val resultData: Intent? = if (hasProjectionExtra) {
            extractProjectionIntent(intent)
        } else {
            null
        }

        val hasNewProjectionGrant = resultCode != null && resultData != null
        val useMediaProjectionForegroundType =
            hasNewProjectionGrant || captureEngine.hasActiveProjection

        // CRITICAL ANDROID 14/15 ORDER:
        // Call ServiceCompat.startForeground(...) BEFORE calling mediaProjectionManager.getMediaProjection(...)
        startForegroundWithType(useMediaProjectionForegroundType)

        if (!Settings.canDrawOverlays(this)) {
            OverlayStateHolder.resetStopped()
            stopSelf()
            return START_NOT_STICKY
        }

        if (hasNewProjectionGrant && resultCode != null && resultData != null) {
            val started = startMediaProjectionCapture(resultCode, resultData)
            OverlayStateHolder.update { current ->
                current.copy(
                    isRunning = true,
                    hasMediaProjection = started,
                    scanMode = if (started) ScanMode.AUTO_WATCH else ScanMode.BATTERY_SAVER_ONE_TAP,
                    lastScanMessage = if (started) null else "Manual drawer mode"
                )
            }
        } else {
            val alreadyCapturing = captureEngine.hasActiveProjection
            OverlayStateHolder.update { current ->
                current.copy(
                    isRunning = true,
                    hasMediaProjection = alreadyCapturing,
                    scanMode = if (alreadyCapturing) current.scanMode else ScanMode.BATTERY_SAVER_ONE_TAP
                )
            }
        }

        ensureOverlayWindowsAttached()
        ensureAutoWatchLoop()

        return START_STICKY
    }

    private fun startForegroundWithType(withMediaProjectionType: Boolean) {
        createNotificationChannel()
        val notification = buildForegroundNotification()
        try {
            when {
                withMediaProjectionType && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    )
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                }
                else -> {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        } catch (_: Throwable) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun startMediaProjectionCapture(resultCode: Int, resultData: Intent): Boolean {
        return try {
            val projectionManager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
                ?: return false
            captureEngine.start(mediaProjection)
        } catch (_: Throwable) {
            false
        }
    }

    private fun extractProjectionIntent(intent: Intent?): Intent? {
        if (intent == null) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
    }

    private fun ensureOverlayWindowsAttached() {
        if (edgePillView == null) {
            attachEdgePillWindow()
        }
        if (bannerView == null) {
            attachBannerWindow()
        }
        if (drawerView == null) {
            attachDrawerWindow()
        }
    }

    private fun attachEdgePillWindow() {
        val owner = OverlayLifecycleOwner().also {
            it.performCreate()
            it.performStart()
            it.performResume()
            pillLifecycleOwner = it
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 320
        }
        edgePillParams = params

        val view = ComposeView(this).apply {
            owner.attachToView(this)
            setContent {
                PoGoOverlayTheme {
                    val runtimeState by OverlayStateHolder.state.collectAsState()
                    val drawerOpen by isDrawerOpen.collectAsState()
                    CompactEdgePillOverlay(
                        state = runtimeState,
                        isDrawerOpen = drawerOpen,
                        onDrag = { dx, dy ->
                            updateEdgePillPosition(dx, dy)
                        },
                        onToggleScanMode = {
                            toggleScanMode()
                        },
                        onOneTapScan = {
                            triggerManualScan()
                        },
                        onToggleDrawer = {
                            setDrawerOpen(!drawerOpen)
                        }
                    )
                }
            }
        }

        try {
            windowManager.addView(view, params)
            edgePillView = view
        } catch (_: Throwable) {
            owner.performDestroy()
            pillLifecycleOwner = null
        }
    }

    private fun attachBannerWindow() {
        val owner = OverlayLifecycleOwner().also {
            it.performCreate()
            it.performStart()
            it.performResume()
            bannerLifecycleOwner = it
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 28
        }
        bannerParams = params

        val view = ComposeView(this).apply {
            owner.attachToView(this)
            visibility = View.GONE
            setContent {
                PoGoOverlayTheme {
                    val runtimeState by OverlayStateHolder.state.collectAsState()
                    val savedPokemon by pokeboxRepository.savedPokemon.collectAsState()
                    val movesById by pokeboxRepository.movesById.collectAsState()
                    val allSpecies by repository.species.collectAsState()
                    val speciesById = remember(allSpecies) { allSpecies.associateBy { it.speciesId } }
                    val banner = runtimeState.activeBanner
                    if (banner != null) {
                        PriorityWeaknessBannerOverlay(
                            banner = banner,
                            savedPokemon = savedPokemon,
                            speciesById = speciesById,
                            movesById = movesById,
                            onSelectForm = { selectedForm ->
                                OverlayStateHolder.selectForm(
                                    species = selectedForm,
                                    allForms = banner.match.alternateForms,
                                    detectedPrefix = banner.match.detectedPrefix
                                )
                            },
                            onDismiss = {
                                OverlayStateHolder.dismissBanner()
                            }
                        )
                    }
                }
            }
        }

        try {
            windowManager.addView(view, params)
            bannerView = view

            serviceScope.launch {
                OverlayStateHolder.state.collect { state ->
                    val shouldBeVisible = state.activeBanner != null
                    val targetVisibility = if (shouldBeVisible) View.VISIBLE else View.GONE
                    if (view.visibility != targetVisibility) {
                        view.visibility = targetVisibility
                    }
                }
            }
        } catch (_: Throwable) {
            owner.performDestroy()
            bannerLifecycleOwner = null
        }
    }

    private fun attachDrawerWindow() {
        val owner = OverlayLifecycleOwner().also {
            it.performCreate()
            it.performStart()
            it.performResume()
            drawerLifecycleOwner = it
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        drawerParams = params

        val view = ComposeView(this).apply {
            owner.attachToView(this)
            visibility = View.GONE
            setContent {
                PoGoOverlayTheme {
                    val drawerOpen by isDrawerOpen.collectAsState()
                    val runtimeState by OverlayStateHolder.state.collectAsState()
                    val activeRaids by repository.activeRaids.collectAsState()
                    val isRefreshing by repository.isRefreshing.collectAsState()
                    val allSpecies by repository.species.collectAsState()
                    val movesById by pokeboxRepository.movesById.collectAsState()

                    val pendingSave = runtimeState.pendingPokemonSave
                    if (pendingSave != null) {
                        PendingPokemonSaveOverlay(
                            detected = pendingSave,
                            movesById = movesById,
                            onSave = { saved ->
                                pokeboxRepository.savePokemon(saved)
                                OverlayStateHolder.dismissPendingPokemonSave()
                                showTransientScanMessage("Saved ${pendingSave.species.speciesName} to Pokébox")
                            },
                            onDismiss = {
                                OverlayStateHolder.dismissPendingPokemonSave()
                            }
                        )
                    } else if (drawerOpen) {
                        QuickDrawerOverlay(
                            activeRaids = activeRaids,
                            allSpecies = allSpecies,
                            isRefreshingRaids = isRefreshing,
                            hasMediaProjection = runtimeState.hasMediaProjection,
                            onScanOwnPokemon = {
                                triggerScanMyPokemon()
                            },
                            onRefreshRaids = {
                                serviceScope.launch {
                                    repository.refreshLiveRaids()
                                }
                            },
                            onSelectRaidBoss = { raid ->
                                val match = repository.getMatcher().matchText(raid.name, activeRaids)
                                if (match != null) {
                                    OverlayStateHolder.showMatchedBoss(match)
                                    setDrawerOpen(false)
                                }
                            },
                            onSelectSpecies = { species ->
                                val forms = repository.getMatcher().getFormsForSpecies(species)
                                OverlayStateHolder.showManualSpecies(species, forms)
                                setDrawerOpen(false)
                            },
                            onSearchSpecies = { query ->
                                repository.getMatcher().search(query, limit = 30)
                            },
                            onResolveRaidSprite = { raid ->
                                repository.getMatcher().matchText(raid.name, activeRaids)?.species?.spriteFile
                            },
                            onCloseDrawer = {
                                setDrawerOpen(false)
                            },
                            onStopOverlay = {
                                stopOverlayService()
                            }
                        )
                    }
                }
            }
        }

        try {
            windowManager.addView(view, params)
            drawerView = view
            serviceScope.launch {
                OverlayStateHolder.state.collect { state ->
                    syncDrawerWindowVisibility(state.pendingPokemonSave != null || _isDrawerOpen.value)
                }
            }
        } catch (_: Throwable) {
            owner.performDestroy()
            drawerLifecycleOwner = null
        }
    }

    private fun setDrawerOpen(open: Boolean) {
        _isDrawerOpen.value = open
        val hasPendingSave = OverlayStateHolder.state.value.pendingPokemonSave != null
        syncDrawerWindowVisibility(open || hasPendingSave)
    }

    private fun syncDrawerWindowVisibility(visible: Boolean) {
        val view = drawerView ?: return
        val params = drawerParams ?: return

        if (visible) {
            // Focusable while open (NO FLAG_NOT_FOCUSABLE) so keyboard works for Instant Search
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            view.visibility = View.VISIBLE
        } else {
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            view.visibility = View.GONE
        }

        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Throwable) {
        }
    }

    private fun updateEdgePillPosition(deltaX: Float, deltaY: Float) {
        val view = edgePillView ?: return
        val params = edgePillParams ?: return
        params.x = (params.x + deltaX.roundToInt()).coerceAtLeast(0)
        params.y = (params.y + deltaY.roundToInt()).coerceAtLeast(48)
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Throwable) {
        }
    }

    private fun toggleScanMode() {
        val current = OverlayStateHolder.state.value
        if (current.scanMode == ScanMode.AUTO_WATCH) {
            OverlayStateHolder.update {
                it.copy(scanMode = ScanMode.BATTERY_SAVER_ONE_TAP)
            }
            showTransientScanMessage("Saver mode (tap 📷 to scan)")
        } else {
            if (current.hasMediaProjection && captureEngine.hasActiveProjection) {
                OverlayStateHolder.update {
                    it.copy(scanMode = ScanMode.AUTO_WATCH)
                }
                showTransientScanMessage("Auto-watch active")
            } else {
                showTransientScanMessage("Grant capture in app for Auto")
            }
        }
    }

    private fun ensureAutoWatchLoop() {
        if (autoWatchJob?.isActive == true) return
        autoWatchJob = serviceScope.launch {
            while (isActive) {
                delay(AUTO_WATCH_INTERVAL_MS)
                val state = OverlayStateHolder.state.value
                val shouldPoll = state.isRunning &&
                    state.scanMode == ScanMode.AUTO_WATCH &&
                    state.hasMediaProjection &&
                    captureEngine.hasActiveProjection &&
                    isScreenOn &&
                    !_isDrawerOpen.value

                if (shouldPoll) {
                    performSingleScan(isManualTrigger = false)
                }
            }
        }
    }

    private fun triggerManualScan() {
        val state = OverlayStateHolder.state.value
        if (!state.hasMediaProjection || !captureEngine.hasActiveProjection) {
            showTransientScanMessage("No capture — try ≡ Drawer")
            setDrawerOpen(true)
            return
        }

        serviceScope.launch {
            val matched = performSingleScan(isManualTrigger = true, allowOwnPokemonFallback = true)
            if (!matched) {
                showTransientScanMessage("No boss or Pokémon found — try ≡")
            }
        }
    }

    private fun triggerScanMyPokemon() {
        val state = OverlayStateHolder.state.value
        if (!state.hasMediaProjection || !captureEngine.hasActiveProjection) {
            showTransientScanMessage("Grant capture in app to scan")
            return
        }
        setDrawerOpen(false)
        serviceScope.launch {
            delay(220L)
            val found = performOwnPokemonScanOnly()
            if (!found) {
                showTransientScanMessage("No Pokémon details found on screen")
            }
        }
    }

    private suspend fun performOwnPokemonScanOnly(): Boolean {
        if (!scanMutex.tryLock()) return false
        OverlayStateHolder.update { it.copy(isScanningNow = true, lastScanMessage = null) }
        var frameBitmap: android.graphics.Bitmap? = null
        return try {
            frameBitmap = captureEngine.captureLatestFrame()
            val bitmap = frameBitmap ?: return false
            val lines = ocrPipeline.recognizeLines(bitmap = bitmap, cropNormalized = myPokemonDetector.roiNormalized)
            val detected = myPokemonDetector.detectFromLinesAndBitmap(
                lines = lines,
                bitmap = bitmap,
                activeRaids = repository.activeRaids.value
            )
            if (detected != null) {
                OverlayStateHolder.showPendingPokemonSave(detected)
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        } finally {
            try {
                if (frameBitmap != null && !frameBitmap.isRecycled) frameBitmap.recycle()
            } catch (_: Throwable) {}
            OverlayStateHolder.update { it.copy(isScanningNow = false) }
            scanMutex.unlock()
        }
    }

    private suspend fun performSingleScan(
        isManualTrigger: Boolean,
        allowOwnPokemonFallback: Boolean = false
    ): Boolean {
        if (!scanMutex.tryLock()) {
            return false
        }
        if (isManualTrigger) {
            OverlayStateHolder.update {
                it.copy(isScanningNow = true, lastScanMessage = null)
            }
        }

        var frameBitmap: android.graphics.Bitmap? = null
        return try {
            frameBitmap = captureEngine.captureLatestFrame()
            val bitmap = frameBitmap ?: return false
            val roi = raidBossDetector.roiFor(isManualTrigger = isManualTrigger)
            val lines = ocrPipeline.recognizeLines(bitmap = bitmap, cropNormalized = roi)
            val matchResult = raidBossDetector.detect(
                lines = lines,
                isManualTrigger = isManualTrigger
            )
            if (matchResult != null) {
                OverlayStateHolder.showMatchedBoss(matchResult)
                true
            } else if (allowOwnPokemonFallback) {
                val fullLines = ocrPipeline.recognizeLines(bitmap = bitmap, cropNormalized = myPokemonDetector.roiNormalized)
                val own = myPokemonDetector.detectFromLinesAndBitmap(
                    lines = fullLines,
                    bitmap = bitmap,
                    activeRaids = repository.activeRaids.value
                )
                if (own != null) {
                    OverlayStateHolder.showPendingPokemonSave(own)
                    true
                } else {
                    false
                }
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        } finally {
            try {
                if (frameBitmap != null && !frameBitmap.isRecycled) {
                    frameBitmap.recycle()
                }
            } catch (_: Throwable) {
            }
            if (isManualTrigger) {
                OverlayStateHolder.update {
                    it.copy(isScanningNow = false)
                }
            }
            scanMutex.unlock()
        }
    }

    private fun showTransientScanMessage(message: String) {
        messageClearJob?.cancel()
        OverlayStateHolder.update {
            it.copy(lastScanMessage = message)
        }
        messageClearJob = serviceScope.launch {
            delay(3000L)
            OverlayStateHolder.update { current ->
                if (current.lastScanMessage == message) {
                    current.copy(lastScanMessage = null)
                } else {
                    current
                }
            }
        }
    }

    private fun stopOverlayService() {
        if (::raidBossDetector.isInitialized) {
            raidBossDetector.resetDeduplication()
        }
        OverlayStateHolder.resetStopped()
        stopSelf()
    }

    override fun onDestroy() {
        autoWatchJob?.cancel()
        messageClearJob?.cancel()
        serviceScope.cancel()

        unregisterScreenStateReceiver()

        removeOverlayWindow(edgePillView, pillLifecycleOwner)
        edgePillView = null
        pillLifecycleOwner = null

        removeOverlayWindow(bannerView, bannerLifecycleOwner)
        bannerView = null
        bannerLifecycleOwner = null

        removeOverlayWindow(drawerView, drawerLifecycleOwner)
        drawerView = null
        drawerLifecycleOwner = null

        if (::captureEngine.isInitialized) {
            captureEngine.release()
        }
        if (::ocrPipeline.isInitialized) {
            ocrPipeline.close()
        }

        OverlayStateHolder.resetStopped()
        super.onDestroy()
    }

    private fun removeOverlayWindow(view: ComposeView?, owner: OverlayLifecycleOwner?) {
        if (view != null) {
            try {
                windowManager.removeViewImmediate(view)
            } catch (_: Throwable) {
                try {
                    windowManager.removeView(view)
                } catch (_: Throwable) {
                }
            }
        }
        try {
            owner?.performDestroy()
        } catch (_: Throwable) {
        }
    }

    private fun registerScreenStateReceiver() {
        if (isScreenReceiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            ContextCompat.registerReceiver(
                this,
                screenStateReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isScreenReceiverRegistered = true
        } catch (_: Throwable) {
        }
    }

    private fun unregisterScreenStateReceiver() {
        if (!isScreenReceiverRegistered) return
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (_: Throwable) {
        } finally {
            isScreenReceiverRegistered = false
        }
    }

    private fun queryIsScreenInteractive(): Boolean {
        return try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isInteractive ?: true
        } catch (_: Throwable) {
            true
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "PoGo Raid Overlay Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Active while the Pokémon GO Raid Weakness overlay is running"
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            101,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Raid weakness detector overlay is active")
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.ic_notification,
                "Stop Overlay",
                stopPendingIntent
            )
            .build()
    }
}

@Composable
private fun CompactEdgePillOverlay(
    state: OverlayRuntimeState,
    isDrawerOpen: Boolean,
    onDrag: (Float, Float) -> Unit,
    onToggleScanMode: () -> Unit,
    onOneTapScan: () -> Unit,
    onToggleDrawer: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = Color(0xFFFFFFFF),
            border = BorderStroke(1.dp, Color(0xFFECEFF4)),
            shadowElevation = 6.dp,
            modifier = Modifier
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                // [Auto ●] / [Saver] toggle pill
                val isAutoActive = state.scanMode == ScanMode.AUTO_WATCH && state.hasMediaProjection
                val modeBg = if (isAutoActive) Color(0xFFF7CF31) else Color(0xFFF1F3F7)
                val modeFg = Color(0xFF1A1C20)

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(modeBg)
                        .clickable { onToggleScanMode() }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (isAutoActive) "Auto ●" else "Saver",
                        color = modeFg,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // [📷] 1-tap scan button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF7F8FA))
                        .border(1.dp, Color(0xFFECEFF4), CircleShape)
                        .clickable(enabled = !state.isScanningNow) { onOneTapScan() }
                ) {
                    if (state.isScanningNow) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF1A1C20)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "1-Tap Scan",
                            tint = Color(0xFF1A1C20),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                // [≡] Quick Drawer toggle button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (isDrawerOpen) Color(0xFF1A1C20) else Color(0xFFF7F8FA))
                        .border(1.dp, Color(0xFFECEFF4), CircleShape)
                        .clickable { onToggleDrawer() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Quick Drawer",
                        tint = if (isDrawerOpen) Color(0xFFFFFFFF) else Color(0xFF1A1C20),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        val scanMessage = state.lastScanMessage
        if (!scanMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFFFFFFFF),
                border = BorderStroke(1.dp, Color(0xFFECEFF4)),
                shadowElevation = 4.dp
            ) {
                Text(
                    text = scanMessage,
                    color = Color(0xFF1A1C20),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PriorityWeaknessBannerOverlay(
    banner: ActiveBossBanner,
    savedPokemon: List<SavedPokemon>,
    speciesById: Map<String, PokemonSpecies>,
    movesById: Map<String, MoveData>,
    onSelectForm: (PokemonSpecies) -> Unit,
    onDismiss: () -> Unit
) {
    val speciesId = banner.match.species.speciesId
    var showAvoidExpanded by remember(speciesId) { mutableStateOf(false) }
    var showFormSelector by remember(speciesId) { mutableStateOf(false) }
    var showTeamExpanded by remember(speciesId) { mutableStateOf(false) }

    val primaryType = banner.matchup.defendingTypes.firstOrNull() ?: PokemonType.NORMAL
    val headerBgColor = primaryType.badgeColor()
    val headerContentColor = primaryType.contentColor()

    var horizontalDragAccumulator by remember(speciesId) { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pointerInput(speciesId) {
                detectHorizontalDragGestures(
                    onDragStart = { horizontalDragAccumulator = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        horizontalDragAccumulator += dragAmount
                        if (kotlin.math.abs(horizontalDragAccumulator) > 100f) {
                            horizontalDragAccumulator = 0f
                            onDismiss()
                        }
                    }
                )
            }
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFFFFFFFF),
            border = BorderStroke(1.dp, Color(0xFFECEFF4)),
            shadowElevation = 10.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Slim top header band tinted in primaryType.badgeColor()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerBgColor)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    PokemonSprite(
                        spriteFile = banner.match.species.spriteFile,
                        contentDescription = banner.match.displayTitle,
                        size = 36.dp,
                        fallbackTint = headerContentColor
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = banner.match.displayTitle,
                                color = headerContentColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            val prefix = banner.match.detectedPrefix
                            if (!prefix.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color.Black.copy(alpha = 0.22f))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = prefix,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = banner.matchup.defendingTypes.joinToString(" / ") { it.displayName },
                                color = headerContentColor.copy(alpha = 0.9f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Interactive [Form ▾] chip when alternateForms.size > 1
                    if (banner.match.alternateForms.size > 1) {
                        val currentFormLabel = banner.match.species.formLabel() ?: "Base"
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.White.copy(alpha = 0.92f))
                                .clickable { showFormSelector = !showFormSelector }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "$currentFormLabel ▾",
                                color = Color(0xFF1A1C20),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // ✕ dismiss button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.18f))
                            .clickable { onDismiss() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss Banner",
                            tint = headerContentColor,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                // Inline expandable form switcher row
                AnimatedVisibility(visible = showFormSelector && banner.match.alternateForms.size > 1) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF7F8FA))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        banner.match.alternateForms.forEach { form ->
                            val isSelected = form.speciesId == banner.match.species.speciesId
                            val label = form.formLabel() ?: "Standard"
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (isSelected) Color(0xFF1A1C20) else Color(0xFFFFFFFF))
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color(0xFF1A1C20) else Color(0xFFECEFF4),
                                        shape = RoundedCornerShape(999.dp)
                                    )
                                    .clickable {
                                        onSelectForm(form)
                                        showFormSelector = false
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color(0xFFFFFFFF) else Color(0xFF1A1C20),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Pure-white body rendering WeaknessPriorityStrip (2.56× first, 1.6× second, Avoid toggle)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFFFFF))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WeaknessPriorityStrip(
                        matchup = banner.matchup,
                        showAvoidExpanded = showAvoidExpanded,
                        onToggleAvoid = { showAvoidExpanded = !showAvoidExpanded }
                    )

                    if (savedPokemon.isNotEmpty()) {
                        val verdict = remember(speciesId, savedPokemon, speciesById, movesById) {
                            RaidBattleSimulator.simulatePersonalTeam(
                                savedBox = savedPokemon,
                                speciesById = speciesById,
                                movesById = movesById,
                                bossSpecies = banner.match.species,
                                raidTier = 5
                            )
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF7F8FA))
                                .clickable { showTeamExpanded = !showTeamExpanded }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "My Best Team: ${verdict.verdictLabel}",
                                    color = Color(0xFF1A1C20),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (showTeamExpanded) "Hide ▲" else "${String.format(java.util.Locale.US, "Est %.2f", verdict.teamEstimator)} ▾",
                                    color = Color(0xFF6E7582),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (showTeamExpanded) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    verdict.team.forEach { member ->
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color.White)
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            PokemonSprite(
                                                spriteFile = member.species.spriteFile,
                                                contentDescription = member.species.speciesName,
                                                size = 28.dp
                                            )
                                            Text(
                                                text = "CP ${member.pokemon.cp}",
                                                color = Color(0xFF1A1C20),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickDrawerOverlay(
    activeRaids: List<RaidBossEntry>,
    allSpecies: List<PokemonSpecies>,
    isRefreshingRaids: Boolean,
    hasMediaProjection: Boolean,
    onScanOwnPokemon: () -> Unit,
    onRefreshRaids: () -> Unit,
    onSelectRaidBoss: (RaidBossEntry) -> Unit,
    onSelectSpecies: (PokemonSpecies) -> Unit,
    onSearchSpecies: (String) -> List<PokemonSpecies>,
    onResolveRaidSprite: (RaidBossEntry) -> String?,
    onCloseDrawer: () -> Unit,
    onStopOverlay: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCalcTypes by remember {
        mutableStateOf(listOf(PokemonType.DRAGON, PokemonType.FLYING))
    }
    var showCalcAvoid by remember { mutableStateOf(false) }

    val raidSprites = remember(activeRaids, allSpecies) {
        activeRaids.associateWith { raid -> onResolveRaidSprite(raid) }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 20.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFFFFFFFF),
            border = BorderStroke(1.dp, Color(0xFFECEFF4)),
            shadowElevation = 14.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                // Top Drawer Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pokédex Quick Drawer",
                            color = Color(0xFF1A1C20),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Tap any boss to pin its weakness banner",
                            color = Color(0xFF6E7582),
                            fontSize = 11.sp
                        )
                    }

                    IconButton(
                        onClick = onRefreshRaids,
                        enabled = !isRefreshingRaids,
                        modifier = Modifier.size(32.dp)
                    ) {
                        if (isRefreshingRaids) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF1A1C20)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Live Raids",
                                tint = Color(0xFF1A1C20),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onCloseDrawer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Drawer",
                            tint = Color(0xFF1A1C20),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Minimalist #1A1C20 underline tabs: Raids | Search | Type Calc
                val tabs = listOf("Raids", "Search", "Type Calc")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { selectedTab = index }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = title,
                                color = if (isSelected) Color(0xFF1A1C20) else Color(0xFF6E7582),
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .width(48.dp)
                                    .height(2.5.dp)
                                    .background(
                                        if (isSelected) Color(0xFF1A1C20) else Color.Transparent,
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFECEFF4))
                Spacer(modifier = Modifier.height(10.dp))

                // Tab Content
                when (selectedTab) {
                    0 -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(vertical = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp, max = 300.dp)
                        ) {
                            itemsIndexed(
                                items = activeRaids,
                                key = { index, raid -> "raid:$index:${raid.tier}:${raid.name}" }
                            ) { _, raid ->
                                val raidSpriteFile = raidSprites[raid]
                                val parsedTypes = raid.types.mapNotNull { PokemonType.fromApiKey(it) }
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFFF7F8FA),
                                    border = BorderStroke(1.dp, Color(0xFFECEFF4)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectRaidBoss(raid) }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        PokemonSprite(
                                            spriteFile = raidSpriteFile,
                                            contentDescription = raid.name,
                                            size = 38.dp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = raid.name,
                                                color = Color(0xFF1A1C20),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = raid.tier,
                                                color = Color(0xFF6E7582),
                                                fontSize = 11.sp
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            parsedTypes.forEach { type ->
                                                TypeChip(type = type, compact = true)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = "Search any Pokémon or Mega...",
                                    fontSize = 13.sp,
                                    color = Color(0xFF6E7582)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color(0xFF6E7582),
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = Color(0xFF6E7582),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(999.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF7F8FA),
                                unfocusedContainerColor = Color(0xFFF7F8FA),
                                focusedBorderColor = Color(0xFF1A1C20),
                                unfocusedBorderColor = Color(0xFFECEFF4),
                                focusedTextColor = Color(0xFF1A1C20),
                                unfocusedTextColor = Color(0xFF1A1C20)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val searchResults = remember(searchQuery, allSpecies) {
                            onSearchSpecies(searchQuery)
                        }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp, max = 250.dp)
                        ) {
                            items(searchResults, key = { it.speciesId }) { species ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFFF7F8FA),
                                    border = BorderStroke(1.dp, Color(0xFFECEFF4)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectSpecies(species) }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                    ) {
                                        PokemonSprite(
                                            spriteFile = species.spriteFile,
                                            contentDescription = species.speciesName,
                                            size = 36.dp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = species.speciesName,
                                                color = Color(0xFF1A1C20),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "#${species.dex}",
                                                color = Color(0xFF6E7582),
                                                fontSize = 11.sp
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            species.parsedTypes().forEach { type ->
                                                TypeChip(type = type, compact = true)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Tap 1 or 2 defending types:",
                                color = Color(0xFF6E7582),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                PokemonType.entries.forEach { type ->
                                    val isSelected = type in selectedCalcTypes
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(
                                                if (isSelected) type.badgeColor() else Color(0xFFF7F8FA)
                                            )
                                            .border(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) Color(0xFF1A1C20) else Color(0xFFECEFF4),
                                                shape = RoundedCornerShape(999.dp)
                                            )
                                            .clickable {
                                                selectedCalcTypes = when {
                                                    isSelected && selectedCalcTypes.size > 1 ->
                                                        selectedCalcTypes - type
                                                    isSelected ->
                                                        selectedCalcTypes
                                                    selectedCalcTypes.size < 2 ->
                                                        selectedCalcTypes + type
                                                    else ->
                                                        listOf(selectedCalcTypes.last(), type)
                                                }
                                            }
                                            .padding(horizontal = 9.dp, vertical = 5.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (!isSelected) {
                                                TypeDot(type = type, size = 10.dp)
                                            }
                                            Text(
                                                text = type.displayName,
                                                color = if (isSelected) type.contentColor() else Color(0xFF1A1C20),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            val calcMatchup = remember(selectedCalcTypes) {
                                TypeEffectivenessChart.calculate(selectedCalcTypes)
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFF7F8FA),
                                border = BorderStroke(1.dp, Color(0xFFECEFF4)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(modifier = Modifier.padding(10.dp)) {
                                    WeaknessPriorityStrip(
                                        matchup = calcMatchup,
                                        showAvoidExpanded = showCalcAvoid,
                                        onToggleAvoid = { showCalcAvoid = !showCalcAvoid }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFECEFF4))
                Spacer(modifier = Modifier.height(10.dp))

                // Bottom actions: Scan My Pokémon + Stop Overlay
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (hasMediaProjection) {
                        Button(
                            onClick = onScanOwnPokemon,
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1A1C20),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Scan My Pokémon",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onStopOverlay,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, Color(0xFFE53935)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFD32F2F)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Stop Overlay",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingPokemonSaveOverlay(
    detected: DetectedOwnPokemon,
    movesById: Map<String, MoveData>,
    onSave: (SavedPokemon) -> Unit,
    onDismiss: () -> Unit
) {
    var ivAtk by remember(detected) { mutableIntStateOf(detected.ivAtk ?: 12) }
    var ivDef by remember(detected) { mutableIntStateOf(detected.ivDef ?: 12) }
    var ivSta by remember(detected) { mutableIntStateOf(detected.ivSta ?: 12) }

    val solvedLevel = remember(detected, ivAtk, ivDef, ivSta) {
        PokemonMathEngine.solveLevel(
            baseAtk = detected.species.baseAtk,
            baseDef = detected.species.baseDef,
            baseSta = detected.species.baseSta,
            ivAtk = ivAtk,
            ivDef = ivDef,
            ivSta = ivSta,
            cp = detected.cp,
            hp = detected.hp,
            powerUpDust = detected.powerUpDust,
            isShadow = detected.isShadow,
            isPurified = detected.isPurified,
            isLucky = detected.isLucky
        )
    }

    val moveEval = remember(detected, movesById) {
        RaidBattleSimulator.evaluateMoveset(
            species = detected.species,
            movesById = movesById,
            currentFastId = detected.fastMove?.moveId,
            currentCharged1Id = detected.chargedMove1?.moveId,
            currentCharged2Id = detected.chargedMove2?.moveId
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFECEFF4)),
            shadowElevation = 14.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PokemonSprite(
                            spriteFile = detected.species.spriteFile,
                            contentDescription = detected.species.speciesName,
                            size = 42.dp
                        )
                        Column {
                            Text(
                                text = "Save ${detected.species.speciesName}",
                                color = Color(0xFF1A1C20),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "CP ${detected.cp} • Lv ${formatLevel(solvedLevel)}${if (detected.isShadow) " • Shadow" else ""}",
                                color = Color(0xFF6E7582),
                                fontSize = 11.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF1A1C20))
                    }
                }

                val moveLine = listOfNotNull(
                    detected.fastMove?.displayName,
                    detected.chargedMove1?.displayName,
                    detected.chargedMove2?.displayName
                ).joinToString(" / ")
                if (moveLine.isNotEmpty()) {
                    Text(
                        text = "Moves: $moveLine",
                        color = Color(0xFF1A1C20),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                moveEval?.let { eval ->
                    Text(
                        text = eval.recommendationText,
                        color = if (eval.isCurrentOptimal || eval.currentNeutralDps == null) {
                            Color(0xFF16A34A)
                        } else {
                            Color(0xFFD97706)
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = if (detected.ivAtk != null) "Detected Appraisal IVs: $ivAtk/$ivDef/$ivSta" else "Confirm IVs: $ivAtk / $ivDef / $ivSta",
                    color = Color(0xFF1A1C20),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                OverlayIvRow("Atk", ivAtk) { ivAtk = it }
                OverlayIvRow("Def", ivDef) { ivDef = it }
                OverlayIvRow("Sta", ivSta) { ivSta = it }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            onSave(
                                SavedPokemon(
                                    id = UUID.randomUUID().toString(),
                                    speciesId = detected.species.speciesId,
                                    pokebattlerId = detected.species.pokebattlerId
                                        ?: detected.species.speciesId.uppercase(),
                                    cp = detected.cp,
                                    hp = detected.hp,
                                    ivAtk = ivAtk,
                                    ivDef = ivDef,
                                    ivSta = ivSta,
                                    level = solvedLevel,
                                    isShadow = detected.isShadow,
                                    isPurified = detected.isPurified,
                                    isLucky = detected.isLucky,
                                    isMegaCapable = detected.species.tags.contains("mega"),
                                    fastMoveId = detected.fastMove?.moveId,
                                    chargedMove1Id = detected.chargedMove1?.moveId,
                                    chargedMove2Id = detected.chargedMove2?.moveId,
                                    savedAtMs = System.currentTimeMillis()
                                )
                            )
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1A1C20),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save to Box", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayIvRow(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("$label $value", color = Color(0xFF1A1C20), fontSize = 11.sp, modifier = Modifier.width(46.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(0, 15)) },
            valueRange = 0f..15f,
            steps = 14,
            modifier = Modifier.weight(1f)
        )
    }
}
