package com.pogooverlay.app.core.data

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class PokemonNameMatcher(
    private val allSpecies: List<PokemonSpecies>
) {
    private data class AliasEntry(
        val alias: String,
        val tokenCount: Int,
        val species: PokemonSpecies,
        val isExplicitForm: Boolean
    )

    private data class CandidateMatch(
        val species: PokemonSpecies,
        val isExplicitForm: Boolean,
        val exactRank: Int,
        val similarity: Float,
        val confidence: Float,
        val aliasLength: Int,
        val detectedPrefix: String?,
        val isActiveRaidMatch: Boolean
    )

    private data class CandidateScore(
        val exactRank: Int,
        val similarityBucket: Int,
        val explicitFormRank: Int,
        val activeRaidRank: Int,
        val aliasLength: Int,
        val prefixRank: Int,
        val baseFormTieBreak: Int,
        val dexTieBreak: Int
    ) : Comparable<CandidateScore> {
        override fun compareTo(other: CandidateScore): Int = compareValuesBy(
            this,
            other,
            { it.exactRank },
            { it.similarityBucket },
            { it.explicitFormRank },
            { it.activeRaidRank },
            { it.aliasLength },
            { it.prefixRank },
            { it.baseFormTieBreak },
            { it.dexTieBreak }
        )
    }

    private val formsByDex: Map<Int, List<PokemonSpecies>> = allSpecies
        .groupBy { it.dex }
        .mapValues { (_, forms) ->
            forms.sortedWith(
                compareBy<PokemonSpecies> { it.formLabel() != null }
                    .thenBy { formPriority(it.formLabel()) }
                    .thenBy { it.speciesName }
            )
        }

    private val aliasMap: Map<String, List<AliasEntry>>
    private val allAliases: List<AliasEntry>
    private val speciesAliasesById: Map<String, List<String>>

    init {
        val grouped = LinkedHashMap<String, MutableList<AliasEntry>>()
        val perSpecies = HashMap<String, List<String>>(allSpecies.size)

        for (species in allSpecies) {
            val aliases = buildAliasesForSpecies(species)
            perSpecies[species.speciesId] = aliases.map { it.first }
            for ((alias, isExplicitForm) in aliases) {
                if (alias.isBlank()) continue
                val tokenCount = alias.split(' ').count { it.isNotBlank() }
                val entry = AliasEntry(
                    alias = alias,
                    tokenCount = tokenCount,
                    species = species,
                    isExplicitForm = isExplicitForm
                )
                grouped.getOrPut(alias) { mutableListOf() }.add(entry)
            }
        }

        aliasMap = grouped.mapValues { (_, list) ->
            list.sortedWith(
                compareBy<AliasEntry> { it.species.formLabel() != null }
                    .thenBy { formPriority(it.species.formLabel()) }
                    .thenBy { it.species.dex }
            )
        }
        allAliases = aliasMap.values.flatten()
        speciesAliasesById = perSpecies
    }

    fun getFormsForSpecies(species: PokemonSpecies): List<PokemonSpecies> {
        val forms = formsByDex[species.dex]
        return if (forms.isNullOrEmpty()) listOf(species) else forms
    }

    fun matchText(
        rawText: String,
        activeRaids: List<RaidBossEntry> = emptyList()
    ): SpeciesMatchResult? {
        if (rawText.isBlank() || allSpecies.isEmpty()) return null

        val rawLines = rawText
            .split('\n', '\r')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val candidatesToScan = buildList {
            // Check combined text first in case a form modifier and name span adjacent lines
            if (rawLines.size > 1) {
                add(rawLines.joinToString(" "))
                for (i in 0 until rawLines.size - 1) {
                    add("${rawLines[i]} ${rawLines[i + 1]}")
                }
            }
            addAll(rawLines)
        }.distinct()

        val activeRaidSpecies = if (activeRaids.isEmpty()) {
            emptyList()
        } else {
            activeRaids.mapNotNull { raid ->
                matchText(raid.name, activeRaids = emptyList())?.species
            }
        }
        val activeRaidSpeciesIds = activeRaidSpecies.mapTo(HashSet()) { it.speciesId }

        val cleanedSegments = candidatesToScan
            .map { cleanOcrSegment(it) }
            .filter { it.normalizedText.isNotBlank() }

        if (cleanedSegments.isEmpty()) return null

        val exactCandidates = ArrayList<CandidateMatch>()
        for (cleaned in cleanedSegments) {
            findExactCandidates(
                normalizedText = cleaned.normalizedText,
                detectedPrefix = cleaned.detectedPrefix,
                activeRaidSpeciesIds = activeRaidSpeciesIds,
                out = exactCandidates
            )
        }

        val bestMatch: CandidateMatch? = if (exactCandidates.isNotEmpty()) {
            exactCandidates.maxByOrNull { scoreCandidate(it) }
        } else {
            val fuzzyCandidates = ArrayList<CandidateMatch>()
            for (cleaned in cleanedSegments) {
                findFuzzyCandidates(
                    normalizedText = cleaned.normalizedText,
                    detectedPrefix = cleaned.detectedPrefix,
                    activeRaidSpeciesIds = activeRaidSpeciesIds,
                    out = fuzzyCandidates
                )
            }
            fuzzyCandidates.maxByOrNull { scoreCandidate(it) }
        }

        val winner = bestMatch ?: return null

        // Active raid disambiguation when OCR detected a base/ambiguous name without an explicit form modifier
        val resolvedSpecies = if (!winner.isExplicitForm && activeRaidSpecies.isNotEmpty()) {
            disambiguateWithActiveRaids(winner.species, activeRaidSpecies)
        } else {
            winner.species
        }

        val alternateForms = getFormsForSpecies(resolvedSpecies)
        val displayTitle = formatDisplayTitle(resolvedSpecies, winner.detectedPrefix)

        return SpeciesMatchResult(
            species = resolvedSpecies,
            displayTitle = displayTitle,
            detectedPrefix = winner.detectedPrefix,
            alternateForms = alternateForms,
            confidence = winner.confidence
        )
    }

    fun search(query: String, limit: Int = 25): List<PokemonSpecies> {
        if (limit <= 0) return emptyList()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return allSpecies.take(limit)
        }

        val dexQuery = trimmed.removePrefix("#").toIntOrNull()
        val normalizedQuery = normalizeText(trimmed)
        val queryTokens = normalizedQuery.split(' ').filter { it.isNotBlank() }

        val scored = ArrayList<Pair<Int, PokemonSpecies>>()
        for (species in allSpecies) {
            val score = scoreSearchCandidate(species, dexQuery, normalizedQuery, queryTokens)
            if (score > 0) {
                scored.add(score to species)
            }
        }

        return scored
            .sortedWith(
                compareByDescending<Pair<Int, PokemonSpecies>> { it.first }
                    .thenBy { it.second.dex }
                    .thenBy { it.second.formLabel() != null }
                    .thenBy { it.second.speciesName }
            )
            .take(limit)
            .map { it.second }
    }

    private fun scoreSearchCandidate(
        species: PokemonSpecies,
        dexQuery: Int?,
        normalizedQuery: String,
        queryTokens: List<String>
    ): Int {
        if (dexQuery != null && species.dex == dexQuery) {
            return if (species.formLabel() == null) 1000 else 950
        }
        if (normalizedQuery.isEmpty()) return 0

        val aliases = speciesAliasesById[species.speciesId].orEmpty()
        var best = 0

        for (alias in aliases) {
            when {
                alias == normalizedQuery -> best = max(best, 900)
                alias.startsWith(normalizedQuery) -> best = max(best, 750)
                alias.contains(normalizedQuery) -> best = max(best, 550)
                queryTokens.size > 1 && queryTokens.all { tok ->
                    alias.split(' ').any { it.startsWith(tok) }
                } -> best = max(best, 600)
                queryTokens.size > 1 && queryTokens.all { alias.contains(it) } -> best = max(best, 450)
            }
        }

        // Allow light typo tolerance in search for queries >= 4 chars if no substring hit
        if (best == 0 && normalizedQuery.length >= 4) {
            val baseNorm = normalizeText(species.baseSpeciesName())
            val dist = levenshtein(normalizedQuery, baseNorm)
            if (dist <= 1 || (normalizedQuery.length >= 7 && dist <= 2)) {
                best = 300 - dist * 40
            }
        }

        return best
    }

    private fun findExactCandidates(
        normalizedText: String,
        detectedPrefix: String?,
        activeRaidSpeciesIds: Set<String>,
        out: MutableList<CandidateMatch>
    ) {
        // 1. Direct full-string match (evaluates ALL entries for this alias, never firstOrNull)
        aliasMap[normalizedText]?.forEach { entry ->
            out.add(
                CandidateMatch(
                    species = entry.species,
                    isExplicitForm = entry.isExplicitForm,
                    exactRank = 2,
                    similarity = 1.0f,
                    confidence = 1.0f,
                    aliasLength = entry.alias.length,
                    detectedPrefix = detectedPrefix,
                    isActiveRaidMatch = entry.species.speciesId in activeRaidSpeciesIds
                )
            )
        }

        // 2. Sliding n-gram window over tokens (evaluates ALL entries for every matching slice)
        val tokens = normalizedText.split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return

        val maxWindow = min(tokens.size, 5)
        for (windowSize in maxWindow downTo 1) {
            for (start in 0..(tokens.size - windowSize)) {
                val slice = tokens.subList(start, start + windowSize).joinToString(" ")
                if (slice == normalizedText) continue
                val entries = aliasMap[slice] ?: continue

                val ratio = slice.length.toFloat() / normalizedText.length.toFloat()
                val confidence = (0.90f + 0.09f * ratio).coerceAtMost(0.99f)
                for (entry in entries) {
                    out.add(
                        CandidateMatch(
                            species = entry.species,
                            isExplicitForm = entry.isExplicitForm,
                            exactRank = 1,
                            similarity = ratio,
                            confidence = confidence,
                            aliasLength = entry.alias.length,
                            detectedPrefix = detectedPrefix,
                            isActiveRaidMatch = entry.species.speciesId in activeRaidSpeciesIds
                        )
                    )
                }
            }
        }
    }

    private fun findFuzzyCandidates(
        normalizedText: String,
        detectedPrefix: String?,
        activeRaidSpeciesIds: Set<String>,
        out: MutableList<CandidateMatch>
    ) {
        val tokens = normalizedText.split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return

        val windows = LinkedHashSet<String>()
        val maxWindow = min(tokens.size, 4)
        for (windowSize in maxWindow downTo 1) {
            for (start in 0..(tokens.size - windowSize)) {
                val slice = tokens.subList(start, start + windowSize).joinToString(" ")
                if (slice.length >= 4) {
                    windows.add(slice)
                    val ocrFixed = normalizeOcrDigits(slice)
                    if (ocrFixed != slice) {
                        windows.add(ocrFixed)
                    }
                }
            }
        }

        for (window in windows) {
            // Check exact hit after OCR digit substitution (e.g. "mewtw0" -> "mewtwo")
            val exactFixedEntries = aliasMap[window]
            if (!exactFixedEntries.isNullOrEmpty()) {
                for (entry in exactFixedEntries) {
                    out.add(
                        CandidateMatch(
                            species = entry.species,
                            isExplicitForm = entry.isExplicitForm,
                            exactRank = 0,
                            similarity = 0.95f,
                            confidence = 0.94f,
                            aliasLength = entry.alias.length,
                            detectedPrefix = detectedPrefix,
                            isActiveRaidMatch = entry.species.speciesId in activeRaidSpeciesIds
                        )
                    )
                }
                continue
            }

            val maxAllowedDist = when {
                window.length <= 5 -> 1
                window.length <= 10 -> 2
                else -> 3
            }

            for (entry in allAliases) {
                val target = entry.alias
                if (target.length < 4) continue
                if (abs(target.length - window.length) > maxAllowedDist) continue

                val dist = levenshtein(window, target)
                if (dist > maxAllowedDist) continue

                val maxLen = max(window.length, target.length).toFloat()
                val similarity = 1f - (dist.toFloat() / maxLen)
                if (similarity < MIN_FUZZY_SIMILARITY) continue

                val confidence = (0.78f + (similarity - MIN_FUZZY_SIMILARITY) * (0.14f / (1f - MIN_FUZZY_SIMILARITY)))
                    .coerceIn(0.78f, 0.92f)
                out.add(
                    CandidateMatch(
                        species = entry.species,
                        isExplicitForm = entry.isExplicitForm,
                        exactRank = 0,
                        similarity = similarity,
                        confidence = confidence,
                        aliasLength = target.length,
                        detectedPrefix = detectedPrefix,
                        isActiveRaidMatch = entry.species.speciesId in activeRaidSpeciesIds
                    )
                )
            }
        }
    }

    private fun disambiguateWithActiveRaids(
        baseMatch: PokemonSpecies,
        activeRaidSpecies: List<PokemonSpecies>
    ): PokemonSpecies {
        val matchingRaidSpecies = activeRaidSpecies.filter { it.dex == baseMatch.dex }
        if (matchingRaidSpecies.isEmpty()) return baseMatch

        // If the exact base form itself is currently in active raids, keep it
        if (matchingRaidSpecies.any { it.speciesId == baseMatch.speciesId }) {
            return baseMatch
        }

        // Otherwise prefer the active raid form (e.g. "Meowth" -> "Alolan Meowth")
        return matchingRaidSpecies.first()
    }

    private fun scoreCandidate(candidate: CandidateMatch): CandidateScore {
        return CandidateScore(
            exactRank = candidate.exactRank,
            similarityBucket = (candidate.similarity * 10_000f).roundToInt(),
            explicitFormRank = if (candidate.isExplicitForm) 1 else 0,
            activeRaidRank = if (candidate.isActiveRaidMatch) 1 else 0,
            aliasLength = candidate.aliasLength,
            prefixRank = if (candidate.detectedPrefix != null) 1 else 0,
            baseFormTieBreak = -formPriority(candidate.species.formLabel()),
            dexTieBreak = -candidate.species.dex
        )
    }

    private data class CleanedSegment(
        val normalizedText: String,
        val detectedPrefix: String?
    )

    private fun cleanOcrSegment(segment: String): CleanedSegment {
        // Preserve Nidoran gender glyphs explicitly
        var working = segment
            .replace("♀", " female ")
            .replace("♂", " male ")

        // Strip OCR timers (02:45, 1:23:45), HP/CP counters, levels, and stars
        working = TIMER_REGEX.replace(working, " ")
        working = CP_REGEX.replace(working, " ")
        working = HP_REGEX.replace(working, " ")
        working = LEVEL_REGEX.replace(working, " ")
        working = TIER_REGEX.replace(working, " ")
        working = STANDALONE_NUMBER_REGEX.replace(working, " ")

        var normalized = normalizeText(working)

        // Extract and strip non-type prefixes while preserving "Shadow Rider" (Calyrex)
        val prefixes = mutableListOf<String>()
        var changed = true
        while (changed) {
            changed = false
            for ((regex, label) in NON_TYPE_PREFIXES) {
                val match = regex.find(normalized)
                if (match != null) {
                    if (label !in prefixes) {
                        prefixes.add(label)
                    }
                    normalized = normalized.removeRange(match.range).trim().replace(MULTI_SPACE_REGEX, " ")
                    changed = true
                    break
                }
            }
        }

        // Remove common Pokémon GO raid lobby UI chrome words
        val filteredTokens = normalized
            .split(' ')
            .filter { it.isNotBlank() && it !in UI_NOISE_WORDS }

        return CleanedSegment(
            normalizedText = filteredTokens.joinToString(" "),
            detectedPrefix = prefixes.takeIf { it.isNotEmpty() }?.joinToString(" ")
        )
    }

    private fun buildAliasesForSpecies(species: PokemonSpecies): List<Pair<String, Boolean>> {
        val result = LinkedHashMap<String, Boolean>()
        val base = normalizeText(species.baseSpeciesName())
        val form = species.formLabel()?.let { normalizeText(it) }?.ifBlank { null }
        val fullNorm = normalizeText(species.speciesName)
        val idNorm = normalizeText(species.speciesId.replace('_', ' '))

        fun add(alias: String, explicit: Boolean) {
            val cleaned = alias.trim().replace(MULTI_SPACE_REGEX, " ")
            if (cleaned.isNotEmpty()) {
                val existing = result[cleaned]
                if (existing == null || explicit) {
                    result[cleaned] = explicit
                }
            }
        }

        if (form == null) {
            add(fullNorm, false)
            add(base, false)
            add(idNorm, false)
            if (species.speciesId == "nidoran_female") {
                add("nidoran f", true)
                add("nidoran female", true)
            } else if (species.speciesId == "nidoran_male") {
                add("nidoran m", true)
                add("nidoran male", true)
            }
            return result.entries.map { it.key to it.value }
        }

        add(fullNorm, true)
        add(idNorm, true)
        add("$form $base", true)
        add("$base $form", true)

        when (form) {
            "mega x" -> {
                add("mega $base x", true)
                add("$base mega x", true)
            }
            "mega y" -> {
                add("mega $base y", true)
                add("$base mega y", true)
            }
            "alolan" -> {
                add("alola $base", true)
                add("$base alola", true)
            }
            "galarian" -> {
                add("galar $base", true)
                add("$base galar", true)
            }
            "hisuian" -> {
                add("hisui $base", true)
                add("$base hisui", true)
            }
            "paldean" -> {
                add("paldea $base", true)
                add("$base paldea", true)
            }
            "origin", "altered", "therian", "incarnate", "aria", "pirouette" -> {
                add("$form forme $base", true)
                add("$base $form forme", true)
                add("$form form $base", true)
                add("$base $form form", true)
            }
            "crowned sword", "crowned shield" -> {
                add("crowned $base", true)
                add("$base crowned", true)
            }
        }

        if (form.endsWith(" forme")) {
            val shortForm = form.removeSuffix(" forme").trim()
            add("$shortForm $base", true)
            add("$base $shortForm", true)
        }

        // For species that ONLY exist with explicit form labels in PvPoke (e.g. Giratina Altered/Origin,
        // Landorus Incarnate/Therian, Zacian Hero/Crowned Sword), map bare base name to default form.
        if (isDefaultFormForBaseAlias(species, form)) {
            add(base, false)
        }

        return result.entries.map { it.key to it.value }
    }

    private fun isDefaultFormForBaseAlias(species: PokemonSpecies, normalizedForm: String): Boolean {
        val forms = formsByDex[species.dex].orEmpty()
        if (forms.any { it.formLabel() == null }) return false
        return normalizedForm in DEFAULT_BASE_FORMS
    }

    companion object {
        private const val MIN_FUZZY_SIMILARITY = 0.82f
        private val TIMER_REGEX = Regex("""\b\d{1,2}:\d{2}(?::\d{2})?\b|\b\d{1,3}s\b""", RegexOption.IGNORE_CASE)
        private val CP_REGEX = Regex("""\bc\.?p\.?\s*[:\-]?\s*[\d,?]+\b|\b\d{3,6}\s*cp\b""", RegexOption.IGNORE_CASE)
        private val HP_REGEX = Regex("""\bh\.?p\.?\s*[:\-]?\s*[\d,/]+\b|\b\d+\s*/\s*\d+\s*hp\b""", RegexOption.IGNORE_CASE)
        private val LEVEL_REGEX = Regex("""\blv\.?\s*\d+\b|\blvl\.?\s*\d+\b|\blevel\s*\d+\b""", RegexOption.IGNORE_CASE)
        private val TIER_REGEX = Regex("""\b\d+\s*[- ]?\s*star\b|\btier\s*\d+\b""", RegexOption.IGNORE_CASE)
        private val STANDALONE_NUMBER_REGEX = Regex("""(?<!\p{L})\d{3,6}(?!\p{L})""")
        private val MULTI_SPACE_REGEX = Regex("""\s+""")
        private val NON_ALNUM_REGEX = Regex("""[^a-z0-9\s]""")

        // Note: negative lookahead `(?!\s+rider\b)` ensures "Shadow Rider Calyrex" is NOT stripped!
        private val NON_TYPE_PREFIXES: List<Pair<Regex, String>> = listOf(
            Regex("""\bgigantamax\b""") to "Gigantamax",
            Regex("""\bg\s*max\b""") to "Gigantamax",
            Regex("""\bdynamax\b""") to "Dynamax",
            Regex("""\bd\s*max\b""") to "Dynamax",
            Regex("""\bshadow\b(?!\s+rider\b)""") to "Shadow",
            Regex("""\bpurified\b""") to "Purified",
            Regex("""\bapex\b""") to "Apex"
        )

        private val UI_NOISE_WORDS = setOf(
            "raid", "raids", "boss", "ready", "battle", "lobby", "players",
            "time", "left", "recommended", "party", "private", "group",
            "invite", "friends", "pass", "remote", "gym", "max", "power", "spot"
        )

        private val DEFAULT_BASE_FORMS = setOf(
            "altered", "incarnate", "hero", "aria", "confined", "standard",
            "normal", "land", "plant", "overcast", "ordinary", "shield",
            "midday", "solo", "baile", "amped", "ice", "full belly",
            "single strike", "average", "50 forme", "50"
        )

        private val PREFIX_DISPLAY_FORMS = setOf(
            "Mega", "Primal", "Alolan", "Galarian", "Hisuian", "Paldean",
            "Dusk Mane", "Dawn Wings", "Crowned Sword", "Crowned Shield",
            "Black", "White", "Armored", "Ultra", "Ice Rider", "Shadow Rider"
        )

        fun normalizeText(input: String): String {
            return input
                .lowercase()
                .replace("♀", " female ")
                .replace("♂", " male ")
                .replace("é", "e")
                .replace("'", "")
                .replace("’", "")
                .replace('-', ' ')
                .replace('.', ' ')
                .replace('_', ' ')
                .let { NON_ALNUM_REGEX.replace(it, " ") }
                .replace(MULTI_SPACE_REGEX, " ")
                .trim()
        }

        fun formatDisplayTitle(species: PokemonSpecies, detectedPrefix: String?): String {
            val base = species.baseSpeciesName()
            val form = species.formLabel()
            val formattedSpecies = when {
                form == null -> species.speciesName
                form.equals("Mega X", ignoreCase = true) -> "Mega $base X"
                form.equals("Mega Y", ignoreCase = true) -> "Mega $base Y"
                form in PREFIX_DISPLAY_FORMS -> "$form $base"
                else -> "$base ($form)"
            }

            val cleanPrefix = detectedPrefix?.trim()?.takeIf { it.isNotEmpty() }
            return if (cleanPrefix != null && !formattedSpecies.startsWith(cleanPrefix, ignoreCase = true)) {
                "$cleanPrefix $formattedSpecies"
            } else {
                formattedSpecies
            }
        }

        private fun normalizeOcrDigits(text: String): String = buildString(text.length) {
            for (ch in text) {
                append(
                    when (ch) {
                        '0' -> 'o'
                        '1' -> 'i'
                        '5' -> 's'
                        else -> ch
                    }
                )
            }
        }

        private fun formPriority(formLabel: String?): Int {
            if (formLabel == null) return 0
            return when {
                formLabel.startsWith("Mega", ignoreCase = true) -> 1
                formLabel.equals("Primal", ignoreCase = true) -> 1
                formLabel in setOf("Alolan", "Galarian", "Hisuian", "Paldean") -> 2
                formLabel in setOf(
                    "Origin", "Altered", "Dusk Mane", "Dawn Wings",
                    "Crowned Sword", "Crowned Shield", "Therian", "Incarnate",
                    "Black", "White", "Unbound", "Complete Forme"
                ) -> 3
                else -> 4
            }
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
    }
}
