package eu.kanade.tachiyomi.ui.reader.loader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import mihon.core.archive.EpubReader
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Presents local EPUB spine content using the existing manga reader's navigation modes. */
internal class EpubPageLoader(private val reader: EpubReader) : PageLoader() {
    override var isLocal: Boolean = true

    private val preferences = Injekt.get<ReaderPreferences>()
    private val fontSize = preferences.epubFontSize.get().coerceIn(26, 54).toFloat()
    private val font = when (preferences.epubFont.get()) {
        1 -> Typeface.SANS_SERIF
        2 -> Typeface.MONOSPACE
        else -> Typeface.SERIF
    }
    private val theme = preferences.epubTheme.get()
    private val lineHeight = fontSize * 1.5f
    private val maxLines = ((1500f - 110f) / lineHeight).toInt().coerceAtLeast(1)

    private data class Line(val text: String, val bold: Boolean = false)
    private sealed interface RenderPage {
        data class Text(val lines: List<Line>) : RenderPage
        data class Image(val path: String) : RenderPage
    }

    override suspend fun getPages(): List<ReaderPage> {
        val pages = mutableListOf<RenderPage>()
        val lines = mutableListOf<Line>()
        val paint = textPaint()

        fun flushPage() {
            if (lines.isNotEmpty()) {
                pages.add(RenderPage.Text(lines.toList()))
                lines.clear()
            }
        }
        fun addLine(line: Line) {
            if (lines.size == maxLines) flushPage()
            lines.add(line)
        }
        fun addParagraph(value: String, heading: Boolean = false) {
            if (value.isBlank()) return
            if (lines.isNotEmpty()) addLine(Line(""))
            val activePaint = if (heading) textPaint(bold = true) else paint
            var line = ""
            value.split(Regex("\\s+")).forEach { word ->
                var remaining = word
                while (remaining.isNotEmpty()) {
                    val candidate = if (line.isEmpty()) remaining else "$line $remaining"
                    if (activePaint.measureText(candidate) <= 900f) {
                        line = candidate
                        break
                    }
                    if (line.isNotEmpty()) {
                        addLine(Line(line, heading))
                        line = ""
                    } else {
                        val count = activePaint.breakText(remaining, true, 900f, null).coerceAtLeast(1)
                        addLine(Line(remaining.take(count), heading))
                        remaining = remaining.drop(count)
                    }
                }
            }
            if (line.isNotEmpty()) addLine(Line(line, heading))
        }

        val toc = reader.getTableOfContents()
        if (toc.isNotEmpty()) {
            addParagraph("Table of contents", heading = true)
            toc.forEach { addParagraph(it) }
            flushPage()
        }
        reader.getContent().forEach { item ->
            when (item) {
                is EpubReader.Content.Text -> addParagraph(item.value, item.heading)
                is EpubReader.Content.Image -> {
                    flushPage()
                    pages.add(RenderPage.Image(item.path))
                }
            }
        }
        flushPage()

        return pages.mapIndexed { index, item ->
            ReaderPage(index).apply {
                stream = {
                    when (item) {
                        is RenderPage.Image -> reader.getInputStream(item.path)!!
                        is RenderPage.Text -> ByteArrayInputStream(renderText(item.lines))
                    }
                }
                status = Page.State.Ready
            }
        }
    }

    private fun textPaint(bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (theme == 2) Color.rgb(225, 225, 225) else Color.rgb(35, 32, 29)
        textSize = fontSize
        typeface = Typeface.create(font, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun renderText(lines: List<Line>): ByteArray {
        val bitmap = Bitmap.createBitmap(1080, 1600, Bitmap.Config.RGB_565)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(when (theme) {
                1 -> Color.rgb(245, 235, 211)
                2 -> Color.rgb(26, 27, 30)
                else -> Color.WHITE
            })
            val normal = textPaint()
            val bold = textPaint(bold = true)
            lines.forEachIndexed { index, line ->
                canvas.drawText(line.text, 90f, 100f + index * lineHeight, if (line.bold) bold else normal)
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
