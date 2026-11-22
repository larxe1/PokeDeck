package com.pogooverlay.app.core.scan

import android.graphics.Bitmap
import com.pogooverlay.app.core.data.PokemonNameMatcher
import com.pogooverlay.app.core.data.PokemonSpecies
import com.pogooverlay.app.core.data.PokemonType
import com.pogooverlay.app.core.ocr.OcrLine
import com.pogooverlay.app.core.pokebox.MoveData

class ScanScreenParser(
    private val matcherProvider: () -> PokemonNameMatcher,
    private val movesProvider: () -> Map<String, MoveData>,
    private val appraisalIvParser: AppraisalIvParser = AppraisalIvParser(),
    private val movesetParser: MovesetParser = MovesetParser()
) {
    private data class SpeciesResolution(
        val species: PokemonSpecies,
        val nickname: String?
    )

    fun parse(
        lines: List<OcrLine>,
        bitmap: Bitmap?,
        activeSpeciesHint: PokemonSpecies? = null
    ): ScanFrameExtraction {
        if (lines.isEmpty()) {
            return ScanFrameExtraction()
        }

        val appraisalResult = appraisalIvParser.parse(lines, bitmap)
        val appraisalBounds = appraisalResult?.appraisalCardBoundsNormalized

        val cp = extractCp(lines)
        val hp = extractHp(lines)
        val flags = extractFlags(lines)
        val detectedTypes = extractVisibleTypes(lines)

        val matcher = matcherProvider()
        val movesById = movesProvider()
        val detectedResolution = resolveSpeciesAndForm(
            lines = lines,
            matcher = matcher,
            detectedTypes = detectedTypes,
            movesById = movesById,
            appraisalBounds = appraisalBounds,
            activeSpeciesHint = activeSpeciesHint
        )

        val moveHintSpecies = detectedResolution?.species ?: activeSpeciesHint
        val movesetResult = movesetParser.parse(
            lines = lines,
            species = moveHintSpecies,
            movesById = movesById,
            appraisalBounds = appraisalBounds
        )

        return ScanFrameExtraction(
            species = detectedResolution?.species,
            speciesId = detectedResolution?.species?.speciesId,
            cp = cp,
            hp = hp,
            nickname = detectedResolution?.nickname,
            isShadow = flags.isShadow,
            isPurified = flags.isPurified,
            isLucky = flags.isLucky,
            ivData = appraisalResult?.ivSpread,
            appraisalResult = appraisalResult,
            movesetResult = movesetResult
        )
    }

    private data class PokemonFlags(
        val isShadow: Boolean,
        val isPurified: Boolean,
        val isLucky: Boolean
    )

    private fun extractFlags(lines: List<OcrLine>): PokemonFlags {
        val upperLines = lines.map { it.text.trim().uppercase() }
        val fullUpper = upperLines.joinToString("\n")

        val isPurified = fullUpper.contains("PURIFIED") ||
            RETURN_MOVE_REGEX.containsMatchIn(fullUpper)
        val isLucky = LUCKY_REGEX.containsMatchIn(fullUpper)
        val isShadow = fullUpper.contains("PURIFY") ||
            FRUSTRATION_REGEX.containsMatchIn(fullUpper) ||
            upperLines.any { line ->
                line.contains("SHADOW") && SHADOW_NON_POKEMON_TERMS.none { line.contains(it) }
            }

        return PokemonFlags(
            isShadow = isShadow,
            isPurified = isPurified,
            isLucky = isLucky
        )
    }

    private fun extractCp(lines: List<OcrLine>): Int? {
        val topLines = lines.filter { it.normalizedTop <= 0.40f }.ifEmpty { lines.take(4) }
        for (line in topLines) {
            val match = CP_REGEX.find(line.text.uppercase())
            if (match != null) {
                val value = match.groupValues[1].toIntOrNull()
                if (value != null && value in 10..9999) {
                    return value
                }
            }
        }
        return null
    }

    private fun extractHp(lines: List<OcrLine>): Int? {
        for (line in lines) {
            val upper = line.text.uppercase()
            HP_SLASH_REGEX.find(upper)?.let { match ->
                val maxHp = match.groupValues[2].toIntOrNull()
                if (maxHp != null && maxHp in 10..999) return maxHp
            }
            HP_SINGLE_REGEX.find(upper)?.let { match ->
                val hp = match.groupValues[1].toIntOrNull()
                if (hp != null && hp in 10..999) return hp
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

    private fun resolveSpeciesAndForm(
        lines: List<OcrLine>,
        matcher: PokemonNameMatcher,
        detectedTypes: List<PokemonType>,
        movesById: Map<String, MoveData>,
        appraisalBounds: NormalizedRect?,
        activeSpeciesHint: PokemonSpecies?
    ): SpeciesResolution? {
        val nameZoneLines = lines
            .filter { it.normalizedTop in 0.14f..0.48f }
            .filter { appraisalBounds == null || !appraisalBounds.containsCenter(it) }

        var matchedFromName: PokemonSpecies? = null
        var rawHeaderCandidate: String? = null

        for (line in nameZoneLines) {
            val clean = line.text.trim()
            if (clean.length < 2) continue
            val upper = clean.uppercase()
            if (CP_REGEX.containsMatchIn(upper) ||
                HP_SLASH_REGEX.containsMatchIn(upper) ||
                HEADER_IGNORE_KEYWORDS.any { upper.contains(it) } ||
                isPureTypeLine(upper)
            ) {
                continue
            }
            if (rawHeaderCandidate == null && clean.any { it.isLetter() }) {
                rawHeaderCandidate = clean
            }
            val matched = matcher.matchText(clean)
            if (matched != null && matched.confidence >= 0.92f) {
                matchedFromName = matched.species
                break
            }
        }

        val candyFamilyName = extractCandyFamilyName(lines)
        val matchedFromCandy = candyFamilyName?.let { family ->
            matcher.matchText(family)?.takeIf { it.confidence >= 0.85f }?.species
        }

        val baseSpecies = matchedFromCandy ?: matchedFromName ?: return null
        val forms = matcher.getFormsForSpecies(baseSpecies).ifEmpty { listOf(baseSpecies) }

        val lowerMoveLines = lines
            .filter { it.normalizedTop >= 0.42f }
            .filter { appraisalBounds == null || !appraisalBounds.containsCenter(it) }
            .map { movesetParser.cleanMoveLine(it.text) }
            .filter { it.length >= 3 }

        val hintedForm = activeSpeciesHint?.takeIf { it.dex == baseSpecies.dex }
        val defaultChoice = matchedFromName ?: hintedForm ?: baseSpecies

        val chosenForm = pickBestForm(
            forms = forms,
            defaultChoice = defaultChoice,
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
            nickname = nickname
        )
    }

    private fun isPureTypeLine(upper: String): Boolean {
        val tokens = upper.split('/', ' ', ',', '|')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return tokens.isNotEmpty() && tokens.all { PokemonType.fromApiKey(it.lowercase()) != null }
    }

    private fun pickBestForm(
        forms: List<PokemonSpecies>,
        defaultChoice: PokemonSpecies,
        detectedTypes: List<PokemonType>,
        lowerMoveLines: List<String>,
        movesById: Map<String, MoveData>
    ): PokemonSpecies {
        if (forms.size <= 1) return defaultChoice

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

            if (movesById.isNotEmpty() && lowerMoveLines.isNotEmpty()) {
                val formMoves = (form.quickMoves + form.cinematicMoves).mapNotNull { movesById[it] }
                for (move in formMoves) {
                    val norm = movesetParser.cleanMoveLine(move.displayName)
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

    companion object {
        private val CP_REGEX = Regex("""\bCP\s*[:.]?\s*(\d{2,5})\b""")
        private val HP_SLASH_REGEX = Regex("""(\d{2,3})\s*/\s*(\d{2,3})\s*HP""")
        private val HP_SINGLE_REGEX = Regex("""\bHP\s*[:.]?\s*(\d{2,3})\b""")
        private val CANDY_FAMILY_REGEX = Regex("""([A-Z][A-Z0-9 .'-]{1,22}?)\s+CANDY\b""")
        private val RETURN_MOVE_REGEX = Regex("""\bRETURN\b""")
        private val FRUSTRATION_REGEX = Regex("""\bFRUSTRATION\b""")
        private val LUCKY_REGEX = Regex("""\bLUCKY\b""")

        private val SHADOW_NON_POKEMON_TERMS = listOf(
            "SHADOW BALL",
            "SHADOW CLAW",
            "SHADOW SNEAK",
            "SHADOW PUNCH",
            "SHADOW FORCE",
            "SHADOW BONE",
            "SHADOW RIDER"
        )

        private val HEADER_IGNORE_KEYWORDS = listOf(
            "HP",
            "WEIGHT",
            "HEIGHT",
            "STARDUST",
            "CANDY",
            "POWER UP",
            "MAX MOVES",
            "LUCKY",
            "PURIFIED",
            "SHADOW"
        )
    }
}
