package eu.kanade.tachiyomi.ui.reader.loader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EpubTextSearchTest {
    @Test
    fun `search finds case insensitive literal text and maps across page boundaries`() {
        val text = "One literal [test] and another [TEST]."
        val matches = EpubTextSearch.find(text, "[test]")
        assertEquals(listOf(12L, 31L), matches.map { it.offset })
        assertEquals(1, EpubReadingPosition.pageForOffset(listOf(0L, 10L, 20L), matches.first().offset))
    }

    @Test
    fun `empty queries and result limits are safe`() {
        assertTrue(EpubTextSearch.find("abc", " ").isEmpty())
        assertEquals(2, EpubTextSearch.find("a a a a", "a", 2).size)
        assertTrue(EpubTextSearch.find("abc", "missing").isEmpty())
    }
}
