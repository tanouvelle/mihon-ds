package eu.kanade.tachiyomi.ui.reader.loader

internal data class EpubTextMatch(val offset: Long, val snippet: String)

internal object EpubTextSearch {
    fun find(text: String, query: String, limit: Int = 200): List<EpubTextMatch> {
        val needle = query.trim()
        if (needle.isEmpty() || limit <= 0) return emptyList()
        val matches = mutableListOf<EpubTextMatch>()
        var from = 0
        while (matches.size < limit) {
            val found = text.indexOf(needle, from, ignoreCase = true)
            if (found < 0) break
            val start = (found - 40).coerceAtLeast(0)
            val end = (found + needle.length + 80).coerceAtMost(text.length)
            matches.add(EpubTextMatch(found.toLong(), text.substring(start, end).replace('\n', ' ')))
            from = found + needle.length
        }
        return matches
    }
}
