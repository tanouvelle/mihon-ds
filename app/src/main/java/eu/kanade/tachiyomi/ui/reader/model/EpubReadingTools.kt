package eu.kanade.tachiyomi.ui.reader.model

data class EpubBookmark(val offset: Long, val pageIndex: Int)

data class EpubReadingProgress(val chapter: EpubChapterLink?, val page: Int, val pages: Int, val percent: Int) {
    companion object {
        fun calculate(contents: List<EpubChapterLink>, pageIndex: Int, pageCount: Int): EpubReadingProgress? {
            if (pageCount <= 0) return null
            val index = pageIndex.coerceIn(0, pageCount - 1)
            val entries = contents.filter { it.pageIndex in 0 until pageCount }
            val chapter = entries.filter { it.pageIndex <= index }
                .maxWithOrNull(compareBy<EpubChapterLink> { it.pageIndex }.thenBy { it.depth })
            val start = chapter?.pageIndex ?: 0
            val end = entries.filter { it.pageIndex > index }.minOfOrNull { it.pageIndex } ?: pageCount
            return EpubReadingProgress(chapter, index - start + 1, end - start, (index + 1) * 100 / pageCount)
        }
    }
}
