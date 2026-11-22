package com.pogooverlay.app.ui.pokebox

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pogooverlay.app.core.data.PokemonNameMatcher
import com.pogooverlay.app.core.data.PokemonSpecies
import com.pogooverlay.app.core.data.PokemonType
import com.pogooverlay.app.core.pokebox.MoveData
import com.pogooverlay.app.core.pokebox.PokebattlerClient
import com.pogooverlay.app.core.pokebox.PokebattlerTopCounter
import com.pogooverlay.app.core.pokebox.PokemonMathEngine
import com.pogooverlay.app.core.pokebox.RaidBattleSimulator
import com.pogooverlay.app.core.pokebox.SavedPokemon
import com.pogooverlay.app.ui.components.PokemonSprite
import com.pogooverlay.app.ui.components.TypeDot
import com.pogooverlay.app.ui.theme.PokedexBorder
import com.pogooverlay.app.ui.theme.PokedexDanger
import com.pogooverlay.app.ui.theme.PokedexGold
import com.pogooverlay.app.ui.theme.PokedexInk
import com.pogooverlay.app.ui.theme.PokedexMuted
import com.pogooverlay.app.ui.theme.PokedexSubtle
import com.pogooverlay.app.ui.theme.PokedexSuccess
import com.pogooverlay.app.ui.theme.PokedexSurface
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun PokeboxTabContent(
    savedPokemon: List<SavedPokemon>,
    speciesById: Map<String, PokemonSpecies>,
    movesById: Map<String, MoveData>,
    matcher: PokemonNameMatcher,
    onSavePokemon: (SavedPokemon) -> Unit,
    onDeletePokemon: (String) -> Unit,
) {
    var selectedForDetail by remember { mutableStateOf<SavedPokemon?>(null) }
    var editingPokemon by remember { mutableStateOf<SavedPokemon?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Saved Pokébox (${savedPokemon.size})",
                    color = PokedexInk,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Used to simulate your best counters & raid winnability",
                    color = PokedexMuted,
                    fontSize = 11.sp,
                )
            }
            Button(
                onClick = {
                    editingPokemon = null
                    showAddDialog = true
                },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PokedexInk,
                    contentColor = Color.White,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (savedPokemon.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = PokedexSubtle,
                border = BorderStroke(1.dp, PokedexBorder),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Your Pokébox is empty",
                        color = PokedexInk,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Tap + Add above, or tap 📷 / Scan Pokémon in the overlay while viewing one of your Pokémon in-game to auto-save CP, level, moveset, and IVs.",
                        color = PokedexMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(savedPokemon, key = { it.id }) { item ->
                    val sp = speciesById[item.speciesId]
                    val moveEval = remember(item, sp, movesById) {
                        sp?.let {
                            RaidBattleSimulator.evaluateMoveset(
                                it, movesById, item.fastMoveId, item.chargedMove1Id, item.chargedMove2Id
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { selectedForDetail = item },
                        shape = RoundedCornerShape(16.dp),
                        color = PokedexSurface,
                        border = BorderStroke(1.dp, PokedexBorder),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PokemonSprite(
                                spriteFile = sp?.spriteFile,
                                contentDescription = sp?.speciesName ?: item.speciesId,
                                size = 44.dp,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = sp?.speciesName ?: item.speciesId,
                                        color = PokedexInk,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (item.isShadow) {
                                        MiniTag("Shadow", Color(0xFF5E35B1))
                                    } else if (item.isLucky) {
                                        MiniTag("Lucky", Color(0xFFD99B00))
                                    }
                                }
                                Text(
                                    text = "CP ${item.cp} • Lv ${formatLevel(item.level)} • IV ${item.ivAtk}/${item.ivDef}/${item.ivSta} (${item.ivPercent()}%)",
                                    color = PokedexMuted,
                                    fontSize = 11.sp,
                                )
                                val fastName = item.fastMoveId?.let { movesById[it]?.displayName }
                                val chargedName = item.chargedMove1Id?.let { movesById[it]?.displayName }
                                if (fastName != null || chargedName != null) {
                                    Text(
                                        text = listOfNotNull(fastName, chargedName).joinToString(" + "),
                                        color = PokedexInk.copy(alpha = 0.78f),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                moveEval?.let { eval ->
                                    val badgeColor = if (eval.isCurrentOptimal || eval.currentNeutralDps == null) {
                                        PokedexSuccess
                                    } else {
                                        Color(0xFFD97706)
                                    }
                                    Text(
                                        text = eval.recommendationText,
                                        color = badgeColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            sp?.parsedTypes()?.forEach { t -> TypeDot(type = t, size = 18.dp) }
                        }
                    }
                }
            }
        }
    }

    selectedForDetail?.let { detail ->
        val sp = speciesById[detail.speciesId]
        if (sp != null) {
            PokemonDetailDialog(
                pokemon = detail,
                species = sp,
                movesById = movesById,
                onDismiss = { selectedForDetail = null },
                onEdit = {
                    selectedForDetail = null
                    editingPokemon = detail
                    showAddDialog = true
                },
                onDelete = {
                    onDeletePokemon(detail.id)
                    selectedForDetail = null
                },
            )
        }
    }

    if (showAddDialog) {
        AddEditPokemonDialog(
            initial = editingPokemon,
            matcher = matcher,
            speciesById = speciesById,
            movesById = movesById,
            onDismiss = { showAddDialog = false },
            onSave = { saved ->
                onSavePokemon(saved)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun PokemonDetailDialog(
    pokemon: SavedPokemon,
    species: PokemonSpecies,
    movesById: Map<String, MoveData>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var targetLevel by remember(pokemon) {
        mutableDoubleStateOf(maxOf(pokemon.level, 40.0).coerceIn(pokemon.level, 50.0))
    }

    val cost = remember(pokemon, targetLevel) {
        PokemonMathEngine.calculateUpgradeCost(
            species = species,
            ivAtk = pokemon.ivAtk,
            ivDef = pokemon.ivDef,
            ivSta = pokemon.ivSta,
            currentLevel = pokemon.level,
            targetLevel = targetLevel,
            isShadow = pokemon.isShadow,
            isPurified = pokemon.isPurified,
            isLucky = pokemon.isLucky,
        )
    }
    val cp40 = remember(pokemon, species) {
        PokemonMathEngine.calculateCp(
            species.baseAtk, species.baseDef, species.baseSta,
            pokemon.ivAtk, pokemon.ivDef, pokemon.ivSta, 40.0
        )
    }
    val cp50 = remember(pokemon, species) {
        PokemonMathEngine.calculateCp(
            species.baseAtk, species.baseDef, species.baseSta,
            pokemon.ivAtk, pokemon.ivDef, pokemon.ivSta, 50.0
        )
    }
    val moveEval = remember(pokemon, species, movesById) {
        RaidBattleSimulator.evaluateMoveset(
            species, movesById, pokemon.fastMoveId, pokemon.chargedMove1Id, pokemon.chargedMove2Id
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = PokedexSurface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PokemonSprite(species.spriteFile, species.speciesName, size = 48.dp)
                        Column {
                            Text(
                                text = species.speciesName,
                                color = PokedexInk,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Text(
                                text = "CP ${pokemon.cp} • Lv ${formatLevel(pokemon.level)} • ${pokemon.ivAtk}/${pokemon.ivDef}/${pokemon.ivSta} (${pokemon.ivPercent()}%)",
                                color = PokedexMuted,
                                fontSize = 12.sp,
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = PokedexInk)
                    }
                }

                // Max CP stats
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = PokedexSubtle,
                    border = BorderStroke(1.dp, PokedexBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Lv 40 Max CP", color = PokedexMuted, fontSize = 11.sp)
                            Text("$cp40", color = PokedexInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Lv 50 Max CP", color = PokedexMuted, fontSize = 11.sp)
                            Text("$cp50", color = PokedexInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Moveset Grade
                moveEval?.let { eval ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (eval.isCurrentOptimal) Color(0xFFECFDF5) else Color(0xFFFFFBEB),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = eval.recommendationText,
                                color = if (eval.isCurrentOptimal) Color(0xFF065F46) else Color(0xFF92400E),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Top PvE: ${eval.bestFast.displayName} + ${eval.bestCharged.displayName}",
                                color = PokedexMuted,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }

                // Investment Slider
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Power-Up Planner → Lv ${formatLevel(cost.targetLevel)} (CP ${cost.targetCp})",
                        color = PokedexInk,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Slider(
                        value = targetLevel.toFloat(),
                        onValueChange = { raw ->
                            targetLevel = ((raw * 2f).roundToInt() / 2.0).coerceIn(pokemon.level, 50.0)
                        },
                        valueRange = pokemon.level.toFloat().coerceAtMost(50f)..50f,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(30.0, 35.0, 40.0, 50.0).filter { it >= pokemon.level }.forEach { lv ->
                            FilterChip(
                                selected = targetLevel == lv,
                                onClick = { targetLevel = lv },
                                label = { Text("Lv ${lv.toInt()}", fontSize = 11.sp) },
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = PokedexSubtle,
                        border = BorderStroke(1.dp, PokedexBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            CostItem("Stardust", formatNumber(cost.stardust))
                            CostItem("Candy", "${cost.candy}")
                            CostItem("XL Candy", "${cost.xlCandy}")
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onDelete,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PokedexDanger),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", fontSize = 12.sp)
                    }
                    Button(
                        onClick = onEdit,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = PokedexInk, contentColor = Color.White),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddEditPokemonDialog(
    initial: SavedPokemon?,
    matcher: PokemonNameMatcher,
    speciesById: Map<String, PokemonSpecies>,
    movesById: Map<String, MoveData>,
    onDismiss: () -> Unit,
    onSave: (SavedPokemon) -> Unit,
) {
    var speciesQuery by remember {
        mutableStateOf(initial?.let { speciesById[it.speciesId]?.speciesName }.orEmpty())
    }
    var selectedSpecies by remember {
        mutableStateOf(initial?.let { speciesById[it.speciesId] })
    }
    var cpText by remember { mutableStateOf(initial?.cp?.toString() ?: "") }
    var hpText by remember { mutableStateOf(initial?.hp?.toString() ?: "") }
    var ivAtk by remember { mutableIntStateOf(initial?.ivAtk ?: 15) }
    var ivDef by remember { mutableIntStateOf(initial?.ivDef ?: 15) }
    var ivSta by remember { mutableIntStateOf(initial?.ivSta ?: 15) }
    var isShadow by remember { mutableStateOf(initial?.isShadow ?: false) }
    var isPurified by remember { mutableStateOf(initial?.isPurified ?: false) }
    var isLucky by remember { mutableStateOf(initial?.isLucky ?: false) }
    var fastMoveId by remember { mutableStateOf(initial?.fastMoveId) }
    var chargedMove1Id by remember { mutableStateOf(initial?.chargedMove1Id) }

    val searchResults = remember(speciesQuery, matcher) {
        if (speciesQuery.isBlank() || selectedSpecies?.speciesName == speciesQuery) emptyList()
        else matcher.search(speciesQuery, limit = 8)
    }

    val solvedLevel = remember(selectedSpecies, cpText, hpText, ivAtk, ivDef, ivSta, isShadow, isPurified, isLucky) {
        val sp = selectedSpecies
        val cp = cpText.toIntOrNull() ?: 0
        if (sp != null && cp > 0) {
            PokemonMathEngine.solveLevel(
                sp.baseAtk, sp.baseDef, sp.baseSta,
                ivAtk, ivDef, ivSta,
                cp, hpText.toIntOrNull(),
                isShadow = isShadow, isPurified = isPurified, isLucky = isLucky
            )
        } else 20.0
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = PokedexSurface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (initial == null) "Add Pokémon" else "Edit Pokémon",
                        color = PokedexInk,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = PokedexInk)
                    }
                }

                OutlinedTextField(
                    value = speciesQuery,
                    onValueChange = {
                        speciesQuery = it
                        if (selectedSpecies?.speciesName != it) selectedSpecies = null
                    },
                    label = { Text("Pokémon Species") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (searchResults.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        searchResults.forEach { sp ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = PokedexSubtle,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedSpecies = sp
                                        speciesQuery = sp.speciesName
                                        fastMoveId = sp.quickMoves.firstOrNull()
                                        chargedMove1Id = sp.cinematicMoves.firstOrNull()
                                    },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    PokemonSprite(sp.spriteFile, sp.speciesName, size = 24.dp)
                                    Text(sp.speciesName, color = PokedexInk, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = cpText,
                        onValueChange = { cpText = it.filter(Char::isDigit).take(4) },
                        label = { Text("CP") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = hpText,
                        onValueChange = { hpText = it.filter(Char::isDigit).take(3) },
                        label = { Text("HP (opt)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    )
                }

                Text(
                    text = "IVs: $ivAtk / $ivDef / $ivSta  •  Solved Lv ${formatLevel(solvedLevel)}",
                    color = PokedexInk,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                IvSliderRow("Atk", ivAtk) { ivAtk = it }
                IvSliderRow("Def", ivDef) { ivDef = it }
                IvSliderRow("Sta", ivSta) { ivSta = it }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = isShadow,
                        onClick = { isShadow = !isShadow; if (isShadow) isPurified = false },
                        label = { Text("Shadow", fontSize = 11.sp) },
                    )
                    FilterChip(
                        selected = isPurified,
                        onClick = { isPurified = !isPurified; if (isPurified) isShadow = false },
                        label = { Text("Purified", fontSize = 11.sp) },
                    )
                    FilterChip(
                        selected = isLucky,
                        onClick = { isLucky = !isLucky },
                        label = { Text("Lucky", fontSize = 11.sp) },
                    )
                }

                selectedSpecies?.let { sp ->
                    if (sp.quickMoves.isNotEmpty()) {
                        MoveSelectorDropdown(
                            label = "Fast Move",
                            selectedMoveId = fastMoveId,
                            moveIds = sp.quickMoves,
                            movesById = movesById,
                            onSelect = { fastMoveId = it },
                        )
                    }
                    if (sp.cinematicMoves.isNotEmpty()) {
                        MoveSelectorDropdown(
                            label = "Charged Move",
                            selectedMoveId = chargedMove1Id,
                            moveIds = sp.cinematicMoves,
                            movesById = movesById,
                            onSelect = { chargedMove1Id = it },
                        )
                    }
                }

                val cpVal = cpText.toIntOrNull() ?: 0
                Button(
                    onClick = {
                        val sp = selectedSpecies ?: return@Button
                        if (cpVal <= 0) return@Button
                        onSave(
                            SavedPokemon(
                                id = initial?.id ?: UUID.randomUUID().toString(),
                                speciesId = sp.speciesId,
                                pokebattlerId = sp.pokebattlerId ?: sp.speciesId.uppercase(),
                                cp = cpVal,
                                hp = hpText.toIntOrNull(),
                                ivAtk = ivAtk,
                                ivDef = ivDef,
                                ivSta = ivSta,
                                level = solvedLevel,
                                isShadow = isShadow,
                                isPurified = isPurified,
                                isLucky = isLucky,
                                isMegaCapable = sp.tags.contains("mega"),
                                fastMoveId = fastMoveId,
                                chargedMove1Id = chargedMove1Id,
                                savedAtMs = System.currentTimeMillis(),
                            )
                        )
                    },
                    enabled = selectedSpecies != null && cpVal > 0,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = PokedexInk, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save to Pokébox", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun BossBestTeamCard(
    bossSpecies: PokemonSpecies,
    savedPokemon: List<SavedPokemon>,
    speciesById: Map<String, PokemonSpecies>,
    movesById: Map<String, MoveData>,
    initialTier: Int = 5,
) {
    var selectedTier by remember(bossSpecies.speciesId) { mutableIntStateOf(initialTier) }
    var weatherBoost by remember { mutableStateOf(false) }
    var pokebattlerCounters by remember(bossSpecies.speciesId, selectedTier) {
        mutableStateOf<List<PokebattlerTopCounter>?>(null)
    }
    var loadingPb by remember { mutableStateOf(false) }
    var pbError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val client = remember { PokebattlerClient() }

    val verdict = remember(bossSpecies, savedPokemon, speciesById, movesById, selectedTier, weatherBoost) {
        val weatherTypes = if (weatherBoost) {
            RaidBattleSimulator.simulatePersonalTeam(savedPokemon, speciesById, movesById, bossSpecies, selectedTier)
                .team.firstOrNull()?.chargedMove?.type?.let { PokemonType.fromApiKey(it) }
                ?.let { setOf(it) } ?: emptySet()
        } else emptySet()

        RaidBattleSimulator.simulatePersonalTeam(
            savedBox = savedPokemon,
            speciesById = speciesById,
            movesById = movesById,
            bossSpecies = bossSpecies,
            raidTier = selectedTier,
            weatherBoostTypes = weatherTypes,
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = PokedexSurface,
        border = BorderStroke(1.dp, PokedexBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "My Best Team & Winnability",
                    color = PokedexInk,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                val badgeBg = when {
                    savedPokemon.isEmpty() -> PokedexSubtle
                    verdict.teamEstimator <= 1.5 -> Color(0xFFDCFCE7)
                    verdict.teamEstimator <= 3.0 -> Color(0xFFFEF3C7)
                    else -> Color(0xFFFEE2E2)
                }
                val badgeInk = when {
                    savedPokemon.isEmpty() -> PokedexMuted
                    verdict.teamEstimator <= 1.5 -> Color(0xFF166534)
                    verdict.teamEstimator <= 3.0 -> Color(0xFF92400E)
                    else -> Color(0xFF991B1B)
                }
                Surface(shape = RoundedCornerShape(50), color = badgeBg) {
                    Text(
                        text = verdict.verdictLabel,
                        color = badgeInk,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(1 to "T1", 3 to "T3", 4 to "T4/Mega", 5 to "T5", 6 to "T6").forEach { (t, lbl) ->
                    FilterChip(
                        selected = selectedTier == t,
                        onClick = { selectedTier = t },
                        label = { Text(lbl, fontSize = 10.sp) },
                    )
                }
                FilterChip(
                    selected = weatherBoost,
                    onClick = { weatherBoost = !weatherBoost },
                    label = { Text("Weather +20%", fontSize = 10.sp) },
                )
            }

            Text(
                text = verdict.verdictDetail,
                color = PokedexMuted,
                fontSize = 11.sp,
            )

            verdict.team.forEachIndexed { idx, c ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "${idx + 1}.",
                        color = PokedexMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    PokemonSprite(c.species.spriteFile, c.species.speciesName, size = 28.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${c.species.speciesName}${if (c.pokemon.isShadow) " (Shadow)" else ""} • CP ${c.pokemon.cp}",
                            color = PokedexInk,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val moveStr = listOfNotNull(c.fastMove?.displayName, c.chargedMove?.displayName)
                            .joinToString(" + ")
                            .ifEmpty { "Default Moves" }
                        Text(
                            text = "$moveStr${if (c.usingSuboptimalMoves) " • ${c.recommendedMoveHint}" else ""}",
                            color = if (c.usingSuboptimalMoves) Color(0xFFD97706) else PokedexMuted,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = String.format(Locale.US, "%.1f DPS", c.dps),
                            color = PokedexInk,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = String.format(Locale.US, "Est %.2f", c.estimator),
                            color = PokedexMuted,
                            fontSize = 10.sp,
                        )
                    }
                }
            }

            HorizontalDivider(color = PokedexBorder)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = {
                        val pbId = bossSpecies.pokebattlerId ?: return@TextButton
                        loadingPb = true
                        pbError = null
                        scope.launch {
                            val res = client.fetchTopCounters(pbId, selectedTier)
                            loadingPb = false
                            res.onSuccess { pokebattlerCounters = it }
                                .onFailure { pbError = it.message ?: "Unable to fetch Pokébattler" }
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = if (loadingPb) "Loading Pokébattler..." else "Fetch Live Pokébattler Top 6",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (loadingPb) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            }

            pbError?.let {
                Text(it, color = PokedexDanger, fontSize = 10.sp)
            }
            pokebattlerCounters?.let { list ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    list.forEach { pb ->
                        val m1 = movesById[pb.move1]?.displayName ?: pb.move1
                        val m2 = movesById[pb.move2]?.displayName ?: pb.move2
                        Text(
                            text = "${pb.pokemonId} ($m1 + $m2) — Est ${String.format(Locale.US, "%.2f", pb.estimator)}",
                            color = PokedexMuted,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoveSelectorDropdown(
    label: String,
    selectedMoveId: String?,
    moveIds: List<String>,
    movesById: Map<String, MoveData>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = selectedMoveId?.let { movesById[it]?.displayName } ?: "Select $label"
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = PokedexSubtle,
            border = BorderStroke(1.dp, PokedexBorder),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("$label: $selectedName", color = PokedexInk, fontSize = 12.sp)
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = PokedexMuted)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            moveIds.forEach { id ->
                val display = movesById[id]?.displayName ?: id
                DropdownMenuItem(
                    text = { Text(display, fontSize = 13.sp) },
                    onClick = {
                        expanded = false
                        onSelect(id)
                    },
                )
            }
        }
    }
}

@Composable
private fun IvSliderRow(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("$label: $value", color = PokedexInk, fontSize = 12.sp, modifier = Modifier.width(56.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(0, 15)) },
            valueRange = 0f..15f,
            steps = 14,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CostItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = PokedexMuted, fontSize = 10.sp)
        Text(value, color = PokedexInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MiniTag(text: String, bg: Color) {
    Surface(shape = RoundedCornerShape(50), color = bg.copy(alpha = 0.14f)) {
        Text(
            text = text,
            color = bg,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

fun formatLevel(lv: Double): String =
    if ((lv * 2.0).roundToInt() % 2 == 0) "${lv.toInt()}" else String.format(Locale.US, "%.1f", lv)

private fun formatNumber(v: Int): String = String.format(Locale.US, "%,d", v)
