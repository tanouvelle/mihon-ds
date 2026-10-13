package eu.kanade.tachiyomi.ui.books

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.viewModelScope
import com.hippo.unifile.UniFile
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.core.archive.EpubReader
import mihon.core.viewmodel.StateViewModel
import nl.adaptivity.xmlutil.serialization.XML
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.coroutines.coroutineContext

class BookImportViewModel(
    private val application: Application = Injekt.get(),
    storageManager: StorageManager = Injekt.get(),
    xml: XML = Injekt.get(),
    private val network: NetworkHelper = Injekt.get(),
) : StateViewModel<BookImportViewModel.State>(State()) {
    private val importer = BookImporter(application, storageManager, xml)
    private var activeJob: Job? = null
    private var prepared: BookImporter.Prepared? = null
    private var documents = emptyList<Uri>()

    fun prepare(uri: Uri) = prepareMany(listOf(uri))

    fun prepareMany(uris: List<Uri>) {
        if (state.value.busy || uris.isEmpty()) return
        reset()
        documents = uris.distinct().take(MAX_BOOKS)
        documents.forEach(::retainPermission)
        mutableState.value = State(total = documents.size, busy = true, truncated = uris.size > MAX_BOOKS)
        activeJob = viewModelScope.launch { prepareCurrent() }
    }

    fun prepareFolder(uri: Uri) {
        if (state.value.busy) return
        reset()
        retainPermission(uri)
        mutableState.value = State(busy = true)
        activeJob = viewModelScope.launch {
            try {
                val found = withContext(Dispatchers.IO) {
                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
                    val root = UniFile.fromUri(application, documentUri) ?: error("Cannot open folder")
                    val folders = ArrayDeque<Pair<UniFile, Int>>()
                    folders.add(root to 0)
                    val books = mutableListOf<Uri>()
                    var visited = 0
                    var truncated = false
                    while (folders.isNotEmpty()) {
                        coroutineContext.ensureActive()
                        if (++visited > 2000 || books.size >= MAX_BOOKS) { truncated = true; break }
                        val (folder, depth) = folders.removeFirst()
                        for (file in folder.listFiles().orEmpty().sortedBy { it.name.orEmpty().lowercase() }) {
                            coroutineContext.ensureActive()
                            if (file.name.orEmpty().startsWith('.')) continue
                            if (file.isDirectory) {
                                if (depth < 16) folders.add(file to depth + 1) else truncated = true
                            } else if (file.name.orEmpty().substringAfterLast('.', "").lowercase() in setOf("epub", "pdf")) {
                                if (books.size < MAX_BOOKS) books.add(file.uri) else truncated = true
                            }
                        }
                    }
                    books.distinct() to truncated
                }
                documents = found.first
                mutableState.value = State(total = documents.size, busy = true, truncated = found.second)
                prepareCurrent()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutableState.update { it.copy(busy = false, error = MR.strings.book_import_invalid) }
            }
        }
    }

    private fun retainPermission(uri: Uri) {
        try { application.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        catch (_: SecurityException) { /* Some document providers supply temporary grants only. */ }
    }

    fun cancelBatch() {
        val job = activeJob
        viewModelScope.launch {
            job?.cancelAndJoin()
            prepared?.file?.delete()
            prepared = null
            documents = emptyList()
            mutableState.update { it.copy(busy = false, complete = true, metadata = null, error = null, matches = emptyList()) }
        }
    }

    private fun reset() {
        prepared?.file?.delete()
        prepared = null
        documents = emptyList()
    }

    private suspend fun prepareCurrent() {
        val index = state.value.index
        if (index >= documents.size) {
            mutableState.update { it.copy(busy = false, complete = true, metadata = null) }
            return
        }
        mutableState.update { it.copy(busy = true, finished = false, metadata = null, error = null, matches = emptyList(), lookedUp = false, lookupFailed = false, coverFound = false) }
        try {
            withContext(Dispatchers.IO) { prepared = importer.prepare(documents[index]) }
            val book = prepared ?: return
            mutableState.update { it.copy(
                busy = false, metadata = book.metadata, title = book.metadata.title,
                author = book.metadata.authors.joinToString(", "),
            ) }
        } catch (e: Exception) {
            prepared?.file?.delete()
            prepared = null
            if (e is CancellationException) throw e
            mutableState.update { it.copy(busy = false, error = if (e is BookImportTooLarge) {
                MR.strings.book_import_too_large
            } else { MR.strings.book_import_invalid }) }
        }
    }

    fun setTitle(value: String) { mutableState.update { it.copy(title = value) } }
    fun setAuthor(value: String) { mutableState.update { it.copy(author = value) } }

    fun next() {
        val current = state.value
        if (current.busy || current.complete) return
        prepared?.file?.delete()
        prepared = null
        mutableState.update { it.copy(index = it.index + 1, skipped = it.skipped + if (it.finished) 0 else 1) }
        activeJob = viewModelScope.launch { prepareCurrent() }
    }

    fun importBook() = importBooks(false)
    fun importRemaining() = importBooks(true)

    private fun importBooks(remaining: Boolean) {
        if (state.value.busy || state.value.finished || state.value.title.isBlank() || prepared == null) return
        mutableState.update { it.copy(busy = true, error = null) }
        activeJob = viewModelScope.launch {
            try {
                do {
                    coroutineContext.ensureActive()
                    val book = prepared ?: break
                    val current = state.value
                    val result = withContext(Dispatchers.IO) { importer.importBook(book, current.title.trim(), current.author.trim()) }
                    book.file.delete()
                    prepared = null
                    mutableState.update { it.copy(
                        finished = true, duplicate = result.duplicate,
                        imported = it.imported + if (result.duplicate) 0 else 1,
                        duplicates = it.duplicates + if (result.duplicate) 1 else 0,
                    ) }
                    if (!remaining || current.index + 1 >= documents.size) break
                    mutableState.update { it.copy(index = it.index + 1) }
                    prepareCurrent()
                    // Invalid documents stop the batch for review; already imported books remain intact.
                    if (prepared == null) break
                    mutableState.update { it.copy(busy = true) }
                } while (remaining)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutableState.update { it.copy(error = if (e is BookImporter.StorageUnavailable) {
                    MR.strings.book_import_storage
                } else { MR.strings.book_import_failed }) }
            } finally { mutableState.update { it.copy(busy = false) } }
        }
    }

    fun lookupMetadata() {
        val current = state.value
        if (current.busy || current.finished || current.title.isBlank()) return
        mutableState.update { it.copy(busy = true, matches = emptyList(), lookupFailed = false, lookedUp = false) }
        activeJob = viewModelScope.launch {
            try {
                val matches = withContext(Dispatchers.IO) {
                    val url = "https://openlibrary.org/search.json".toHttpUrl().newBuilder()
                        .addQueryParameter("title", current.title)
                        .apply { if (current.author.isNotBlank()) addQueryParameter("author", current.author) }
                        .addQueryParameter("limit", "5")
                        .addQueryParameter("fields", "key,title,author_name,first_publish_year,cover_i").build()
                    network.client.newCall(Request.Builder().url(url).build()).awaitSuccess().use { response ->
                        val bytes = response.body.byteStream().use { readBookSourceBytes(it, 1024 * 1024) }
                        parseBookMetadataMatches(bytes.toString(Charsets.UTF_8))
                    }
                }
                mutableState.update { it.copy(matches = matches, lookedUp = true) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutableState.update { it.copy(lookupFailed = true) }
            } finally { mutableState.update { it.copy(busy = false) } }
        }
    }

    fun useMatch(match: BookMetadataMatch) {
        if (state.value.busy || state.value.finished) return
        val book = prepared ?: return
        mutableState.update { it.copy(title = match.title, author = match.author, matches = emptyList(), lookedUp = false, busy = true) }
        activeJob = viewModelScope.launch {
            var description: String? = null
            var cover: ByteArray? = null
            var failed = false
            try {
                withContext(Dispatchers.IO) {
                    match.workKey?.let { key ->
                        try {
                            val request = Request.Builder().url("https://openlibrary.org$key.json").build()
                            network.client.newCall(request).awaitSuccess().use { response ->
                                val bytes = response.body.byteStream().use { readBookSourceBytes(it, 1024 * 1024) }
                                description = parseBookDescription(bytes.toString(Charsets.UTF_8))
                            }
                        } catch (e: Exception) { if (e is CancellationException) throw e; failed = true }
                    }
                    match.coverId?.let { id ->
                        try {
                            val request = Request.Builder().url("https://covers.openlibrary.org/b/id/$id-L.jpg?default=false").build()
                            network.client.newCall(request).awaitSuccess().use { response ->
                                val bytes = response.body.byteStream().use { readBookSourceBytes(it, 2 * 1024 * 1024) }
                                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                                require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outMimeType == "image/jpeg")
                                cover = bytes
                            }
                        } catch (e: Exception) { if (e is CancellationException) throw e; failed = true }
                    }
                }
                prepared = book.copy(metadata = book.metadata.copy(description = description ?: book.metadata.description), cover = cover)
                mutableState.update { it.copy(metadata = prepared?.metadata, coverFound = cover != null, lookupFailed = failed) }
            } finally { mutableState.update { it.copy(busy = false) } }
        }
    }

    override fun onCleared() { super.onCleared(); reset() }

    data class State(
        val metadata: EpubReader.BookMetadata? = null,
        val title: String = "", val author: String = "",
        val busy: Boolean = false, val finished: Boolean = false, val duplicate: Boolean = false,
        val error: StringResource? = null,
        val index: Int = 0, val total: Int = 0, val imported: Int = 0, val duplicates: Int = 0, val skipped: Int = 0,
        val complete: Boolean = false, val truncated: Boolean = false,
        val matches: List<BookMetadataMatch> = emptyList(), val lookedUp: Boolean = false, val lookupFailed: Boolean = false, val coverFound: Boolean = false,
    )
    private companion object { const val MAX_BOOKS = 250 }
}
