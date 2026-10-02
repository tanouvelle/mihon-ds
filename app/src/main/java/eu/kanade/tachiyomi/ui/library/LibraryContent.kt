package eu.kanade.tachiyomi.ui.library

internal data class LibraryContent(val hasBooks: Boolean, val hasManga: Boolean) {
    companion object {
        fun fromChapterUrls(urls: List<String>): LibraryContent = LibraryContent(
            hasBooks = urls.any { it.endsWith(".epub", ignoreCase = true) },
            hasManga = urls.isEmpty() || urls.any { !it.endsWith(".epub", ignoreCase = true) },
        )
    }
}
