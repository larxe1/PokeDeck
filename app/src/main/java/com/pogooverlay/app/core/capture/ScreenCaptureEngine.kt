package com.pogooverlay.app.core.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class ScreenCaptureEngine(
    private val context: Context,
    private val onProjectionStopped: (() -> Unit)? = null
) {
    companion object {
        private const val MAX_CAPTURE_WIDTH = 900
        private const val VIRTUAL_DISPLAY_NAME = "PoGoRaidOverlayCapture"
        private const val MAX_IMAGES = 2
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val captureMutex = Mutex()

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var captureWidth: Int = 0
    private var captureHeight: Int = 0
    private var captureDensityDpi: Int = DisplayMetrics.DENSITY_DEFAULT

    @Volatile
    private var isCapturing: Boolean = false

    @Volatile
    var isScreenOn: Boolean = isDeviceInteractive()

    val hasActiveProjection: Boolean
        get() = isCapturing && mediaProjection != null && virtualDisplay != null && imageReader != null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            releaseInternal(stopProjection = false)
            onProjectionStopped?.invoke()
        }
    }

    fun start(projection: MediaProjection): Boolean {
        release()
        return try {
            val (width, height, densityDpi) = computeScaledMetrics()
            captureWidth = width
            captureHeight = height
            captureDensityDpi = densityDpi

            // Android 14/15 requirement: register MediaProjection.Callback BEFORE createVirtualDisplay
            projection.registerCallback(projectionCallback, mainHandler)

            val reader = ImageReader.newInstance(
                captureWidth,
                captureHeight,
                PixelFormat.RGBA_8888,
                MAX_IMAGES
            )

            val display = projection.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                captureWidth,
                captureHeight,
                captureDensityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                mainHandler
            )

            if (display == null) {
                reader.close()
                try {
                    projection.unregisterCallback(projectionCallback)
                } catch (_: Throwable) {
                }
                return false
            }

            mediaProjection = projection
            imageReader = reader
            virtualDisplay = display
            isCapturing = true
            isScreenOn = isDeviceInteractive()
            true
        } catch (_: Throwable) {
            release()
            false
        }
    }

    suspend fun captureLatestFrame(): Bitmap? = withContext(Dispatchers.Default) {
        if (!isScreenOn || !hasActiveProjection) {
            return@withContext null
        }

        captureMutex.withLock {
            val reader = imageReader ?: return@withLock null
            var image: Image? = null
            try {
                image = reader.acquireLatestImage()
                if (image == null) {
                    delay(50L)
                    image = reader.acquireLatestImage()
                }
                val acquired = image ?: return@withLock null
                val width = acquired.width
                val height = acquired.height
                val planes = acquired.planes
                if (width <= 0 || height <= 0 || planes.isEmpty()) {
                    return@withLock null
                }

                val plane = planes[0]
                val buffer = plane.buffer ?: return@withLock null
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                if (pixelStride <= 0 || rowStride <= 0) {
                    return@withLock null
                }

                val rowPadding = (rowStride - pixelStride * width).coerceAtLeast(0)
                val paddedWidth = width + (rowPadding / pixelStride)

                val paddedBitmap = Bitmap.createBitmap(
                    paddedWidth,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                buffer.rewind()
                paddedBitmap.copyPixelsFromBuffer(buffer)

                if (paddedWidth == width) {
                    paddedBitmap
                } else {
                    val croppedBitmap = Bitmap.createBitmap(paddedBitmap, 0, 0, width, height)
                    paddedBitmap.recycle()
                    croppedBitmap
                }
            } catch (_: Throwable) {
                null
            } finally {
                try {
                    image?.close()
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun release() {
        releaseInternal(stopProjection = true)
    }

    private fun releaseInternal(stopProjection: Boolean) {
        isCapturing = false

        try {
            virtualDisplay?.release()
        } catch (_: Throwable) {
        } finally {
            virtualDisplay = null
        }

        try {
            imageReader?.close()
        } catch (_: Throwable) {
        } finally {
            imageReader = null
        }

        val projection = mediaProjection
        mediaProjection = null
        if (projection != null) {
            try {
                projection.unregisterCallback(projectionCallback)
            } catch (_: Throwable) {
            }
            if (stopProjection) {
                try {
                    projection.stop()
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun computeScaledMetrics(): Triple<Int, Int, Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val rawWidth: Int
        val rawHeight: Int
        val densityDpi: Int

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            rawWidth = bounds.width().coerceAtLeast(1)
            rawHeight = bounds.height().coerceAtLeast(1)
            densityDpi = context.resources.configuration.densityDpi
                .takeIf { it > 0 } ?: DisplayMetrics.DENSITY_DEFAULT
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            rawWidth = metrics.widthPixels.coerceAtLeast(1)
            rawHeight = metrics.heightPixels.coerceAtLeast(1)
            densityDpi = metrics.densityDpi
                .takeIf { it > 0 } ?: DisplayMetrics.DENSITY_DEFAULT
        }

        val scale = if (rawWidth > MAX_CAPTURE_WIDTH) {
            MAX_CAPTURE_WIDTH.toFloat() / rawWidth.toFloat()
        } else {
            1f
        }

        val scaledWidth = (rawWidth * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (rawHeight * scale).roundToInt().coerceAtLeast(1)
        return Triple(scaledWidth, scaledHeight, densityDpi)
    }

    private fun isDeviceInteractive(): Boolean {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.isInteractive ?: true
        } catch (_: Throwable) {
            true
        }
    }
}
