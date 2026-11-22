package com.pogooverlay.app.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.IOException

/**
 * In-memory LRU cache for bundled offline pixel sprites in `assets/sprites/`.
 */
object SpriteAssetCache {
    private const val MAX_ENTRIES = 128
    private val cache = LruCache<String, ImageBitmap>(MAX_ENTRIES)

    fun load(context: Context, spriteFile: String?): ImageBitmap? {
        val normalized = spriteFile?.trim()?.removePrefix("sprites/")?.takeIf { it.isNotEmpty() }
            ?: return null

        cache.get(normalized)?.let { return it }

        val decoded = try {
            val options = BitmapFactory.Options().apply {
                inScaled = false
            }
            context.assets.open("sprites/$normalized").use { stream ->
                BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
            }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

        if (decoded != null) {
            cache.put(normalized, decoded)
        }
        return decoded
    }
}

/**
 * Renders a crisp bundled pixel sprite using [FilterQuality.None], or a vector Pokéball
 * fallback when the sprite asset is unavailable.
 */
@Composable
fun PokemonSprite(
    spriteFile: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    fallbackTint: Color = Color(0xFF9AA3B2),
) {
    val context = LocalContext.current
    val bitmap = remember(spriteFile) {
        SpriteAssetCache.load(context, spriteFile)
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier.size(size),
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.None,
        )
    } else {
        PokeballFallbackIcon(
            contentDescription = contentDescription,
            tint = fallbackTint,
            modifier = modifier.size(size),
        )
    }
}

@Composable
private fun PokeballFallbackIcon(
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val accessibilityModifier = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else {
        modifier
    }

    Canvas(modifier = accessibilityModifier) {
        val diameter = size.minDimension
        val radius = diameter * 0.38f
        val strokeWidth = (diameter * 0.075f).coerceAtLeast(1.5f)
        val center = Offset(size.width / 2f, size.height / 2f)
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2f, radius * 2f)

        // Subtle top-half fill
        drawArc(
            color = tint.copy(alpha = 0.22f),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = topLeft,
            size = arcSize,
        )

        // Outer ring
        drawCircle(
            color = tint,
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth),
        )

        val innerRadius = radius * 0.32f
        // Equator left and right segments
        drawLine(
            color = tint,
            start = Offset(center.x - radius, center.y),
            end = Offset(center.x - innerRadius, center.y),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = tint,
            start = Offset(center.x + innerRadius, center.y),
            end = Offset(center.x + radius, center.y),
            strokeWidth = strokeWidth,
        )

        // Center button
        drawCircle(
            color = tint,
            radius = innerRadius,
            center = center,
            style = Stroke(width = strokeWidth),
        )
        drawCircle(
            color = tint.copy(alpha = 0.45f),
            radius = innerRadius * 0.45f,
            center = center,
        )
    }
}
