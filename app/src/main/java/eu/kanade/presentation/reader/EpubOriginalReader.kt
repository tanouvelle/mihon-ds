package eu.kanade.presentation.reader

import android.graphics.Color
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.kanade.tachiyomi.ui.reader.loader.EpubPageLoader
import java.io.ByteArrayInputStream
import kotlin.math.roundToInt
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** Static publisher HTML/CSS, without extracting text or executing book scripts. */
@Composable
internal fun EpubOriginalReader(
    loader: EpubPageLoader,
    initialPage: Int,
    onDocumentChange: (Int) -> Unit,
    onProgress: (Int, Float) -> Unit,
    onNavigationChange: (navigation: ((Boolean) -> Unit)?) -> Unit,
    onTextMode: () -> Unit,
    onDismiss: () -> Unit,
) {
    val documents = remember(loader) { loader.originalDocuments }
    val resources = remember(loader) { loader.originalResources }
    val toc = remember(loader) { loader.originalContents }
    val saved = remember(loader) { loader.originalPosition.get().split('\n', limit = 2) }
    val savedIndex = documents.indexOfFirst { it.path == saved.firstOrNull() }
    var index by remember(loader) { mutableIntStateOf(
        if (savedIndex >= 0) savedIndex else loader.originalDocumentForPage(initialPage),
    ) }
    var ratio by remember(loader) { mutableStateOf(if (savedIndex >= 0) saved.getOrNull(1)?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f else 0f) }
    var showContents by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(documents.isNotEmpty()) }
    val context = LocalContext.current
    val browser = remember(loader) { WebView(context) }
    fun range(): Int = (browser.contentHeight * browser.scale - browser.height).roundToInt().coerceAtLeast(0)
    fun save() {
        documents.getOrNull(index)?.let {
            loader.originalPosition.set("${it.path}\n$ratio")
            onProgress(index, ratio)
        }
    }
    fun navigate(target: String, initialRatio: Float = 0f) {
        val path = target.substringBefore('#').replace('\\', '/')
        val next = documents.indexOfFirst { it.path == path }
        if (next < 0) return
        save()
        index = next
        ratio = initialRatio
        loadError = false
        onDocumentChange(next)
        browser.loadUrl(EpubResourceUrl.build(path, target.substringAfter('#', "")))
        showContents = false
    }
    fun turnPage(forward: Boolean) {
        if (loading) return
        if (forward) {
            if (!browser.pageDown(false) && index < documents.lastIndex) navigate(documents[index + 1].path)
        } else {
            if (!browser.pageUp(false) && index > 0) navigate(documents[index - 1].path, 1f)
        }
    }
    DisposableEffect(browser) {
        onNavigationChange { forward -> turnPage(forward) }
        browser.setBackgroundColor(Color.WHITE)
        browser.settings.apply {
            javaScriptEnabled = false
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        var restorePending = true
        var restoreTask: Runnable? = null
        val saveTask = Runnable { save() }
        browser.webViewClient = object : WebViewClient() {
            private fun missing() = WebResourceResponse("text/plain", "UTF-8", 404, "Not found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse {
                val path = request?.url?.toString()?.let(EpubResourceUrl::path) ?: return missing()
                val resource = resources[path] ?: return missing()
                if (resource.mimeType.contains("javascript", true)) return missing()
                return try {
                    val bytes = loader.originalResource(path) ?: return missing()
                    WebResourceResponse(resource.mimeType.ifBlank { "application/octet-stream" }, null, ByteArrayInputStream(bytes))
                } catch (_: Exception) { missing() }
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return true
                val path = EpubResourceUrl.path(url) ?: return true
                val next = documents.indexOfFirst { it.path == path }
                if (next < 0) return true
                // Same-document footnotes and anchors are handled natively by WebView.
                if (next != index) {
                    save()
                    index = next
                    ratio = 0f
                    onDocumentChange(next)
                }
                return false
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                restoreTask?.let(browser::removeCallbacks)
                restorePending = true
                loading = true
                loadError = false
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                if (loadError) return
                val targetRatio = ratio
                restoreTask?.let(browser::removeCallbacks)
                restoreTask = Runnable {
                    if (browser.url != url) return@Runnable
                    // Let native fragment navigation position footnotes and TOC anchors.
                    if (android.net.Uri.parse(url.orEmpty()).fragment.isNullOrEmpty()) {
                        browser.scrollTo(0, (range() * targetRatio).roundToInt())
                    }
                    restorePending = false
                    loading = false
                    ratio = if (range() == 0) 1f else (browser.scrollY.toFloat() / range()).coerceIn(0f, 1f)
                    save()
                }.also { browser.postDelayed(it, 250) }
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                if (request?.isForMainFrame == true) { loadError = true; loading = false }
            }
        }
        browser.setOnScrollChangeListener { _, _, y, _, _ ->
            if (!restorePending) {
                ratio = if (range() == 0) 1f else (y.toFloat() / range()).coerceIn(0f, 1f)
                browser.removeCallbacks(saveTask)
                browser.postDelayed(saveTask, 600)
            }
        }
        documents.getOrNull(index)?.let { browser.loadUrl(EpubResourceUrl.build(it.path)) }
        onDispose {
            onNavigationChange(null)
            browser.removeCallbacks(saveTask)
            restoreTask?.let(browser::removeCallbacks)
            save()
            browser.setOnScrollChangeListener(null)
            browser.stopLoading()
            browser.webViewClient = WebViewClient()
            browser.removeAllViews()
            browser.destroy()
        }
    }
    Dialog(onDismissRequest = { save(); onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.White, contentColor = androidx.compose.ui.graphics.Color.Black) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { showContents = !showContents }) { Text(stringResource(MR.strings.epub_contents)) }
                    TextButton(onClick = { save(); onTextMode() }) { Text(stringResource(MR.strings.epub_text_mode)) }
                    TextButton(onClick = { save(); onDismiss() }) { Text(stringResource(MR.strings.epub_original_close)) }
                }
                Text(stringResource(MR.strings.epub_original_section, (index + 1).coerceAtMost(documents.size), documents.size), modifier = Modifier.padding(horizontal = 16.dp))
                if (loading) androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (loadError || documents.isEmpty()) Text(stringResource(MR.strings.epub_original_error), color = MaterialTheme.colorScheme.error)
                if (showContents) {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        if (toc.isNotEmpty()) items(toc) { entry ->
                            TextButton(onClick = { navigate(entry.target) }) { Text(entry.title) }
                        } else items(documents) { document ->
                            TextButton(onClick = { navigate(document.path) }) { Text(document.title) }
                        }
                    }
                } else AndroidView(factory = { browser }, modifier = Modifier.weight(1f).fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = !loading && (index > 0 || ratio > 0f), onClick = {
                        turnPage(false)
                    }) { Text(stringResource(MR.strings.epub_original_previous)) }
                    TextButton(enabled = !loading && (index < documents.lastIndex || ratio < 1f), onClick = {
                        turnPage(true)
                    }) { Text(stringResource(MR.strings.epub_original_next)) }
                }
            }
        }
    }
}
