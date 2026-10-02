package eu.kanade.presentation.reader

import android.graphics.Typeface
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.kanade.tachiyomi.ui.reader.loader.EpubPageLoader
import eu.kanade.tachiyomi.ui.reader.loader.EpubTextMatch
import eu.kanade.tachiyomi.ui.reader.loader.EpubTextSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** Native selectable text on the primary screen; image readers remain available underneath. */
@Composable
internal fun EpubTextReader(
    loader: EpubPageLoader,
    initialPage: Int,
    pageCount: Int,
    onPageChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var page by remember(loader) { mutableIntStateOf(initialPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))) }
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<EpubTextMatch>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val appearance = loader.textAppearance
    val text = remember(loader, page) { loader.selectablePage(page) }
    val scroll = rememberScrollState()
    LaunchedEffect(page) { scroll.scrollTo(0) }
    LaunchedEffect(query, loader) {
        matches = emptyList()
        searching = query.isNotBlank()
        if (query.isNotBlank()) {
            delay(250)
            matches = withContext(Dispatchers.Default) { EpubTextSearch.find(loader.searchableText, query) }
        }
        searching = false
    }
    fun navigate(index: Int) {
        page = index.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        onPageChange(page)
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(MR.strings.epub_text_mode), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.epub_text_close)) }
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text(stringResource(MR.strings.epub_text_search)) }, modifier = Modifier.fillMaxWidth(),
                )
                if (query.isNotBlank()) {
                    Text(
                        if (searching) stringResource(MR.strings.epub_text_searching)
                        else stringResource(MR.strings.epub_text_results, matches.size),
                    )
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(matches, key = { it.offset }) { match ->
                            TextButton(onClick = {
                                loader.pageForOffset(match.offset)?.let(::navigate)
                                query = ""
                            }) { Text(match.snippet) }
                        }
                    }
                } else {
                    Surface(color = Color(loader.backgroundColor), modifier = Modifier.weight(1f).fillMaxWidth()) {
                        Column(modifier = Modifier.verticalScroll(scroll).padding(16.dp)) {
                            if (text == null) {
                                Text(stringResource(MR.strings.epub_text_image), color = if (appearance.theme >= 2) Color.White else Color.Black)
                            } else {
                                AndroidView(
                                    factory = { context -> TextView(context).apply { setTextIsSelectable(true) } },
                                    modifier = Modifier.fillMaxWidth(),
                                    update = { view ->
                                        if (view.tag != page) {
                                            view.text = text
                                            view.tag = page
                                        }
                                        view.textSize = appearance.size.coerceIn(26, 64) / 2f
                                        view.setTextColor(if (appearance.theme >= 2) android.graphics.Color.LTGRAY else android.graphics.Color.DKGRAY)
                                        view.typeface = when (appearance.font) {
                                            1 -> Typeface.SANS_SERIF
                                            2 -> Typeface.MONOSPACE
                                            else -> Typeface.SERIF
                                        }
                                        view.setLineSpacing(0f, appearance.lineSpacing.coerceIn(110, 200) / 100f)
                                    },
                                )
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = page > 0, onClick = { navigate(page - 1) }) {
                        Text(stringResource(MR.strings.epub_text_previous))
                    }
                    Text(stringResource(MR.strings.epub_text_page, page + 1, pageCount))
                    TextButton(enabled = page + 1 < pageCount, onClick = { navigate(page + 1) }) {
                        Text(stringResource(MR.strings.epub_text_next))
                    }
                }
            }
        }
    }
}
