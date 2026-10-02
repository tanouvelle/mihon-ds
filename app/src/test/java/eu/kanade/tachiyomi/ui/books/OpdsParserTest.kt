package eu.kanade.tachiyomi.ui.books

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpdsParserTest {
    @Test
    fun `relative navigation EPUB acquisition and pagination remain distinct`() {
        val feed = OpdsParser.parse("""
            <feed xmlns="http://www.w3.org/2005/Atom"><title>Books</title>
            <link rel="next" href="?page=2"/>
            <entry><title>Authors</title><link href="authors" type="application/atom+xml;profile=opds-catalog"/></entry>
            <entry><title>A Book</title><author><name>A Writer</name></author>
            <link rel="http://opds-spec.org/acquisition/open-access" href="../books/1.epub" type="application/epub+zip"/>
            </entry></feed>
        """.trimIndent(), "https://example.org/opds/index")
        assertEquals("https://example.org/opds/index?page=2", feed.next)
        assertEquals("https://example.org/opds/authors", feed.entries[0].url)
        assertFalse(feed.entries[0].epub)
        assertEquals("https://example.org/books/1.epub", feed.entries[1].url)
        assertTrue(feed.entries[1].epub)
    }

    @Test
    fun `unsupported purchase links scripts and credential URLs are not acquired`() {
        val feed = OpdsParser.parse("""
            <feed><entry><title>Buy</title><link rel="http://opds-spec.org/acquisition/buy"
            type="application/epub+zip" href="https://example.org/paid"/></entry>
            <entry><title>Bad</title><link rel="http://opds-spec.org/acquisition"
            type="application/epub+zip" href="javascript:alert(1)"/></entry></feed>
        """.trimIndent(), "https://example.org/")
        assertTrue(feed.entries.isEmpty())
        assertNull(safeBookUrl("https://user:password@example.org/"))
        assertThrows(IllegalArgumentException::class.java) { BookSource("bad", "Bad", "file:///tmp/book").validate() }
    }

    @Test
    fun `search templates encode user input without injecting additional parameters`() {
        val url = OpdsParser.searchUrl("https://example.org/search?q=%7BsearchTerms%7D", "a&b")
        assertEquals("https://example.org/search?q=a%26b", url)
        assertNull(OpdsParser.searchUrl("https://example.org/{unsupported}", "book"))
        val template = OpdsParser.searchDescription("""
            <OpenSearchDescription><Url type="application/atom+xml" template="/search?q={searchTerms}"/></OpenSearchDescription>
        """.trimIndent(), "https://example.org/opensearch.xml")
        assertEquals("https://example.org/search?q=book", OpdsParser.searchUrl(template!!, "book"))
    }
}
