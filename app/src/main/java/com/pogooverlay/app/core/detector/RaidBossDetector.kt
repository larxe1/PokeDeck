package com.pogooverlay.app.core.detector

import android.graphics.RectF
import com.pogooverlay.app.core.data.PokemonNameMatcher
import com.pogooverlay.app.core.data.RaidBossEntry
import com.pogooverlay.app.core.data.SpeciesMatchResult
import com.pogooverlay.app.core.ocr.OcrLine
import kotlin.math.abs

class RaidBossDetector(
    private val matcherProvider: () -> PokemonNameMatcher,
    private val activeRaidsProvider: () -> List<RaidBossEntry> = { emptyList() }
) : ScreenDetector<SpeciesMatchResult> {

    companion object {
        const val DETECTOR_ID = "raid_boss_detector"

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
            "INVITE",
            "PRIVATE GROUP",
            "PUBLIC GROUP",
            "GROUP CODE",
            "JOIN RAID",
            "JOIN",
            "LEAVE",
            "LOBBY",
            "RECOMMENDED",
            "PARTY",
            "TRAINERS",
            "USE RAID PASS",
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

    val autoRoi: RectF by lazy {
        RectF(0.05f, 0.14f, 0.95f, 0.44f)
    }

    val manualRoi: RectF by lazy {
        RectF(0.02f, 0.05f, 0.98f, 0.62f)
    }

    override val roiNormalized: RectF
        get() = autoRoi

    @Volatile
    var lastAutoMatchedSpeciesId: String? = null
        private set

    @Volatile
    var lastAutoPrefix: String? = null
        private set

    fun roiFor(isManualTrigger: Boolean): RectF {
        return if (isManualTrigger) manualRoi else autoRoi
    }

    fun resetDeduplication() {
        lastAutoMatchedSpeciesId = null
        lastAutoPrefix = null
    }

    override suspend fun detect(
        lines: List<OcrLine>,
        isManualTrigger: Boolean
    ): SpeciesMatchResult? {
        if (lines.isEmpty()) return null
        if (ScreenClassifier.classify(lines) == ScreenKind.MY_POKEMON) return null

        val cleanLines = lines
            .filter { !isNoiseLine(it.text) }
            .sortedWith(
                compareBy<OcrLine> { it.normalizedTop }
                    .thenBy { it.normalizedLeft }
            )

        if (cleanLines.isEmpty()) return null

        val matcher = matcherProvider()
        val activeRaids = activeRaidsProvider()

        data class ScoredCandidate(
            val result: SpeciesMatchResult,
            val score: Float
        )

        var bestCandidate: ScoredCandidate? = null

        fun evaluateCandidate(text: String, isTwoLineJoin: Boolean, firstLineWasPrefix: Boolean) {
            val match = matcher.matchText(text, activeRaids) ?: return
            if (match.confidence < MIN_MATCH_CONFIDENCE) return

            var effectiveScore = match.confidence
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
                bestCandidate = ScoredCandidate(match, effectiveScore)
            }
        }

        // 1. Evaluate adjacent 2-line joins first (e.g., "MEGA" + "CHARIZARD X", "SHADOW" + "MEWTWO")
        for (i in 0 until cleanLines.size - 1) {
            val line1 = cleanLines[i]
            val line2 = cleanLines[i + 1]
            val verticalGap = abs(line2.normalizedTop - line1.normalizedTop)
            if (verticalGap <= MAX_TWO_LINE_VERTICAL_GAP) {
                val combinedText = "${line1.text.trim()} ${line2.text.trim()}"
                val firstLineUpper = normalizeNoiseToken(line1.text)
                val firstLineWasPrefix = firstLineUpper in STANDALONE_PREFIX_TOKENS ||
                    STANDALONE_PREFIX_TOKENS.any { firstLineUpper.startsWith("$it ") }
                evaluateCandidate(
                    text = combinedText,
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
                isTwoLineJoin = false,
                firstLineWasPrefix = false
            )
        }

        val winningMatch = bestCandidate?.result ?: return null

        if (!isManualTrigger) {
            val isDuplicate = winningMatch.species.speciesId == lastAutoMatchedSpeciesId &&
                winningMatch.detectedPrefix == lastAutoPrefix
            if (isDuplicate) {
                return null
            }
        }

        lastAutoMatchedSpeciesId = winningMatch.species.speciesId
        lastAutoPrefix = winningMatch.detectedPrefix
        return winningMatch
    }

    internal fun isNoiseLine(rawText: String): Boolean {
        val trimmed = rawText.trim()
        if (trimmed.length < 2) return true

        // Must contain at least two letters to be a valid species or prefix line
        val letterCount = trimmed.count { it.isLetter() }
        if (letterCount < 2) return true

        if (TIMER_OR_DIGITS_REGEX.matches(trimmed)) return true
        if (CP_REGEX.matches(trimmed)) return true

        val normalized = normalizeNoiseToken(trimmed)
        if (normalized.isEmpty()) return true
        if (normalized in NOISE_EXACT_TOKENS) return true

        // Skip common raid UI phrases
        if (normalized.startsWith("CP ") && normalized.drop(3).all { it.isDigit() || it == ',' || it == '.' || it == '?' || it == ' ' }) {
            return true
        }
        if (normalized.startsWith("PRIVATE GROUP") ||
            normalized.startsWith("RECOMMENDED") ||
            normalized.startsWith("TIME REMAINING") ||
            normalized.startsWith("TRAINERS IN LOBBY") ||
            normalized.startsWith("WALK CLOSER")
        ) {
            return true
        }

        return false
    }

    private fun normalizeNoiseToken(raw: String): String {
        return raw.uppercase()
            .replace(Regex("""[^A-Z0-9:\-\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
