package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import androidx.core.view.isVisible
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.databinding.ReaderErrorBinding
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.loader.EpubPageLoader
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.panel.PanelDetectionImage
import eu.kanade.tachiyomi.ui.reader.panel.PanelDetectionInput
import eu.kanade.tachiyomi.ui.reader.panel.PanelPageKey
import eu.kanade.tachiyomi.ui.reader.panel.PanelPageRenderVariant
import eu.kanade.tachiyomi.ui.reader.panel.panelPageKey
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderProgressIndicator
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.widget.ViewPagerAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import okio.Buffer
import okio.BufferedSource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR

private data class ProcessedPageImage(
    val source: BufferedSource,
    val renderVariant: PanelPageRenderVariant,
)

private data class LoadedPageImage(
    val source: BufferedSource,
    val detectionInput: PanelDetectionInput?,
    val detectionUnavailableKey: PanelPageKey?,
    val isAnimated: Boolean,
    val background: Drawable?,
)

/**
 * View of the ViewPager that contains a page of a chapter.
 */
@SuppressLint("ViewConstructor")
class PagerPageHolder(
    readerThemedContext: Context,
    val viewer: PagerViewer,
    val page: ReaderPage,
) : ReaderPageImageView(readerThemedContext), ViewPagerAdapter.PositionableView {

    /**
     * Item that identifies this view. Needed by the adapter to not recreate views.
     */
    override val item
        get() = page

    /**
     * Loading progress bar to indicate the current progress.
     */
    private var progressIndicator: ReaderProgressIndicator? = null // = ReaderProgressIndicator(readerThemedContext)

    /**
     * Error layout to show when the image fails to load.
     */
    private var errorLayout: ReaderErrorBinding? = null

    private val scope = MainScope()

    /**
     * Job for loading the page and processing changes to the page's status.
     */
    private var loadJob: Job? = null

    private val epubBackground = (page.chapter.pageLoader as? EpubPageLoader)?.backgroundColor?.let(::ColorDrawable)

    init {
        epubBackground?.let {
            background = it
            pageBackground = it
        }
        loadJob = scope.launch { loadPageAndProcessStatus() }
    }

    /**
     * Called when this view is detached from the window. Unsubscribes any active subscription.
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        loadJob?.cancel()
        loadJob = null
    }

    private fun initProgressIndicator() {
        if (progressIndicator == null) {
            progressIndicator = ReaderProgressIndicator(context)
            addView(progressIndicator)
        }
    }

    /**
     * Loads the page and processes changes to the page's status.
     *
     * Returns immediately if the page has no PageLoader.
     * Otherwise, this function does not return. It will continue to process status changes until
     * the Job is cancelled.
     */
    private suspend fun loadPageAndProcessStatus() {
        val loader = page.chapter.pageLoader ?: return

        supervisorScope {
            launchIO {
                loader.loadPage(page)
            }
            page.statusFlow.collectLatest { state ->
                when (state) {
                    Page.State.Queue -> setQueued()
                    Page.State.LoadPage -> setLoading()
                    Page.State.DownloadImage -> {
                        setDownloading()
                        page.progressFlow.collectLatest { value ->
                            progressIndicator?.setProgress(value)
                        }
                    }
                    Page.State.Ready -> setImage()
                    is Page.State.Error -> setError(state.error)
                }
            }
        }
    }

    /**
     * Called when the page is queued.
     */
    private fun setQueued() {
        initProgressIndicator()
        progressIndicator?.show()
        removeErrorLayout()
    }

    /**
     * Called when the page is loading.
     */
    private fun setLoading() {
        initProgressIndicator()
        progressIndicator?.show()
        removeErrorLayout()
    }

    /**
     * Called when the page is downloading.
     */
    private fun setDownloading() {
        initProgressIndicator()
        progressIndicator?.show()
        removeErrorLayout()
    }

    /**
     * Called when the page is ready.
     */
    private suspend fun setImage() {
        progressIndicator?.setProgress(0)

        val streamFn = page.stream ?: return

        try {
            val loadedImage = withIOContext {
                val processedImage = streamFn().use { process(item, Buffer().readFrom(it)) }
                val source = processedImage.source
                val isAnimated = ImageUtil.isAnimatedAndSupported(source)
                val background = if (!isAnimated && viewer.config.automaticBackground) {
                    ImageUtil.chooseBackground(context, source.peek().inputStream())
                } else {
                    null
                }
                val shouldDetectPanels = !isAnimated && viewer.activity.isPanelReadingActive()
                val detectionKey = if (shouldDetectPanels) {
                    page.panelPageKey(processedImage.renderVariant)
                } else {
                    null
                }
                val detectionInput = detectionKey?.let { key ->
                    val imageSize = ImageUtil.extractImageSize(source)
                    PanelDetectionImage
                        .fromSourceOrNull(source)
                        ?.let { image ->
                            PanelDetectionInput(
                                key = key,
                                imageWidth = imageSize.width,
                                imageHeight = imageSize.height,
                                image = image,
                            )
                        }
                }
                LoadedPageImage(
                    source = source,
                    detectionInput = detectionInput,
                    detectionUnavailableKey = detectionKey.takeIf { detectionInput == null },
                    isAnimated = isAnimated,
                    background = background,
                )
            }
            withUIContext {
                if (!isAttachedToWindow) return@withUIContext
                loadedImage.detectionInput?.let { detectionInput ->
                    viewer.activity.panelReadingController.onPageImageReady(detectionInput)
                }
                loadedImage.detectionUnavailableKey?.let { key ->
                    viewer.activity.panelReadingController.onPageDetectionUnavailable(key)
                }
                setImage(
                    loadedImage.source,
                    loadedImage.isAnimated,
                    Config(
                        zoomDuration = viewer.config.doubleTapAnimDuration,
                        panelTransitionDuration = viewer.config.panelReadingTransitionDuration,
                        panelFocusEffect = viewer.config.panelReadingFocusEffect,
                        panelFocusStrength = viewer.config.panelReadingFocusStrength,
                        panelPrimaryOverlay = viewer.config.panelReadingPrimaryOverlay,
                        minimumScaleType = viewer.config.imageScaleType,
                        // Panel bounds are detected against the original file, and the companion
                        // deliberately renders uncropped for the same reason (see
                        // ReaderPresentation). Cropping here would leave this view in a different
                        // coordinate space to the bounds -- on a 1200x1752 page trimmed to
                        // 1115x1532 that put the focused panel ~150px off centre, growing toward
                        // the bottom of the page, while the companion's highlight stayed correct.
                        cropBorders = viewer.config.imageCropBorders &&
                            !viewer.activity.isPanelReadingActive(),
                        zoomStartPosition = viewer.config.imageZoomType,
                        landscapeZoom = viewer.config.landscapeZoom,
                    ),
                )
                if (!loadedImage.isAnimated) {
                    pageBackground = epubBackground ?: loadedImage.background
                }
                removeErrorLayout()
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
            withUIContext {
                setError(e)
            }
        }
    }

    private fun process(page: ReaderPage, imageSource: BufferedSource): ProcessedPageImage {
        if (viewer.config.dualPageRotateToFit) {
            return rotateDualPage(imageSource)
        }

        if (!viewer.config.dualPageSplit) {
            return ProcessedPageImage(imageSource, PanelPageRenderVariant.FULL)
        }

        if (page is InsertPage) {
            return splitInHalf(imageSource)
        }

        val isDoublePage = ImageUtil.isWideImage(imageSource)
        if (!isDoublePage) {
            return ProcessedPageImage(imageSource, PanelPageRenderVariant.FULL)
        }

        onPageSplit(page)

        return splitInHalf(imageSource)
    }

    private fun rotateDualPage(imageSource: BufferedSource): ProcessedPageImage {
        val isDoublePage = ImageUtil.isWideImage(imageSource)
        return if (isDoublePage) {
            val rotation = if (viewer.config.dualPageRotateToFitInvert) -90f else 90f
            val renderVariant = if (viewer.config.dualPageRotateToFitInvert) {
                PanelPageRenderVariant.ROTATE_NEGATIVE_90
            } else {
                PanelPageRenderVariant.ROTATE_90
            }
            ProcessedPageImage(ImageUtil.rotateImage(imageSource, rotation), renderVariant)
        } else {
            ProcessedPageImage(imageSource, PanelPageRenderVariant.FULL)
        }
    }

    private fun splitInHalf(imageSource: BufferedSource): ProcessedPageImage {
        var side = when {
            viewer is L2RPagerViewer && page is InsertPage -> ImageUtil.Side.RIGHT
            viewer !is L2RPagerViewer && page is InsertPage -> ImageUtil.Side.LEFT
            viewer is L2RPagerViewer && page !is InsertPage -> ImageUtil.Side.LEFT
            viewer !is L2RPagerViewer && page !is InsertPage -> ImageUtil.Side.RIGHT
            else -> error("We should choose a side!")
        }

        if (viewer.config.dualPageInvert) {
            side = when (side) {
                ImageUtil.Side.RIGHT -> ImageUtil.Side.LEFT
                ImageUtil.Side.LEFT -> ImageUtil.Side.RIGHT
            }
        }

        val renderVariant = when (side) {
            ImageUtil.Side.LEFT -> PanelPageRenderVariant.SPLIT_LEFT
            ImageUtil.Side.RIGHT -> PanelPageRenderVariant.SPLIT_RIGHT
        }

        return ProcessedPageImage(ImageUtil.splitInHalf(imageSource, side), renderVariant)
    }

    private fun onPageSplit(page: ReaderPage) {
        val newPage = InsertPage(page)
        viewer.onPageSplit(page, newPage)
    }

    /**
     * Called when the page has an error.
     */
    private fun setError(error: Throwable?) {
        progressIndicator?.hide()
        showErrorLayout(error)
    }

    override fun onImageLoaded() {
        super.onImageLoaded()
        progressIndicator?.hide()
    }

    /**
     * Called when an image fails to decode.
     */
    override fun onImageLoadError(error: Throwable?) {
        super.onImageLoadError(error)
        setError(error)
    }

    /**
     * Called when an image is zoomed in/out.
     */
    override fun onScaleChanged(newScale: Float) {
        super.onScaleChanged(newScale)
        viewer.activity.hideMenu()
    }

    private fun showErrorLayout(error: Throwable?): ReaderErrorBinding {
        if (errorLayout == null) {
            errorLayout = ReaderErrorBinding.inflate(LayoutInflater.from(context), this, true)
            errorLayout?.actionRetry?.viewer = viewer
            errorLayout?.actionRetry?.setOnClickListener {
                page.chapter.pageLoader?.retryPage(page)
            }
        }

        val imageUrl = page.imageUrl
        errorLayout?.actionOpenInWebView?.isVisible = imageUrl != null
        if (imageUrl != null) {
            if (imageUrl.startsWith("http", true)) {
                errorLayout?.actionOpenInWebView?.viewer = viewer
                errorLayout?.actionOpenInWebView?.setOnClickListener {
                    val sourceId = viewer.activity.viewModel.manga?.source

                    val intent = WebViewActivity.newIntent(context, imageUrl, sourceId)
                    context.startActivity(intent)
                }
            }
        }

        errorLayout?.errorMessage?.text = with(context) { error?.formattedMessage }
            ?: context.stringResource(MR.strings.decode_image_error)

        errorLayout?.root?.isVisible = true
        return errorLayout!!
    }

    /**
     * Removes the decode error layout from the holder, if found.
     */
    private fun removeErrorLayout() {
        errorLayout?.root?.isVisible = false
        errorLayout = null
    }
}
