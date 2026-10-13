package eu.kanade.tachiyomi.ui.books

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BookMetadataLookupTest {
    @Test
    fun `matches tolerate missing fields and constrain remote identifiers`() {
        val matches = parseBookMetadataMatches("""{"docs":[
            {"title":"Book","author_name":["Author"],"key":"/works/OL123W","cover_i":42},
            {"title":"Other","key":"//example.com/unsafe","cover_i":-1},
            {"title":null}, false]}""")
        assertEquals(2, matches.size)
        assertEquals("Author", matches[0].author)
        assertEquals("/works/OL123W", matches[0].workKey)
        assertEquals(42L, matches[0].coverId)
        assertNull(matches[1].workKey)
        assertNull(matches[1].coverId)
    }

    @Test
    fun `descriptions accept both API representations and missing descriptions`() {
        assertEquals("A book", parseBookDescription("""{"description":" A book "}"""))
        assertEquals("A book", parseBookDescription("""{"description":{"value":"A book"}}"""))
        assertNull(parseBookDescription("""{"description":null}"""))
        assertNull(parseBookDescription("{}"))
        assertEquals(20000, parseBookDescription("""{"description":"${"a".repeat(21000)}"}""")?.length)
    }
}
