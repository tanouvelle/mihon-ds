package eu.kanade.tachiyomi.ui.reader.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EpubReadingProgressTest {
    private val contents = listOf(EpubChapterLink("One", 2), EpubChapterLink("Two", 7))

    @Test
    fun `front matter ends at first chapter`() {
        assertEquals(EpubReadingProgress(null, 2, 2, 20), EpubReadingProgress.calculate(contents, 1, 10))
    }

    @Test
    fun `chapter boundaries and last page have correct progress`() {
        assertEquals(EpubReadingProgress(contents[0], 1, 5, 30), EpubReadingProgress.calculate(contents, 2, 10))
        assertEquals(EpubReadingProgress(contents[1], 3, 3, 100), EpubReadingProgress.calculate(contents, 9, 10))
    }

    @Test
    fun `unsorted nested entries select deepest section without zero length ranges`() {
        val nested = listOf(contents[1], EpubChapterLink("Subsection", 2, 1), contents[0])
        val progress = EpubReadingProgress.calculate(nested, 2, 10)!!
        assertEquals("Subsection", progress.chapter?.title)
        assertEquals(5, progress.pages)
    }

    @Test
    fun `empty books and out of range navigation remain safe`() {
        assertNull(EpubReadingProgress.calculate(contents, 0, 0))
        assertEquals(EpubReadingProgress(null, 1, 3, 33), EpubReadingProgress.calculate(emptyList(), -1, 3))
        assertEquals(100, EpubReadingProgress.calculate(emptyList(), 99, 3)?.percent)
    }
}
