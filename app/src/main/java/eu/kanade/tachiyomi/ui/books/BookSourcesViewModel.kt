package eu.kanade.tachiyomi.ui.books

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.core.viewmodel.StateViewModel
import nl.adaptivity.xmlutil.serialization.XML
import okhttp3.Request
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal class BookSourcesViewModel(
    private val application: Application = Injekt.get(),
    private val network: NetworkHelper = Injekt.get(),
    private val preferences: PreferenceStore = Injekt.get(),
    private val json: Json = Injekt.get(),
    storage: StorageManager = Injekt.get(),
    xml: XML = Injekt.get(),
) : StateViewModel<BookSourcesViewModel.State>(State()) {
    private val savedSources = preferences.getStringSet("book_source_definitions", emptySet())
    private val importer = BookImporter(application, storage, xml)
    private val history = mutableListOf<String>()

    init { refreshSources() }

    private fun refreshSources() {
        val custom = savedSources.get().mapNotNull { value ->
            runCatching { json.decodeFromString<BookSource>(value).also { it.validate() } }.getOrNull()
        }
        mutableState.update { it.copy(sources = (custom + BookSource.builtIns).distinctBy { source -> source.id }) }
    }

    fun saveCatalogue(url: String) {
        val checked = safeBookUrl(url) ?: return
        val id = java.security.MessageDigest.getInstance("SHA-256").digest(checked.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(16)
        val source = BookSource("custom-$id", java.net.URI(checked).host, checked)
        savedSources.set(savedSources.get() + json.encodeToString(source))
        refreshSources()
    }

    fun importSource(uri: Uri) = task {
        val text = application.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = readBookSourceBytes(input, 65536)
            bytes.toString(Charsets.UTF_8)
        } ?: error("Cannot read source")
        val source = json.decodeFromString<BookSource>(text).also { it.validate() }
        val old = savedSources.get().filterNot {
            runCatching { json.decodeFromString<BookSource>(it).id == source.id }.getOrDefault(false)
        }.toSet()
        savedSources.set(old + json.encodeToString(source))
        refreshSources()
    }

    fun open(url: String, remember: Boolean = true) = task {
        val checked = safeBookUrl(url) ?: error("Invalid catalogue URL")
        val (body, actualUrl) = fetchText(checked)
        var catalog = OpdsParser.parse(body, actualUrl)
        if (catalog.searchTemplate == null && catalog.searchDescription != null) {
            val template = try {
                val (description, descriptionUrl) = fetchText(catalog.searchDescription!!)
                OpdsParser.searchDescription(description, descriptionUrl)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
            catalog = catalog.copy(searchTemplate = template)
        }
        if (remember) {
            state.value.url?.let { history.add(it) }
        } else if (history.isNotEmpty()) {
            history.removeAt(history.lastIndex)
        }
        mutableState.update { it.copy(catalog = catalog, url = actualUrl, canGoBack = history.isNotEmpty()) }
    }

    fun back() {
        if (state.value.busy || history.isEmpty()) return
        open(history.last(), remember = false)
    }

    fun search(query: String) {
        val url = state.value.catalog?.searchTemplate?.let { OpdsParser.searchUrl(it, query) } ?: return
        open(url)
    }

    fun acquire(entry: BookCatalogEntry) = task {
        val file = File.createTempFile("catalog-book-", ".epub", application.cacheDir)
        var prepared: BookImporter.Prepared? = null
        try {
            val request = Request.Builder().url(safeBookUrl(entry.url) ?: error("Invalid download URL")).build()
            network.client.newCall(request).awaitSuccess().use { response ->
                response.body.byteStream().use { source -> file.outputStream().use { copyBookBytes(source, it) } }
            }
            val book = importer.prepare(file.toUri())
            prepared = book
            val result = importer.importBook(
                book,
                book.metadata.title.ifBlank { entry.title },
                book.metadata.authors.joinToString(", ").ifBlank { entry.author },
            )
            mutableState.update {
                it.copy(message = if (result.duplicate) MR.strings.book_import_duplicate else MR.strings.book_import_done)
            }
        } finally {
            file.delete()
            prepared?.file?.delete()
        }
    }

    private suspend fun fetchText(url: String): Pair<String, String> {
        val request = Request.Builder().url(url).header("Accept", "application/atom+xml, application/xml").build()
        return network.client.newCall(request).awaitSuccess().use { response ->
            val bytes = response.body.byteStream().use { readBookSourceBytes(it, 4 * 1024 * 1024) }
            bytes.toString(Charsets.UTF_8) to response.request.url.toString()
        }
    }

    private fun task(block: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true, message = null, error = false) }
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { block() } } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutableState.update { it.copy(error = true) }
            } finally { mutableState.update { it.copy(busy = false) } }
        }
    }

    data class State(
        val sources: List<BookSource> = emptyList(),
        val catalog: BookCatalog? = null,
        val url: String? = null,
        val busy: Boolean = false,
        val error: Boolean = false,
        val canGoBack: Boolean = false,
        val message: StringResource? = null,
    )
}
