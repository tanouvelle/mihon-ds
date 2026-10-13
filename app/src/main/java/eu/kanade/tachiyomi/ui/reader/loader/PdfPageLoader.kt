package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import mihon.core.archive.PdfBookReader

internal class PdfPageLoader(private val reader: PdfBookReader) : PageLoader() {
    override var isLocal = true
    override suspend fun getPages(): List<ReaderPage> = List(reader.pageCount) { index ->
        ReaderPage(index).apply {
            stream = { reader.pageStream(index) }
            status = Page.State.Ready
        }
    }
    override fun recycle() {
        super.recycle()
        reader.close()
    }
}
