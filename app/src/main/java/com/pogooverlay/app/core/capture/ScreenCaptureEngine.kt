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
import java.nio.ByteBuffer
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
    private val lifecycleLock = Any()

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
        return synchronized(lifecycleLock) {
            try {
                val (width, height, densityDpi) = computeScaledMetrics()
                captureWidth = width
                captureHeight = height
                captureDensityDpi = densityDpi

                // Android 14/15 requirement: register MediaProjection.Callback BEFORE createVirtualDisplay
                projection.registerCallback(projectionCallback, mainHandler)

                // Persistent ImageReader with no background listener — remains idle until manual captureLatestFrame()
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
                    return@synchronized false
                }

                mediaProjection = projection
                imageReader = reader
                virtualDisplay = display
                isCapturing = true
                isScreenOn = isDeviceInteractive()
                true
            } catch (_: Throwable) {
                releaseInternal(stopProjection = true)
                false
            }
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
                imageToBitmap(acquired)
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

    private fun imageToBitmap(image: Image): Bitmap? {
        val width = image.width
        val height = image.height
        val planes = image.planes
        if (width <= 0 || height <= 0 || planes.isEmpty()) {
            return null
        }

        val plane = planes[0]
        val buffer = plane.buffer ?: return null
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        if (pixelStride <= 0 || rowStride <= 0) {
            return null
        }

        val rowPadding = (rowStride - pixelStride * width).coerceAtLeast(0)
        buffer.rewind()

        if (pixelStride == 4 && rowPadding == 0) {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)
            return bitmap
        }

        if (pixelStride == 4 && rowPadding % pixelStride == 0) {
            val paddedWidth = width + (rowPadding / pixelStride)
            val expectedBytes = paddedWidth.toLong() * height.toLong() * 4L
            if (buffer.remaining().toLong() >= expectedBytes) {
                val paddedBitmap = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
                paddedBitmap.copyPixelsFromBuffer(buffer)
                val croppedBitmap = Bitmap.createBitmap(paddedBitmap, 0, 0, width, height)
                paddedBitmap.recycle()
                return croppedBitmap
            }
        }

        // Row-by-row copy handles arbitrary rowStride/pixelStride and buffers without trailing padding on the last row
        val packed = ByteArray(width * height * 4)
        val rowBuffer = ByteArray(rowStride.coerceAtLeast(width * pixelStride))
        var dstOffset = 0
        for (row in 0 until height) {
            val rowStart = row * rowStride
            if (rowStart >= buffer.limit()) break
            val bytesToRead = minOf(rowStride, buffer.limit() - rowStart)
            buffer.position(rowStart)
            buffer.get(rowBuffer, 0, bytesToRead)

            if (pixelStride == 4) {
                val copyLen = minOf(width * 4, bytesToRead)
                System.arraycopy(rowBuffer, 0, packed, dstOffset, copyLen)
                dstOffset += width * 4
            } else {
                for (col in 0 until width) {
                    val srcIdx = col * pixelStride
                    if (srcIdx + 3 < bytesToRead) {
                        packed[dstOffset] = rowBuffer[srcIdx]
                        packed[dstOffset + 1] = rowBuffer[srcIdx + 1]
                        packed[dstOffset + 2] = rowBuffer[srcIdx + 2]
                        packed[dstOffset + 3] = rowBuffer[srcIdx + 3]
                    }
                    dstOffset += 4
                }
            }
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(packed))
        return bitmap
    }

    fun release() {
        releaseInternal(stopProjection = true)
    }

    private fun releaseInternal(stopProjection: Boolean) {
        synchronized(lifecycleLock) {
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
