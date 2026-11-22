package com.pogooverlay.app.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pogooverlay.app.core.data.OverlayRuntimeState
import com.pogooverlay.app.core.data.PokemonNameMatcher
import com.pogooverlay.app.core.data.PokemonSpecies
import com.pogooverlay.app.core.data.PokemonType
import com.pogooverlay.app.core.data.RaidBossEntry
import com.pogooverlay.app.core.data.ScanMode
import com.pogooverlay.app.core.data.SpeciesMatchResult
import com.pogooverlay.app.core.data.TypeEffectivenessChart
import com.pogooverlay.app.ui.components.PokemonSprite
import com.pogooverlay.app.ui.components.TypeChip
import com.pogooverlay.app.ui.components.TypeDot
import com.pogooverlay.app.ui.components.WeaknessPriorityStrip
import com.pogooverlay.app.ui.components.badgeColor
import com.pogooverlay.app.ui.components.contentColor
import com.pogooverlay.app.ui.theme.PokedexBackground
import com.pogooverlay.app.ui.theme.PokedexBorder
import com.pogooverlay.app.ui.theme.PokedexDanger
import com.pogooverlay.app.ui.theme.PokedexGold
import com.pogooverlay.app.ui.theme.PokedexInk
import com.pogooverlay.app.ui.theme.PokedexMuted
import com.pogooverlay.app.ui.theme.PokedexSubtle
import com.pogooverlay.app.ui.theme.PokedexSuccess
import com.pogooverlay.app.ui.theme.PokedexSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class HomeTab(val label: String) {
    RAIDS("Today's Raids"),
    SETUP("Setup & Status"),
}

private data class PreviewBoss(
    val id: String,
    val displayName: String,
    val dex: Int?,
    val spriteFile: String?,
    val types: List<PokemonType>,
    val subtitle: String? = null,
    val alternateForms: List<PokemonSpecies> = emptyList(),
)

