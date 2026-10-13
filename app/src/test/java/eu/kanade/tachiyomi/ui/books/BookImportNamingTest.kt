package eu.kanade.tachiyomi.ui.books

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BookImportNamingTest {
    @Test
    fun `book filenames remain usable without an author and with unsafe characters`() {
        assertEquals("Author - Title", bookImportStem(" Title ", " Author "))
        assertEquals("Title", bookImportStem("Title", ""))
        assertEquals("Book", bookImportStem(" ", ""))
        assertTrue(bookImportStem("a/b:c", "Author").none { it in "/\\:" })
    }

    @Test
    fun `long Unicode titles fit the filesystem byte limit`() {
        val name = bookImportStem("日本語".repeat(100), "Author")
        assertTrue(name.toByteArray(Charsets.UTF_8).size <= 160)
        assertTrue(!name.contains('\uFFFD'))
    }
}
