package eu.kanade.tachiyomi.ui.books

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.source.local.LocalSource

class BookSourcesScreen : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = viewModel<BookSourcesViewModel>()
        val state by model.state.collectAsState()
        var url by remember { mutableStateOf("") }
        var query by remember { mutableStateOf("") }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(model::importSource)
        }
        Scaffold(topBar = {
            AppBar(title = stringResource(MR.strings.book_sources), navigateUp = navigator::pop, scrollBehavior = it)
        }) { padding ->
            LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(MR.strings.book_sources_hint))
                        state.sources.forEach { source ->
                            TextButton(enabled = !state.busy, onClick = {
                                url = source.catalogUrl
                                if (url.isNotBlank()) model.open(url)
                            }) { Text(source.name) }
                        }
                        TextButton(enabled = !state.busy, onClick = { picker.launch(arrayOf("application/json", "text/plain")) }) {
                            Text(stringResource(MR.strings.book_sources_install))
                        }
                        OutlinedTextField(value = url, onValueChange = { url = it }, singleLine = true,
                            label = { Text(stringResource(MR.strings.book_sources_url)) }, modifier = Modifier.fillMaxWidth())
                        TextButton(enabled = !state.busy && url.isNotBlank(), onClick = { model.open(url) }) {
                            Text(stringResource(MR.strings.book_sources_open))
                        }
                        TextButton(enabled = !state.busy && safeBookUrl(url) != null, onClick = { model.saveCatalogue(url) }) {
                            Text(stringResource(MR.strings.book_sources_save))
                        }
                        if (state.catalog?.searchTemplate != null) {
                            OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                                label = { Text(stringResource(MR.strings.book_sources_search)) }, modifier = Modifier.fillMaxWidth())
                            TextButton(enabled = !state.busy && query.isNotBlank(), onClick = { model.search(query) }) {
                                Text(stringResource(MR.strings.book_sources_search))
                            }
                        }
                        if (state.busy) CircularProgressIndicator()
                        if (state.error) Text(stringResource(MR.strings.book_sources_error), color = MaterialTheme.colorScheme.error)
                        state.message?.let {
                            Text(stringResource(it))
                            TextButton(onClick = { navigator.push(BrowseSourceScreen(LocalSource.ID, null)) }) {
                                Text(stringResource(MR.strings.book_import_open_local))
                            }
                        }
                        Text(state.catalog?.title.orEmpty(), style = MaterialTheme.typography.titleLarge)
                        Row {
                            TextButton(enabled = state.canGoBack && !state.busy, onClick = model::back) {
                                Text(stringResource(MR.strings.book_sources_back))
                            }
                            state.catalog?.next?.let { next ->
                                TextButton(enabled = !state.busy, onClick = { model.open(next) }) {
                                    Text(stringResource(MR.strings.epub_text_next))
                                }
                            }
                        }
                    }
                }
                itemsIndexed(state.catalog?.entries.orEmpty()) { _, entry ->
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(entry.title, style = MaterialTheme.typography.titleMedium)
                        if (entry.author.isNotBlank()) Text(entry.author)
                        if (entry.summary.isNotBlank()) Text(entry.summary, maxLines = 4)
                        TextButton(enabled = !state.busy, onClick = {
                            if (entry.epub) model.acquire(entry) else model.open(entry.url)
                        }) { Text(stringResource(if (entry.epub) MR.strings.book_sources_download else MR.strings.book_sources_open)) }
                    }
                }
            }
        }
    }
}
