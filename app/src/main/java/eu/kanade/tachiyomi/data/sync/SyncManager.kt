package eu.kanade.tachiyomi.data.sync

import android.content.Context
import android.net.Uri
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreJob
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaRestorer
import eu.kanade.tachiyomi.data.sync.service.SyncYomiSyncService
import java.io.File
import java.io.IOException
import java.util.Date
import kotlin.system.measureTimeMillis
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Chapters
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.manga.MangaMapper
import tachiyomi.data.manga.MangaMapper.mapManga
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * A manager to handle synchronization tasks in the app, such as updating
 * sync preferences and performing synchronization with a remote server.
 *
 * @property context The application context.
 */
class SyncManager(
    private val context: Context,
    private val handler: DatabaseHandler = Injekt.get(),
    private val syncPreferences: SyncPreferences = Injekt.get(),
    private var json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    },
    private val getCategories: GetCategories = Injekt.get(),
    private val protoBuf: ProtoBuf = Injekt.get(),
) {
    private val backupCreator: BackupCreator = BackupCreator(context, false)
    private val notifier: SyncNotifier = SyncNotifier(context)
    private val mangaRestorer: MangaRestorer = MangaRestorer()

    enum class SyncService(val value: Int) {
        NONE(0),
        SYNCYOMI(1),
        ;

        companion object {
            fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: NONE
        }
    }

    /**
     * Syncs data with a sync service.
     *
     * This function retrieves local data (favorites, manga, extensions, and categories)
     * from the database using the BackupManager, then synchronizes the data with a sync service.
     */
    suspend fun syncData() {
        // Reset isSyncing in case it was left over or failed syncing during restore.
        handler.await(inTransaction = true) {
            mangasQueries.resetIsSyncing()
            chaptersQueries.resetIsSyncing()
        }

        val syncStartTime = Date().time
        val syncOptions = syncPreferences.getSyncSettings()
        val lastSyncTimestamp = syncPreferences.lastSyncTimestamp.get() / 1000

        val isLocalDirty = try {
            val modifiedMangaCount = handler.awaitOne { mangasQueries.countModifiedSince(lastSyncTimestamp) }
            val modifiedChapterCount = handler.awaitOne { chaptersQueries.countModifiedSince(lastSyncTimestamp) }
            // EPUB preferences have no database dirty timestamp. Include them whenever their sync is enabled.
            (modifiedMangaCount > 0) || (modifiedChapterCount > 0) || syncOptions.epubData
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to check local dirty state" }
            true
        }

        val localBackupCreator: suspend () -> Backup = {
            val backupOptions = BackupOptions(
                libraryEntries = syncOptions.libraryEntries,
                categories = syncOptions.categories,
                chapters = syncOptions.chapters,
                tracking = syncOptions.tracking,
                history = syncOptions.history,
                extensionStores = syncOptions.extensionRepoSettings,
                appSettings = syncOptions.appSettings,
                epubData = syncOptions.epubData,
                sourceSettings = syncOptions.sourceSettings,
                privateSettings = syncOptions.privateSettings,

                // SY -->
                customInfo = syncOptions.customInfo,
                readEntries = syncOptions.readEntries,
                savedSearches = syncOptions.savedSearches,
                // SY <--
            )

            getIncrementalBackup(lastSyncTimestamp, backupOptions)
        }

        // Handle sync based on the selected service
        val syncService = when (val syncService = SyncService.fromInt(syncPreferences.syncService.get())) {
            SyncService.SYNCYOMI -> {
                SyncYomiSyncService(
                    context,
                    json,
                    syncPreferences,
                    notifier,
                )
            }

            else -> {
                logcat(LogPriority.ERROR) { "Invalid sync service type: $syncService" }
                null
            }
        }

        val remoteBackup = syncService?.doSync(localBackupCreator, isLocalDirty)

        if (remoteBackup == null) {
            logcat(LogPriority.DEBUG) { "Skip restore due to network issues or clean sync" }
            // Update timestamp on successful push or idle sync
            syncPreferences.lastSyncTimestamp.set(syncStartTime)
            return
        }

        // Cache the merged state for future incremental syncs
        saveBackupToCache(remoteBackup)

        // Stop the sync early if the remote backup is null or empty
        if (remoteBackup.backupManga.isEmpty() && remoteBackup.backupCategories.isEmpty() && remoteBackup.backupSources.isEmpty()) {
            notifier.showSyncError("No data found on remote server.")
            return
        }

        // Check if it's first sync based on lastSyncTimestamp
        if (syncPreferences.lastSyncTimestamp.get() == 0L) {
            // It's first sync no need to restore data. (just update remote data)
            syncPreferences.lastSyncTimestamp.set(syncStartTime)
            notifier.showSyncSuccess("Updated remote data successfully")
            return
        }

        val (filteredFavorites, nonFavorites) = filterFavoritesAndNonFavorites(remoteBackup)
        updateNonFavorites(nonFavorites)

        if (filteredFavorites.isEmpty() &&
            remoteBackup.backupCategories.isEmpty() &&
            remoteBackup.backupSources.isEmpty() &&
            remoteBackup.backupPreferences.isEmpty() &&
            remoteBackup.backupSourcePreferences.isEmpty() &&
            remoteBackup.backupExtensionStores.isEmpty()
        ) {
            logcat(LogPriority.DEBUG) { "No changes to restore from remote" }
            syncPreferences.lastSyncTimestamp.set(syncStartTime)
            notifier.showSyncSuccess("Sync completed successfully")
            return
        }

        // We use remoteBackup as the base for restoration as it contains the merged state
        val newSyncData = remoteBackup.copy(
            backupManga = filteredFavorites,
        )

        if (syncOptions.categories) {
            val mergedUids = newSyncData.backupCategories.map { it.uid.toString() }.toSet()
            val mergedNames = newSyncData.backupCategories.map { it.name }.toSet()
            val localCategories = getCategories.await().filterNot { it.id == 0L } // Exclude system category
            val categoriesToDelete = localCategories.filter {
                (it.id.toString() !in mergedUids) && (it.name !in mergedNames)
            }
            if (categoriesToDelete.isNotEmpty()) {
                handler.await(inTransaction = true) {
                    categoriesToDelete.forEach {
                        categoriesQueries.delete(it.id)
                    }
                }
            }
        }

        val backupUri = writeSyncDataToCache(context, newSyncData)
        logcat(LogPriority.DEBUG) { "Got Backup Uri: $backupUri" }
        if (backupUri != null) {
            BackupRestoreJob.start(
                context,
                backupUri,
                sync = true,
                options = RestoreOptions(
                    appSettings = syncOptions.appSettings,
                    epubData = syncOptions.epubData,
                    sourceSettings = syncOptions.sourceSettings,
                    libraryEntries = syncOptions.libraryEntries,
                    categories = syncOptions.categories,
                    extensionStores = syncOptions.extensionRepoSettings,
                ),
            )

            // update the sync timestamp
            syncPreferences.lastSyncTimestamp.set(syncStartTime)
            saveBackupToCache(newSyncData)

            // Reset is_syncing flags after restore is done
            handler.await(inTransaction = true) {
                mangasQueries.resetIsSyncing()
                chaptersQueries.resetIsSyncing()
            }
        } else {
            logcat(LogPriority.ERROR) { "Failed to write sync data to file" }
        }
    }

    private suspend fun getIncrementalBackup(
        lastSyncTimestamp: Long,
        backupOptions: BackupOptions,
    ): Backup {
        val cacheFile = File(context.cacheDir, "last_sync_backup.proto")

        val modifiedMangaIds = handler.awaitList { mangasQueries.getModifiedIdsSince(lastSyncTimestamp) }.toMutableSet()
        modifiedMangaIds.addAll(
            handler.awaitList { chaptersQueries.getMangaIdsWithModifiedChaptersSince(lastSyncTimestamp) },
        )

        if (cacheFile.exists() && modifiedMangaIds.isNotEmpty()) {
            try {
                val cachedBackup = cacheFile.inputStream().use {
                    protoBuf.decodeFromByteArray(Backup.serializer(), it.readBytes())
                }

                // Re-backup only modified manga
                val dirtyMangas = handler.awaitList {
                    mangasQueries.getMangaByIds(modifiedMangaIds.toList(), MangaMapper::mapManga)
                }

                logcat(LogPriority.DEBUG) { "Building incremental backup for ${modifiedMangaIds.size} manga" }
                dirtyMangas.forEach {
                    logcat(LogPriority.INFO) { "Incremental push for: ${it.title}" }
                }

                val updatedBackupMangas = backupCreator.backupMangas(dirtyMangas, backupOptions)

                // Patch the list
                val newMangaList = cachedBackup.backupManga.toMutableList()
                val updatedUrls = updatedBackupMangas.map { it.url }.toSet()

                // Remove old versions of the updated manga
                newMangaList.removeAll { it.url in updatedUrls }
                newMangaList.addAll(updatedBackupMangas)

                return cachedBackup.copy(
                    backupManga = newMangaList,
                    backupCategories = backupCreator.backupCategories(backupOptions),
                    backupSources = backupCreator.backupSources(newMangaList),
                    backupPreferences = backupCreator.backupAppPreferences(backupOptions),
                    backupSourcePreferences = backupCreator.backupSourcePreferences(backupOptions),
                    backupExtensionStores = backupCreator.backupExtensionStores(backupOptions),
                )
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load incremental cache, falling back to full backup" }
            }
        }

        // Full backup fallback
        logcat(LogPriority.DEBUG) { "Performing full library backup" }
        val databaseManga = getAllMangaThatNeedsSync()
        val backupManga = backupCreator.backupMangas(databaseManga, backupOptions)
        val fullBackup = Backup(
            backupManga = backupManga,
            backupCategories = backupCreator.backupCategories(backupOptions),
            backupSources = backupCreator.backupSources(backupManga),
            backupPreferences = backupCreator.backupAppPreferences(backupOptions),
            backupSourcePreferences = backupCreator.backupSourcePreferences(backupOptions),
            backupExtensionStores = backupCreator.backupExtensionStores(backupOptions),
        )
        saveBackupToCache(fullBackup)
        return fullBackup
    }

    private fun saveBackupToCache(backup: Backup) {
        val cacheFile = File(context.cacheDir, "last_sync_backup.proto")
        try {
            cacheFile.outputStream().use {
                it.write(protoBuf.encodeToByteArray(Backup.serializer(), backup))
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to save backup to cache" }
        }
    }

    private fun writeSyncDataToCache(context: Context, backup: Backup): Uri? {
        val cacheFile = File(context.cacheDir, "tachiyomi_sync_data.proto.gz")
        return try {
            cacheFile.outputStream().use { output ->
                output.write(ProtoBuf.encodeToByteArray(Backup.serializer(), backup))
                Uri.fromFile(cacheFile)
            }
        } catch (e: IOException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to write sync data to cache" }
            null
        }
    }

    /**
     * Retrieves all manga from the local database.
     *
     * @return a list of all manga stored in the database
     */
    private suspend fun getAllMangaFromDB(): List<Manga> {
        return handler.awaitList { mangasQueries.getAllManga(::mapManga) }
    }

    private suspend fun getAllMangaThatNeedsSync(): List<Manga> {
        return handler.awaitList { mangasQueries.getMangasWithFavoriteTimestamp(::mapManga) }
    }

    /**
     * Filters the favorite and non-favorite manga from the backup and checks
     * if the favorite manga is different from the local database.
     * @param backup the Backup object containing the backup data.
     * @return a Pair of lists, where the first list contains different favorite manga
     * and the second list contains non-favorite manga.
     */
    private suspend fun filterFavoritesAndNonFavorites(backup: Backup): Pair<List<BackupManga>, List<BackupManga>> {
        val favorites = mutableListOf<BackupManga>()
        val nonFavorites = mutableListOf<BackupManga>()

        val elapsedTimeMillis = measureTimeMillis {
            val databaseManga = getAllMangaFromDB()
            val localMangaMap = databaseManga.associateBy {
                Triple(it.source, it.url, it.title)
            }

        logcat(LogPriority.DEBUG) { "filterFavoritesAndNonFavorites: Starting to filter favorites and non-favorites from backup data." }

            backup.backupManga.forEach { remoteManga ->
                val compositeKey = Triple(remoteManga.source, remoteManga.url, remoteManga.title)
                val localManga = localMangaMap[compositeKey]
                when {
                    // Checks if the manga is in favorites and needs updating or adding
                    remoteManga.favorite -> {
                        if (localManga == null) {
                            logcat(LogPriority.DEBUG) { "filterFavoritesAndNonFavorites: New remote favorite: ${remoteManga.title}" }
                            favorites.add(remoteManga)
                        } else if (localManga.version < remoteManga.version) {
                            logcat(LogPriority.DEBUG) { "filterFavoritesAndNonFavorites: Remote has newer version for: ${remoteManga.title} (Local: ${localManga.version}, Remote: ${remoteManga.version})" }
                            favorites.add(remoteManga)
                        } else if (localManga.version == remoteManga.version && remoteManga.chapters.size > localMangaMap[compositeKey]?.id?.let { id -> handler.await { chaptersQueries.getChaptersByMangaId(id, 0).executeAsList().size } } ?: 0) {
                             logcat(LogPriority.DEBUG) { "filterFavoritesAndNonFavorites: Remote has more chapters for: ${remoteManga.title}" }
                             favorites.add(remoteManga)
                        }
                    }
                    // Handle non-favorites
                    !remoteManga.favorite -> {
                        if (localManga?.favorite == true) {
                             logcat(LogPriority.DEBUG) { "filterFavoritesAndNonFavorites: Remote unfavorited: ${remoteManga.title}" }
                             nonFavorites.add(remoteManga)
                        }
                    }
                }
            }
        }

        val minutes = elapsedTimeMillis / 60000
        val seconds = (elapsedTimeMillis % 60000) / 1000
        logcat(LogPriority.DEBUG) {
            "filterFavoritesAndNonFavorites: Filtering completed in ${minutes}m ${seconds}s. Favorites found: ${favorites.size}, " +
                "Non-favorites found: ${nonFavorites.size}"
        }

        return Pair(favorites, nonFavorites)
    }

    /**
     * Updates the non-favorite manga in the local database with their favorite status from the backup.
     * @param nonFavorites the list of non-favorite BackupManga objects from the backup.
     */
    private suspend fun updateNonFavorites(nonFavorites: List<BackupManga>) {
        val localMangaList = getAllMangaFromDB()

        val localMangaMap = localMangaList.associateBy { Triple(it.source, it.url, it.title) }

        nonFavorites.forEach { nonFavorite ->
            val key = Triple(nonFavorite.source, nonFavorite.url, nonFavorite.title)
            localMangaMap[key]?.let { localManga ->
                if (localManga.favorite != nonFavorite.favorite) {
                    val updatedManga = localManga.copy(favorite = nonFavorite.favorite)
                    mangaRestorer.updateManga(updatedManga)
                }
            }
        }
    }
}
