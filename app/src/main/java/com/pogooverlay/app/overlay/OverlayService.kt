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
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.pogooverlay.app.core.capture.CaptureDebugAnnotator
import com.pogooverlay.app.core.capture.CaptureDebugOverlayData
import com.pogooverlay.app.core.capture.ScreenCaptureEngine
import com.pogooverlay.app.core.data.ActiveBossBanner
import com.pogooverlay.app.core.data.OverlayMode
import com.pogooverlay.app.core.data.OverlayRuntimeState
import com.pogooverlay.app.core.data.OverlayStateHolder
import com.pogooverlay.app.core.data.PokemonRepository
import com.pogooverlay.app.core.data.PokemonSpecies
import com.pogooverlay.app.core.data.PokemonType
import com.pogooverlay.app.core.data.RaidBossEntry
import com.pogooverlay.app.core.data.RaidCardTab
import com.pogooverlay.app.core.data.TypeEffectivenessChart
import com.pogooverlay.app.core.detector.CapturePipelineRouter
import com.pogooverlay.app.core.detector.CaptureRouteResult
import com.pogooverlay.app.core.detector.RaidBossDetector
import com.pogooverlay.app.core.detector.RaidCaptureAction
import com.pogooverlay.app.core.detector.RaidFlowCoordinator
import com.pogooverlay.app.core.ocr.OcrLine
import com.pogooverlay.app.core.ocr.OcrPipeline
import com.pogooverlay.app.core.pokebox.MoveData
import com.pogooverlay.app.core.pokebox.PokeboxRepository
import com.pogooverlay.app.core.pokebox.RaidBattleSimulator
import com.pogooverlay.app.core.pokebox.SavedPokemon
import com.pogooverlay.app.core.scan.AppraisalIvParseResult
import com.pogooverlay.app.core.scan.CaptureSessionManager
import com.pogooverlay.app.core.scan.CaptureStepOutcome
import com.pogooverlay.app.core.scan.ScanScreenParser
import com.pogooverlay.app.ui.MainActivity
import com.pogooverlay.app.ui.components.PokemonSprite
import com.pogooverlay.app.ui.components.TypeChip
import com.pogooverlay.app.ui.components.TypeDot
import com.pogooverlay.app.ui.components.WeaknessPriorityStrip
import com.pogooverlay.app.ui.components.badgeColor
import com.pogooverlay.app.ui.components.contentColor
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class OverlayService : Service() {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "pogo_raid_overlay_channel"
        private const val NOTIFICATION_ID = 4101
        private const val CAPTURE_HIDE_DELAY_MS = 100L
        private const val TRANSIENT_MESSAGE_MS = 3000L
        private const val PREFS_NAME = "pogo_overlay_prefs"
        private const val KEY_DEBUG_ANNOTATED_CAPTURE = "debug_annotated_capture"

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
    private val captureMutex = Mutex()

    private lateinit var windowManager: WindowManager
    private lateinit var repository: PokemonRepository
    private lateinit var pokeboxRepository: PokeboxRepository
    private lateinit var captureEngine: ScreenCaptureEngine
    private lateinit var ocrPipeline: OcrPipeline
    private lateinit var raidBossDetector: RaidBossDetector
    private lateinit var raidFlowCoordinator: RaidFlowCoordinator
    private lateinit var scanScreenParser: ScanScreenParser
    private lateinit var captureSessionManager: CaptureSessionManager

    private var pillLifecycleOwner: OverlayLifecycleOwner? = null
    private var bannerLifecycleOwner: OverlayLifecycleOwner? = null
    private var drawerLifecycleOwner: OverlayLifecycleOwner? = null

    private var edgePillView: ComposeView? = null
    private var bannerView: ComposeView? = null
    private var drawerView: ComposeView? = null

    private var edgePillParams: WindowManager.LayoutParams? = null
    private var bannerParams: WindowManager.LayoutParams? = null
    private var drawerParams: WindowManager.LayoutParams? = null

    private var messageClearJob: Job? = null

    private val _isDrawerOpen = MutableStateFlow(false)
    private val isDrawerOpen: StateFlow<Boolean> = _isDrawerOpen.asStateFlow()

    @Volatile
    private var isHidingForCapture: Boolean = false

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

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        OverlayStateHolder.setDebugSaveAnnotatedCapture(
            prefs.getBoolean(KEY_DEBUG_ANNOTATED_CAPTURE, false)
        )

        isScreenOn = queryIsScreenInteractive()

        captureEngine = ScreenCaptureEngine(
            context = applicationContext,
            onProjectionStopped = {
                stopOverlayService()
            }
        )
        captureEngine.isScreenOn = isScreenOn

        ocrPipeline = OcrPipeline()
        raidBossDetector = RaidBossDetector(
            matcherProvider = { repository.getMatcher() },
            activeRaidsProvider = { repository.activeRaids.value }
        )
        raidFlowCoordinator = RaidFlowCoordinator(raidBossDetector)
        scanScreenParser = ScanScreenParser(
            matcherProvider = { repository.getMatcher() },
            movesProvider = { pokeboxRepository.movesById.value }
        )
        captureSessionManager = CaptureSessionManager(
            onSavePokemon = { saved ->
                pokeboxRepository.savePokemon(saved)
            }
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
        // Call startForegroundWithType(FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        // BEFORE calling mediaProjectionManager.getMediaProjection(resultCode, data)
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
                    lastScanMessage = if (started) null else "Manual drawer mode"
                )
            }
        } else {
            val alreadyCapturing = captureEngine.hasActiveProjection
            OverlayStateHolder.update { current ->
                current.copy(
                    isRunning = true,
                    hasMediaProjection = alreadyCapturing
                )
            }
        }

        ensureOverlayWindowsAttached()

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
                        onToggleOverlayMode = {
                            toggleOverlayMode()
                        },
                        onManualCapture = {
                            triggerManualCapture()
                        },
                        onToggleDrawer = {
                            setDrawerOpen(!drawerOpen)
                        },
                        onTapStatusPill = { displayedMessage ->
                            if (runtimeState.overlayMode == OverlayMode.RAID &&
                                displayedMessage.contains("pick boss", ignoreCase = true)
                            ) {
                                OverlayStateHolder.clearScanMessage()
                                setDrawerOpen(true)
                            }
                        },
                        onDismissStatus = {
                            handleDismissStatusPill()
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
            y = 6
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
                    val banner = runtimeState.activeRaidBoss
                    if (banner != null && runtimeState.isRaidCardVisible) {
                        RaidOverlayCard(
                            banner = banner,
                            activeTab = runtimeState.activeRaidTab,
                            savedPokemon = savedPokemon,
                            speciesById = speciesById,
                            movesById = movesById,
                            onSelectTab = { tab ->
                                OverlayStateHolder.setRaidCardTab(tab)
                            },
                            onSelectForm = { selectedForm ->
                                val updatedMatch = OverlayStateHolder.selectForm(
                                    species = selectedForm,
                                    allForms = banner.match.alternateForms,
                                    detectedPrefix = banner.match.detectedPrefix
                                )
                                raidFlowCoordinator.setManualBoss(updatedMatch)
                            },
                            onDismiss = {
                                OverlayStateHolder.dismissRaidCard()
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
                    if (!isHidingForCapture) {
                        val shouldBeVisible = state.activeRaidBoss != null && state.isRaidCardVisible
                        val targetVisibility = if (shouldBeVisible) View.VISIBLE else View.GONE
                        if (view.visibility != targetVisibility) {
                            view.visibility = targetVisibility
                        }
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
                    val activeRaids by repository.activeRaids.collectAsState()
                    val isRefreshing by repository.isRefreshing.collectAsState()
                    val allSpecies by repository.species.collectAsState()

                    if (drawerOpen) {
                        QuickDrawerOverlay(
                            activeRaids = activeRaids,
                            allSpecies = allSpecies,
                            isRefreshingRaids = isRefreshing,
                            onRefreshRaids = {
                                serviceScope.launch {
                                    repository.refreshLiveRaids()
                                }
                            },
                            onSelectRaidBoss = { raid ->
                                val match = repository.getMatcher().matchText(raid.name, activeRaids)
                                if (match != null) {
                                    if (OverlayStateHolder.state.value.overlayMode != OverlayMode.RAID) {
                                        captureSessionManager.reset()
                                        OverlayStateHolder.setOverlayMode(OverlayMode.RAID)
                                    }
                                    raidFlowCoordinator.setManualBoss(match)
                                    OverlayStateHolder.showRaidPreviewBoss(match)
                                    scheduleTransientMessageClear("Detected ${match.displayTitle}")
                                    setDrawerOpen(false)
                                }
                            },
                            onSelectSpecies = { species ->
                                val forms = repository.getMatcher().getFormsForSpecies(species)
                                if (OverlayStateHolder.state.value.overlayMode != OverlayMode.RAID) {
                                    captureSessionManager.reset()
                                    OverlayStateHolder.setOverlayMode(OverlayMode.RAID)
                                }
                                val match = OverlayStateHolder.showManualSpecies(species, forms)
                                raidFlowCoordinator.setManualBoss(match)
                                scheduleTransientMessageClear("Selected ${match.displayTitle}")
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
        } catch (_: Throwable) {
            owner.performDestroy()
            drawerLifecycleOwner = null
        }
    }

    private fun setDrawerOpen(open: Boolean) {
        _isDrawerOpen.value = open
        if (!isHidingForCapture) {
            syncDrawerWindowVisibility(open)
        }
    }

    private fun syncBannerWindowVisibility() {
        val view = bannerView ?: return
        val state = OverlayStateHolder.state.value
        val shouldBeVisible = state.activeRaidBoss != null && state.isRaidCardVisible
        view.visibility = if (shouldBeVisible) View.VISIBLE else View.GONE
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

    private fun toggleOverlayMode() {
        val currentMode = OverlayStateHolder.state.value.overlayMode
        val nextMode = if (currentMode == OverlayMode.RAID) OverlayMode.SCAN else OverlayMode.RAID
        messageClearJob?.cancel()
        captureSessionManager.reset()
        raidFlowCoordinator.clearActiveBoss()
        OverlayStateHolder.setOverlayMode(nextMode)
    }

    private fun handleDismissStatusPill() {
        messageClearJob?.cancel()
        if (OverlayStateHolder.state.value.overlayMode == OverlayMode.SCAN) {
            captureSessionManager.reset()
            OverlayStateHolder.clearScanDraft()
        } else {
            OverlayStateHolder.clearScanMessage()
        }
    }

    private fun triggerManualCapture() {
        val state = OverlayStateHolder.state.value
        if (!state.hasMediaProjection || !captureEngine.hasActiveProjection) {
            showTransientScanMessage("No capture — try ≡ Drawer")
            setDrawerOpen(true)
            return
        }

        serviceScope.launch {
            performManualCapture()
        }
    }

    private suspend fun performManualCapture() {
        if (!captureMutex.tryLock()) return
        val activeMode = OverlayStateHolder.state.value.overlayMode
        OverlayStateHolder.update { it.copy(isScanningNow = true) }

        var frameBitmap: Bitmap? = null
        try {
            // Hide ALL attached overlay windows before capturing so OCR sees clean game UI
            isHidingForCapture = true
            try {
                edgePillView?.visibility = View.INVISIBLE
                bannerView?.visibility = View.INVISIBLE
                drawerView?.visibility = View.INVISIBLE
                delay(CAPTURE_HIDE_DELAY_MS)
                frameBitmap = captureEngine.captureLatestFrame()
            } finally {
                isHidingForCapture = false
                edgePillView?.visibility = View.VISIBLE
                syncBannerWindowVisibility()
                syncDrawerWindowVisibility(_isDrawerOpen.value)
            }

            val bitmap = frameBitmap
            if (bitmap == null) {
                showTransientScanMessage("Capture failed — try again")
                return
            }

            // Run ML Kit OCR once on the full frame
            val lines = ocrPipeline.recognizeLines(bitmap)

            var debugRaidLine: OcrLine? = null
            var debugRaidLabel: String? = null
            var debugAppraisal: AppraisalIvParseResult? = null
            var debugMoveLines: List<OcrLine> = emptyList()

            // Strictly route by OverlayMode (RAID vs SCAN — zero cross-mode leakage)
            val routeResult = CapturePipelineRouter.dispatch(
                mode = activeMode,
                onRaid = {
                    raidFlowCoordinator.handleCapturedLines(lines)
                },
                onScan = {
                    val extraction = scanScreenParser.parse(
                        lines = lines,
                        bitmap = bitmap,
                        activeSpeciesHint = captureSessionManager.currentDraft.species
                    )
                    val outcome = captureSessionManager.processFrame(extraction)
                    extraction to outcome
                }
            )

            when (routeResult) {
                is CaptureRouteResult.Raid -> {
                    when (val action = routeResult.result) {
                        is RaidCaptureAction.ShowPreviewWeaknesses -> {
                            debugRaidLine = action.detection.matchedLine
                            debugRaidLabel = action.detection.match.displayTitle
                            OverlayStateHolder.showRaidPreviewBoss(action.detection.match)
                            scheduleTransientMessageClear("Detected ${action.detection.match.displayTitle}")
                        }

                        is RaidCaptureAction.ShowLobbySuggestedParty -> {
                            debugRaidLabel = "LOBBY · ${action.cachedBoss.displayTitle}"
                            OverlayStateHolder.showRaidLobbySuggestedParty(action.cachedBoss)
                            scheduleTransientMessageClear("Suggested Party · ${action.cachedBoss.displayTitle}")
                        }

                        is RaidCaptureAction.PromptScanPreviewFirst -> {
                            debugRaidLabel = "LOBBY (no cached boss)"
                            showTransientScanMessage(action.message)
                        }

                        is RaidCaptureAction.BossNotDetected -> {
                            debugRaidLabel = "No boss detected"
                            showTransientScanMessage(action.message)
                        }
                    }
                }

                is CaptureRouteResult.Scan -> {
                    val (extraction, outcome) = routeResult.result
                    debugAppraisal = extraction.appraisalResult
                    debugMoveLines = extraction.movesetResult.matchedLines
                    OverlayStateHolder.updateScanOutcome(outcome)

                    when (outcome) {
                        is CaptureStepOutcome.Saved -> {
                            triggerSaveHaptic()
                            scheduleTransientMessageClear(outcome.flashMessage)
                        }

                        is CaptureStepOutcome.InProgress -> {
                            outcome.transientFlash?.let { scheduleTransientMessageClear(it) }
                        }

                        is CaptureStepOutcome.NoData -> {
                            scheduleTransientMessageClear(outcome.transientFlash)
                        }
                    }
                }
            }

            // Save annotated debug PNG BEFORE recycling the bitmap if debug toggle is enabled
            if (OverlayStateHolder.state.value.debugSaveAnnotatedCapture) {
                val debugData = CaptureDebugOverlayData(
                    mode = activeMode,
                    allOcrLines = lines,
                    raidBossLine = debugRaidLine,
                    raidBossLabel = debugRaidLabel,
                    appraisalResult = debugAppraisal,
                    matchedMoveLines = debugMoveLines
                )
                val savedPath = withContext(Dispatchers.IO) {
                    CaptureDebugAnnotator.saveAnnotatedCapture(
                        context = applicationContext,
                        bitmap = bitmap,
                        data = debugData
                    )
                }
                if (savedPath != null) {
                    OverlayStateHolder.setLastDebugCapturePath(savedPath)
                }
            }
        } catch (_: Throwable) {
            showTransientScanMessage("Scan error — try again")
        } finally {
            try {
                if (frameBitmap != null && !frameBitmap.isRecycled) {
                    frameBitmap.recycle()
                }
            } catch (_: Throwable) {
            }
            OverlayStateHolder.update { it.copy(isScanningNow = false) }
            captureMutex.unlock()
        }
    }

    private fun triggerSaveHaptic() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(50L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50L)
            }
        } catch (_: Throwable) {
        }
    }

    private fun showTransientScanMessage(message: String) {
        OverlayStateHolder.update { it.copy(lastScanMessage = message) }
        scheduleTransientMessageClear(message)
    }

    private fun scheduleTransientMessageClear(expectedMessage: String) {
        messageClearJob?.cancel()
        messageClearJob = serviceScope.launch {
            delay(TRANSIENT_MESSAGE_MS)
            OverlayStateHolder.update { current ->
                if (current.lastScanMessage == expectedMessage) {
                    current.copy(lastScanMessage = null)
                } else {
                    current
                }
            }
        }
    }

    private fun stopOverlayService() {
        if (::captureSessionManager.isInitialized) {
            captureSessionManager.reset()
        }
        if (::raidFlowCoordinator.isInitialized) {
            raidFlowCoordinator.clearActiveBoss()
        }
        OverlayStateHolder.resetStopped()
        stopSelf()
    }

    override fun onDestroy() {
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
                    description = "Active while the Pokémon GO Raid & Pokébox overlay is running"
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
            .setContentText("Manual Raid & Scan overlay is active")
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
    onToggleOverlayMode: () -> Unit,
    onManualCapture: () -> Unit,
    onToggleDrawer: () -> Unit,
    onTapStatusPill: (String) -> Unit,
    onDismissStatus: () -> Unit
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
                // [Raid] / [Scan] 1-tap mode toggle chip
                val isRaidMode = state.overlayMode == OverlayMode.RAID
                val modeBg = if (isRaidMode) Color(0xFFF7CF31) else Color(0xFF10B981)
                val modeFg = if (isRaidMode) Color(0xFF1A1C20) else Color(0xFFFFFFFF)

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(modeBg)
                        .clickable { onToggleOverlayMode() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (isRaidMode) "Raid" else "Scan",
                        color = modeFg,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // [📷] Manual 1-tap capture button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF7F8FA))
                        .border(1.dp, Color(0xFFECEFF4), CircleShape)
                        .clickable(enabled = !state.isScanningNow) { onManualCapture() }
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
                            contentDescription = "Manual Capture",
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

        val statusText = state.lastScanMessage?.takeIf { it.isNotBlank() }
            ?: state.scanStatusPrompt?.takeIf {
                state.overlayMode == OverlayMode.SCAN && it.isNotBlank()
            }

        if (!statusText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFFFFFFFF),
                border = BorderStroke(1.dp, Color(0xFFECEFF4)),
                shadowElevation = 4.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    Text(
                        text = statusText,
                        color = Color(0xFF1A1C20),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clickable { onTapStatusPill(statusText) }
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F3F7))
                            .clickable { onDismissStatus() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss status",
                            tint = Color(0xFF6E7582),
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RaidOverlayCard(
    banner: ActiveBossBanner,
    activeTab: RaidCardTab,
    savedPokemon: List<SavedPokemon>,
    speciesById: Map<String, PokemonSpecies>,
    movesById: Map<String, MoveData>,
    onSelectTab: (RaidCardTab) -> Unit,
    onSelectForm: (PokemonSpecies) -> Unit,
    onDismiss: () -> Unit
) {
    val speciesId = banner.match.species.speciesId
    var showFormSelector by remember(speciesId) { mutableStateOf(false) }
    var showAvoidExpanded by remember(speciesId) { mutableStateOf(false) }

    val primaryType = banner.matchup.defendingTypes.firstOrNull() ?: PokemonType.NORMAL
    val headerBgColor = primaryType.badgeColor()
    val headerContentColor = primaryType.contentColor()

    var horizontalDragAccumulator by remember(speciesId) { mutableStateOf(0f) }

    val verdict = remember(speciesId, savedPokemon, speciesById, movesById) {
        RaidBattleSimulator.simulatePersonalTeam(
            savedBox = savedPokemon,
            speciesById = speciesById,
            movesById = movesById,
            bossSpecies = banner.match.species,
            raidTier = 5
        )
    }

    val priorityWeaknesses = remember(banner.matchup) {
        banner.matchup.doubleWeaknesses + banner.matchup.singleWeaknesses
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .pointerInput(speciesId) {
                detectHorizontalDragGestures(
                    onDragStart = { horizontalDragAccumulator = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        horizontalDragAccumulator += dragAmount
                        if (abs(horizontalDragAccumulator) > 100f) {
                            horizontalDragAccumulator = 0f
                            onDismiss()
                        }
                    }
                )
            }
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFFFFFFF),
            border = BorderStroke(1.dp, Color(0xFFECEFF4)),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Colored single-line boss header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerBgColor)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    PokemonSprite(
                        spriteFile = banner.match.species.spriteFile,
                        contentDescription = banner.match.displayTitle,
                        size = 22.dp,
                        fallbackTint = headerContentColor
                    )

                    Text(
                        text = banner.match.displayTitle,
                        color = headerContentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    val prefix = banner.match.detectedPrefix
                    if (!prefix.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.Black.copy(alpha = 0.22f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = prefix,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (banner.match.alternateForms.size > 1) {
                        val currentFormLabel = banner.match.species.formLabel() ?: "Form"
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.White.copy(alpha = 0.92f))
                                .clickable { showFormSelector = !showFormSelector }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$currentFormLabel ▾",
                                color = Color(0xFF1A1C20),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.18f))
                            .clickable { onDismiss() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss Raid Card",
                            tint = headerContentColor,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                // Inline expandable form switcher row
                AnimatedVisibility(visible = showFormSelector && banner.match.alternateForms.size > 1) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF7F8FA))
                            .padding(horizontal = 8.dp, vertical = 5.dp)
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
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color(0xFFFFFFFF) else Color(0xFF1A1C20),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Compact segmented tab toggle: Weaknesses | Suggested Party
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFFFFF))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFFF1F3F7))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        RaidCardSegmentButton(
                            label = "Weaknesses",
                            selected = activeTab == RaidCardTab.WEAKNESSES,
                            onClick = { onSelectTab(RaidCardTab.WEAKNESSES) },
                            modifier = Modifier.weight(1f)
                        )
                        RaidCardSegmentButton(
                            label = "Suggested Party (${verdict.team.size}/6)",
                            selected = activeTab == RaidCardTab.SUGGESTED_PARTY,
                            onClick = { onSelectTab(RaidCardTab.SUGGESTED_PARTY) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                when (activeTab) {
                    RaidCardTab.WEAKNESSES -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFFFFFF))
                                .padding(start = 10.dp, end = 10.dp, bottom = 8.dp)
                        ) {
                            WeaknessPriorityStrip(
                                matchup = banner.matchup,
                                showAvoidExpanded = showAvoidExpanded,
                                onToggleAvoid = { showAvoidExpanded = !showAvoidExpanded }
                            )
                        }
                    }

                    RaidCardTab.SUGGESTED_PARTY -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .verticalScroll(rememberScrollState())
                                .background(Color(0xFFFFFFFF))
                                .padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (verdict.team.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = verdict.verdictLabel,
                                        color = Color(0xFF1A1C20),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = String.format(Locale.US, "Est %.1f trainers", verdict.teamEstimator),
                                        color = Color(0xFF6E7582),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // 6-slot Suggested Party list
                            for (slotIndex in 0 until 6) {
                                val member = verdict.team.getOrNull(slotIndex)
                                if (member != null) {
                                    val movesText = listOfNotNull(
                                        member.fastMove?.displayName,
                                        member.chargedMove?.displayName
                                    ).joinToString(" + ").ifEmpty { "Moveset unset" }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(Color(0xFFF7F8FA))
                                            .border(1.dp, Color(0xFFECEFF4), RoundedCornerShape(9.dp))
                                            .padding(horizontal = 7.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "#${slotIndex + 1}",
                                            color = Color(0xFF6E7582),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.width(18.dp)
                                        )
                                        PokemonSprite(
                                            spriteFile = member.species.spriteFile,
                                            contentDescription = member.species.speciesName,
                                            size = 24.dp
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = member.species.speciesName,
                                                    color = Color(0xFF1A1C20),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (member.pokemon.isShadow) {
                                                    Text(
                                                        text = "Shadow",
                                                        color = Color(0xFF5E35B1),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            Text(
                                                text = movesText,
                                                color = Color(0xFF6E7582),
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = "CP ${member.pokemon.cp}",
                                            color = Color(0xFF1A1C20),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                } else {
                                    val suggestedMatchup = if (priorityWeaknesses.isNotEmpty()) {
                                        val offset = slotIndex - verdict.team.size
                                        priorityWeaknesses[offset % priorityWeaknesses.size]
                                    } else {
                                        null
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(Color(0xFFFAFAFC))
                                            .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(9.dp))
                                            .padding(horizontal = 7.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "#${slotIndex + 1}",
                                            color = Color(0xFF9CA3AF),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(18.dp)
                                        )
                                        Text(
                                            text = if (suggestedMatchup != null) {
                                                val mult = if (suggestedMatchup.multiplier >= 2.0) "2.56×" else "1.6×"
                                                "Empty slot · Bring $mult"
                                            } else {
                                                "Empty slot · Add counter in Scan mode"
                                            },
                                            color = Color(0xFF6E7582),
                                            fontSize = 10.sp,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (suggestedMatchup != null) {
                                            TypeChip(type = suggestedMatchup.attackType, compact = true)
                                        }
                                    }
                                }
                            }

                            if (verdict.team.size < 6 && priorityWeaknesses.isNotEmpty()) {
                                val remaining = 6 - verdict.team.size
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 2.dp)
                                ) {
                                    Text(
                                        text = if (verdict.team.isEmpty()) {
                                            "No saved counters yet — switch pill to Scan mode or fill 6 slots with:"
                                        } else {
                                            "Fill remaining $remaining slot${if (remaining == 1) "" else "s"} with top weakness types:"
                                        },
                                        color = Color(0xFF6E7582),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        priorityWeaknesses.take(5).forEach { w ->
                                            TypeChip(type = w.attackType, compact = true)
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

@Composable
private fun RaidCardSegmentButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Color(0xFF1A1C20) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color(0xFF6E7582),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickDrawerOverlay(
    activeRaids: List<RaidBossEntry>,
    allSpecies: List<PokemonSpecies>,
    isRefreshingRaids: Boolean,
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
                            text = "Pick a raid boss to show weaknesses & party",
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

                OutlinedButton(
                    onClick = onStopOverlay,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, Color(0xFFE53935)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFD32F2F)
                    ),
                    modifier = Modifier.fillMaxWidth()
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
