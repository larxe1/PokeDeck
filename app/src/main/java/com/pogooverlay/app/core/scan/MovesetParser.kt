package com.pogooverlay.app.core.scan

import com.pogooverlay.app.core.data.PokemonSpecies
import com.pogooverlay.app.core.ocr.OcrLine
import com.pogooverlay.app.core.pokebox.MoveData
import kotlin.math.max
import kotlin.math.min

class MovesetParser {

    private data class MoveLineMatch(
        val line: OcrLine,
        val move: MoveData,
        val similarity: Float,
        val isLegal: Boolean
    )

    fun parse(
        lines: List<OcrLine>,
        species: PokemonSpecies?,
        movesById: Map<String, MoveData>,
        appraisalBounds: NormalizedRect? = null
    ): MovesetParseResult {
        if (lines.isEmpty() || movesById.isEmpty()) {
            return MovesetParseResult(MovesetParseStatus.NONE_FOUND)
        }

        val lowerLines = lines
            .filter { it.normalizedTop >= 0.42f }
            .filter { line -> appraisalBounds == null || !appraisalBounds.containsCenter(line) }
            .sortedWith(compareBy<OcrLine> { it.normalizedTop }.thenBy { it.normalizedLeft })

        if (lowerLines.isEmpty()) {
            return MovesetParseResult(MovesetParseStatus.NONE_FOUND)
        }

        val allMoves = movesById.values.toList()
        val legalMoveIds: Set<String> = if (species != null) {
            (species.quickMoves + species.cinematicMoves + SPECIAL_SHADOW_PURIFIED_MOVES).toSet()
        } else {
            emptySet()
        }
        val legalMoves = if (legalMoveIds.isNotEmpty()) {
            legalMoveIds.mapNotNull { movesById[it] }
        } else {
            emptyList()
        }

        val matchedCandidates = mutableListOf<MoveLineMatch>()
        for (line in lowerLines) {
            if (isNonMoveUiLine(line.text)) continue
            val cleaned = cleanMoveLine(line.text)
            if (cleaned.length < 3) continue

            val bestMatch = findBestMoveMatch(
                cleanedLine = cleaned,
                legalMoves = legalMoves,
                allMoves = allMoves
            ) ?: continue

            matchedCandidates.add(
                MoveLineMatch(
                    line = line,
                    move = bestMatch.first,
                    similarity = bestMatch.second,
                    isLegal = bestMatch.third
                )
            )
        }

        if (matchedCandidates.isEmpty()) {
            return MovesetParseResult(MovesetParseStatus.NONE_FOUND)
        }

        val fastMatch = matchedCandidates
            .filter { it.move.isFast }
            .maxWithOrNull(
                compareBy<MoveLineMatch> { it.isLegal }
                    .thenBy { it.similarity }
                    .thenByDescending { it.line.normalizedTop }
            ) ?: return MovesetParseResult(MovesetParseStatus.NONE_FOUND)

        val chargedMatches = matchedCandidates
            .asSequence()
            .filter { !it.move.isFast }
            .filter { it.line !== fastMatch.line && it.line.normalizedTop >= fastMatch.line.normalizedTop }
            .sortedWith(
                compareBy<MoveLineMatch> { it.line.normalizedTop }
                    .thenByDescending { it.isLegal }
                    .thenByDescending { it.similarity }
            )
            .distinctBy { it.move.moveId }
            .take(2)
            .toList()

        if (chargedMatches.isEmpty()) {
            return MovesetParseResult(MovesetParseStatus.NONE_FOUND)
        }

        val matchedOcrLines = buildList {
            add(fastMatch.line)
            chargedMatches.forEach { add(it.line) }
        }

        if (chargedMatches.size >= 2) {
            val complete = CompleteMoveset(
                fastMove = fastMatch.move,
                chargedMoves = chargedMatches.map { it.move }
            )
            return MovesetParseResult(
                status = MovesetParseStatus.COMPLETE,
                moveset = complete,
                matchedLines = matchedOcrLines
            )
        }

        // Single charged move detected: verify whether a 2nd move could be scrolled off the bottom.
        val charged1 = chargedMatches[0]
        val hasLowerBoundaryAnchor = lowerLines.any { candidate ->
            candidate.normalizedTop > charged1.line.normalizedTop && isLowerBoundaryAnchor(candidate.text)
        }

        return if (hasLowerBoundaryAnchor || charged1.line.normalizedBottom <= 0.85f) {
            MovesetParseResult(
                status = MovesetParseStatus.COMPLETE,
                moveset = CompleteMoveset(
                    fastMove = fastMatch.move,
                    chargedMoves = listOf(charged1.move)
                ),
                matchedLines = matchedOcrLines
            )
        } else {
            MovesetParseResult(
                status = MovesetParseStatus.INCOMPLETE_CUTOFF,
                moveset = null,
                matchedLines = matchedOcrLines
            )
        }
    }

