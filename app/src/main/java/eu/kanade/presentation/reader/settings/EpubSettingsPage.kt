package eu.kanade.presentation.reader.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.ui.reader.model.EpubBookmark
import eu.kanade.tachiyomi.ui.reader.model.EpubChapterLink
import eu.kanade.tachiyomi.ui.reader.model.EpubReadingProgress
import eu.kanade.tachiyomi.ui.reader.setting.EpubAppearance
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

@Composable
internal fun ColumnScope.EpubSettingsPage(
    prefs: ReaderPreferences,
    onApplyLayout: () -> Unit,
    onOpenText: () -> Unit,
) {
    TextButton(onClick = onOpenText, modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(stringResource(MR.strings.epub_text_mode))
    }
    Text(stringResource(MR.strings.epub_text_hint), modifier = Modifier.padding(horizontal = 24.dp))
    val font by prefs.epubFont.collectAsState()
    val size by prefs.epubFontSize.collectAsState()
    val theme by prefs.epubTheme.collectAsState()
    val lineSpacing by prefs.epubLineSpacing.collectAsState()
    val paragraphSpacing by prefs.epubParagraphSpacing.collectAsState()
    val margin by prefs.epubMargin.collectAsState()

    TextButton(onClick = onApplyLayout, modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(stringResource(MR.strings.epub_apply))
    }
    Text(
        stringResource(MR.strings.epub_book_appearance_hint),
        modifier = Modifier.padding(horizontal = 24.dp),
        style = MaterialTheme.typography.bodySmall,
    )
    HeadingItem(MR.strings.epub_presets)
    var savedNotice by remember { mutableStateOf(false) }
    listOf(false, true).forEach { night ->
        Row(modifier = Modifier.padding(horizontal = 16.dp)) {
            TextButton(onClick = {
                val preset = EpubAppearance.decode(prefs.epubPreset(night).get())
                    ?: EpubAppearance(theme = if (night) 2 else 1)
                preset.applyTo(prefs)
                savedNotice = false
            }) {
                Text(stringResource(if (night) MR.strings.epub_load_night else MR.strings.epub_load_day))
            }
            TextButton(onClick = {
                prefs.epubPreset(night).set(EpubAppearance.capture(prefs).encode())
                savedNotice = true
            }) {
                Text(stringResource(if (night) MR.strings.epub_save_night else MR.strings.epub_save_day))
            }
        }
    }
    TextButton(onClick = {
        prefs.saveEpubDefaults()
        savedNotice = true
    }, modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(stringResource(MR.strings.epub_save_defaults))
    }
    if (savedNotice) {
        Text(stringResource(MR.strings.epub_saved), modifier = Modifier.padding(horizontal = 24.dp))
    }
    HeadingItem(MR.strings.epub_preview_label)
    Surface(
        modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = when (theme) {
            1 -> Color(0xFFF5EBD3)
            2 -> Color(0xFF1A1B1E)
            3 -> Color.Black
            else -> Color.White
        },
    ) {
        Text(
            text = stringResource(MR.strings.epub_preview),
            modifier = Modifier.padding(16.dp),
            style = TextStyle(
                color = if (theme in 2..3) Color(0xFFE1E1E1) else Color(0xFF23201D),
                fontFamily = when (font) {
                    1 -> FontFamily.SansSerif
                    2 -> FontFamily.Monospace
                    else -> FontFamily.Serif
                },
                fontSize = (size / 2f).sp,
                lineHeight = (size / 2f * lineSpacing / 100f).sp,
            ),
        )
    }
    Text(
        stringResource(MR.strings.epub_reopen_hint),
        modifier = Modifier.padding(24.dp),
        style = MaterialTheme.typography.bodySmall,
    )
    SettingsChipRow(MR.strings.epub_font) {
        listOf(
            MR.strings.epub_font_serif, MR.strings.epub_font_sans, MR.strings.epub_font_mono,
        ).forEachIndexed { index, label ->
            FilterChip(
                selected = font == index,
                onClick = { prefs.epubFont.set(index) },
                label = { Text(stringResource(label)) },
            )
        }
    }
    SliderItem(
        label = stringResource(MR.strings.epub_font_size),
        value = size,
        valueRange = 26..64,
        steps = 18,
        onChange = prefs.epubFontSize::set,
    )
    SettingsChipRow(MR.strings.epub_page_colour) {
        listOf(
            MR.strings.epub_theme_paper, MR.strings.epub_theme_sepia,
            MR.strings.epub_theme_night, MR.strings.epub_theme_black,
        ).forEachIndexed { index, label ->
            FilterChip(
                selected = theme == index,
                onClick = { prefs.epubTheme.set(index) },
                label = { Text(stringResource(label)) },
            )
        }
    }
    HeadingItem(MR.strings.epub_layout)
    SliderItem(
        label = stringResource(MR.strings.epub_line_spacing),
        value = lineSpacing,
        valueRange = 110..200,
        steps = 8,
        onChange = prefs.epubLineSpacing::set,
    )
    SliderItem(
        label = stringResource(MR.strings.epub_paragraph_spacing),
        value = paragraphSpacing,
        valueRange = 0..100,
        steps = 3,
        onChange = prefs.epubParagraphSpacing::set,
    )
    SliderItem(
        label = stringResource(MR.strings.epub_margins),
        value = margin,
        valueRange = 40..140,
        steps = 4,
        onChange = prefs.epubMargin::set,
    )
    CheckboxItem(label = stringResource(MR.strings.epub_compact_pages), pref = prefs.epubCompactPages)
    Text(
        stringResource(MR.strings.epub_continuous_hint),
        modifier = Modifier.padding(horizontal = 24.dp),
        style = MaterialTheme.typography.bodySmall,
    )
    TextButton(
        modifier = Modifier.padding(horizontal = 16.dp),
        onClick = {
            prefs.epubFont.set(0)
            prefs.epubFontSize.set(38)
            prefs.epubTheme.set(0)
            prefs.epubLineSpacing.set(150)
            prefs.epubParagraphSpacing.set(50)
            prefs.epubMargin.set(80)
            prefs.epubCompactPages.set(false)
        },
    ) { Text(stringResource(MR.strings.epub_reset)) }
    CheckboxItem(label = stringResource(MR.strings.epub_reduce_flashing), pref = prefs.epubReduceFlashing)
    CheckboxItem(label = stringResource(MR.strings.epub_animate_pages), pref = prefs.epubAnimatePages)
    TextButton(onClick = onApplyLayout, modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(stringResource(MR.strings.epub_apply))
    }
}

