package eu.kanade.tachiyomi.ui.reader.loader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.StyleSpan
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.EpubChapterLink
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.EpubAppearance
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import mihon.core.archive.EpubReader
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Renders styled EPUB paragraphs into the existing reader's page and dual-screen pipeline. */
internal class EpubPageLoader(private val reader: EpubReader, bookKey: String) : PageLoader() {
    override var isLocal: Boolean = true
    private val stableBookKey = try {
        reader.getBookIdentity()
    } catch (e: Exception) {
        reader.close()
        throw e
    }
    private val rootPreferences = Injekt.get<ReaderPreferences>().also {
        it.migrateEpubBook(bookKey, stableBookKey)
    }
    val preferences = rootPreferences.forEpubBook(stableBookKey)
    val bookmarks = preferences.epubBookmarks(stableBookKey)
    private val savedPosition = preferences.epubReadingPosition(stableBookKey)
    private var layout = newLayout()
    val contents: List<EpubChapterLink> get() = layout.contents
    val backgroundColor: Int get() = layout.background
    val textAppearance: EpubAppearance get() = layout.appearance
    val searchableText: String get() = layout.searchableText
    fun selectablePage(index: Int): CharSequence? = layout.selectablePage(index)

    fun offsetForPage(index: Int): Long = layout.offsetForPage(index)
    fun pageForOffset(offset: Long): Int? = layout.pageForOffset(offset)

    private fun newLayout() = EpubLayout(reader, preferences) { isRecycled }

    override suspend fun getPages(): List<ReaderPage> = layout.getPages().also {
        if (it.isNotEmpty()) layout.appearance.applyTo(preferences)
    }

    // Build off-thread without mutating the layout still displayed by the reader.
    suspend fun prepareLayout(pageIndex: Int): EpubReflow {
        val offset = layout.offsetForPage(pageIndex)
        val next = newLayout()
        val pages = next.getPages()
        check(pages.isNotEmpty()) { "EPUB contains no readable pages" }
        return EpubReflow(pages, next.pageForOffset(offset) ?: 0) {
            layout = next
            // Materialise every field so later changes to defaults cannot alter this book.
            next.appearance.applyTo(preferences)
        }
    }

    fun savePosition(pageIndex: Int) {
        savedPosition.set(layout.offsetForPage(pageIndex).toString())
    }

    fun restoredPageIndex(): Int? = savedPosition.get().toLongOrNull()?.let(layout::pageForOffset)

    override suspend fun loadPage(page: ReaderPage) {
        check(!isRecycled)
    }

    override fun recycle() {
        super.recycle()
        reader.close()
    }
}

internal data class EpubReflow(val pages: List<ReaderPage>, val pageIndex: Int, val commit: () -> Unit)

