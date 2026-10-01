package mihon.core.archive

import java.io.IOException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EpubReaderTest {
    private fun book(
        body: String,
        extraManifest: String = "",
        extraFiles: Map<String, String> = emptyMap(),
    ): EpubReader {
        val files = mapOf(
            "META-INF/container.xml" to
                "<container><rootfiles><rootfile full-path=\"OPS/book.opf\"/></rootfiles></container>",
            "OPS/book.opf" to """
                <package><manifest>
                <item id="chapter" href="text/chapter.xhtml" media-type="application/xhtml+xml"/>
                $extraManifest
                </manifest><spine><itemref idref="chapter"/></spine></package>
            """.trimIndent(),
            "OPS/text/chapter.xhtml" to "<html><body>$body</body></html>",
        ) + extraFiles
        return EpubReader(openEntry = { files[it]?.byteInputStream() })
    }

    @Test
    fun `inline formatting does not add spaces before punctuation or inside words`() {
        val text = book("<p>Hello <em>world</em>! Book<strong>s</strong> are here.</p>")
            .getContent().filterIsInstance<EpubReader.Content.Text>().single()
        assertEquals("Hello world! Books are here.", text.value)
        assertTrue(text.styles.any { it.italic && text.value.substring(it.start, it.end) == "world" })
        assertTrue(text.styles.any { it.bold && text.value.substring(it.start, it.end) == "s" })
    }

    @Test
    fun `headings paragraphs and explicit line breaks remain distinct`() {
        val text = book("<div><h2>Chapter One</h2><p>First<br/>Second</p><p>Third</p></div>")
            .getContent().filterIsInstance<EpubReader.Content.Text>()
        assertEquals(listOf("Chapter One", "First\nSecond", "Third"), text.map { it.value })
        assertTrue(text.first().heading)
        assertFalse(text.last().heading)
    }

    @Test
    fun `hidden script style and navigation text are not part of the novel`() {
        val text = book("<script>bad()</script><style>body{}</style><nav>Menu</nav><p hidden>Hidden</p><p>Visible</p>")
            .getContent().filterIsInstance<EpubReader.Content.Text>()
        assertEquals(listOf("Visible"), text.map { it.value })
    }

    @Test
    fun `body divisions and list items have paragraph boundaries`() {
        val text = book("<div>One</div><div>Two</div><ul><li>Three</li></ul><ol><li>Four</li></ol>")
            .getContent().filterIsInstance<EpubReader.Content.Text>()
        assertEquals(listOf("One", "Two", "• Three", "1. Four"), text.map { it.value })
    }

    @Test
    fun `encoded image paths preserve literal plus and resolve parent directories`() {
        val images = book("<img src='../images/cover%20a+b.jpg#view'/>").getImagesFromPages()
        assertEquals(listOf("OPS/images/cover a+b.jpg"), images)
    }

    @Test
    fun `external images are not requested`() {
        val images = book(
            "<img src='https://example.org/a.jpg'/><img src='//example.org/b.jpg'/><img src='../local.jpg'/>",
        )
            .getImagesFromPages()
        assertEquals(listOf("OPS/local.jpg"), images)
    }

    @Test
    fun `archive path normalization rejects traversal above archive root`() {
        val epub = book("<p>Text</p>")
        assertEquals("OPS/image.jpg", epub.resolveZipPath("OPS/text", "../image.jpg"))
        assertThrows(IllegalArgumentException::class.java) { epub.resolveZipPath("OPS", "../../image.jpg") }
    }

    @Test
    fun `EPUB3 contents retain fragments and nesting`() {
        val epub = book(
            "<h2 id='part 1'>One</h2>",
            "<item id='nav' href='nav.xhtml' media-type='application/xhtml+xml' properties='nav'/>",
            mapOf("OPS/nav.xhtml" to """
                <html><body><nav epub:type="toc"><ol><li><a href="text/chapter.xhtml">One</a>
                <ol><li><a href="text/chapter.xhtml#part%201">Part</a></li></ol></li></ol></nav></body></html>
            """.trimIndent()),
        )
        assertEquals(
            listOf(
                EpubReader.TocEntry("One", "OPS/text/chapter.xhtml"),
                EpubReader.TocEntry("Part", "OPS/text/chapter.xhtml#part 1", 1),
            ),
            epub.getTableOfContents(),
        )
        assertTrue(epub.getContent().contains(EpubReader.Content.Anchor("OPS/text/chapter.xhtml#part 1")))
    }

    @Test
    fun `EPUB2 nested NCX labels do not absorb child labels`() {
        val epub = book(
            "<p>Text</p>",
            "<item id='ncx' href='toc.ncx' media-type='application/x-dtbncx+xml'/>",
            mapOf("OPS/toc.ncx" to """
                <ncx><navMap><navPoint><navLabel><text>Parent</text></navLabel><content src="text/chapter.xhtml"/>
                <navPoint><navLabel><text>Child</text></navLabel><content src="text/chapter.xhtml#child"/></navPoint>
                </navPoint></navMap></ncx>
            """.trimIndent()),
        )
        assertEquals(listOf("Parent", "Child"), epub.getTableOfContents().map { it.title })
        assertEquals(listOf(0, 1), epub.getTableOfContents().map { it.depth })
    }

    @Test
    fun `missing spine files produce an error instead of silently losing chapters`() {
        val epub = book("", extraFiles = mapOf("OPS/book.opf" to """
            <package><manifest><item id="missing" href="missing.xhtml" media-type="application/xhtml+xml"/></manifest>
            <spine><itemref idref="missing"/></spine></package>
        """.trimIndent()))
        val error = assertThrows(IOException::class.java) { epub.getContent() }
        assertTrue(error.message.orEmpty().contains("OPS/missing.xhtml"))
    }

    @Test
    fun `spine order wins over manifest order`() {
        val epub = book("First", extraFiles = mapOf(
            "OPS/book.opf" to """
                <package><manifest><item id="b" href="b.xhtml" media-type="application/xhtml+xml"/>
                <item id="a" href="a.xhtml" media-type="application/xhtml+xml"/></manifest>
                <spine><itemref idref="a"/><itemref idref="b"/></spine></package>
            """.trimIndent(),
            "OPS/a.xhtml" to "<html><body><p>First</p></body></html>",
            "OPS/b.xhtml" to "<html><body><p>Second</p></body></html>",
        ))
        assertEquals(
            listOf("First", "Second"),
            epub.getContent().filterIsInstance<EpubReader.Content.Text>().map { it.value },
        )
    }
}
