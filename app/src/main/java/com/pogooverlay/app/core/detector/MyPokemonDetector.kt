package com.pogooverlay.app.core.detector

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import com.pogooverlay.app.core.data.PokemonNameMatcher
import com.pogooverlay.app.core.data.PokemonSpecies
import com.pogooverlay.app.core.data.PokemonType
import com.pogooverlay.app.core.data.RaidBossEntry
import com.pogooverlay.app.core.ocr.OcrLine
import com.pogooverlay.app.core.pokebox.MoveData
import com.pogooverlay.app.core.pokebox.PokemonMathEngine
import kotlin.math.abs
import kotlin.math.roundToInt

data class DetectedOwnPokemon(
    val species: PokemonSpecies,
    val cp: Int?,
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
    val estimatedLevel: Double,
    val nickname: String? = null,
    val detectedTypes: List<PokemonType> = emptyList(),
    val alternateForms: List<PokemonSpecies> = emptyList()
) {
    fun mergeWith(incoming: DetectedOwnPokemon): DetectedOwnPokemon {
        val chosenSpecies = when {
            incoming.species.dex != species.dex -> species
            incoming.species.formLabel() != null && species.formLabel() == null -> incoming.species
            incoming.detectedTypes.isNotEmpty() && detectedTypes.isEmpty() -> incoming.species
            incoming.fastMove != null && fastMove == null && incoming.species.speciesId != species.speciesId -> incoming.species
            else -> species
        }
        val mergedCp = incoming.cp ?: cp
        val mergedHp = incoming.hp ?: hp
        val mergedDust = incoming.powerUpDust ?: powerUpDust
        val mergedAtk = incoming.ivAtk ?: ivAtk
        val mergedDef = incoming.ivDef ?: ivDef
        val mergedSta = incoming.ivSta ?: ivSta
        val mergedShadow = isShadow || incoming.isShadow
        val mergedPurified = isPurified || incoming.isPurified
        val mergedLucky = isLucky || incoming.isLucky
        val mergedFast = incoming.fastMove ?: fastMove
        val mergedC1 = incoming.chargedMove1 ?: chargedMove1
        val mergedC2 = incoming.chargedMove2 ?: chargedMove2
        val mergedForms = if (incoming.alternateForms.size > alternateForms.size) {
            incoming.alternateForms
        } else {
            alternateForms
        }

        val resolvedLevel = if (mergedCp != null && mergedCp > 0) {
            PokemonMathEngine.solveLevel(
                baseAtk = chosenSpecies.baseAtk,
                baseDef = chosenSpecies.baseDef,
                baseSta = chosenSpecies.baseSta,
                ivAtk = mergedAtk ?: 12,
                ivDef = mergedDef ?: 12,
                ivSta = mergedSta ?: 12,
                cp = mergedCp,
                hp = mergedHp,
                powerUpDust = mergedDust,
                isShadow = mergedShadow,
                isPurified = mergedPurified,
                isLucky = mergedLucky
            )
        } else {
            incoming.estimatedLevel
        }

        return copy(
            species = chosenSpecies,
            cp = mergedCp,
            hp = mergedHp,
            powerUpDust = mergedDust,
            ivAtk = mergedAtk,
            ivDef = mergedDef,
            ivSta = mergedSta,
            isShadow = mergedShadow,
            isPurified = mergedPurified,
            isLucky = mergedLucky,
            fastMove = mergedFast,
            chargedMove1 = mergedC1,
            chargedMove2 = mergedC2,
            estimatedLevel = resolvedLevel,
            nickname = nickname ?: incoming.nickname,
            detectedTypes = detectedTypes.ifEmpty { incoming.detectedTypes },
            alternateForms = mergedForms
        )
    }
}

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
        if (ScreenClassifier.classify(lines) != ScreenKind.MY_POKEMON) return null

        val fullUpper = lines.joinToString("\n") { it.text.uppercase() }
        val cp = extractCp(lines)
        val hp = extractHp(lines)
        val powerUpDust = extractPowerUpDust(lines)
        val isShadow = fullUpper.contains("PURIFY") ||
            fullUpper.contains("FRUSTRATION") ||
            fullUpper.contains("SHADOW")
        val isPurified = fullUpper.contains("PURIFIED") || fullUpper.contains("RETURN")
        val isLucky = fullUpper.contains("LUCKY")

        val detectedTypes = extractVisibleTypes(lines)
        val candyFamilyName = extractCandyFamilyName(lines)
        val matcher = matcherProvider()
        val movesById = movesProvider()

        val resolution = resolveSpeciesAndForm(
            lines = lines,
            matcher = matcher,
            activeRaids = activeRaids,
            candyFamilyName = candyFamilyName,
            detectedTypes = detectedTypes,
            movesById = movesById
        ) ?: return null

        val species = resolution.species
        val (fastMove, charged1, charged2) = extractMoves(lines, species, movesById)

        val appraisalIvs = if (bitmap != null) extractAppraisalIvs(lines, bitmap) else null
        val atk = appraisalIvs?.first
        val def = appraisalIvs?.second
        val sta = appraisalIvs?.third

        val estimatedLevel = if (cp != null && cp > 0) {
            PokemonMathEngine.solveLevel(
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
        } else {
            20.0
        }

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
            estimatedLevel = estimatedLevel,
            nickname = resolution.nickname,
            detectedTypes = detectedTypes,
            alternateForms = resolution.forms
        )
    }

    private data class SpeciesResolution(
        val species: PokemonSpecies,
        val forms: List<PokemonSpecies>,
        val nickname: String?
    )

    private fun resolveSpeciesAndForm(
        lines: List<OcrLine>,
        matcher: PokemonNameMatcher,
        activeRaids: List<RaidBossEntry>,
        candyFamilyName: String?,
        detectedTypes: List<PokemonType>,
        movesById: Map<String, MoveData>
    ): SpeciesResolution? {
        val nameZoneLines = lines
            .filter { it.normalizedTop in 0.14f..0.48f }
            .ifEmpty { lines }

        var matchedFromName: PokemonSpecies? = null
        var rawHeaderCandidate: String? = null

        for (line in nameZoneLines) {
            val clean = line.text.trim()
            if (clean.length < 2) continue
            val upper = clean.uppercase()
            if (CP_REGEX.containsMatchIn(upper) ||
                upper.contains("HP") ||
                upper.contains("WEIGHT") ||
                upper.contains("HEIGHT") ||
                upper.contains("STARDUST") ||
                upper.contains("CANDY") ||
                upper.contains("POWER UP") ||
                upper.contains("MAX MOVES")
            ) {
                continue
            }
            if (rawHeaderCandidate == null && clean.any { it.isLetter() }) {
                rawHeaderCandidate = clean
            }
            val matched = matcher.matchText(clean, activeRaids)
            if (matched != null && matched.confidence >= 0.92f) {
                matchedFromName = matched.species
                break
            }
        }

        val matchedFromCandy = candyFamilyName?.let { family ->
            matcher.matchText(family, activeRaids)?.takeIf { it.confidence >= 0.85f }?.species
        }

        val baseSpecies = matchedFromCandy ?: matchedFromName ?: return null
        val forms = matcher.getFormsForSpecies(baseSpecies).ifEmpty { listOf(baseSpecies) }

        val lowerMoveLines = lines
            .filter { it.normalizedTop >= 0.42f }
            .ifEmpty { lines }
            .map { normalizeMoveText(it.text) }
            .filter { it.length >= 3 }

        val chosenForm = pickBestForm(
            forms = forms,
            defaultChoice = matchedFromName ?: baseSpecies,
            detectedTypes = detectedTypes,
            lowerMoveLines = lowerMoveLines,
            movesById = movesById
        )

        val nickname = if (matchedFromName == null && rawHeaderCandidate != null) {
            rawHeaderCandidate
        } else {
            null
        }

        return SpeciesResolution(
            species = chosenForm,
            forms = forms,
            nickname = nickname
        )
    }

    private fun pickBestForm(
        forms: List<PokemonSpecies>,
        defaultChoice: PokemonSpecies,
        detectedTypes: List<PokemonType>,
        lowerMoveLines: List<String>,
        movesById: Map<String, MoveData>
    ): PokemonSpecies {
        if (forms.size <= 1) return defaultChoice

        val moveNamesInLines = lowerMoveLines.toSet()
        var bestForm = defaultChoice
        var bestScore = Int.MIN_VALUE

        for (form in forms) {
            var score = 0
            val formTypes = form.parsedTypes()
            if (detectedTypes.isNotEmpty() && formTypes.toSet() == detectedTypes.toSet()) {
                score += 10
            } else if (detectedTypes.isNotEmpty() && formTypes.containsAll(detectedTypes)) {
                score += 4
            }

            if (movesById.isNotEmpty() && moveNamesInLines.isNotEmpty()) {
                val formMoves = (form.quickMoves + form.cinematicMoves).mapNotNull { movesById[it] }
                for (move in formMoves) {
                    val norm = normalizeMoveText(move.displayName)
                    if (norm.length >= 4 && lowerMoveLines.any { it == norm || it.contains(norm) }) {
                        score += 5
                    }
                }
            }

            if (form.speciesId == defaultChoice.speciesId) {
                score += 1
            }

            if (score > bestScore) {
                bestScore = score
                bestForm = form
            }
        }
        return bestForm
    }

    private fun extractCandyFamilyName(lines: List<OcrLine>): String? {
        for (line in lines) {
            val upper = line.text.uppercase().replace("CANDY XL", "CANDY")
            CANDY_FAMILY_REGEX.find(upper)?.let { match ->
                val raw = match.groupValues[1].trim()
                if (raw.length >= 3 && !raw.all { it.isDigit() }) {
                    return raw
                }
            }
        }
        return null
    }

    private fun extractVisibleTypes(lines: List<OcrLine>): List<PokemonType> {
        val midLines = lines.filter { it.normalizedTop in 0.25f..0.60f }.ifEmpty { lines }
        for (line in midLines) {
            val tokens = line.text.uppercase()
                .split('/', ' ', ',', '|')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val types = tokens.mapNotNull { PokemonType.fromApiKey(it.lowercase()) }.distinct()
            if (types.isNotEmpty()) {
                return types.take(2)
            }
        }
        return emptyList()
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
            val upper = line.text.uppercase()
            // Skip personal Stardust balance or New Attack 100,000 cost
            if (upper.contains("STARDUST") || upper.contains("NEW ATTACK")) continue
            val nums = NUMBER_REGEX.findAll(line.text.replace(",", ""))
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .toList()
            for (n in nums) {
                if (n in VALID_STARDUST_VALUES) return n
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

    private data class BarScanSample(
        val firstBarX: Int,
        val lastBarX: Int,
        val lastFilledX: Int,
        val filledCount: Int,
        val pinkCount: Int,
        val grayCount: Int
    )

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

        val atkSample = scanBarBelowLine(bitmap, atkLine) ?: return null
        val defSample = scanBarBelowLine(bitmap, defLine) ?: return null
        val staSample = scanBarBelowLine(bitmap, hpLine) ?: return null

        val allSamples = listOf(atkSample, defSample, staSample)
        val sharedBarLeft = allSamples.minOf { it.firstBarX }
        val sharedBarRight = allSamples.maxOf { it.lastBarX }
        if (sharedBarRight - sharedBarLeft < 20) return null

        val atk = computeIvFromBarSpan(
            sharedBarLeft, sharedBarRight,
            atkSample.lastFilledX, atkSample.filledCount, atkSample.pinkCount, atkSample.grayCount
        )
        val def = computeIvFromBarSpan(
            sharedBarLeft, sharedBarRight,
            defSample.lastFilledX, defSample.filledCount, defSample.pinkCount, defSample.grayCount
        )
        val sta = computeIvFromBarSpan(
            sharedBarLeft, sharedBarRight,
            staSample.lastFilledX, staSample.filledCount, staSample.pinkCount, staSample.grayCount
        )
        return Triple(atk, def, sta)
    }

    private fun scanBarBelowLine(bitmap: Bitmap, labelLine: OcrLine): BarScanSample? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 40 || h <= 40) return null

        val scanStartX = (w * 0.08f).roundToInt().coerceIn(0, w - 1)
        // Appraisal card sits on left half of screen; strict clamp avoids leader's jacket
        val scanEndX = (w * 0.495f).roundToInt().coerceIn(scanStartX + 20, w - 1)

        val labelBottomY = ((labelLine.normalizedTop + labelLine.normalizedHeight) * h).roundToInt()
        val yMin = (labelBottomY + (h * 0.004f).roundToInt()).coerceIn(0, h - 1)
        val yMax = (labelBottomY + (h * 0.042f).roundToInt()).coerceIn(yMin, h - 1)

        var bestSample: BarScanSample? = null
        var bestTotalPixels = 0

        for (y in yMin..yMax step 2) {
            var firstBarX = Int.MAX_VALUE
            var lastBarX = -1
            var lastFilledX = -1
            var filledCount = 0
            var pinkCount = 0
            var grayCount = 0

            for (x in scanStartX..scanEndX) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                val isOrange = r > 200 && g in 115..205 && b < 100
                val isPink15 = r > 195 && g in 80..155 && b in 80..155 && (r - g) > 60
                val isGrayTrack = r in 202..238 && abs(r - g) <= 10 && abs(g - b) <= 10

                when {
                    isPink15 -> {
                        pinkCount++
                        filledCount++
                        if (x < firstBarX) firstBarX = x
                        lastBarX = x
                        lastFilledX = x
                    }
                    isOrange -> {
                        filledCount++
                        if (x < firstBarX) firstBarX = x
                        lastBarX = x
                        lastFilledX = x
                    }
                    isGrayTrack -> {
                        grayCount++
                        if (x < firstBarX) firstBarX = x
                        lastBarX = x
                    }
                }
            }

            val total = filledCount + grayCount
            if (total > bestTotalPixels && lastBarX > firstBarX) {
                bestTotalPixels = total
                bestSample = BarScanSample(
                    firstBarX = firstBarX,
                    lastBarX = lastBarX,
                    lastFilledX = lastFilledX,
                    filledCount = filledCount,
                    pinkCount = pinkCount,
                    grayCount = grayCount
                )
            }
        }

        return bestSample?.takeIf { (it.filledCount + it.grayCount) >= 15 }
    }

    companion object {
        private val CP_REGEX = Regex("""\bCP\s*[:.]?\s*(\d{2,4})\b""")
        private val HP_SLASH_REGEX = Regex("""(\d{2,3})\s*/\s*(\d{2,3})\s*HP""")
        private val HP_SINGLE_REGEX = Regex("""\bHP\s*[:.]?\s*(\d{2,3})\b""")
        private val NUMBER_REGEX = Regex("""\b(\d{3,5})\b""")
        private val CANDY_FAMILY_REGEX = Regex("""([A-Z][A-Z0-9 .'-]{1,22}?)\s+CANDY\b""")

        private val VALID_STARDUST_VALUES = setOf(
            200, 400, 600, 800, 1000, 1300, 1600, 1900, 2200, 2500,
            3000, 3500, 4000, 4500, 5000, 6000, 7000, 8000, 9000,
            10000, 11000, 12000, 13000, 15000,
            240, 480, 720, 960, 1200, 1560, 1920, 2280, 2640,
            3600, 4200, 4800, 5400, 7200, 8400, 9600, 10800, 13200, 14400, 18000,
            180, 360, 540, 1170, 1440, 1710, 1980, 2250, 2700, 3150, 6300,
            100, 300, 500, 650, 950, 1100, 1250, 1500, 1750, 2000, 5500, 6500, 7500
        )

        fun computeIvFromBarSpan(
            barLeft: Int,
            barRight: Int,
            lastFilledX: Int,
            filledCount: Int,
            pinkCount: Int,
            grayCount: Int
        ): Int {
            if (pinkCount >= 10 && grayCount <= 3) return 15
            if (filledCount <= 1 || lastFilledX < barLeft) return 0
            val span = (barRight - barLeft).coerceAtLeast(1).toFloat()
            val ratio = ((lastFilledX - barLeft) / span).coerceIn(0f, 1f)
            val maxCap = if (grayCount > 4) 14 else 15
            return (ratio * 15f).roundToInt().coerceIn(0, maxCap)
        }
    }
}