private class EpubLayout(
    private val reader: EpubReader,
    private val preferences: ReaderPreferences,
    private val isRecycled: () -> Boolean,
) {
    val appearance = EpubAppearance.capture(preferences)
    private val pageOffsets = mutableListOf<Long>()
    private var renderedPages: List<RenderPage> = emptyList()
    var searchableText: String = ""
        private set

    fun selectablePage(index: Int): CharSequence? {
        val page = renderedPages.getOrNull(index) as? RenderPage.Text ?: return null
        return SpannableStringBuilder().apply {
            page.slices.forEachIndexed { sliceIndex, slice ->
                if (sliceIndex > 0) append("\n\n")
                val start = length
                append(slice.layout.text.subSequence(
                    slice.layout.getLineStart(slice.firstLine), slice.layout.getLineEnd(slice.endLine - 1),
                ))
                if (slice.layout.paint.typeface?.isBold == true) {
                    setSpan(StyleSpan(Typeface.BOLD), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
    }
    private val fontSize = preferences.epubFontSize.get().coerceIn(26, 64).toFloat()
    private val margin = preferences.epubMargin.get().coerceIn(40, 140)
    private val lineSpacing = preferences.epubLineSpacing.get().coerceIn(110, 200) / 100f
    private val paragraphSpacing = preferences.epubParagraphSpacing.get().coerceIn(0, 100) / 100f
    private val theme = preferences.epubTheme.get()
    private val font = when (preferences.epubFont.get()) {
        1 -> Typeface.SANS_SERIF
        2 -> Typeface.MONOSPACE
        else -> Typeface.SERIF
    }
    private val compact = preferences.epubCompactPages.get()
    private val topMargin = if (compact) 0 else 80
    private val bottomMargin = if (compact) 0 else 80
    private val foreground = when (theme) {
        2, 3 -> Color.rgb(225, 225, 225)
        else -> Color.rgb(35, 32, 29)
    }
    val background = when (theme) {
        1 -> Color.rgb(245, 235, 211)
        2 -> Color.rgb(26, 27, 30)
        3 -> Color.BLACK
        else -> Color.WHITE
    }

    var contents: List<EpubChapterLink> = emptyList()
        private set

    private data class Slice(val layout: StaticLayout, val firstLine: Int, val endLine: Int, val top: Int)
    private sealed interface RenderPage {
        data class Text(val slices: List<Slice>, val height: Int) : RenderPage
        data class Image(val path: String) : RenderPage
    }

    suspend fun getPages(): List<ReaderPage> {
        val pages = mutableListOf<RenderPage>()
        val slices = mutableListOf<Slice>()
        val targets = mutableMapOf<String, Int>()
        val pendingTargets = mutableListOf<String>()
        val fallbackContents = mutableListOf<EpubChapterLink>()
        val sourceText = StringBuilder()
        var sourceOffset = 0L
        var pageStart = 0L
        pageOffsets.clear()
        var y = topMargin

        fun bindTargets() {
            pendingTargets.forEach { targets.putIfAbsent(it, pages.size) }
            pendingTargets.clear()
        }
        fun flushPage() {
            if (slices.isNotEmpty()) {
                pageOffsets.add(pageStart)
                pages.add(RenderPage.Text(slices.toList(), if (compact) y.coerceAtLeast(1) else PAGE_HEIGHT))
                slices.clear()
            }
            y = topMargin
        }

        reader.getContent().forEach { item ->
            coroutineContext.ensureActive()
            when (item) {
                EpubReader.Content.Section -> if (!compact) flushPage()
                is EpubReader.Content.Anchor -> pendingTargets.add(item.target)
                is EpubReader.Content.Image -> {
                    flushPage()
                    bindTargets()
                    pageOffsets.add(sourceOffset)
                    sourceText.append('\n')
                    sourceOffset++
                    pages.add(RenderPage.Image(item.path))
                }
                is EpubReader.Content.Text -> {
                    val text = SpannableString(item.value)
                    item.styles.forEach { style ->
                        val flags = (if (style.bold) Typeface.BOLD else 0) or (if (style.italic) Typeface.ITALIC else 0)
                        text.setSpan(StyleSpan(flags), style.start, style.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    val layout = StaticLayout.Builder.obtain(
                        text, 0, text.length, textPaint(item.heading), PAGE_WIDTH - margin * 2,
                    )
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                        .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
                        .setIncludePad(false)
                        .setLineSpacing(0f, lineSpacing)
                        .build()
                    val gap = if (slices.isEmpty()) 0 else (fontSize * paragraphSpacing).toInt()
                    // Keep a heading with at least one following body line whenever it fits on a page.
                    val required = if (item.heading) {
                        layout.height + (fontSize * lineSpacing).toInt()
                    } else {
                        layout.getLineBottom(0)
                    }
                    if (slices.isNotEmpty() && y + gap + required > PAGE_HEIGHT - bottomMargin) flushPage()
                    if (slices.isNotEmpty()) y += gap
                    bindTargets()
                    if (item.heading) fallbackContents.add(EpubChapterLink(item.value, pages.size))
                    var firstLine = 0
                    while (firstLine < layout.lineCount) {
                        coroutineContext.ensureActive()
                        val startY = layout.getLineTop(firstLine)
                        var endLine = firstLine
                        while (
                            endLine < layout.lineCount &&
                            y + layout.getLineBottom(endLine) - startY <= PAGE_HEIGHT - bottomMargin
                        ) {
                            endLine++
                        }
                        if (endLine == firstLine) {
                            check(slices.isNotEmpty()) { "EPUB text line exceeds page height" }
                            flushPage()
                            continue
                        }
                        if (slices.isEmpty()) pageStart = sourceOffset + layout.getLineStart(firstLine)
                        slices.add(Slice(layout, firstLine, endLine, y))
                        y += layout.getLineBottom(endLine - 1) - startY
                        firstLine = endLine
                        if (firstLine < layout.lineCount) flushPage()
                    }
                    sourceText.append(item.value).append('\n')
                    sourceOffset += item.value.length + 1
                }
            }
        }
        flushPage()
        val links = reader.getTableOfContents().mapNotNull { entry ->
            val page = targets[entry.target] ?: targets[entry.target.substringBefore('#')] ?: return@mapNotNull null
            EpubChapterLink(entry.title, page, entry.depth)
        }
        contents = links.ifEmpty { fallbackContents }.distinctBy { it.title to it.pageIndex }

        renderedPages = pages.toList()
        searchableText = sourceText.toString()
        return pages.mapIndexed { index, item ->
            ReaderPage(index).apply {
                stream = {
                    check(!isRecycled())
                    when (item) {
                        is RenderPage.Image -> reader.getInputStream(item.path)
                            ?: throw java.io.IOException("EPUB image is missing: ${item.path}")
                        is RenderPage.Text -> ByteArrayInputStream(renderText(item))
                    }
                }
                status = Page.State.Ready
            }
        }
    }

    fun offsetForPage(index: Int): Long = pageOffsets.getOrNull(index) ?: 0L

    fun pageForOffset(offset: Long): Int? = EpubReadingPosition.pageForOffset(pageOffsets, offset)

    private fun textPaint(heading: Boolean = false) = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = foreground
        textSize = if (heading) fontSize * 1.2f else fontSize
        typeface = Typeface.create(font, if (heading) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun renderText(page: RenderPage.Text): ByteArray {
        val bitmap = Bitmap.createBitmap(PAGE_WIDTH, page.height, Bitmap.Config.RGB_565)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(background)
            page.slices.forEach { slice ->
                val top = slice.layout.getLineTop(slice.firstLine)
                val height = slice.layout.getLineBottom(slice.endLine - 1) - top
                canvas.save()
                canvas.clipRect(margin, slice.top, PAGE_WIDTH - margin, slice.top + height)
                canvas.translate(margin.toFloat(), (slice.top - top).toFloat())
                slice.layout.draw(canvas)
                canvas.restore()
            }
            return ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val PAGE_WIDTH = 1080
        const val PAGE_HEIGHT = 1600
    }
}
