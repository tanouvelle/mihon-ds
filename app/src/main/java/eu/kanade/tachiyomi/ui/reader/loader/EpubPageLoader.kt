package eu.kanade.tachiyomi.ui.reader.loader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import mihon.core.archive.EpubReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Loads EPUB images and paginated text in spine order. */
internal class EpubPageLoader(private val reader: EpubReader) : PageLoader() {
    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        val content = reader.getContent().flatMap { item ->
            when (item) {
                is EpubReader.Content.Image -> listOf(item)
                is EpubReader.Content.Text -> paginate(item.value).map { EpubReader.Content.Text(it) }
            }
        }
        return content.mapIndexed { index, item ->
            ReaderPage(index).apply {
                stream = {
                    when (item) {
                        is EpubReader.Content.Image -> reader.getInputStream(item.path)!!
                        is EpubReader.Content.Text -> ByteArrayInputStream(renderText(item.value))
                    }
                }
                status = Page.State.Ready
            }
        }
    }

    private fun textPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 38f
    }

    private fun paginate(text: String): List<String> {
        val paint = textPaint()
        val lines = mutableListOf<String>()
        var line = ""
        text.split(Regex("\\s+")).filter(String::isNotBlank).forEach { word ->
            var remaining = word
            while (remaining.isNotEmpty()) {
                val candidate = if (line.isEmpty()) remaining else "$line $remaining"
                if (paint.measureText(candidate) <= 900f) {
                    line = candidate
                    break
                }
                if (line.isNotEmpty()) {
                    lines.add(line)
                    line = ""
                } else {
                    val count = paint.breakText(remaining, true, 900f, null).coerceAtLeast(1)
                    lines.add(remaining.take(count))
                    remaining = remaining.drop(count)
                }
            }
        }
        if (line.isNotEmpty()) lines.add(line)
        return lines.chunked(25).map { it.joinToString("\n") }
    }

    private fun renderText(text: String): ByteArray {
        val bitmap = Bitmap.createBitmap(1080, 1600, Bitmap.Config.RGB_565)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = textPaint()
            text.lines().forEachIndexed { index, line ->
                canvas.drawText(line, 90f, 100f + index * 55f, paint)
            }
            return ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    override suspend fun loadPage(page: ReaderPage) {
        check(!isRecycled)
    }

    override fun recycle() {
        super.recycle()
        reader.close()
    }
}
