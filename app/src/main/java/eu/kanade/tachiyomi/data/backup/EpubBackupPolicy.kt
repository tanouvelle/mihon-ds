package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.models.BackupPreference

/** Keeps book data independently selectable in both old and new backup files. */
internal object EpubBackupPolicy {
    private val appearance = setOf(
        "font", "font_size", "theme", "line_spacing", "paragraph_spacing", "margin", "compact_pages",
    )
    private val perBook = Regex("epub_[0-9a-f]{64}_reader_epub_(.+)")
    private val location = Regex("reader_epub_(position|bookmarks)_[0-9a-f]{64}")

    fun isEpubKey(key: String): Boolean =
        key == "epub_reduce_flashing" || key == "reader_epub_animate_pages" ||
            key == "reader_epub_day_preset" || key == "reader_epub_night_preset" ||
            key.removePrefix("reader_epub_").let { key.startsWith("reader_epub_") && it in appearance } ||
            perBook.matchEntire(key)?.groupValues?.get(1) in appearance || location.matches(key)

    fun select(preferences: List<BackupPreference>, appSettings: Boolean, epubData: Boolean) =
        preferences.filter { if (isEpubKey(it.key)) epubData else appSettings }

    fun isBookmarkKey(key: String): Boolean = key.startsWith("reader_epub_bookmarks_") && location.matches(key)

    fun mergeBookmarks(existing: Set<String>, restored: Set<String>): Set<String> =
        (existing + restored).mapNotNull { it.toLongOrNull()?.takeIf { offset -> offset >= 0 }?.toString() }.toSet()
}
