package eu.kanade.tachiyomi.ui.books

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.storage.DiskUtil
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mihon.core.archive.EpubReader
import mihon.core.archive.epubReader
import nl.adaptivity.xmlutil.serialization.XML
import tachiyomi.core.common.storage.extension
import tachiyomi.core.metadata.comicinfo.COMIC_INFO_FILE
import tachiyomi.core.metadata.comicinfo.ComicInfo
import tachiyomi.core.metadata.comicinfo.getComicInfo
import tachiyomi.domain.storage.service.StorageManager

internal class BookImporter(
    private val context: Context,
    private val storageManager: StorageManager,
    private val xml: XML,
) {
    data class Prepared(val file: File, val identity: String, val metadata: EpubReader.BookMetadata, val extension: String = "epub")
    data class Result(val duplicate: Boolean)
    class StorageUnavailable : IOException()

    suspend fun prepare(uri: Uri): Prepared {
        val file = File.createTempFile("book-import-", ".epub", context.cacheDir)
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open document")
            input.use { source -> file.outputStream().use { copyBookBytes(source, it) } }
            val archive = UniFile.fromUri(context, file.toUri()) ?: throw IOException("Cannot open staged book")
            val isPdf = file.inputStream().use { input ->
                val header = ByteArray(5)
                input.read(header) == 5 && header.toString(Charsets.US_ASCII) == "%PDF-"
            }
            if (isPdf) {
                mihon.core.archive.PdfBookReader.open(context, archive).use { it.pageStream(0).close() }
                val name = UniFile.fromUri(context, uri)?.name?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
                    ?: "PDF book"
                return Prepared(file, pdfIdentity(archive), EpubReader.BookMetadata(name, emptyList(), null, null, null), "pdf")
            }
            return archive.epubReader(context).use { reader ->
                val identity = reader.getBookIdentity()
                require(reader.getContent().any { it is EpubReader.Content.Text || it is EpubReader.Content.Image }) {
                    "EPUB has no supported content"
                }
                Prepared(file, identity, reader.getBookMetadata())
            }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

    suspend fun importBook(prepared: Prepared, title: String, author: String): Result = importMutex.withLock {
        val root = storageManager.getLocalSourceDirectory() ?: throw StorageUnavailable()
        // Include manually copied books, rather than trusting a potentially stale duplicate index.
        for (directory in root.listFiles().orEmpty()) {
            coroutineContext.ensureActive()
            if (!directory.isDirectory || directory.name.orEmpty().startsWith('.')) continue
            for (file in directory.listFiles().orEmpty()) {
                if (!file.extension.equals(prepared.extension, true)) continue
                coroutineContext.ensureActive()
                val identity = try {
                    if (prepared.extension == "pdf") pdfIdentity(file) else file.epubReader(context).use { it.getBookIdentity() }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    null // An unreadable existing file must not block a different import.
                }
                if (identity == prepared.identity) return@withLock Result(duplicate = true)
            }
        }
        val name = DiskUtil.buildValidFilename(title, 160) + " [${prepared.identity.takeLast(12)}]"
        // Never overwrite another folder, including one left by a differently named book.
        if (root.findFile(name) != null) throw IOException("Destination already exists")
        val staging = root.createDirectory(".book-import-${UUID.randomUUID()}") ?: throw StorageUnavailable()
        var published = false
        try {
            val book = staging.createFile("book.${prepared.extension}") ?: throw IOException("Cannot create book")
            prepared.file.inputStream().use { source -> book.openOutputStream().use { copyBookBytes(source, it) } }
            val manga = SManga.create().apply {
                this.title = title
                this.author = author.takeIf { it.isNotBlank() }
                description = prepared.metadata.description
            }
            val metadata = xml.encodeToString(ComicInfo.serializer(), manga.getComicInfo())
            val info = staging.createFile(COMIC_INFO_FILE) ?: throw IOException("Cannot create metadata")
            info.openOutputStream().use { it.write(metadata.toByteArray(Charsets.UTF_8)) }
            coroutineContext.ensureActive()
            check(staging.renameTo(name)) { "Storage provider could not finish the import" }
            published = true
            Result(duplicate = false)
        } finally {
            if (!published) staging.delete()
        }
    }

    private fun pdfIdentity(file: UniFile): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.openInputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return "pdf-v1:" + digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val importMutex = Mutex()
    }
}
