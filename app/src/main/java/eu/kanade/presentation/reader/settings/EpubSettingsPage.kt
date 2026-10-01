package eu.kanade.presentation.reader.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.ui.reader.model.EpubChapterLink
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsViewModel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

@Composable
internal fun ColumnScope.EpubSettingsPage(
    viewModel: ReaderSettingsViewModel,
    contents: List<EpubChapterLink>,
    onSelectPage: (Int) -> Unit,
) {
    val prefs = viewModel.preferences
    val font by prefs.epubFont.collectAsState()
    val size by prefs.epubFontSize.collectAsState()
    val theme by prefs.epubTheme.collectAsState()
    val lineSpacing by prefs.epubLineSpacing.collectAsState()
    val paragraphSpacing by prefs.epubParagraphSpacing.collectAsState()
    val margin by prefs.epubMargin.collectAsState()

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
    HeadingItem(MR.strings.epub_contents)
    if (contents.isEmpty()) {
        Text(stringResource(MR.strings.epub_no_contents), modifier = Modifier.padding(horizontal = 24.dp))
    }
    contents.forEach { entry ->
        TextButton(
            modifier = Modifier.fillMaxWidth().padding(start = (16 + entry.depth.coerceIn(0, 4) * 12).dp, end = 16.dp),
            onClick = { onSelectPage(entry.pageIndex) },
        ) {
            Text(entry.title, modifier = Modifier.weight(1f))
            Text(
                stringResource(MR.strings.epub_contents_page, entry.pageIndex + 1),
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
