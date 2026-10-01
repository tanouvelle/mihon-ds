package eu.kanade.tachiyomi.ui.reader.loader

/** Maps a source-text offset to a page after fonts, spacing, or margins change. */
internal object EpubReadingPosition {
    fun pageForOffset(pageOffsets: List<Long>, offset: Long): Int? {
        if (pageOffsets.isEmpty() || offset < 0) return null
        return pageOffsets.indexOfLast { it <= offset }.coerceAtLeast(0)
    }
}
