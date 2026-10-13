package mihon.core.archive

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.hippo.unifile.UniFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.InputStream
import kotlin.math.roundToInt

/** Owns a seekable staging file. Rendering/closing are serialized for PdfRenderer. */
class PdfBookReader private constructor(private val file: File, private val renderer: PdfRenderer) : Closeable {
    private var closed = false
    val pageCount: Int = renderer.pageCount

    @Synchronized
    fun pageStream(index: Int): InputStream {
        check(!closed)
        return renderer.openPage(index).use { page ->
            // Bound allocation even for unusual page dimensions.
            val scale = minOf(1800f / page.width.coerceAtLeast(1), 2400f / page.height.coerceAtLeast(1))
            val bitmap = Bitmap.createBitmap(
                (page.width * scale).roundToInt().coerceAtLeast(1),
                (page.height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888,
            )
            try {
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                ByteArrayOutputStream().use { bytes ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes))
                    ByteArrayInputStream(bytes.toByteArray())
                }
            } finally { bitmap.recycle() }
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        try { renderer.close() } finally { file.delete() }
    }

    companion object {
        fun open(context: Context, source: UniFile): PdfBookReader {
            val file = File.createTempFile("pdf-reader-", ".pdf", context.cacheDir)
            try {
                source.openInputStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(65536)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= 256L * 1024 * 1024) { "PDF exceeds 256 MiB" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = try { PdfRenderer(descriptor) } catch (e: Exception) {
                    descriptor.close()
                    throw e
                }
                if (renderer.pageCount == 0) {
                    renderer.close()
                    error("PDF has no pages")
                }
                return PdfBookReader(file, renderer)
            } catch (e: Exception) {
                file.delete()
                throw e
            }
        }
    }
}
