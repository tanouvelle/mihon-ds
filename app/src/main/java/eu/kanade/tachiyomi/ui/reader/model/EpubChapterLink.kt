package eu.kanade.tachiyomi.ui.reader.model

/** A resolved EPUB navigation entry; pageIndex uses the reader's zero-based page numbering. */
data class EpubChapterLink(val title: String, val pageIndex: Int, val depth: Int = 0)
