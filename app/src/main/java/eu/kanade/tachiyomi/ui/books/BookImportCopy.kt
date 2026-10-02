package eu.kanade.tachiyomi.ui.books

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

internal class BookImportTooLarge : IOException()

internal suspend fun copyBookBytes(input: InputStream, output: OutputStream, limit: Long = 256L * 1024 * 1024) {
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        coroutineContext.ensureActive()
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        if (total > limit) throw BookImportTooLarge()
        output.write(buffer, 0, count)
    }
}
