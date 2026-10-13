package eu.kanade.tachiyomi.ui.books

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

// Restrict remote identifiers before constructing follow-up URLs.
data class BookMetadataMatch(
    val title: String, val author: String, val year: String,
    val workKey: String?, val coverId: Long?,
)

internal fun parseBookMetadataMatches(text: String): List<BookMetadataMatch> {
    val root = Json.parseToJsonElement(text) as? JsonObject ?: return emptyList()
    val docs = root["docs"] as? JsonArray ?: return emptyList()
    return docs.take(5).mapNotNull { value ->
        val doc = value as? JsonObject ?: return@mapNotNull null
        fun field(name: String) = (doc[name] as? JsonPrimitive)?.contentOrNull
        val title = field("title")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val author = (doc["author_name"] as? JsonArray)?.mapNotNull {
            (it as? JsonPrimitive)?.contentOrNull
        }?.joinToString(", ").orEmpty()
        BookMetadataMatch(title, author, field("first_publish_year").orEmpty(),
            field("key")?.takeIf { Regex("/works/OL[0-9]+W").matches(it) },
            (doc["cover_i"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 })
    }
}

internal fun parseBookDescription(text: String): String? {
    val root = Json.parseToJsonElement(text) as? JsonObject ?: return null
    val field = root["description"]
    return ((field as? JsonPrimitive)?.contentOrNull
        ?: ((field as? JsonObject)?.get("value") as? JsonPrimitive)?.contentOrNull)
        ?.trim()?.take(20000)?.takeIf { it.isNotEmpty() }
}
