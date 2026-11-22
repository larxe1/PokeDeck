package com.pogooverlay.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pogooverlay.app.core.data.MatchupSummary
import com.pogooverlay.app.core.data.PokemonType
import com.pogooverlay.app.core.data.TypeEffectivenessChart
import com.pogooverlay.app.core.data.TypeMatchup
import com.pogooverlay.app.ui.theme.PokedexBorder
import com.pogooverlay.app.ui.theme.PokedexDanger
import com.pogooverlay.app.ui.theme.PokedexGold
import com.pogooverlay.app.ui.theme.PokedexInk
import com.pogooverlay.app.ui.theme.PokedexMuted
import com.pogooverlay.app.ui.theme.PokedexSubtle
import com.pogooverlay.app.ui.theme.PokedexSurface
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

fun PokemonType.badgeColor(): Color = when (this) {
    PokemonType.FIRE -> Color(0xFFF08030)
    PokemonType.WATER -> Color(0xFF5B96F7)
    PokemonType.ELECTRIC -> Color(0xFFF7CF31)
    PokemonType.GRASS -> Color(0xFF68C956)
    PokemonType.ICE -> Color(0xFF7ED6DF)
    PokemonType.FIGHTING -> Color(0xFFD64541)
    PokemonType.POISON -> Color(0xFFA652BB)
    PokemonType.GROUND -> Color(0xFFD9B34A)
    PokemonType.FLYING -> Color(0xFF8BA8F8)
    PokemonType.PSYCHIC -> Color(0xFFF45B84)
    PokemonType.BUG -> Color(0xFF9BB82F)
    PokemonType.ROCK -> Color(0xFFBA9F4B)
    PokemonType.GHOST -> Color(0xFF735B9B)
    PokemonType.DRAGON -> Color(0xFF6F4CF5)
    PokemonType.DARK -> Color(0xFF4B4340)
    PokemonType.STEEL -> Color(0xFF9AA4B8)
    PokemonType.FAIRY -> Color(0xFFEE99AC)
    PokemonType.NORMAL -> Color(0xFF9E9E8B)
}

fun PokemonType.contentColor(): Color = when (this) {
    PokemonType.ELECTRIC,
    PokemonType.ICE,
    PokemonType.GROUND,
    PokemonType.FAIRY,
    PokemonType.NORMAL,
    PokemonType.STEEL -> PokedexInk

    else -> Color.White
}

