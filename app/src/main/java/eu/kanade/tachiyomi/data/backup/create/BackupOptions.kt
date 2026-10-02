package eu.kanade.tachiyomi.data.backup.create

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR

data class BackupOptions(
    val libraryEntries: Boolean = true,
    val categories: Boolean = true,
    val chapters: Boolean = true,
    val tracking: Boolean = true,
    val history: Boolean = true,
    val readEntries: Boolean = true,
    val appSettings: Boolean = true,
    val extensionStores: Boolean = true,
    val sourceSettings: Boolean = true,
    val privateSettings: Boolean = false,

    // SY -->
    val customInfo: Boolean = true,
    val mergedManga: Boolean = true,
    val savedSearches: Boolean = true,
    // SY <--
    val epubData: Boolean = true,
) {

    fun asBooleanArray() = booleanArrayOf(
        libraryEntries,
        categories,
        chapters,
        tracking,
        history,
        readEntries,
        appSettings,
        extensionStores,
        sourceSettings,
        privateSettings,

        // SY -->
        customInfo,
        mergedManga,
        savedSearches,
        // SY <--
        epubData,
    )

    fun canCreate() = libraryEntries || categories || appSettings || extensionStores || sourceSettings || epubData

    companion object {
        val libraryOptions = listOf(
            Entry(
                label = MR.strings.manga,
                getter = BackupOptions::libraryEntries,
                setter = { options, enabled -> options.copy(libraryEntries = enabled) },
            ),
            Entry(
                label = MR.strings.chapters,
                getter = BackupOptions::chapters,
                setter = { options, enabled -> options.copy(chapters = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = MR.strings.track,
                getter = BackupOptions::tracking,
                setter = { options, enabled -> options.copy(tracking = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = MR.strings.history,
                getter = BackupOptions::history,
                setter = { options, enabled -> options.copy(history = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = MR.strings.categories,
                getter = BackupOptions::categories,
                setter = { options, enabled -> options.copy(categories = enabled) },
            ),
            Entry(
                label = MR.strings.non_library_settings,
                getter = BackupOptions::readEntries,
                setter = { options, enabled -> options.copy(readEntries = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = MR.strings.merged_entries,
                getter = BackupOptions::mergedManga,
                setter = { options, enabled -> options.copy(mergedManga = enabled) },
                enabled = { it.libraryEntries },
            ),
        )

        val settingsOptions = listOf(
            Entry(
                label = MR.strings.epub_backup_data,
                getter = BackupOptions::epubData,
                setter = { options, enabled -> options.copy(epubData = enabled) },
            ),
            Entry(
                label = MR.strings.app_settings,
                getter = BackupOptions::appSettings,
                setter = { options, enabled -> options.copy(appSettings = enabled) },
            ),
            Entry(
                label = MR.strings.extensionStores,
                getter = BackupOptions::extensionStores,
                setter = { options, enabled -> options.copy(extensionStores = enabled) },
            ),
            Entry(
                label = MR.strings.source_settings,
                getter = BackupOptions::sourceSettings,
                setter = { options, enabled -> options.copy(sourceSettings = enabled) },
            ),
            Entry(
                label = MR.strings.private_settings,
                getter = BackupOptions::privateSettings,
                setter = { options, enabled -> options.copy(privateSettings = enabled) },
                enabled = { it.appSettings || it.sourceSettings },
            ),
        )

        fun fromBooleanArray(array: BooleanArray) = BackupOptions(
            libraryEntries = array[0],
            categories = array[1],
            chapters = array[2],
            tracking = array[3],
            history = array[4],
            readEntries = array[5],
            appSettings = array[6],
            extensionStores = array[7],
            sourceSettings = array[8],
            privateSettings = array[9],
            epubData = array.getOrElse(13) { array[6] },

            // SY -->
            customInfo = array.getOrElse(10) { true },
            mergedManga = array.getOrElse(11) { true },
            savedSearches = array.getOrElse(12) { true },
            // SY <--
        )
    }

    data class Entry(
        val label: StringResource,
        val getter: (BackupOptions) -> Boolean,
        val setter: (BackupOptions, Boolean) -> BackupOptions,
        val enabled: (BackupOptions) -> Boolean = { true },
    )
}
