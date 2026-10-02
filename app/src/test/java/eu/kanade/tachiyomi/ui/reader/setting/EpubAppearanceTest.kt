package eu.kanade.tachiyomi.ui.reader.setting

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.InMemoryPreferenceStore.InMemoryPreference
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class EpubAppearanceTest {
    @Test
    fun `legacy data migrates once and does not resurrect a removed bookmark`() {
        val prefs = ReaderPreferences(PersistentTestStore(), Json)
        prefs.forEpubBook("old/path.epub").epubFontSize.set(52)
        prefs.epubReadingPosition("old/path.epub").set("300")
        prefs.epubBookmarks("old/path.epub").set(setOf("100"))
        prefs.migrateEpubBook("old/path.epub", "epub-v1:stable")
        assertEquals(52, prefs.forEpubBook("epub-v1:stable").epubFontSize.get())
        assertEquals("300", prefs.epubReadingPosition("epub-v1:stable").get())
        assertEquals(setOf("100"), prefs.epubBookmarks("epub-v1:stable").get())
        prefs.epubBookmarks("epub-v1:stable").set(emptySet())
        prefs.migrateEpubBook("old/path.epub", "epub-v1:stable")
        assertEquals(emptySet<String>(), prefs.epubBookmarks("epub-v1:stable").get())
    }

    @Test
    fun `another path merges bookmarks but preserves existing identity keyed appearance and position`() {
        val prefs = ReaderPreferences(PersistentTestStore(), Json)
        prefs.forEpubBook("epub-v1:stable").epubFontSize.set(60)
        prefs.epubReadingPosition("epub-v1:stable").set("900")
        prefs.epubBookmarks("epub-v1:stable").set(setOf("800"))
        prefs.forEpubBook("copy.epub").epubFontSize.set(30)
        prefs.epubReadingPosition("copy.epub").set("200")
        prefs.epubBookmarks("copy.epub").set(setOf("100"))
        prefs.migrateEpubBook("copy.epub", "epub-v1:stable")
        assertEquals(60, prefs.forEpubBook("epub-v1:stable").epubFontSize.get())
        assertEquals("900", prefs.epubReadingPosition("epub-v1:stable").get())
        assertEquals(setOf("100", "800"), prefs.epubBookmarks("epub-v1:stable").get())
    }

    @Test
    fun `appearance presets round trip and reject malformed data`() {
        val preset = EpubAppearance(2, 52, 2, 170, 75, 100, true)
        assertEquals(preset, EpubAppearance.decode(preset.encode()))
        listOf("", "2,0,38,0,150,50,80,0", "1,0,38", "1,0,38,0,150,50,80,9", "bad").forEach {
            assertNull(EpubAppearance.decode(it))
        }
    }

    @Test
    fun `book overrides survive reopening without changing another book or defaults`() {
        val store = PersistentTestStore()
        val defaults = ReaderPreferences(store, Json)
        val first = defaults.forEpubBook("book-a")
        val second = defaults.forEpubBook("book-b")
        EpubAppearance.capture(second).applyTo(second)
        EpubAppearance(size = 54, theme = 2).applyTo(first)
        assertEquals(54, defaults.forEpubBook("book-a").epubFontSize.get())
        assertEquals(38, second.epubFontSize.get())
        assertEquals(38, defaults.epubFontSize.get())
        first.saveEpubDefaults()
        assertEquals(54, defaults.forEpubBook("book-c").epubFontSize.get())
        assertEquals(38, defaults.forEpubBook("book-b").epubFontSize.get())
    }

    @Test
    fun `bookmarks are independent of typography and other books`() {
        val prefs = ReaderPreferences(PersistentTestStore(), Json)
        prefs.epubBookmarks("book-a").set(setOf("1234"))
        prefs.forEpubBook("book-a").epubFontSize.set(64)
        assertEquals(setOf("1234"), prefs.epubBookmarks("book-a").get())
        assertEquals(emptySet<String>(), prefs.epubBookmarks("book-b").get())
        assertNotEquals(prefs.epubBookmarks("book-a").key(), prefs.epubBookmarks("book-b").key())
    }

    @Test
    fun `preset values are clamped before use`() {
        val prefs = ReaderPreferences(PersistentTestStore(), Json)
        EpubAppearance(-1, 999, 99, 0, -1, 999).applyTo(prefs)
        assertEquals(EpubAppearance(0, 64, 3, 110, 0, 140), EpubAppearance.capture(prefs))
    }

    // The preview store returns detached copies; this store retains writes across preference handles.
    private class PersistentTestStore : PreferenceStore by InMemoryPreferenceStore() {
        private val ints = mutableMapOf<String, Preference<Int>>()
        private val booleans = mutableMapOf<String, Preference<Boolean>>()
        private val strings = mutableMapOf<String, Preference<String>>()
        private val sets = mutableMapOf<String, Preference<Set<String>>>()
        override fun getInt(key: String, defaultValue: Int) =
            ints.getOrPut(key) { InMemoryPreference(key, null, defaultValue) }
        override fun getBoolean(key: String, defaultValue: Boolean) =
            booleans.getOrPut(key) { InMemoryPreference(key, null, defaultValue) }
        override fun getString(key: String, defaultValue: String) =
            strings.getOrPut(key) { InMemoryPreference(key, null, defaultValue) }
        override fun getStringSet(key: String, defaultValue: Set<String>) =
            sets.getOrPut(key) { InMemoryPreference(key, null, defaultValue) }
    }
}
