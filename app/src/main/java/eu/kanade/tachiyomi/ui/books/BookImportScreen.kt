package eu.kanade.tachiyomi.ui.books

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

class BookImportScreen : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = viewModel<BookImportViewModel>()
        val state by model.state.collectAsState()
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(model::prepare)
        }
        Scaffold(topBar = {
            AppBar(title = stringResource(MR.strings.book_import), navigateUp = navigator::pop, scrollBehavior = it)
        }) { padding ->
            Column(
                modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(MR.strings.book_import_hint))
                Button(enabled = !state.busy, onClick = {
                    picker.launch(arrayOf("application/epub+zip", "application/octet-stream", "application/zip"))
                }) { Text(stringResource(MR.strings.book_import_choose)) }
                if (state.busy) {
                    CircularProgressIndicator()
                    Text(stringResource(MR.strings.book_import_working))
                }
                state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                state.metadata?.let { metadata ->
                    OutlinedTextField(
                        value = state.title, onValueChange = model::setTitle,
                        label = { Text(stringResource(MR.strings.book_import_title)) },
                        enabled = !state.busy && !state.finished, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.author, onValueChange = model::setAuthor,
                        label = { Text(stringResource(MR.strings.book_import_author)) },
                        enabled = !state.busy && !state.finished, modifier = Modifier.fillMaxWidth(),
                    )
                    metadata.language?.let { Text(stringResource(MR.strings.book_import_language, it)) }
                    metadata.series?.let { Text(stringResource(MR.strings.book_import_series, it)) }
                    metadata.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    if (!state.finished) {
                        Button(enabled = !state.busy && state.title.isNotBlank(), onClick = model::importBook) {
                            Text(stringResource(MR.strings.book_import_confirm))
                        }
                    }
                }
                if (state.finished) {
                    Text(stringResource(if (state.duplicate) MR.strings.book_import_duplicate else MR.strings.book_import_done))
                    Button(onClick = { navigator.push(BrowseSourceScreen(LocalSource.ID, null)) }) {
                        Text(stringResource(MR.strings.book_import_open_local))
                    }
                }
            }
        }
    }
}