@Composable
fun HomeScreen(
    species: List<PokemonSpecies>,
    activeRaids: List<RaidBossEntry>,
    lastRaidsRefreshEpochMs: Long?,
    isRefreshing: Boolean,
    lastError: String?,
    overlayState: OverlayRuntimeState,
    hasOverlayPermission: Boolean,
    hasNotificationPermission: Boolean,
    needsNotificationPermission: Boolean,
    matcher: PokemonNameMatcher,
    onRefreshRaids: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onActivateOverlay: () -> Unit,
    onStartManualOnly: () -> Unit,
    onStopOverlay: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(HomeTab.RAIDS) }
    var searchQuery by remember { mutableStateOf("") }
    var previewBoss by remember { mutableStateOf<PreviewBoss?>(null) }
    var showAvoidExpanded by remember { mutableStateOf(false) }

    val targetHeroColor = previewBoss?.types?.firstOrNull()?.badgeColor() ?: PokedexGold
    val heroColor by animateColorAsState(
        targetValue = targetHeroColor,
        animationSpec = tween(durationMillis = 320),
        label = "HeroBackgroundColor",
    )
    val targetHeroInk = previewBoss?.types?.firstOrNull()?.contentColor() ?: PokedexInk
    val heroContentColor by animateColorAsState(
        targetValue = targetHeroInk,
        animationSpec = tween(durationMillis = 280),
        label = "HeroContentColor",
    )

    val resolvedRaids = remember(activeRaids, matcher) {
        activeRaids.map { raid ->
            raid to matcher.matchText(raid.name, activeRaids)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PokedexSurface),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(heroColor),
        ) {
            HeroHeader(
                previewBoss = previewBoss,
                heroContentColor = heroContentColor,
                overlayState = overlayState,
                activeRaidCount = activeRaids.size,
                speciesCount = species.size,
                showAvoidExpanded = showAvoidExpanded,
                onToggleAvoid = { showAvoidExpanded = !showAvoidExpanded },
                onSelectForm = { form ->
                    previewBoss = PreviewBoss(
                        id = "species:${form.speciesId}",
                        displayName = form.speciesName,
                        dex = form.dex,
                        spriteFile = form.spriteFile,
                        types = form.parsedTypes(),
                        subtitle = form.formLabel()?.let { "Form: $it" },
                        alternateForms = previewBoss?.alternateForms.orEmpty(),
                    )
                },
                onClearPreview = {
                    previewBoss = null
                    showAvoidExpanded = false
                },
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = PokedexSurface,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    UnderlineTabs(
                        selectedTab = selectedTab,
                        onSelectTab = { selectedTab = it },
                    )

                    when (selectedTab) {
                        HomeTab.RAIDS -> TodayRaidsContent(
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            resolvedRaids = resolvedRaids,
                            matcher = matcher,
                            selectedBossId = previewBoss?.id,
                            isRefreshing = isRefreshing,
                            lastError = lastError,
                            lastRaidsRefreshEpochMs = lastRaidsRefreshEpochMs,
                            onRefreshRaids = onRefreshRaids,
                            onSelectRaid = { raid, matched ->
                                val raidTypes = raid.types.mapNotNull { PokemonType.fromApiKey(it) }
                                    .ifEmpty { matched?.species?.parsedTypes().orEmpty() }
                                previewBoss = PreviewBoss(
                                    id = "raid:${raid.name}:${raid.tier}",
                                    displayName = raid.name,
                                    dex = matched?.species?.dex,
                                    spriteFile = matched?.species?.spriteFile,
                                    types = raidTypes,
                                    subtitle = formatRaidSubtitle(raid),
                                    alternateForms = matched?.alternateForms.orEmpty(),
                                )
                                showAvoidExpanded = false
                            },
                            onSelectSpecies = { chosen ->
                                previewBoss = PreviewBoss(
                                    id = "species:${chosen.speciesId}",
                                    displayName = chosen.speciesName,
                                    dex = chosen.dex,
                                    spriteFile = chosen.spriteFile,
                                    types = chosen.parsedTypes(),
                                    subtitle = chosen.formLabel()?.let { "Form: $it" },
                                    alternateForms = matcher.getFormsForSpecies(chosen),
                                )
                                showAvoidExpanded = false
                            },
                        )

                        HomeTab.SETUP -> SetupStatusContent(
                            hasOverlayPermission = hasOverlayPermission,
                            hasNotificationPermission = hasNotificationPermission,
                            needsNotificationPermission = needsNotificationPermission,
                            species = species,
                            activeRaidsCount = activeRaids.size,
                            lastRaidsRefreshEpochMs = lastRaidsRefreshEpochMs,
                            isRefreshing = isRefreshing,
                            lastError = lastError,
                            onRequestOverlayPermission = onRequestOverlayPermission,
                            onRequestNotificationPermission = onRequestNotificationPermission,
                            onRefreshRaids = onRefreshRaids,
                        )
                    }
                }
            }
        }

        BottomCtaBar(
            isOverlayRunning = overlayState.isRunning,
            onActivateOverlay = onActivateOverlay,
            onStartManualOnly = onStartManualOnly,
            onStopOverlay = onStopOverlay,
        )
    }
}

