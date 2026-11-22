package com.pogooverlay.app.core.detector

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import com.pogooverlay.app.core.data.PokemonNameMatcher
import com.pogooverlay.app.core.data.PokemonSpecies
import com.pogooverlay.app.core.data.RaidBossEntry
import com.pogooverlay.app.core.ocr.OcrLine
import com.pogooverlay.app.core.pokebox.MoveData
import com.pogooverlay.app.core.pokebox.PokemonMathEngine
import kotlin.math.roundToInt

data class DetectedOwnPokemon(
    val species: PokemonSpecies,
    val cp: Int,
    val hp: Int?,
    val powerUpDust: Int?,
    val ivAtk: Int?,
    val ivDef: Int?,
    val ivSta: Int?,
    val isShadow: Boolean,
    val isPurified: Boolean,
    val isLucky: Boolean,
    val fastMove: MoveData?,
    val chargedMove1: MoveData?,
    val chargedMove2: MoveData?,
    val estimatedLevel: Double
)

class MyPokemonDetector(
    private val matcherProvider: () -> PokemonNameMatcher,
    private val movesProvider: () -> Map<String, MoveData>,
    private val activeRaidsProvider: () -> List<RaidBossEntry> = { emptyList() }
) : ScreenDetector<DetectedOwnPokemon> {

    override val id: String = "my_pokemon"

    override val roiNormalized: RectF = RectF(0.02f, 0.04f, 0.98f, 0.95f)

    override suspend fun detect(
        lines: List<OcrLine>,
        isManualTrigger: Boolean
    ): DetectedOwnPokemon? {
        return detectFromLinesAndBitmap(lines, bitmap = null, activeRaids = activeRaidsProvider())
    }

    fun detectFromLinesAndBitmap(
        lines: List<OcrLine>,
        bitmap: Bitmap?,
        activeRaids: List<RaidBossEntry> = activeRaidsProvider()
    ): DetectedOwnPokemon? {
        if (lines.isEmpty()) return null

        val fullUpper = lines.joinToString("\n") { it.text.uppercase() }
        val cp = extractCp(lines) ?: return null

        // Confirm this is a Pokémon Details or Appraisal screen (not a Raid Lobby timer)
        val hasDetailsCue = fullUpper.contains("HP") ||
            fullUpper.contains("POWER UP") ||
            fullUpper.contains("EVOLVE") ||
            fullUpper.contains("PURIFY") ||
            fullUpper.contains("ATTACK") ||
            fullUpper.contains("DEFENSE") ||
            fullUpper.contains("APPRAISE")
        if (!hasDetailsCue) return null

        val hp = extractHp(lines)
        val powerUpDust = extractPowerUpDust(lines)
        val isShadow = fullUpper.contains("PURIFY") || fullUpper.contains("FRUSTRATION") || fullUpper.contains("SHADOW")
        val isPurified = fullUpper.contains("PURIFIED") || fullUpper.contains("RETURN")
        val isLucky = fullUpper.contains("LUCKY POK") || fullUpper.contains("LUCKY")

        val matcher = matcherProvider()
        val species = matchSpecies(lines, matcher, activeRaids) ?: return null

        val movesById = movesProvider()
        val (fastMove, charged1, charged2) = extractMoves(lines, species, movesById)

        val appraisalIvs = if (bitmap != null) extractAppraisalIvs(lines, bitmap) else null
        val atk = appraisalIvs?.first
        val def = appraisalIvs?.second
        val sta = appraisalIvs?.third

        val estimatedLevel = PokemonMathEngine.solveLevel(
            baseAtk = species.baseAtk,
            baseDef = species.baseDef,
            baseSta = species.baseSta,
            ivAtk = atk ?: 12,
            ivDef = def ?: 12,
            ivSta = sta ?: 12,
            cp = cp,
            hp = hp,
            powerUpDust = powerUpDust,
            isShadow = isShadow,
            isPurified = isPurified,
            isLucky = isLucky
        )

        return DetectedOwnPokemon(
            species = species,
            cp = cp,
            hp = hp,
            powerUpDust = powerUpDust,
            ivAtk = atk,
            ivDef = def,
            ivSta = sta,
            isShadow = isShadow,
            isPurified = isPurified,
            isLucky = isLucky,
            fastMove = fastMove,
            chargedMove1 = charged1,
            chargedMove2 = charged2,
            estimatedLevel = estimatedLevel
        )
    }

    private fun extractCp(lines: List<OcrLine>): Int? {
        val topLines = lines.filter { it.normalizedTop <= 0.35f }.ifEmpty { lines.take(4) }
        for (line in topLines) {
            val match = CP_REGEX.find(line.text.uppercase())
            if (match != null) {
                val v = match.groupValues[1].toIntOrNull()
                if (v != null && v in 10..7000) return v
            }
        }
        return null
    }

    private fun extractHp(lines: List<OcrLine>): Int? {
        for (line in lines) {
            val upper = line.text.uppercase()
            HP_SLASH_REGEX.find(upper)?.let { m ->
                val maxHp = m.groupValues[2].toIntOrNull()
                if (maxHp != null && maxHp in 10..999) return maxHp
            }
            HP_SINGLE_REGEX.find(upper)?.let { m ->
                val hp = m.groupValues[1].toIntOrNull()
                if (hp != null && hp in 10..999) return hp
            }
        }
        return null
    }

    private fun extractPowerUpDust(lines: List<OcrLine>): Int? {
        val lowerLines = lines.filter { it.normalizedTop >= 0.40f }.ifEmpty { lines }
        for (line in lowerLines) {
            val nums = NUMBER_REGEX.findAll(line.text.replace(",", ""))
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .toList()
            for (n in nums) {
                if (n in VALID_STARDUST_VALUES) return n
            }
        }
        return null
    }

    private fun matchSpecies(
        lines: List<OcrLine>,
        matcher: PokemonNameMatcher,
        activeRaids: List<RaidBossEntry>
    ): PokemonSpecies? {
        val midTopLines = lines
            .filter { it.normalizedTop in 0.18f..0.62f }
            .ifEmpty { lines }
        for (line in midTopLines) {
            val clean = line.text.trim()
            if (clean.length < 3) continue
            val upper = clean.uppercase()
            if (CP_REGEX.containsMatchIn(upper) || upper.contains("HP") || upper.contains("POWER UP")) {
                continue
            }
            val matched = matcher.matchText(clean, activeRaids)
            if (matched != null && matched.confidence >= 0.8f) {
                return matched.species
            }
        }
        // Fallback: try joining adjacent lines in middle area
        for (i in 0 until midTopLines.lastIndex) {
            val joined = "${midTopLines[i].text.trim()} ${midTopLines[i + 1].text.trim()}"
            val matched = matcher.matchText(joined, activeRaids)
            if (matched != null && matched.confidence >= 0.85f) {
                return matched.species
            }
        }
        return null
    }

    private fun extractMoves(
        lines: List<OcrLine>,
        species: PokemonSpecies,
        movesById: Map<String, MoveData>
    ): Triple<MoveData?, MoveData?, MoveData?> {
        if (movesById.isEmpty()) return Triple(null, null, null)

        val lowerTextLines = lines
            .filter { it.normalizedTop >= 0.45f }
            .ifEmpty { lines }
            .map { normalizeMoveText(it.text) }
            .filter { it.length >= 3 }

        val quickCandidates = species.quickMoves.mapNotNull { movesById[it] }
        val chargedCandidates = species.cinematicMoves.mapNotNull { movesById[it] }

        fun findMatchingMoves(pool: List<MoveData>): List<MoveData> {
            val hits = mutableListOf<Pair<Int, MoveData>>()
            for ((idx, lineNorm) in lowerTextLines.withIndex()) {
                for (move in pool) {
                    val moveNorm = normalizeMoveText(move.displayName)
                    if (moveNorm.length >= 3 && (lineNorm == moveNorm || lineNorm.contains(moveNorm))) {
                        hits.add(idx to move)
                    }
                }
            }
            return hits.sortedBy { it.first }.map { it.second }.distinctBy { it.moveId }
        }

        val fastMatches = findMatchingMoves(
            quickCandidates.ifEmpty { movesById.values.filter { it.isFast } }
        )
        val chargedMatches = findMatchingMoves(
            chargedCandidates.ifEmpty { movesById.values.filter { !it.isFast } }
        )

        return Triple(
            fastMatches.firstOrNull(),
            chargedMatches.getOrNull(0),
            chargedMatches.getOrNull(1)
        )
    }

    private fun normalizeMoveText(raw: String): String =
        raw.lowercase()
            .replace(Regex("""\b\d+\b"""), " ")
            .replace(Regex("""[^a-z\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun extractAppraisalIvs(
        lines: List<OcrLine>,
        bitmap: Bitmap
    ): Triple<Int, Int, Int>? {
        val atkLine = lines.firstOrNull { it.text.uppercase().contains("ATTACK") } ?: return null
        val defLine = lines.firstOrNull { it.text.uppercase().contains("DEFENSE") } ?: return null
        val hpLine = lines.firstOrNull {
            val u = it.text.uppercase()
            u.contains("HP") && it.normalizedTop > defLine.normalizedTop
        } ?: return null

        val atkIv = sampleIvBarBelowLine(bitmap, atkLine) ?: return null
        val defIv = sampleIvBarBelowLine(bitmap, defLine) ?: return null
        val staIv = sampleIvBarBelowLine(bitmap, hpLine) ?: return null
        return Triple(atkIv, defIv, staIv)
    }

    private fun sampleIvBarBelowLine(bitmap: Bitmap, labelLine: OcrLine): Int? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 20 || h <= 20) return null

        val startX = (w * 0.13f).roundToInt().coerceIn(0, w - 1)
        val endX = (w * 0.54f).roundToInt().coerceIn(startX + 10, w - 1)
        val barWidth = (endX - startX).toFloat()

        // Appraisal IV bar sits just below the label text row (~2%–4.5% height below label top)
        val yOffsets = listOf(0.025f, 0.032f, 0.040f)
        var maxRightmostFilledRatio = -1f

        for (dy in yOffsets) {
            val y = ((labelLine.normalizedTop + labelLine.normalizedHeight + dy) * h)
                .roundToInt()
                .coerceIn(0, h - 1)

            var lastFilledX = -1
            var filledCount = 0
            for (x in startX..endX step 2) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val isOrangeBar = r > 200 && g in 95..200 && b < 90
                val isPink15Bar = r > 195 && g < 115 && b in 70..145
                if (isOrangeBar || isPink15Bar) {
                    filledCount++
                    lastFilledX = x
                }
            }
            if (filledCount >= 3 && lastFilledX >= startX) {
                val ratio = ((lastFilledX - startX) / barWidth).coerceIn(0f, 1f)
                if (ratio > maxRightmostFilledRatio) {
                    maxRightmostFilledRatio = ratio
                }
            }
        }

        return if (maxRightmostFilledRatio < 0f) {
            null
        } else {
            (maxRightmostFilledRatio * 15f).roundToInt().coerceIn(0, 15)
        }
    }

    companion object {
        private val CP_REGEX = Regex("""\bCP\s*[:.]?\s*(\d{2,4})\b""")
        private val HP_SLASH_REGEX = Regex("""(\d{2,3})\s*/\s*(\d{2,3})\s*HP""")
        private val HP_SINGLE_REGEX = Regex("""\bHP\s*[:.]?\s*(\d{2,3})\b""")
        private val NUMBER_REGEX = Regex("""\b(\d{3,5})\b""")

        private val VALID_STARDUST_VALUES = setOf(
            200, 400, 600, 800, 1000, 1300, 1600, 1900, 2200, 2500,
            3000, 3500, 4000, 4500, 5000, 6000, 7000, 8000, 9000,
            10000, 11000, 12000, 13000, 15000,
            // Shadow / Purified / Lucky variants
            240, 480, 720, 960, 1200, 1560, 1920, 2280, 2640, 3000,
            3600, 4200, 4800, 5400, 7200, 8400, 9600, 10800, 13200, 14400, 18000,
            180, 360, 540, 1170, 1440, 1710, 1980, 2250, 2700, 3150, 6300,
            100, 300, 500, 650, 950, 1100, 1250, 1500, 1750, 2000, 5500, 6500, 7500
        )
    }
}
