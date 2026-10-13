package eu.kanade.presentation.reader

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** A private origin; no file/content URIs or remote book resources are exposed. */
internal object EpubResourceUrl {
    private const val ORIGIN = "https://epub.invalid/"
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    fun build(path: String, fragment: String = ""): String =
        ORIGIN + path.split('/').joinToString("/") { encode(it) } +
            if (fragment.isEmpty()) "" else "#${encode(fragment)}"

    fun path(url: String): String? = try {
        val uri = URI(url)
        if (uri.scheme != "https" || uri.rawAuthority != "epub.invalid") null else {
            val path = URLDecoder.decode(uri.rawPath.orEmpty().removePrefix("/").replace("+", "%2B"), "UTF-8")
            path.takeIf { it.isNotEmpty() && '\\' !in it && '\u0000' !in it &&
                it.split('/').none { segment -> segment == ".." || segment == "." || segment.isEmpty() } }
        }
    } catch (_: Exception) { null }
}
