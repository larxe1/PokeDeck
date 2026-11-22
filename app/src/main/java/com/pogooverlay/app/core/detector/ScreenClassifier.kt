package com.pogooverlay.app.core.detector

import com.pogooverlay.app.core.ocr.OcrLine

enum class RaidScreenType {
    RAID_LOBBY,
    RAID_PREVIEW,
    UNKNOWN
}

object RaidScreenClassifier {

    private val LOBBY_ANCHOR_REGEXES = listOf(
        Regex("""\bRECOMMENDED\s+PARTY\b"""),
        Regex("""\bBATTLE\s+PARTY\b"""),
        Regex("""\bREADY\b"""),
        Regex("""\bREJOIN\b"""),
        Regex("""\bINVITE\b""")
    )

    private val PREVIEW_ANCHOR_REGEXES = listOf(
        Regex("""\bBATTLE\b"""),
        Regex("""\bPRIVATE\s+GROUP\b"""),
        Regex("""\bPUBLIC\s+GROUP\b"""),
        Regex("""\bUSE\s+RAID\s+PASS\b"""),
        Regex("""\bREMOTE\s+RAID\b""")
    )

    private val RAID_TIMER_REGEX = Regex("""\b\d{1,2}:\d{2}(:\d{2})?\b""")
    private val HIGH_RAID_CP_REGEX = Regex("""\bCP\s*[:.]?\s*([\d,]{4,7})\b|\b([\d,]{4,7})\s*CP\b""")

    fun classify(lines: List<OcrLine>): RaidScreenType {
        if (lines.isEmpty()) return RaidScreenType.UNKNOWN
        val joined = lines.joinToString("\n") { it.text.uppercase() }

        if (LOBBY_ANCHOR_REGEXES.any { it.containsMatchIn(joined) }) {
            return RaidScreenType.RAID_LOBBY
        }

        val hasPreviewKeyword = PREVIEW_ANCHOR_REGEXES.any { it.containsMatchIn(joined) } ||
            RAID_TIMER_REGEX.containsMatchIn(joined)
        val hasHighRaidCp = HIGH_RAID_CP_REGEX.findAll(joined).any { match ->
            val digits = (match.groupValues[1].ifEmpty { match.groupValues[2] }).replace(",", "")
            (digits.toIntOrNull() ?: 0) >= 6000
        }

        return if (hasPreviewKeyword || hasHighRaidCp) {
            RaidScreenType.RAID_PREVIEW
        } else {
            RaidScreenType.UNKNOWN
        }
    }
}

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
