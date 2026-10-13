package eu.kanade.presentation.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EpubResourceUrlTest {
    @Test
    fun `resource URLs preserve Unicode spaces plus signs and fragments`() {
        val path = "OPS/Text/日本語 + chapter.xhtml"
        val url = EpubResourceUrl.build(path, "section one")
        assertEquals(path, EpubResourceUrl.path(url))
        assertEquals("OPS/a.xhtml", EpubResourceUrl.path("https://epub.invalid/OPS/a.xhtml#footnote"))
    }

    @Test
    fun `resources reject external origins file URLs and encoded traversal`() {
        listOf("file:///etc/passwd", "content://books/1", "https://other.example/OPS/a.xhtml",
            "https://epub.invalid@other.example/OPS/a.xhtml", "https://epub.invalid:443/OPS/a.xhtml",
            "https://epub.invalid/%2e%2e/private", "https://epub.invalid/OPS/%5cprivate").forEach {
            assertNull(EpubResourceUrl.path(it), it)
        }
    }
}
