package eu.kanade.tachiyomi.ui.books

import eu.kanade.tachiyomi.util.storage.DiskUtil

internal fun bookImportStem(title: String, author: String): String {
    val label = listOf(author.trim(), title.trim()).filter { it.isNotBlank() }.joinToString(" - ")
    if (label.isBlank()) return "Book"
    return DiskUtil.buildValidFilename(label, 160).trim().trimEnd('.').ifBlank { "Book" }
}