@Composable
private fun HeroHeader(
    previewBoss: PreviewBoss?,
    heroContentColor: Color,
    overlayState: OverlayRuntimeState,
    activeRaidCount: Int,
    speciesCount: Int,
    showAvoidExpanded: Boolean,
    onToggleAvoid: () -> Unit,
    onSelectForm: (PokemonSpecies) -> Unit,
    onClearPreview: () -> Unit,
) {
    var formMenuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        // Subtle Canvas Pokéball watermark in top-right
        Canvas(
            modifier = Modifier
                .size(156.dp)
                .align(Alignment.TopEnd),
        ) {
            val watermarkColor = heroContentColor.copy(alpha = 0.11f)
            val center = Offset(size.width * 0.72f, size.height * 0.28f)
            val outerRadius = size.minDimension * 0.46f
            val innerRadius = outerRadius * 0.28f
            val strokeWidth = outerRadius * 0.14f

            drawCircle(
                color = watermarkColor,
                radius = outerRadius,
                center = center,
                style = Stroke(width = strokeWidth),
            )
            drawLine(
                color = watermarkColor,
                start = Offset(center.x - outerRadius, center.y),
                end = Offset(center.x - innerRadius, center.y),
                strokeWidth = strokeWidth,
            )
            drawLine(
                color = watermarkColor,
                start = Offset(center.x + innerRadius, center.y),
                end = Offset(center.x + outerRadius, center.y),
                strokeWidth = strokeWidth,
            )
            drawCircle(
                color = watermarkColor,
                radius = innerRadius,
                center = center,
                style = Stroke(width = strokeWidth),
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Top bar: brand label + live status pill + optional clear button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "PoGo Raid Overlay",
                        color = heroContentColor.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    StatusPill(
                        isRunning = overlayState.isRunning,
                        scanMode = overlayState.scanMode,
                        heroContentColor = heroContentColor,
                    )
                }

                if (previewBoss != null) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = heroContentColor.copy(alpha = 0.14f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onClearPreview),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear preview",
                                tint = heroContentColor,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "Reset",
                                color = heroContentColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            if (previewBoss == null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Raid Counter HUD Ready",
                        color = heroContentColor,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.4).sp,
                    )
                    Text(
                        text = "Tap any boss below to inspect 2.56× and 1.6× counters, or activate the floating overlay over Pokémon GO.",
                        color = heroContentColor.copy(alpha = 0.82f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroStatChip(
                            label = "$activeRaidCount Active Raids",
                            heroContentColor = heroContentColor,
                        )
                        HeroStatChip(
                            label = "$speciesCount Species Offline",
                            heroContentColor = heroContentColor,
                        )
                    }
                }
            } else {
                val matchup = remember(previewBoss.types) {
                    TypeEffectivenessChart.calculate(previewBoss.types)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = heroContentColor.copy(alpha = 0.14f),
                        modifier = Modifier.size(72.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            PokemonSprite(
                                spriteFile = previewBoss.spriteFile,
                                contentDescription = previewBoss.displayName,
                                size = 60.dp,
                                fallbackTint = heroContentColor,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            previewBoss.dex?.let { dex ->
                                Text(
                                    text = String.format(Locale.US, "#%03d", dex),
                                    color = heroContentColor.copy(alpha = 0.78f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            previewBoss.subtitle?.let { sub ->
                                Text(
                                    text = if (previewBoss.dex != null) "• $sub" else sub,
                                    color = heroContentColor.copy(alpha = 0.78f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        Text(
                            text = previewBoss.displayName,
                            color = heroContentColor,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            previewBoss.types.forEach { type ->
                                TypeChip(type = type, compact = true)
                            }

                            if (previewBoss.alternateForms.size > 1) {
                                Box {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = heroContentColor.copy(alpha = 0.16f),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .clickable { formMenuExpanded = true },
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            Text(
                                                text = "Form",
                                                color = heroContentColor,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Icon(
                                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                                contentDescription = "Switch form",
                                                tint = heroContentColor,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = formMenuExpanded,
                                        onDismissRequest = { formMenuExpanded = false },
                                    ) {
                                        previewBoss.alternateForms.forEach { form ->
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    ) {
                                                        PokemonSprite(
                                                            spriteFile = form.spriteFile,
                                                            contentDescription = form.speciesName,
                                                            size = 24.dp,
                                                        )
                                                        Text(
                                                            text = form.speciesName,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Medium,
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    formMenuExpanded = false
                                                    onSelectForm(form)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Pure-white card inside hero for crisp Light Pokédex weakness readability
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = PokedexSurface,
                    shadowElevation = 2.dp,
                ) {
                    WeaknessPriorityStrip(
                        matchup = matchup,
                        showAvoidExpanded = showAvoidExpanded,
                        onToggleAvoid = onToggleAvoid,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    isRunning: Boolean,
    scanMode: ScanMode,
    heroContentColor: Color,
) {
    val label = when {
        !isRunning -> "Idle"
        scanMode == ScanMode.AUTO_WATCH -> "Auto Watch"
        else -> "Battery Saver"
    }
    val dotColor = if (isRunning) PokedexSuccess else heroContentColor.copy(alpha = 0.55f)

    Surface(
        shape = RoundedCornerShape(50),
        color = heroContentColor.copy(alpha = 0.14f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Canvas(modifier = Modifier.size(6.dp)) {
                drawCircle(color = dotColor)
            }
            Text(
                text = label,
                color = heroContentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun HeroStatChip(
    label: String,
    heroContentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = heroContentColor.copy(alpha = 0.14f),
    ) {
        Text(
            text = label,
            color = heroContentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun UnderlineTabs(
    selectedTab: HomeTab,
    onSelectTab: (HomeTab) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            HomeTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                Column(
                    modifier = Modifier
                        .clickable { onSelectTab(tab) }
                        .padding(top = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = tab.label,
                        color = if (isSelected) PokedexInk else PokedexMuted,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .width(if (isSelected) 40.dp else 0.dp)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (isSelected) PokedexInk else Color.Transparent),
                    )
                }
            }
        }
        HorizontalDivider(color = PokedexBorder, thickness = 1.dp)
    }
}

@Composable
private fun TodayRaidsContent(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    resolvedRaids: List<Pair<RaidBossEntry, SpeciesMatchResult?>>,
    matcher: PokemonNameMatcher,
    selectedBossId: String?,
    isRefreshing: Boolean,
    lastError: String?,
    lastRaidsRefreshEpochMs: Long?,
    onRefreshRaids: () -> Unit,
    onSelectRaid: (RaidBossEntry, SpeciesMatchResult?) -> Unit,
    onSelectSpecies: (PokemonSpecies) -> Unit,
) {
    val filteredRaids = remember(searchQuery, resolvedRaids) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            resolvedRaids
        } else {
            resolvedRaids.filter { (raid, matched) ->
                raid.name.contains(q, ignoreCase = true) ||
                    raid.tier.contains(q, ignoreCase = true) ||
                    matched?.species?.dex?.toString() == q.removePrefix("#")
            }
        }
    }

    val pokedexMatches = remember(searchQuery, matcher) {
        val q = searchQuery.trim()
        if (q.isEmpty()) emptyList() else matcher.search(q, limit = 40)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = {
                    Text(
                        text = "Search raids or full Pokédex...",
                        color = PokedexMuted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = PokedexMuted,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear search",
                                tint = PokedexMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = PokedexBackground,
                    unfocusedContainerColor = PokedexBackground,
                    focusedBorderColor = PokedexInk,
                    unfocusedBorderColor = PokedexBorder,
                    focusedTextColor = PokedexInk,
                    unfocusedTextColor = PokedexInk,
                    cursorColor = PokedexInk,
                ),
            )

            Surface(
                shape = CircleShape,
                color = PokedexSurface,
                border = BorderStroke(1.dp, PokedexBorder),
                modifier = Modifier.size(48.dp),
            ) {
                IconButton(
                    onClick = onRefreshRaids,
                    enabled = !isRefreshing,
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = PokedexInk,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh live raids",
                            tint = PokedexInk,
                        )
                    }
                }
            }
        }

        if (lastError != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = PokedexSubtle,
                border = BorderStroke(1.dp, PokedexBorder),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = PokedexMuted,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Offline fallback active ($lastError)",
                        color = PokedexMuted,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (searchQuery.isBlank()) {
                item(key = "raids-header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "ACTIVE RAID BOSSES (${filteredRaids.size})",
                            color = PokedexMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                        )
                        Text(
                            text = lastRaidsRefreshEpochMs?.let { "Live • ${formatTime(it)}" } ?: "Bundled snapshot",
                            color = PokedexMuted,
                            fontSize = 11.sp,
                        )
                    }
                }
            } else if (filteredRaids.isNotEmpty()) {
                item(key = "matching-raids-header") {
                    Text(
                        text = "MATCHING ACTIVE RAIDS (${filteredRaids.size})",
                        color = PokedexMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }

            itemsIndexed(
                items = filteredRaids,
                key = { index, (raid, _) -> "raid:$index:${raid.name}:${raid.tier}" },
            ) { _, (raid, matched) ->
                val isSelected = selectedBossId == "raid:${raid.name}:${raid.tier}"
                RaidBossRow(
                    raid = raid,
                    matched = matched,
                    isSelected = isSelected,
                    onClick = { onSelectRaid(raid, matched) },
                )
            }

            if (searchQuery.isNotBlank() && pokedexMatches.isNotEmpty()) {
                item(key = "pokedex-header") {
                    Text(
                        text = "FULL POKÉDEX (${pokedexMatches.size})",
                        color = PokedexMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                    )
                }

                items(
                    items = pokedexMatches,
                    key = { speciesItem -> "dex:${speciesItem.speciesId}" },
                ) { speciesItem ->
                    val isSelected = selectedBossId == "species:${speciesItem.speciesId}"
                    SpeciesRow(
                        species = speciesItem,
                        isSelected = isSelected,
                        onClick = { onSelectSpecies(speciesItem) },
                    )
                }
            }

            if (filteredRaids.isEmpty() && pokedexMatches.isEmpty()) {
                item(key = "empty-state") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = PokedexBackground,
                        border = BorderStroke(1.dp, PokedexBorder),
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) {
                                "No active raids loaded yet. Tap refresh or search the full Pokédex."
                            } else {
                                "No Pokémon matched \"${searchQuery.trim()}\"."
                            },
                            color = PokedexMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RaidBossRow(
    raid: RaidBossEntry,
    matched: SpeciesMatchResult?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val types = remember(raid, matched) {
        raid.types.mapNotNull { PokemonType.fromApiKey(it) }
            .ifEmpty { matched?.species?.parsedTypes().orEmpty() }
    }
    val cpText = remember(raid) { formatCpRange(raid) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = PokedexSurface,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) PokedexInk else PokedexBorder,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = PokedexBackground,
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    PokemonSprite(
                        spriteFile = matched?.species?.spriteFile,
                        contentDescription = raid.name,
                        size = 44.dp,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = raid.name,
                        color = PokedexInk,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (raid.canBeShiny) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "Shiny possible",
                            tint = PokedexGold,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = PokedexSubtle,
                    ) {
                        Text(
                            text = formatTierLabel(raid.tier),
                            color = PokedexInk,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                    if (cpText != null) {
                        Text(
                            text = cpText,
                            color = PokedexMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                types.forEach { type ->
                    TypeDot(type = type, size = 20.dp)
                }
            }
        }
    }
}

@Composable
private fun SpeciesRow(
    species: PokemonSpecies,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val types = remember(species) { species.parsedTypes() }
    val form = remember(species) { species.formLabel() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = PokedexSurface,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) PokedexInk else PokedexBorder,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = PokedexBackground,
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    PokemonSprite(
                        spriteFile = species.spriteFile,
                        contentDescription = species.speciesName,
                        size = 44.dp,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = species.speciesName,
                    color = PokedexInk,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = String.format(Locale.US, "#%03d", species.dex),
                        color = PokedexMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    if (form != null) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = PokedexSubtle,
                        ) {
                            Text(
                                text = form,
                                color = PokedexInk,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                types.forEach { type ->
                    TypeDot(type = type, size = 20.dp)
                }
            }
        }
    }
}

@Composable
private fun SetupStatusContent(
    hasOverlayPermission: Boolean,
    hasNotificationPermission: Boolean,
    needsNotificationPermission: Boolean,
    species: List<PokemonSpecies>,
    activeRaidsCount: Int,
    lastRaidsRefreshEpochMs: Long?,
    isRefreshing: Boolean,
    lastError: String?,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRefreshRaids: () -> Unit,
) {
    val linkedSpritesCount = remember(species) {
        species.count { !it.spriteFile.isNullOrBlank() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusCard(
            title = "Draw Over Other Apps",
            description = "Required so the floating edge pill, quick drawer, and weakness banner appear above Pokémon GO.",
            badgeLabel = if (hasOverlayPermission) "Granted" else "Required",
            isHealthy = hasOverlayPermission,
            actionLabel = if (hasOverlayPermission) null else "Grant",
            onAction = onRequestOverlayPermission,
        )

        StatusCard(
            title = "Notifications",
            description = if (needsNotificationPermission) {
                "Recommended on Android 13+ so the foreground screen-capture status notification remains visible."
            } else {
                "Not required separately on this Android version."
            },
            badgeLabel = when {
                !needsNotificationPermission -> "Ready"
                hasNotificationPermission -> "Granted"
                else -> "Recommended"
            },
            isHealthy = !needsNotificationPermission || hasNotificationPermission,
            actionLabel = if (needsNotificationPermission && !hasNotificationPermission) "Grant" else null,
            onAction = onRequestNotificationPermission,
        )

        StatusCard(
            title = "Offline Pokédex & Pixel Sprites",
            description = "${species.size} species and alternate forms bundled offline ($linkedSpritesCount pixel sprites linked).",
            badgeLabel = if (species.isNotEmpty()) "Offline Ready" else "Loading",
            isHealthy = species.isNotEmpty(),
        )

        StatusCard(
            title = "Live Raid Feed",
            description = buildString {
                append("$activeRaidsCount active raid bosses loaded")
                if (lastRaidsRefreshEpochMs != null) {
                    append(" • Updated at ${formatTime(lastRaidsRefreshEpochMs)}")
                } else {
                    append(" • Using bundled ScrapedDuck snapshot")
                }
                if (lastError != null) {
                    append("\nLast error: $lastError")
                }
            },
            badgeLabel = when {
                isRefreshing -> "Refreshing"
                lastError != null -> "Fallback"
                lastRaidsRefreshEpochMs != null -> "Synced"
                else -> "Bundled"
            },
            isHealthy = lastError == null,
            actionLabel = if (isRefreshing) "Refreshing..." else "Refresh",
            actionEnabled = !isRefreshing,
            onAction = onRefreshRaids,
        )
    }
}

@Composable
private fun StatusCard(
    title: String,
    description: String,
    badgeLabel: String,
    isHealthy: Boolean,
    actionLabel: String? = null,
    actionEnabled: Boolean = true,
    onAction: () -> Unit = {},
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = PokedexSurface,
        border = BorderStroke(1.dp, PokedexBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (isHealthy) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = if (isHealthy) PokedexSuccess else PokedexDanger,
                modifier = Modifier.size(22.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = title,
                        color = PokedexInk,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isHealthy) {
                            PokedexSuccess.copy(alpha = 0.12f)
                        } else {
                            PokedexDanger.copy(alpha = 0.12f)
                        },
                    ) {
                        Text(
                            text = badgeLabel,
                            color = if (isHealthy) PokedexSuccess else PokedexDanger,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    text = description,
                    color = PokedexMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }

            if (actionLabel != null) {
                OutlinedButton(
                    onClick = onAction,
                    enabled = actionEnabled,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, PokedexInk),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = actionLabel,
                        color = PokedexInk,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomCtaBar(
    isOverlayRunning: Boolean,
    onActivateOverlay: () -> Unit,
    onStartManualOnly: () -> Unit,
    onStopOverlay: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PokedexSurface,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            HorizontalDivider(color = PokedexBorder, thickness = 1.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Button(
                    onClick = if (isOverlayRunning) onStopOverlay else onActivateOverlay,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOverlayRunning) PokedexDanger else PokedexInk,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        text = if (isOverlayRunning) "Stop Overlay" else "Activate Overlay",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (!isOverlayRunning) {
                    TextButton(
                        onClick = onStartManualOnly,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "Start Manual-Only Mode",
                            color = PokedexMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

private fun formatTierLabel(rawTier: String): String {
    val trimmed = rawTier.trim()
    val lower = trimmed.lowercase(Locale.US)
    return when {
        lower == "1" || lower.startsWith("1-star") || lower == "tier 1" -> "1★"
        lower == "3" || lower.startsWith("3-star") || lower == "tier 3" -> "3★"
        lower == "5" || lower.startsWith("5-star") || lower == "tier 5" -> "5★"
        lower.startsWith("mega legendary") -> "Mega 5★"
        lower.startsWith("mega") -> "Mega"
        lower.startsWith("primal") -> "Primal"
        lower.startsWith("shadow") -> "Shadow"
        lower.startsWith("tier ") -> trimmed.removePrefix("Tier ").removePrefix("tier ").trim()
        lower.endsWith(" raids") -> trimmed.dropLast(" raids".length).trim()
        else -> trimmed
    }
}

private fun formatCpRange(raid: RaidBossEntry): String? {
    val min = raid.cpMin
    val max = raid.cpMax
    return when {
        min != null && max != null -> "CP $min–$max"
        raid.boostedCpMin != null && raid.boostedCpMax != null -> "Boosted ${raid.boostedCpMin}–${raid.boostedCpMax}"
        else -> null
    }
}

private fun formatRaidSubtitle(raid: RaidBossEntry): String {
    val parts = buildList {
        add(formatTierLabel(raid.tier))
        formatCpRange(raid)?.let { add(it) }
    }
    return parts.joinToString(" • ")
}

private fun formatTime(epochMs: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(epochMs))
}