    private fun findBestMoveMatch(
        cleanedLine: String,
        legalMoves: List<MoveData>,
        allMoves: List<MoveData>
    ): Triple<MoveData, Float, Boolean>? {
        if (legalMoves.isNotEmpty()) {
            val legalBest = legalMoves
                .mapNotNull { move ->
                    val sim = normalizedSimilarity(cleanedLine, normalizeMoveName(move.displayName))
                    if (sim >= MIN_MOVE_SIMILARITY) move to sim else null
                }
                .maxWithOrNull(
                    compareBy<Pair<MoveData, Float>> { it.second }
                        .thenBy { normalizeMoveName(it.first.displayName).length }
                )
            if (legalBest != null) {
                return Triple(legalBest.first, legalBest.second, true)
            }
        }

        val globalBest = allMoves
            .mapNotNull { move ->
                val sim = normalizedSimilarity(cleanedLine, normalizeMoveName(move.displayName))
                if (sim >= MIN_MOVE_SIMILARITY) move to sim else null
            }
            .maxWithOrNull(
                compareBy<Pair<MoveData, Float>> { it.second }
                    .thenBy { normalizeMoveName(it.first.displayName).length }
            ) ?: return null

        return Triple(globalBest.first, globalBest.second, false)
    }

    private fun isNonMoveUiLine(raw: String): Boolean {
        val upper = raw.trim().uppercase()
        if (upper.isEmpty()) return true
        if (upper in EXACT_UI_IGNORE_LABELS) return true
        val cleanedUpper = cleanMoveLine(raw).uppercase()
        if (cleanedUpper in EXACT_UI_IGNORE_LABELS) return true
        return CONTAINS_UI_IGNORE_PHRASES.any { upper.contains(it) }
    }

    private fun isLowerBoundaryAnchor(raw: String): Boolean {
        val upper = raw.trim().uppercase()
        return LOWER_ANCHOR_KEYWORDS.any { keyword ->
            Regex("""\b$keyword\b""").containsMatchIn(upper)
        }
    }

    internal fun cleanMoveLine(raw: String): String {
        val withoutTrailingDamage = raw
            .replace(TRAILING_DAMAGE_BOOST_REGEX, " ")
            .replace(STANDALONE_NUMBER_REGEX, " ")
        return normalizeMoveName(withoutTrailingDamage)
    }

    private fun normalizeMoveName(raw: String): String {
        return raw
            .lowercase()
            .replace("'", "")
            .replace("’", "")
            .replace('-', ' ')
            .replace('_', ' ')
            .let { NON_ALNUM_REGEX.replace(it, " ") }
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
    }

    private fun normalizedSimilarity(a: String, b: String): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        if (a == b) return 1.0f
        val maxLen = max(a.length, b.length)
        if (maxLen == 0) return 0f
        val dist = levenshtein(a, b)
        return (1f - dist.toFloat() / maxLen.toFloat()).coerceIn(0f, 1f)
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)

        for (i in 1..a.length) {
            curr[0] = i
            val ai = a[i - 1]
            for (j in 1..b.length) {
                val cost = if (ai == b[j - 1]) 0 else 1
                curr[j] = min(
                    min(curr[j - 1] + 1, prev[j] + 1),
                    prev[j - 1] + cost
                )
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }

    companion object {
        const val MIN_MOVE_SIMILARITY = 0.80f

        private val SPECIAL_SHADOW_PURIFIED_MOVES = listOf("FRUSTRATION", "RETURN")
        private val TRAILING_DAMAGE_BOOST_REGEX = Regex("""\s+\d{1,3}\s*\+?\s*$|\s*\+\s*$""")
        private val STANDALONE_NUMBER_REGEX = Regex("""\b\d+\b""")
        private val NON_ALNUM_REGEX = Regex("""[^a-z0-9\s]""")
        private val MULTI_SPACE_REGEX = Regex("""\s+""")

        private val LOWER_ANCHOR_KEYWORDS = listOf(
            "NEW ATTACK",
            "CAUGHT",
            "HATCHED",
            "TRADED",
            "PURIFIED",
            "LUCKY"
        )

        private val EXACT_UI_IGNORE_LABELS = setOf(
            "ATTACK",
            "DEFENSE",
            "POWER UP",
            "PURIFY",
            "EVOLVE",
            "APPRAISE",
            "TRANSFER",
            "FAVORITE"
        )

        private val CONTAINS_UI_IGNORE_PHRASES = listOf(
            "NEW ATTACK",
            "STARDUST",
            "CANDY",
            "GYMS & RAIDS",
            "TRAINER BATTLES",
            "MAX MOVES",
            "CAUGHT",
            "HATCHED",
            "TRADED",
            "PURIFIED",
            "LUCKY",
            "WEIGHT",
            "HEIGHT"
        )
    }
}
