package eu.kanade.tachiyomi.ui.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LibraryContentTest {
    @Test
    fun `EPUB image mixed and unscanned folders remain visible in the appropriate views`() {
        assertEquals(LibraryContent(true, false), LibraryContent.fromChapterUrls(listOf("Books/a.EPUB")))
        assertEquals(LibraryContent(false, true), LibraryContent.fromChapterUrls(listOf("Comics/a.cbz")))
        assertEquals(LibraryContent(true, true), LibraryContent.fromChapterUrls(listOf("a.epub", "b.cbz")))
        assertEquals(LibraryContent(false, true), LibraryContent.fromChapterUrls(emptyList()))
        assertEquals(LibraryContent(false, true), LibraryContent.fromChapterUrls(listOf("a.epub/images")))
    }
}
