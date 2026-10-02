package eu.kanade.tachiyomi.ui.books

import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

/** Declarative source extensions use a versioned OPDS API, not manga image-page extensions. */
@Serializable
internal data class BookSource(
    val id: String,
    val name: String,
    val catalogUrl: String = "",
    val schemaVersion: Int = 1,
    val type: String = "opds1",
) {
    fun validate() {
        require(schemaVersion == 1 && type == "opds1")
        require(id.matches(Regex("[a-z0-9-]{1,64}")) && name.isNotBlank() && name.length <= 100)
        if (catalogUrl.isNotEmpty()) require(safeBookUrl(catalogUrl) != null)
    }

    companion object {
        val builtIns = listOf(
            BookSource("gutenberg", "Project Gutenberg", "https://www.gutenberg.org/ebooks/search.opds/"),
            BookSource("calibre", "Calibre"),
        )
    }
}

internal fun safeBookUrl(value: String): String? = value.toHttpUrlOrNull()?.takeIf {
    it.username.isEmpty() && it.password.isEmpty()
}?.toString()

internal data class BookCatalogEntry(
    val title: String,
    val author: String,
    val summary: String,
    val url: String,
    val epub: Boolean,
)
internal data class BookCatalog(
    val title: String,
    val entries: List<BookCatalogEntry>,
    val next: String?,
    val searchTemplate: String?,
    val searchDescription: String?,
)

internal object OpdsParser {
    private fun Element.childrenNamed(name: String) = children().filter { it.tagName().substringAfter(':') == name }
    private fun resolve(base: String, href: String): String? = base.toHttpUrlOrNull()?.resolve(href)?.toString()?.let(::safeBookUrl)

    fun parse(xml: String, url: String): BookCatalog {
        val document = Jsoup.parse(xml, url, Parser.xmlParser())
        val root = document.children().firstOrNull { it.tagName().substringAfter(':') in setOf("feed", "entry") }
            ?: error("Not an OPDS feed")
        val links = root.childrenNamed("link")
        val entries = if (root.tagName().substringAfter(':') == "entry") listOf(root) else root.childrenNamed("entry")
        val books = entries.mapNotNull { entry ->
            val entryLinks = entry.childrenNamed("link")
            val acquisition = entryLinks.firstOrNull {
                it.attr("type").substringBefore(';').trim() == "application/epub+zip" &&
                    it.attr("rel").split(' ').any { rel ->
                        rel == "http://opds-spec.org/acquisition" || rel == "http://opds-spec.org/acquisition/open-access"
                    }
            }
            val navigation = entryLinks.firstOrNull { it.attr("type").startsWith("application/atom+xml") }
            val link = acquisition ?: navigation ?: return@mapNotNull null
            val target = resolve(url, link.attr("href")) ?: return@mapNotNull null
            BookCatalogEntry(
                entry.childrenNamed("title").firstOrNull()?.text().orEmpty(),
                entry.childrenNamed("author").mapNotNull { it.childrenNamed("name").firstOrNull()?.text() }.joinToString(", "),
                entry.childrenNamed("summary").firstOrNull()?.text().orEmpty(),
                target, acquisition != null,
            )
        }
        val search = links.firstOrNull { it.attr("rel") == "search" }
        return BookCatalog(
            root.childrenNamed("title").firstOrNull()?.text().orEmpty(), books,
            links.firstOrNull { it.attr("rel") == "next" }?.let { resolve(url, it.attr("href")) },
            search?.takeIf { it.attr("type").startsWith("application/atom+xml") }?.let { resolve(url, it.attr("href")) },
            search?.takeIf { it.attr("type") == "application/opensearchdescription+xml" }?.let { resolve(url, it.attr("href")) },
        )
    }

    fun searchDescription(xml: String, base: String): String? = Jsoup.parse(xml, base, Parser.xmlParser())
        .getAllElements().firstOrNull {
            it.tagName().substringAfter(':').equals("Url", true) && it.attr("type").startsWith("application/atom+xml")
        }?.attr("template")?.let { resolve(base, it) }

    fun searchUrl(template: String, query: String): String? {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val normalized = template.replace("%7B", "{", true).replace("%7D", "}", true)
        if (!normalized.contains("{searchTerms}")) return null
        val url = normalized.replace("{searchTerms}", encoded)
            .replace("{startIndex?}", "1").replace("{count?}", "25").replace("{startPage?}", "1")
        if ('{' in url || '}' in url) return null
        return safeBookUrl(url)
    }
}
