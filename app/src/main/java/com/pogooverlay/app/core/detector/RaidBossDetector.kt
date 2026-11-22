package com.pogooverlay.app.core.detector

import android.graphics.RectF
import com.pogooverlay.app.core.data.PokemonNameMatcher
import com.pogooverlay.app.core.data.RaidBossEntry
import com.pogooverlay.app.core.data.SpeciesMatchResult
import com.pogooverlay.app.core.ocr.OcrLine
import kotlin.math.abs

data class RaidBossDetectionResult(
    val match: SpeciesMatchResult,
    val matchedLine: OcrLine
)

class RaidBossDetector(
    private val matcherProvider: () -> PokemonNameMatcher,
    private val activeRaidsProvider: () -> List<RaidBossEntry> = { emptyList() }
) : ScreenDetector<SpeciesMatchResult> {

    companion object {
        const val DETECTOR_ID = "raid_boss_detector"

        private const val BOSS_BAND_MIN_TOP = 0.12f
        private const val BOSS_BAND_MAX_TOP = 0.46f
        private const val MAX_CP_ANCHOR_TOP = 0.45f
        private const val MAX_TWO_LINE_VERTICAL_GAP = 0.20f
        private const val MIN_MATCH_CONFIDENCE = 0.72f

        private val CP_REGEX = Regex(
            pattern = """^(?:CP\s*[\d,.\s?]+|[\d,.\s?]+\s*CP|CP|COMBAT\s+POWER.*)$""",
            option = RegexOption.IGNORE_CASE
        )

        private val TIMER_OR_DIGITS_REGEX = Regex(
            pattern = """^[\d\s:./,\-+%()?!★☆*]+$"""
        )

        private val NOISE_EXACT_TOKENS = setOf(
            "BATTLE",
            "READY",
            "REJOIN",
            "INVITE",
            "PRIVATE GROUP",
            "PUBLIC GROUP",
            "GROUP CODE",
            "JOIN RAID",
            "JOIN",
            "LEAVE",
            "LOBBY",
            "RECOMMENDED",
            "RECOMMENDED PARTY",
            "BATTLE PARTY",
            "PARTY",
            "TRAINERS",
            "USE RAID PASS",
            "REMOTE RAID",
            "REMOTE RAID PASS",
            "PREMIUM BATTLE PASS",
            "FREE RAID PASS",
            "MAX BATTLE",
            "RAID BATTLE",
            "WEATHER BOOSTED",
            "TIME REMAINING",
            "WALK CLOSER TO INTERACT WITH THIS GYM",
            "SHOP",
            "ITEMS",
            "POKEMON",
            "POKÉMON",
            "GO",
            "VS"
        )

        private val STANDALONE_PREFIX_TOKENS = setOf(
            "MEGA",
            "PRIMAL",
            "SHADOW",
            "PURIFIED",
            "DYNAMAX",
            "GIGANTAMAX",
            "G-MAX",
            "GMAX",
            "D-MAX",
            "DMAX",
            "APEX",
            "ALOLAN",
            "ALOLA",
            "GALARIAN",
            "GALAR",
            "HISUIAN",
            "HISUI",
            "PALDEAN",
            "PALDEA",
            "ORIGIN",
            "ALTERED",
            "THERIAN",
            "INCARNATE",
            "CROWNED",
            "DUSK",
            "DAWN",
            "DUSK MANE",
            "DAWN WINGS",
            "CROWNED SWORD",
            "CROWNED SHIELD"
        )
    }

    override val id: String = DETECTOR_ID

    override val roiNormalized: RectF by lazy {
        RectF(0.02f, BOSS_BAND_MIN_TOP, 0.98f, BOSS_BAND_MAX_TOP)
    }

    override suspend fun detect(lines: List<OcrLine>): SpeciesMatchResult? {
        return detectWithBox(lines)?.match
    }

    fun detectWithBox(lines: List<OcrLine>): RaidBossDetectionResult? {
        if (lines.isEmpty()) return null

        val cpLineTop = lines
            .filter { it.normalizedTop <= MAX_CP_ANCHOR_TOP && isCpLine(it.text) }
            .minOfOrNull { it.normalizedTop }

        val cleanLines = lines
            .filter { line ->
                !isNoiseLine(line.text) &&
                    line.normalizedTop >= BOSS_BAND_MIN_TOP &&
                    (cpLineTop == null || line.normalizedTop >= cpLineTop)
            }
            .sortedWith(
                compareBy<OcrLine> { it.normalizedTop }
                    .thenBy { it.normalizedLeft }
            )

        if (cleanLines.isEmpty()) return null

        val matcher = matcherProvider()
        val activeRaids = activeRaidsProvider()

        data class ScoredCandidate(
            val result: RaidBossDetectionResult,
            val score: Float
        )

        var bestCandidate: ScoredCandidate? = null

        fun evaluateCandidate(
            text: String,
            matchedLine: OcrLine,
            displayHeight: Float,
            isTwoLineJoin: Boolean,
            firstLineWasPrefix: Boolean
        ) {
            val match = matcher.matchText(text, activeRaids) ?: return
            if (match.confidence < MIN_MATCH_CONFIDENCE) return

            val inBossBand = matchedLine.normalizedTop in BOSS_BAND_MIN_TOP..BOSS_BAND_MAX_TOP
            var effectiveScore = match.confidence
            effectiveScore += if (inBossBand) 0.25f else -0.25f
            effectiveScore += (displayHeight * 4.0f).coerceIn(0f, 0.30f)

            if (isTwoLineJoin && firstLineWasPrefix) {
                effectiveScore += 0.08f
            }
            if (match.detectedPrefix != null) {
                effectiveScore += 0.03f
            }
            if (match.species.formLabel() != null) {
                effectiveScore += 0.03f
            }

            val currentBest = bestCandidate
            if (currentBest == null || effectiveScore > currentBest.score) {
                bestCandidate = ScoredCandidate(
                    result = RaidBossDetectionResult(
                        match = match,
                        matchedLine = matchedLine
                    ),
                    score = effectiveScore
                )
            }
        }

        // 1. Evaluate adjacent 2-line joins first (e.g., "MEGA" + "CHARIZARD X", "SHADOW" + "MEWTWO")
        for (i in 0 until cleanLines.size - 1) {
            val line1 = cleanLines[i]
            val line2 = cleanLines[i + 1]
            val verticalGap = abs(line2.normalizedTop - line1.normalizedTop)
            if (verticalGap <= MAX_TWO_LINE_VERTICAL_GAP) {
                val firstLineUpper = normalizeNoiseToken(line1.text)
                val firstLineWasPrefix = firstLineUpper in STANDALONE_PREFIX_TOKENS ||
                    STANDALONE_PREFIX_TOKENS.any { firstLineUpper.startsWith("$it ") }
                val mergedBox = mergeLinesBox(line1, line2)
                evaluateCandidate(
                    text = mergedBox.text,
                    matchedLine = mergedBox,
                    displayHeight = maxOf(line1.normalizedHeight, line2.normalizedHeight),
                    isTwoLineJoin = true,
                    firstLineWasPrefix = firstLineWasPrefix
                )
            }
        }

        // 2. Evaluate individual clean lines (skipping lines that are only a standalone modifier/prefix)
        for (line in cleanLines) {
            val normalizedToken = normalizeNoiseToken(line.text)
            if (normalizedToken in STANDALONE_PREFIX_TOKENS) {
                continue
            }
            evaluateCandidate(
                text = line.text,
                matchedLine = line,
                displayHeight = line.normalizedHeight,
                isTwoLineJoin = false,
                firstLineWasPrefix = false
            )
        }

        return bestCandidate?.result
    }

    internal fun isNoiseLine(rawText: String): Boolean {
        val trimmed = rawText.trim()
        if (trimmed.length < 2) return true

        // Must contain at least two letters to be a valid species or prefix line
        val letterCount = trimmed.count { it.isLetter() }
        if (letterCount < 2) return true

        if (TIMER_OR_DIGITS_REGEX.matches(trimmed)) return true
        if (isCpLine(trimmed)) return true

        val normalized = normalizeNoiseToken(trimmed)
        if (normalized.isEmpty()) return true
        if (normalized in NOISE_EXACT_TOKENS) return true

        // Skip common raid UI phrases
        if (normalized.startsWith("PRIVATE GROUP") ||
            normalized.startsWith("PUBLIC GROUP") ||
            normalized.startsWith("RECOMMENDED") ||
            normalized.startsWith("BATTLE PARTY") ||
            normalized.startsWith("REJOIN") ||
            normalized.startsWith("INVITE") ||
            normalized.startsWith("USE RAID PASS") ||
            normalized.startsWith("REMOTE RAID") ||
            normalized.startsWith("TIME REMAINING") ||
            normalized.startsWith("TRAINERS IN LOBBY") ||
            normalized.startsWith("WALK CLOSER")
        ) {
            return true
        }

        return false
    }

    private fun isCpLine(rawText: String): Boolean {
        val trimmed = rawText.trim()
        if (CP_REGEX.matches(trimmed)) return true
        val normalized = normalizeNoiseToken(trimmed)
        return normalized.startsWith("CP ") &&
            normalized.drop(3).all { it.isDigit() || it == ',' || it == '.' || it == '?' || it == ' ' }
    }

    private fun mergeLinesBox(line1: OcrLine, line2: OcrLine): OcrLine {
        val top = minOf(line1.normalizedTop, line2.normalizedTop)
        val left = minOf(line1.normalizedLeft, line2.normalizedLeft)
        val bottom = maxOf(line1.normalizedBottom, line2.normalizedBottom)
        val right = maxOf(line1.normalizedRight, line2.normalizedRight)
        return OcrLine(
            text = "${line1.text.trim()} ${line2.text.trim()}",
            normalizedTop = top,
            normalizedLeft = left,
            normalizedHeight = (bottom - top).coerceAtLeast(maxOf(line1.normalizedHeight, line2.normalizedHeight)),
            normalizedWidth = (right - left).coerceAtLeast(maxOf(line1.normalizedWidth, line2.normalizedWidth))
        )
    }

    private fun normalizeNoiseToken(raw: String): String {
        return raw.uppercase()
            .replace(Regex("""[^A-Z0-9:\-\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
