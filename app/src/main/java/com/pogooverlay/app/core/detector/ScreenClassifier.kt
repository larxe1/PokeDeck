package com.pogooverlay.app.core.detector

import com.pogooverlay.app.core.ocr.OcrLine

enum class ScreenKind {
    RAID_BOSS,
    MY_POKEMON,
    UNKNOWN
}

object ScreenClassifier {

    private val MY_POKEMON_KEYWORDS = listOf(
        "WEIGHT",
        "HEIGHT",
        "STARDUST",
        "CANDY",
        "POWER UP",
        "EVOLVE",
        "PURIFY",
        "NEW ATTACK",
        "GYMS & RAIDS",
        "TRAINER BATTLES",
        "WAS CAUGHT",
        "CHANGE FORM",
        "MAX MOVES",
        "APPRAISE",
        "ADVENTURE EFFECTS"
    )

    private val RAID_KEYWORDS = listOf(
        "BATTLE",
        "REMOTE RAID",
        "PRIVATE GROUP",
        "LOBBY",
        "RAID PASS"
    )

    private val HP_SLASH_REGEX = Regex("""\d{2,3}\s*/\s*\d{2,3}\s*HP""")
    private val RAID_TIMER_REGEX = Regex("""\b\d{1,2}:\d{2}(:\d{2})?\b""")
    private val HIGH_RAID_CP_REGEX = Regex("""\bCP\s*[:.]?\s*(\d{4,6})\b""")

    fun classify(lines: List<OcrLine>): ScreenKind {
        if (lines.isEmpty()) return ScreenKind.UNKNOWN
        val upperLines = lines.map { it.text.uppercase() }
        val joined = upperLines.joinToString("\n")

        // Own Pokémon details / appraisal cues take strict priority
        val hasStrongOwnCue = MY_POKEMON_KEYWORDS.any { joined.contains(it) } ||
            HP_SLASH_REGEX.containsMatchIn(joined) ||
            (joined.contains("ATTACK") && joined.contains("DEFENSE"))

        if (hasStrongOwnCue) {
            return ScreenKind.MY_POKEMON
        }

        val hasRaidKeyword = RAID_KEYWORDS.any { joined.contains(it) } ||
            RAID_TIMER_REGEX.containsMatchIn(joined)
        val hasHighRaidCp = HIGH_RAID_CP_REGEX.findAll(joined).any { m ->
            (m.groupValues[1].toIntOrNull() ?: 0) >= 6000
        }

        return if (hasRaidKeyword || hasHighRaidCp) {
            ScreenKind.RAID_BOSS
        } else {
            ScreenKind.UNKNOWN
        }
    }
}
