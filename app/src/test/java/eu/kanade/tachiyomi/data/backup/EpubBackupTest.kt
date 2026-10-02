package eu.kanade.tachiyomi.data.backup

import android.content.Context
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.create.creators.PreferenceBackupCreator
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import eu.kanade.tachiyomi.data.backup.restore.restorers.PreferenceRestorer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

@OptIn(ExperimentalSerializationApi::class)
class EpubBackupTest {
    private val id = "a".repeat(64)
    private val bookmarksKey = "reader_epub_bookmarks_$id"
    private val records = listOf(
        BackupPreference(bookmarksKey, StringSetPreferenceValue(setOf("123", "456"))),
        BackupPreference("reader_epub_position_$id", StringPreferenceValue("456")),
        BackupPreference("epub_${id}_reader_epub_font_size", IntPreferenceValue(48)),
        BackupPreference("reader_epub_day_preset", StringPreferenceValue("1,0,38,1,150,50,80,0")),
        BackupPreference("reader_theme", IntPreferenceValue(2)),
    )

    @Test
    fun `EPUB selection works independently and reads legacy preference records`() {
        assertEquals(records.take(4), EpubBackupPolicy.select(records, false, true))
        assertEquals(records.takeLast(1), EpubBackupPolicy.select(records, true, false))
        assertEquals(records, EpubBackupPolicy.select(records, true, true))
        assertTrue(EpubBackupPolicy.select(records, false, false).isEmpty())
        assertFalse(EpubBackupPolicy.isEpubKey("epub_not_a_book_reader_epub_font"))
        assertTrue(EpubBackupPolicy.isEpubKey("epub_reduce_flashing"))
    }

    @Test
    fun `creator and protobuf retain bookmarks and settings without schema changes`() {
        val store = mockk<PreferenceStore>()
        every { store.getAll() } returns mapOf(
            bookmarksKey to setOf("123", "456"),
            "reader_epub_position_$id" to "456",
            "epub_${id}_reader_epub_font_size" to 48,
            "reader_epub_day_preset" to "1,0,38,1,150,50,80,0",
            "reader_theme" to 2,
        )
        val preferences = PreferenceBackupCreator(mockk(), store).createApp(false)
        val backup = Backup(emptyList(), backupPreferences = EpubBackupPolicy.select(preferences, false, true))
        val bytes = ProtoBuf.encodeToByteArray(Backup.serializer(), backup)
        val restored = ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)
        assertEquals(records.take(4), restored.backupPreferences)
    }

    @Test
    fun `restore merges bookmarks and discards invalid offsets`() = runTest {
        val store = mockk<PreferenceStore>()
        val bookmarks = mockk<Preference<Set<String>>>(relaxed = true)
        every { store.getAll() } returns mapOf(bookmarksKey to setOf("900"))
        every { store.getStringSet(bookmarksKey, any()) } returns bookmarks
        every { bookmarks.get() } returns setOf("900", "bad")
        val restorer = PreferenceRestorer(mockk<Context>(), mockk(), store)
        restorer.restoreApp(
            listOf(BackupPreference(bookmarksKey, StringSetPreferenceValue(setOf("123", "-1", "0123")))),
            null,
            updateScheduledTasks = false,
        )
        verify { bookmarks.set(setOf("900", "123")) }
    }

    @Test
    fun `new option round trips without shifting existing flags`() {
        val backup = BackupOptions(epubData = false, privateSettings = true, savedSearches = false)
        assertEquals(backup, BackupOptions.fromBooleanArray(backup.asBooleanArray()))
        val restore = RestoreOptions(epubData = false, sourceSettings = false)
        assertEquals(restore, RestoreOptions.fromBooleanArray(restore.asBooleanArray()))
        assertFalse(BackupOptions.fromBooleanArray(BackupOptions(appSettings = false).asBooleanArray().take(13)
            .toBooleanArray()).epubData)
        assertFalse(RestoreOptions.fromBooleanArray(booleanArrayOf(true, true, false, true, true)).epubData)
        assertTrue(RestoreOptions.fromBooleanArray(booleanArrayOf(true, true, true, true, true)).epubData)
    }

    @Test
    fun `EPUB only backups and restores are allowed`() {
        assertTrue(BackupOptions(
            libraryEntries = false, categories = false, appSettings = false,
            extensionStores = false, sourceSettings = false, epubData = true,
        ).canCreate())
        assertTrue(RestoreOptions(false, false, false, false, false, true).canRestore())
        assertFalse(RestoreOptions(false, false, false, false, false, false).canRestore())
    }
}
