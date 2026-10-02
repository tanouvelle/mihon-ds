package mihon.core.archive

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser

/** Reads local EPUB content without executing book scripts or fetching external resources. */
class EpubReader internal constructor(
    private val openEntry: (String) -> InputStream?,
    private val closeReader: () -> Unit = {},
) : Closeable {
    constructor(reader: ArchiveReader) : this(reader::getInputStream, reader::close)

    private val pathSeparator = if (openEntry("META-INF\\container.xml")?.use { true } == true) "\\" else "/"

    fun getInputStream(entryName: String): InputStream? = openEntry(entryName)

    override fun close() = closeReader()

    sealed interface Content {
        data class Style(val start: Int, val end: Int, val bold: Boolean, val italic: Boolean)
        data class Text(
            val value: String,
            val heading: Boolean = false,
            val styles: List<Style> = emptyList(),
        ) : Content
        data class Image(val path: String) : Content
        data object Section : Content
        data class Anchor(val target: String) : Content
    }

    data class TocEntry(val title: String, val target: String, val depth: Int = 0)

    /** Stable across external renames and ZIP recompression; distinguishes changed editions. */
    fun getBookIdentity(): String {
        val ref = getPackageHref()
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val paths = listOf(ref) + getPagesFromDocument(getPackageDocument(ref)).map {
            resolveZipPath(getParentDirectory(ref), it)
        }
        paths.forEach { path ->
            val entryDigest = java.security.MessageDigest.getInstance("SHA-256")
            val stream = getInputStream(path) ?: throw IOException("EPUB content file is missing: $path")
            stream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) entryDigest.update(buffer, 0, count)
                }
            }
            // Fixed-size entry digests preserve boundaries and spine order.
            digest.update(entryDigest.digest())
        }
        return "epub-v1:" + digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun getContent(): List<Content> {
        val ref = getPackageHref()
        val result = mutableListOf<Content>()
        getPagesFromDocument(getPackageDocument(ref)).forEach { page ->
            val path = resolveZipPath(getParentDirectory(ref), page)
            result.add(Content.Section)
            result.add(Content.Anchor(path))
            val document = readDocument(path)
            val paragraph = StringBuilder()
            val styles = mutableListOf<Content.Style>()
            fun flush(heading: Boolean = false) {
                val raw = paragraph.toString()
                val start = raw.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
                val value = raw.trim()
                if (value.isNotEmpty()) {
                    val adjusted = styles.mapNotNull {
                        val from = (it.start - start).coerceIn(0, value.length)
                        val to = (it.end - start).coerceIn(0, value.length)
                        if (from < to) it.copy(start = from, end = to) else null
                    }
                    result.add(Content.Text(value, heading, adjusted))
                }
                paragraph.clear()
                styles.clear()
            }
            fun visit(node: Node, heading: Boolean = false) {
                when (node) {
                    is TextNode -> {
                        // Never insert a space between inline elements: "word<em>s</em>," stays "words,".
                        var text = node.wholeText.replace(Regex("[ \t\r\n]+"), " ")
                        if (paragraph.endsWith(" ") && text.startsWith(" ")) text = text.drop(1)
                        paragraph.append(text)
                    }
                    is Element -> {
                        val tag = node.normalName()
                        if (tag in setOf("script", "style", "nav", "head") || node.hasAttr("hidden")) return
                        if (node.attr("style").replace(" ", "").contains("display:none", ignoreCase = true)) return
                        val isHeading = tag in setOf("h1", "h2", "h3", "h4", "h5", "h6")
                        val block = isHeading ||
                            tag in setOf("p", "div", "section", "article", "li", "blockquote", "pre")
                        if (block) flush(heading)
                        // Honour major chapter headings even when a publisher uses one XHTML file.
                        if (tag == "h1") result.add(Content.Section)
                        if (node.id().isNotBlank()) result.add(Content.Anchor("$path#${node.id()}"))
                        when (tag) {
                            "img", "image" -> {
                                flush(heading)
                                val src = node.attr("src")
                                    .ifBlank { node.attr("href") }
                                    .ifBlank { node.attr("xlink:href") }
                                if (src.isNotBlank() && isLocalReference(src)) {
                                    result.add(Content.Image(resolveZipPath(getParentDirectory(path), src)))
                                }
                                return
                            }
                            "br" -> {
                                paragraph.append('\n')
                                return
                            }
                            "hr" -> {
                                flush()
                                result.add(Content.Text("• • •"))
                                return
                            }
                            "li" -> {
                                val prefix = if (node.parent()?.normalName() == "ol") {
                                    "${node.elementSiblingIndex() + 1}. "
                                } else {
                                    "• "
                                }
                                paragraph.append(prefix)
                            }
                        }
                        val start = paragraph.length
                        node.childNodes().forEach { visit(it, heading || isHeading) }
                        if (tag in setOf("b", "strong", "i", "em")) {
                            styles.add(
                                Content.Style(
                                    start, paragraph.length, tag in setOf("b", "strong"), tag in setOf("i", "em"),
                                ),
                            )
                        }
                        if (block) flush(heading || isHeading)
                    }
                }
            }
            document.body().childNodes().forEach { visit(it) }
            flush()
        }
        return result
    }

    fun getTableOfContents(): List<TocEntry> {
        val ref = getPackageHref()
        val manifest = getPackageDocument(ref).select("manifest > item")
        val nav = manifest.firstOrNull { "nav" in it.attr("properties").split(Regex("\\s+")) }
        if (nav != null) {
            val path = resolveZipPath(getParentDirectory(ref), nav.attr("href"))
            val document = getInputStream(path)?.use { Jsoup.parse(it, null, "") }
            val toc = document?.select("nav")?.firstOrNull {
                "toc" in it.attr("epub:type").split(' ') || it.attr("type") == "toc" || it.id() == "toc"
            }
            val entries = toc?.select("a[href]")?.mapNotNull {
                val href = it.attr("href")
                if (it.text().isBlank() || !isLocalReference(href)) return@mapNotNull null
                val depth = (it.parents().count { parent -> parent.normalName() == "ol" } - 1).coerceAtLeast(0)
                TocEntry(it.text(), resolveTarget(path, href), depth)
            }.orEmpty()
            if (entries.isNotEmpty()) return entries
        }
        val ncx = manifest.firstOrNull { it.attr("media-type") == "application/x-dtbncx+xml" } ?: return emptyList()
        val path = resolveZipPath(getParentDirectory(ref), ncx.attr("href"))
        val document = getInputStream(path)?.use { Jsoup.parse(it, null, "", Parser.xmlParser()) } ?: return emptyList()
        return document.select("navPoint").mapNotNull { point ->
            val title = point.children().firstOrNull { it.tagName() == "navLabel" }?.text().orEmpty()
            val href = point.children().firstOrNull { it.tagName() == "content" }?.attr("src").orEmpty()
            if (title.isBlank() || href.isBlank() || !isLocalReference(href)) return@mapNotNull null
            TocEntry(title, resolveTarget(path, href), point.parents().count { it.tagName() == "navPoint" })
        }
    }

    fun getImagesFromPages(): List<String> {
        val ref = getPackageHref()
        return getPagesFromDocument(getPackageDocument(ref)).flatMap { page ->
            val path = resolveZipPath(getParentDirectory(ref), page)
            readDocument(path).select("img, image").mapNotNull {
                val src = it.attr("src").ifBlank { it.attr("href") }.ifBlank { it.attr("xlink:href") }
                if (src.isBlank() || !isLocalReference(src)) null else resolveZipPath(getParentDirectory(path), src)
            }
        }
    }

    fun getPackageHref(): String {
        val path = "META-INF${pathSeparator}container.xml"
        val document = getInputStream(path)?.use { Jsoup.parse(it, null, "", Parser.xmlParser()) }
        return document?.getElementsByTag("rootfile")?.first()?.attr("full-path")?.takeIf(String::isNotBlank)
            ?.replace("/", pathSeparator) ?: "OEBPS${pathSeparator}content.opf"
    }

    fun getPackageDocument(ref: String): Document = getInputStream(ref)?.use {
        Jsoup.parse(it, null, "", Parser.xmlParser())
    } ?: throw IOException("EPUB package document is missing: $ref")

    private fun readDocument(path: String): Document = getInputStream(path)?.use {
        Jsoup.parse(it, null, "")
    } ?: throw IOException("EPUB content file is missing: $path")

    private fun getPagesFromDocument(document: Document): List<String> {
        val manifest = document.select("manifest > item")
            .filter { it.attr("media-type") in setOf("application/xhtml+xml", "text/html") }
            .associateBy { it.attr("id") }
        return document.select("spine > itemref")
            .mapNotNull { manifest[it.attr("idref")]?.attr("href") }
    }

    private fun resolveTarget(documentPath: String, href: String): String {
        val file = href.substringBefore('#')
        val path = if (file.isEmpty()) documentPath else resolveZipPath(getParentDirectory(documentPath), file)
        val fragment = href.substringAfter('#', "")
        return if (fragment.isEmpty()) path else "$path#${decode(fragment)}"
    }

    internal fun resolveZipPath(basePath: String, relativePath: String): String {
        require(isLocalReference(relativePath)) { "External EPUB resource is not supported" }
        val relative = decode(relativePath.substringBefore('#').substringBefore('?')).replace('\\', '/')
        val base = basePath.replace('\\', '/')
        val combined = if (relative.startsWith('/')) relative else "$base/$relative"
        val parts = mutableListOf<String>()
        combined.split('/').forEach {
            when (it) {
                "", "." -> Unit
                ".." -> {
                    require(parts.isNotEmpty()) { "EPUB resource points outside the archive" }
                    parts.removeAt(parts.lastIndex)
                }
                else -> parts.add(it)
            }
        }
        return parts.joinToString(pathSeparator)
    }

    private fun getParentDirectory(path: String) = path.substringBeforeLast(pathSeparator, "")

    private fun decode(value: String): String = try {
        URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8.name())
    } catch (_: IllegalArgumentException) {
        value
    }

    private fun isLocalReference(value: String): Boolean =
        !value.startsWith("//") && !Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(value)
}