@Composable
fun TypeChip(
    type: PokemonType,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val bg = type.badgeColor()
    val fg = type.contentColor()
    val horizontalPad = if (compact) 8.dp else 10.dp
    val verticalPad = if (compact) 4.dp else 5.dp
    val iconSize = if (compact) 12.dp else 14.dp
    val fontSize = if (compact) 11.sp else 12.sp

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = bg,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPad, vertical = verticalPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 5.dp),
        ) {
            TypeSymbolIcon(
                type = type,
                tint = fg,
                modifier = Modifier.size(iconSize),
            )
            Text(
                text = type.displayName,
                color = fg,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun TypeDot(
    type: PokemonType,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier,
) {
    val bg = type.badgeColor()
    val fg = type.contentColor()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .semantics { contentDescription = type.displayName },
        contentAlignment = Alignment.Center,
    ) {
        TypeSymbolIcon(
            type = type,
            tint = fg,
            modifier = Modifier.size(size * 0.58f),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeaknessPriorityStrip(
    matchup: MatchupSummary,
    showAvoidExpanded: Boolean,
    onToggleAvoid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (matchup.defendingTypes.isEmpty()) {
            Text(
                text = "Select 1 or 2 defender types to calculate weaknesses.",
                color = PokedexMuted,
                fontSize = 12.sp,
            )
            return@Column
        }

        if (matchup.doubleWeaknesses.isNotEmpty()) {
            WeaknessTierRow(
                multiplierLabel = "2.56×",
                matchups = matchup.doubleWeaknesses,
                isDouble = true,
            )
        }

        if (matchup.singleWeaknesses.isNotEmpty()) {
            WeaknessTierRow(
                multiplierLabel = "1.6×",
                matchups = matchup.singleWeaknesses,
                isDouble = false,
            )
        }

        if (matchup.orderedWeaknesses.isEmpty()) {
            Text(
                text = "No weaknesses found",
                color = PokedexMuted,
                fontSize = 12.sp,
            )
        }

        if (matchup.resistances.isNotEmpty()) {
            val toggleText = if (showAvoidExpanded) {
                "Hide Avoid / Resistances ▴"
            } else {
                "Show Avoid / Resistances ▾"
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleAvoid)
                    .padding(vertical = 2.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = toggleText,
                    color = PokedexMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = PokedexSubtle,
                ) {
                    Text(
                        text = matchup.resistances.size.toString(),
                        color = PokedexMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = showAvoidExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                ResistanceBreakdown(resistances = matchup.resistances)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeaknessTierRow(
    multiplierLabel: String,
    matchups: List<TypeMatchup>,
    isDouble: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        MultiplierBadge(
            label = multiplierLabel,
            isDoubleWeakness = isDouble,
        )
        Spacer(modifier = Modifier.width(8.dp))
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            matchups.forEach { item ->
                TypeChip(type = item.attackType, compact = true)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResistanceBreakdown(resistances: List<TypeMatchup>) {
    // Group by formatted multiplier while preserving the incoming priority order (0.153× -> 0.244× -> 0.39× -> 0.625×)
    val grouped = resistances.groupBy { TypeEffectivenessChart.formatMultiplier(it.multiplier) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        grouped.forEach { (label, items) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PokedexSubtle,
                    border = BorderStroke(1.dp, PokedexBorder),
                ) {
                    Text(
                        text = label,
                        color = PokedexMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items.forEach { item ->
                        TypeChip(type = item.attackType, compact = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun MultiplierBadge(
    label: String,
    isDoubleWeakness: Boolean,
) {
    val containerColor = if (isDoubleWeakness) PokedexInk else PokedexSurface
    val textColor = if (isDoubleWeakness) PokedexGold else PokedexInk
    val border = if (isDoubleWeakness) {
        BorderStroke(1.dp, PokedexInk)
    } else {
        BorderStroke(1.dp, PokedexBorder)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        border = border,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (isDoubleWeakness) {
                Canvas(modifier = Modifier.size(6.dp)) {
                    drawCircle(color = PokedexDanger)
                }
            }
            Text(
                text = label,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TypeSymbolIcon(
    type: PokemonType,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = size.minDimension * 0.42f
        val stroke = (size.minDimension * 0.13f).coerceAtLeast(1.2f)

        when (type) {
            PokemonType.FIRE -> {
                val path = Path().apply {
                    moveTo(cx, h * 0.1f)
                    cubicTo(w * 0.85f, h * 0.42f, w * 0.84f, h * 0.86f, cx, h * 0.9f)
                    cubicTo(w * 0.16f, h * 0.86f, w * 0.15f, h * 0.42f, cx, h * 0.1f)
                    close()
                }
                drawPath(path, color = tint)
            }

            PokemonType.WATER -> {
                val path = Path().apply {
                    moveTo(cx, h * 0.12f)
                    cubicTo(w * 0.82f, h * 0.48f, w * 0.82f, h * 0.88f, cx, h * 0.88f)
                    cubicTo(w * 0.18f, h * 0.88f, w * 0.18f, h * 0.48f, cx, h * 0.12f)
                    close()
                }
                drawPath(path, color = tint)
            }

            PokemonType.ELECTRIC -> {
                val path = Path().apply {
                    moveTo(w * 0.58f, h * 0.08f)
                    lineTo(w * 0.24f, h * 0.54f)
                    lineTo(w * 0.50f, h * 0.54f)
                    lineTo(w * 0.40f, h * 0.92f)
                    lineTo(w * 0.78f, h * 0.44f)
                    lineTo(w * 0.52f, h * 0.44f)
                    close()
                }
                drawPath(path, color = tint)
            }

            PokemonType.GRASS -> {
                val path = Path().apply {
                    moveTo(w * 0.82f, h * 0.16f)
                    cubicTo(w * 0.30f, h * 0.16f, w * 0.14f, h * 0.50f, w * 0.20f, h * 0.84f)
                    cubicTo(w * 0.54f, h * 0.86f, w * 0.86f, h * 0.66f, w * 0.82f, h * 0.16f)
                    close()
                }
                drawPath(path, color = tint)
            }

            PokemonType.ICE -> {
                val arms = 3
                for (i in 0 until arms) {
                    val angle = i * (PI / 3.0)
                    val dx = (cos(angle) * r).toFloat()
                    val dy = (sin(angle) * r).toFloat()
                    drawLine(
                        color = tint,
                        start = Offset(cx - dx, cy - dy),
                        end = Offset(cx + dx, cy + dy),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }

            PokemonType.FIGHTING -> {
                drawCircle(color = tint, radius = r * 0.68f, center = Offset(cx, cy + h * 0.06f))
                drawLine(
                    color = tint,
                    start = Offset(cx - r * 0.55f, cy - r * 0.55f),
                    end = Offset(cx + r * 0.55f, cy - r * 0.55f),
                    strokeWidth = stroke * 1.15f,
                    cap = StrokeCap.Round,
                )
            }

            PokemonType.POISON -> {
                drawCircle(color = tint, radius = r * 0.62f, center = Offset(cx, cy - h * 0.06f))
                drawCircle(color = tint, radius = r * 0.26f, center = Offset(cx - r * 0.55f, cy + h * 0.28f))
                drawCircle(color = tint, radius = r * 0.26f, center = Offset(cx + r * 0.55f, cy + h * 0.28f))
            }

            PokemonType.GROUND -> {
                val path = Path().apply {
                    moveTo(w * 0.12f, h * 0.82f)
                    lineTo(w * 0.38f, h * 0.28f)
                    lineTo(w * 0.56f, h * 0.58f)
                    lineTo(w * 0.70f, h * 0.36f)
                    lineTo(w * 0.90f, h * 0.82f)
                    close()
                }
                drawPath(path, color = tint)
            }

            PokemonType.FLYING -> {
                val path = Path().apply {
                    moveTo(w * 0.12f, h * 0.30f)
                    cubicTo(w * 0.40f, h * 0.14f, w * 0.68f, h * 0.22f, w * 0.88f, h * 0.42f)
                    cubicTo(w * 0.64f, h * 0.56f, w * 0.42f, h * 0.64f, w * 0.22f, h * 0.76f)
                    cubicTo(w * 0.32f, h * 0.56f, w * 0.26f, h * 0.42f, w * 0.12f, h * 0.30f)
                    close()
                }
                drawPath(path, color = tint)
            }

            PokemonType.PSYCHIC -> {
                drawCircle(
                    color = tint,
                    radius = r * 0.88f,
                    center = Offset(cx, cy),
                    style = Stroke(width = stroke),
                )
                drawCircle(
                    color = tint,
                    radius = r * 0.36f,
                    center = Offset(cx, cy),
                )
            }

            PokemonType.BUG -> {
                drawOval(
                    color = tint,
                    topLeft = Offset(cx - r * 0.58f, cy - r * 0.72f),
                    size = Size(r * 1.16f, r * 1.44f),
                )
                drawLine(
                    color = tint,
                    start = Offset(cx - r * 0.35f, cy - r * 0.6f),
                    end = Offset(cx - r * 0.7f, cy - r * 0.95f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(cx + r * 0.35f, cy - r * 0.6f),
                    end = Offset(cx + r * 0.7f, cy - r * 0.95f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }

            PokemonType.ROCK -> {
                val path = Path().apply {
                    moveTo(cx, h * 0.12f)
                    lineTo(w * 0.86f, h * 0.44f)
                    lineTo(w * 0.72f, h * 0.86f)
                    lineTo(w * 0.28f, h * 0.86f)
                    lineTo(w * 0.14f, h * 0.44f)
                    close()
                }
                drawPath(path, color = tint)
            }

            PokemonType.GHOST -> {
                val path = Path().apply {
                    moveTo(w * 0.22f, h * 0.84f)
                    lineTo(w * 0.22f, h * 0.44f)
                    cubicTo(w * 0.22f, h * 0.12f, w * 0.78f, h * 0.12f, w * 0.78f, h * 0.44f)
                    lineTo(w * 0.78f, h * 0.84f)
                    lineTo(w * 0.60f, h * 0.70f)
                    lineTo(cx, h * 0.84f)
                    lineTo(w * 0.40f, h * 0.70f)
                    close()
                }
                drawPath(path, color = tint)
            }

            PokemonType.DRAGON -> {
                val path = Path().apply {
                    moveTo(w * 0.20f, h * 0.24f)
                    lineTo(w * 0.84f, h * 0.44f)
                    lineTo(w * 0.48f, h * 0.56f)
                    lineTo(w * 0.76f, h * 0.82f)
                    lineTo(w * 0.24f, h * 0.68f)
                    close()
                }
                drawPath(path, color = tint)
            }

            PokemonType.DARK -> {
                val path = Path().apply {
                    moveTo(w * 0.68f, h * 0.14f)
                    cubicTo(w * 0.28f, h * 0.18f, w * 0.22f, h * 0.78f, w * 0.72f, h * 0.84f)
                    cubicTo(w * 0.44f, h * 0.68f, w * 0.46f, h * 0.30f, w * 0.68f, h * 0.14f)
                    close()
                }
                drawPath(path, color = tint)
            }

            PokemonType.STEEL -> {
                drawHexagon(cx, cy, r * 0.92f, tint, stroke)
                drawCircle(color = tint, radius = r * 0.32f, center = Offset(cx, cy))
            }

            PokemonType.FAIRY -> {
                val path = Path().apply {
                    moveTo(cx, h * 0.10f)
                    cubicTo(cx, h * 0.38f, w * 0.62f, cy, w * 0.90f, cy)
                    cubicTo(w * 0.62f, cy, cx, h * 0.62f, cx, h * 0.90f)
                    cubicTo(cx, h * 0.62f, w * 0.38f, cy, w * 0.10f, cy)
                    cubicTo(w * 0.38f, cy, cx, h * 0.38f, cx, h * 0.10f)
                    close()
                }
                drawPath(path, color = tint)
            }

            PokemonType.NORMAL -> {
                drawCircle(
                    color = tint,
                    radius = r * 0.82f,
                    center = Offset(cx, cy),
                    style = Stroke(width = stroke * 1.15f),
                )
            }
        }
    }
}

private fun DrawScope.drawHexagon(
    cx: Float,
    cy: Float,
    radius: Float,
    color: Color,
    strokeWidth: Float,
) {
    val path = Path()
    for (i in 0 until 6) {
        val angle = (i * 60.0 - 30.0) * (PI / 180.0)
        val x = cx + (cos(angle) * radius).toFloat()
        val y = cy + (sin(angle) * radius).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, join = StrokeJoin.Round),
    )
}
