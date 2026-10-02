package eu.kanade.tachiyomi.ui.books

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import dev.icerock.moko.resources.StringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.core.archive.EpubReader
import mihon.core.viewmodel.StateViewModel
import nl.adaptivity.xmlutil.serialization.XML
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class BookImportViewModel(
    application: Application = Injekt.get(),
    storageManager: StorageManager = Injekt.get(),
    xml: XML = Injekt.get(),
) : StateViewModel<BookImportViewModel.State>(State()) {
    private val importer = BookImporter(application, storageManager, xml)
    private var prepared: BookImporter.Prepared? = null

    fun prepare(uri: Uri) {
        if (state.value.busy) return
        prepared?.file?.delete()
        prepared = null
        mutableState.value = State(busy = true)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Assign before returning across the cancellation boundary so onCleared can clean up.
                    prepared = importer.prepare(uri)
                }
                val book = prepared ?: return@launch
                mutableState.value = State(
                    metadata = book.metadata,
                    title = book.metadata.title,
                    author = book.metadata.authors.joinToString(", "),
                )
            } catch (e: Exception) {
                prepared?.file?.delete()
                prepared = null
                if (e is CancellationException) throw e
                mutableState.value = State(error = if (e is BookImportTooLarge) {
                    MR.strings.book_import_too_large
                } else {
                    MR.strings.book_import_invalid
                })
            }
        }
    }

    fun setTitle(value: String) { mutableState.update { it.copy(title = value) } }
    fun setAuthor(value: String) { mutableState.update { it.copy(author = value) } }

    fun importBook() {
        val book = prepared ?: return
        val current = state.value
        if (current.busy || current.title.isBlank() || current.finished) return
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { importer.importBook(book, current.title.trim(), current.author) }
                mutableState.update { it.copy(busy = false, finished = true, duplicate = result.duplicate) }
                book.file.delete()
                prepared = null
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutableState.update { it.copy(busy = false, error = if (e is BookImporter.StorageUnavailable) {
                    MR.strings.book_import_storage
                } else {
                    MR.strings.book_import_failed
                }) }
            }
        }
    }

    override fun onCleared() {
        prepared?.file?.delete()
        super.onCleared()
    }

    data class State(
        val metadata: EpubReader.BookMetadata? = null,
        val title: String = "",
        val author: String = "",
        val busy: Boolean = false,
        val finished: Boolean = false,
        val duplicate: Boolean = false,
        val error: StringResource? = null,
    )
}