@Composable
internal fun ColumnScope.EpubContentsPage(
    contents: List<EpubChapterLink>,
    currentPage: Int,
    pageCount: Int,
    bookmarks: List<EpubBookmark>,
    onAddBookmark: () -> Unit,
    onRemoveBookmark: (Long) -> Unit,
    canReturn: Boolean,
    onReturn: () -> Unit,
    onSelectPage: (Int) -> Unit,
) {
    val progress = EpubReadingProgress.calculate(contents, currentPage, pageCount)
    progress?.let {
        Text(
            it.chapter?.title ?: stringResource(MR.strings.epub_front_matter),
            modifier = Modifier.padding(horizontal = 24.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(MR.strings.epub_reading_progress, it.page, it.pages, it.percent),
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
    if (canReturn) {
        TextButton(onClick = onReturn, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(MR.strings.epub_return))
        }
    }
    HeadingItem(MR.strings.epub_bookmarks)
    TextButton(
        onClick = onAddBookmark,
        enabled = pageCount > 0 && bookmarks.none { it.pageIndex == currentPage },
        modifier = Modifier.padding(horizontal = 16.dp),
    ) { Text(stringResource(MR.strings.epub_add_bookmark)) }
    if (bookmarks.isEmpty()) {
        Text(stringResource(MR.strings.epub_no_bookmarks), modifier = Modifier.padding(horizontal = 24.dp))
    }
    bookmarks.forEach { bookmark ->
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            TextButton(onClick = { onSelectPage(bookmark.pageIndex) }, modifier = Modifier.weight(1f)) {
                val title = EpubReadingProgress.calculate(contents, bookmark.pageIndex, pageCount)?.chapter?.title
                    ?: stringResource(MR.strings.epub_front_matter)
                Text("$title · " + stringResource(MR.strings.epub_contents_page, bookmark.pageIndex + 1))
            }
            TextButton(onClick = { onRemoveBookmark(bookmark.offset) }) {
                Text(stringResource(MR.strings.epub_remove_bookmark))
            }
        }
    }
    HeadingItem(MR.strings.epub_contents)
    if (contents.isEmpty()) {
        Text(stringResource(MR.strings.epub_no_contents), modifier = Modifier.padding(horizontal = 24.dp))
    }
    contents.forEach { entry ->
        val selected = entry == progress?.chapter
        TextButton(
            modifier = Modifier.fillMaxWidth().padding(start = (16 + entry.depth.coerceIn(0, 4) * 12).dp, end = 16.dp),
            onClick = { onSelectPage(entry.pageIndex) },
        ) {
            Text(
                entry.title,
                modifier = Modifier.weight(1f),
                style = if (selected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (selected) stringResource(MR.strings.epub_current_chapter)
                else stringResource(MR.strings.epub_contents_page, entry.pageIndex + 1),
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
