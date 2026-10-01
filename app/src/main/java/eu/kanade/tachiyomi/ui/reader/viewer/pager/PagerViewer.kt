package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.graphics.PointF
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.viewpager.widget.ViewPager
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.input.ReaderAction
import eu.kanade.tachiyomi.ui.reader.loader.EpubPageLoader
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderItemPair
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.panel.PanelMoveResult
import eu.kanade.tachiyomi.ui.reader.panel.PanelPageKey
import eu.kanade.tachiyomi.ui.reader.panel.hasSameLogicalPage
import eu.kanade.tachiyomi.ui.reader.panel.matchesPanelKey
import eu.kanade.tachiyomi.ui.reader.panel.panelPageKey
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation.NavigationRegion
import kotlin.math.min
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy

internal enum class PagerReaderActionRoute {
    MOVE_RIGHT,
    MOVE_LEFT,
    MOVE_TO_NEXT,
    MOVE_TO_PREVIOUS,
    LOAD_NEXT_PAGE,
    LOAD_PREVIOUS_PAGE,
}

internal fun pagerReaderActionRoute(action: ReaderAction): PagerReaderActionRoute? {
    return when (action) {
        ReaderAction.NEXT -> PagerReaderActionRoute.MOVE_RIGHT
        ReaderAction.PREVIOUS -> PagerReaderActionRoute.MOVE_LEFT
        ReaderAction.NEXT_PANEL -> PagerReaderActionRoute.MOVE_TO_NEXT
        ReaderAction.PREVIOUS_PANEL -> PagerReaderActionRoute.MOVE_TO_PREVIOUS
        ReaderAction.NEXT_PAGE -> PagerReaderActionRoute.LOAD_NEXT_PAGE
        ReaderAction.PREVIOUS_PAGE -> PagerReaderActionRoute.LOAD_PREVIOUS_PAGE
        else -> null
    }
}

/**
 * Implementation of a [Viewer] to display pages with a [ViewPager].
 */
@Suppress("LeakingThis")
abstract class PagerViewer(val activity: ReaderActivity) : Viewer {

    val downloadManager: DownloadManager by injectLazy()

    private val scope = MainScope()

    /**
     * View pager used by this viewer. It's abstract to implement L2R, R2L and vertical pagers on
     * top of this class.
     */
    val pager = createPager()

    /**
     * Configuration used by the pager, like allow taps, scale mode on images, page transitions...
     */
    val config = PagerConfig(this, scope)

    /**
     * Adapter of the pager.
     */
    private val adapter = PagerViewerAdapter(this)

    /**
     * Currently active item. It can be a chapter page or a chapter transition.
     */
    private var currentPage: Any? = null

    // Used by companion-page step logic to detect single-step swipes and advance by 2
    private var lastReportedPosition = -1
    private var lastFocusedPanel: Pair<PanelPageKey, String>? = null

    /**
     * Viewer chapters to set when the pager enters idle mode. Otherwise, if the view was settling
     * or dragging, there'd be a noticeable and annoying jump.
     */
    private var awaitingIdleViewerChapters: ViewerChapters? = null

    /**
     * Whether the view pager is currently in idle mode. It sets the awaiting chapters if setting
     * this field to true.
     */
    private var isIdle = true
        set(value) {
            field = value
            if (value) {
                awaitingIdleViewerChapters?.let { viewerChapters ->
                    setChaptersInternal(viewerChapters)
                    awaitingIdleViewerChapters = null
                    if (viewerChapters.currChapter.pages?.size == 1) {
                        adapter.nextTransition?.to?.let(activity::requestPreloadChapter)
                    }
                }
            }
        }

    private val pagerListener = object : ViewPager.SimpleOnPageChangeListener() {
        override fun onPageSelected(position: Int) {
            if (!activity.isScrollingThroughPages) {
                activity.hideMenu()
            }
            onPageChange(position)
        }

        override fun onPageScrollStateChanged(state: Int) {
            isIdle = state == ViewPager.SCROLL_STATE_IDLE
        }
    }

    init {
        pager.isVisible = false // Don't layout the pager yet
        pager.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        pager.isFocusable = false
        pager.offscreenPageLimit = if (config.sideBySideMode) 2 else 1
        pager.id = R.id.reader_pager
        pager.adapter = adapter
        pager.addOnPageChangeListener(pagerListener)
        pager.tapListener = { event ->
            val viewPosition = IntArray(2)
            pager.getLocationOnScreen(viewPosition)
            val viewPositionRelativeToWindow = IntArray(2)
            pager.getLocationInWindow(viewPositionRelativeToWindow)
            val pos = PointF(
                (event.rawX - viewPosition[0] + viewPositionRelativeToWindow[0]) / pager.width,
                (event.rawY - viewPosition[1] + viewPositionRelativeToWindow[1]) / pager.height,
            )
            when (config.navigator.getAction(pos)) {
                NavigationRegion.MENU -> activity.toggleMenu()
                NavigationRegion.NEXT -> moveToNext()
                NavigationRegion.PREV -> moveToPrevious()
                NavigationRegion.RIGHT -> moveRight()
                NavigationRegion.LEFT -> moveLeft()
            }
        }
        pager.longTapListener = f@{
            if (activity.viewModel.state.value.menuVisible || config.longTapEnabled) {
                val item = adapter.items.getOrNull(pager.currentItem)
                val first = (item as? ReaderItemPair)?.first ?: item
                if (first is ReaderPage) {
                    activity.onPageLongTap(first)
                    return@f true
                }
            }
            false
        }

        config.dualPageSplitChangedListener = { enabled ->
            if (!enabled) {
                cleanupPageSplit()
            }
        }

        config.sideBySideModeChangedListener = { enabled ->
            pager.offscreenPageLimit = if (enabled) 2 else 1
            needsFullAdapterReset = true
        }

        config.imagePropertyChangedListener = {
            refreshAdapter()
        }

        config.panelReadingDisplayChangedListener = {
            applyPanelReadingDisplayConfigToVisiblePages()
        }

        config.navigationModeChangedListener = {
            val showOnStart = config.navigationOverlayOnStart || config.forceNavigationOverlay
            activity.binding.navigationOverlay.setNavigation(config.navigator, showOnStart)
        }

        // Force re-measure of children when Pager size changes (Spanning)
        pager.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val widthChanged = (right - left) != (oldRight - oldLeft)
            if (widthChanged) {
                refreshAdapter()
            }
        }

        scope.launch {
            combine(activity.panelReadingController.state, activity.isPanelCorrectionMode) { state, correctionMode ->
                val key = state.key
                val panel = state.activePanel

                if (key == null || panel == null) {
                    lastFocusedPanel = null
                    clearPanelFocusOnVisiblePages()
                } else {
                    val focusKey = key to panel.id
                    if (lastFocusedPanel != focusKey) {
                        val page = currentReaderPages().firstOrNull { it.matchesPanelKey(key) }
                        if (page != null) {
                            lastFocusedPanel = focusKey
                            pager.post {
                                focusActivePanel(page)
                            }
                        }
                    }

                    // Always refresh overlays for correction mode or panel changes
                    refreshPanelOverlays()
                }
            }.collect()
        }
    }

    override fun refreshPanelOverlays() {
        val state = activity.panelReadingController.state.value
        val correctionMode = activity.isPanelCorrectionMode.value
        val key = state.key ?: return
        val activePanel = state.activePanel

        currentReaderPages().forEach { page ->
            if (page.matchesPanelKey(key)) {
                getPageHolder(page)?.showPanelMap(
                    panels = state.panels,
                    activePanel = activePanel,
                    showNumbers = true,
                    isCorrectionMode = correctionMode,
                    onPanelTap = { panelIndex ->
                        activity.panelReadingController.selectPanel(key, panelIndex)
                    },
                    onPanelSwap = { from, to ->
                        activity.panelReadingController.manuallySwapPanels(from, to)
                    }
                )
            }
        }
    }

    override fun destroy() {
        super.destroy()
        pendingRefresh?.let { pager.removeCallbacks(it) }
        pendingRefresh = null
        scope.cancel()
    }

    /**
     * Creates a new ViewPager.
     */
    abstract fun createPager(): Pager

    /**
     * Returns the view this viewer uses.
     */
    override fun getView(): View {
        return pager
    }

    /**
     * Returns the PagerPageHolder for the provided page
     */
    private fun getPageHolder(page: ReaderPage): PagerPageHolder? {
        pager.children.forEach { child ->
            if (child is PagerPageHolder && child.item == page) {
                return child
            }
            if (child is PagerPagePairHolder) {
                child.children.filterIsInstance(PagerPageHolder::class.java).forEach {
                    if (it.item == page) return it
                }
            }
        }
        return null
    }

    private fun currentReaderPages(): List<ReaderPage> {
        return when (val item = currentPage) {
            is ReaderPage -> listOf(item)
            is ReaderItemPair -> listOfNotNull(
                item.first as? ReaderPage,
                item.second as? ReaderPage,
            )
            else -> emptyList()
        }
    }

    private fun currentPanelReaderPage(): ReaderPage? {
        val pages = currentReaderPages()
        val activeKey = activity.panelReadingController.state.value.key
        return pages.firstOrNull { it.matchesPanelKey(activeKey) }
            ?: pages.firstOrNull()
    }

    private fun focusActivePanel(page: ReaderPage): Boolean {
        val key = panelKeyFor(page)
        val panel = activity.panelReadingController.activePanelFor(key) ?: return false
        val holder = getPageHolder(page) ?: return false
        holder.focusOnPanel(panel)
        return true
    }

    private fun clearPanelFocusOnVisiblePages() {
        currentReaderPages().forEach { page ->
            getPageHolder(page)?.clearPanelFocus()
        }
    }

    private fun applyPanelReadingDisplayConfigToVisiblePages() {
        currentReaderPages().forEach { page ->
            getPageHolder(page)?.applyPanelReadingDisplayConfig(
                panelTransitionDuration = config.panelReadingTransitionDuration,
                panelFocusEffect = config.panelReadingFocusEffect,
                panelFocusStrength = config.panelReadingFocusStrength,
                panelPrimaryOverlay = config.panelReadingPrimaryOverlay,
            )
        }
    }

    private fun panelKeyFor(page: ReaderPage): PanelPageKey {
        val activeKey = activity.panelReadingController.state.value.key
        return activeKey.takeIf { page.matchesPanelKey(it) } ?: page.panelPageKey()
    }


    private fun isCurrentVisiblePage(page: ReaderPage, key: PanelPageKey): Boolean {
        return currentReaderPages().any { it == page && it.matchesPanelKey(key) }
    }

    private fun moveToNextPanel(
        fallback: () -> Boolean,
        onFallbackUnavailable: (() -> Unit)? = null,
    ): Boolean {
        currentPanelReaderPage()?.let { page ->
            val key = panelKeyFor(page)
            when (activity.panelReadingController.moveToNextPanel(key)) {
                PanelMoveResult.Moved -> {
                    focusActivePanel(page)
                    return true
                }
                PanelMoveResult.Pending -> {
                    schedulePendingPanelMove(page, key, moveNext = true, fallback, onFallbackUnavailable)
                    return true
                }
                PanelMoveResult.NoPanelStep,
                PanelMoveResult.Disabled,
                -> Unit
            }
        }
        return false
    }

    private fun moveToPreviousPanel(
        fallback: () -> Boolean,
        onFallbackUnavailable: (() -> Unit)? = null,
    ): Boolean {
        currentPanelReaderPage()?.let { page ->
            val key = panelKeyFor(page)
            when (activity.panelReadingController.moveToPreviousPanel(key)) {
                PanelMoveResult.Moved -> {
                    focusActivePanel(page)
                    return true
                }
                PanelMoveResult.Pending -> {
                    schedulePendingPanelMove(page, key, moveNext = false, fallback, onFallbackUnavailable)
                    return true
                }
                PanelMoveResult.NoPanelStep,
                PanelMoveResult.Disabled,
                -> Unit
            }
        }
        return false
    }

    private fun schedulePendingPanelMove(
        page: ReaderPage,
        key: PanelPageKey,
        moveNext: Boolean,
        fallback: () -> Boolean,
        onFallbackUnavailable: (() -> Unit)?,
    ) {
        scope.launch {
            delay(PANEL_DETECTION_GRACE_MS)

            if (!isCurrentVisiblePage(page, key)) return@launch

            if (moveNext && focusActivePanel(page)) {
                return@launch
            }

            val result = if (moveNext) {
                activity.panelReadingController.moveToNextPanel(key)
            } else {
                activity.panelReadingController.moveToPreviousPanel(key)
            }

            if (result == PanelMoveResult.Moved) {
                focusActivePanel(page)
            } else if (isCurrentVisiblePage(page, key)) {
                if (!fallback()) {
                    onFallbackUnavailable?.invoke()
                }
            }
        }
    }

    /**
     * Called when a new page (either a [ReaderPage] or [ChapterTransition]) is marked as active
     */
    private fun onPageChange(position: Int) {
        // Companion page: advance 2 positions per swipe, but never skip transitions
        if (isCompanionPageSkipEnabled() && lastReportedPosition >= 0) {
            val delta = position - lastReportedPosition
            if (delta == 1 || delta == -1) {
                val currentItem = adapter.items.getOrNull(position)
                if (currentItem !is ChapterTransition) {
                    val nextPos = position + delta
                    val nextItem = adapter.items.getOrNull(nextPos)
                    if (nextItem != null && nextItem !is ChapterTransition) {
                        lastReportedPosition = nextPos
                        pager.removeOnPageChangeListener(pagerListener)
                        pager.setCurrentItem(nextPos, false)
                        pager.addOnPageChangeListener(pagerListener)
                        onPageChange(nextPos)
                        return
                    }
                }
            }
        }
        lastReportedPosition = position

        val page = adapter.items.getOrNull(position)
        if (page != null && currentPage != page) {
            val allowPreload = checkAllowPreload(page as? ReaderPage)
            val forward = when {
                currentPage is ReaderPage && page is ReaderPage -> {
                    // if both pages have the same number, it's a split page with an InsertPage
                    if (page.number == (currentPage as ReaderPage).number) {
                        // the InsertPage is always the second in the reading direction
                        page is InsertPage
                    } else {
                        page.number > (currentPage as ReaderPage).number
                    }
                }
                currentPage is ReaderItemPair && page is ReaderItemPair -> {
                    val prevNum = ((currentPage as ReaderItemPair).first as? ReaderPage)?.number
                    val newNum = (page.first as? ReaderPage)?.number
                    if (prevNum != null && newNum != null) newNum > prevNum else true
                }
                currentPage is ReaderItemPair && page is ReaderPage -> {
                    val prevNum = ((currentPage as ReaderItemPair).first as? ReaderPage)?.number
                    if (prevNum != null) page.number > prevNum else true
                }
                currentPage is ReaderPage && page is ReaderItemPair -> {
                    val newNum = (page.first as? ReaderPage)?.number
                    if (newNum != null) newNum > (currentPage as ReaderPage).number else true
                }
                currentPage is ChapterTransition.Prev && page is ReaderPage ->
                    false
                currentPage is ChapterTransition.Prev && page is ReaderItemPair -> false
                else -> true
            }
            currentPage = page
            activity.panelReadingController.onVisiblePagesChanged(
                currentReaderPages().map { it.panelPageKey() },
            )
            when (page) {
                is ReaderPage -> {
                    onReaderPageSelected(page, allowPreload, forward)
                }
                is ReaderItemPair -> {
                    val first = page.first
                    if (first is ReaderPage) {
                        onReaderPageSelected(first, allowPreload, forward)

                        val second = page.second
                        if (second is ReaderPage) {
                            onReaderPageSelected(second, allowPreload, forward) // for chapter completion + preload
                            getPageHolder(second)?.onPageSelected(forward)
                        }
                    } else if (first is ChapterTransition) {
                        onTransitionSelected(first)
                    }
                }
                is ChapterTransition -> onTransitionSelected(page)
            }
        }
    }

    private fun checkAllowPreload(page: ReaderPage?): Boolean {
        // Page is transition page - preload allowed
        page ?: return true

        // Initial opening - preload allowed
        currentPage ?: return true

        val currentChapter = (currentPage as? ReaderPage)?.chapter
            ?: (currentPage as? ReaderItemPair)?.first.let { (it as? ReaderPage)?.chapter }

        // Allow preload for
        // 1. Going to next chapter from chapter transition
        // 2. Going between pages of same chapter
        // 3. Next chapter page
        return when (page.chapter) {
            (currentPage as? ChapterTransition.Next)?.to -> true
            currentChapter -> true
            adapter.nextTransition?.to -> true
            else -> false
        }
    }

    /**
     * Called when a [ReaderPage] is marked as active. It notifies the
     * activity of the change and requests the preload of the next chapter if this is the last page.
     */
    private fun onReaderPageSelected(page: ReaderPage, allowPreload: Boolean, forward: Boolean) {
        val pages = page.chapter.pages ?: return
        logcat { "onReaderPageSelected: ${page.number}/${pages.size}" }
        activity.onPageSelected(page)

        // Notify holder of page change
        getPageHolder(page)?.onPageSelected(forward)
        getPageHolder(page)?.post {
            focusActivePanel(page)
        }

        // Skip preload on inserts it causes unwanted page jumping
        if (page is InsertPage) {
            return
        }

        // Preload next chapter once we're within the last 5 pages of the current chapter
        val inPreloadRange = pages.size - page.number < 5
        if (inPreloadRange && allowPreload && page.chapter == adapter.currentChapter) {
            logcat { "Request preload next chapter because we're at page ${page.number} of ${pages.size}" }
            adapter.nextTransition?.to?.let(activity::requestPreloadChapter)
        }
    }

    /**
     * Called when a [ChapterTransition] is marked as active. It request the
     * preload of the destination chapter of the transition.
     */
    private fun onTransitionSelected(transition: ChapterTransition) {
        logcat { "onTransitionSelected: $transition" }
        val toChapter = transition.to
        if (toChapter != null) {
            logcat { "Request preload destination chapter because we're on the transition" }
            activity.requestPreloadChapter(toChapter)
        } else if (transition is ChapterTransition.Next) {
            // No more chapters, show menu because the user is probably going to close the reader
            activity.showMenu()
        }
    }

    /**
     * Tells this viewer to set the given [chapters] as active. If the pager is currently idle,
     * it sets the chapters immediately, otherwise they are saved and set when it becomes idle.
     */
    override fun setChapters(chapters: ViewerChapters) {
        if (isIdle) {
            setChaptersInternal(chapters)
        } else {
            awaitingIdleViewerChapters = chapters
        }
    }

    /**
     * Sets the active [chapters] on this pager.
     */
    private fun setChaptersInternal(chapters: ViewerChapters) {
        lastReportedPosition = -1
        // Remove listener so the change in item doesn't trigger it
        pager.removeOnPageChangeListener(pagerListener)

        val forceTransition = config.alwaysShowChapterTransition ||
            adapter.items.getOrNull(pager.currentItem) is ChapterTransition
        adapter.setChapters(chapters, forceTransition)

        // Align resume index to even for companion-page pairing
        var alignedIdx = chapters.currChapter.requestedPage
        if (activity.isCompanionPageActive() && !config.sideBySideMode &&
            chapters.currChapter.requestedPageFromResume &&
            alignedIdx % 2 != 0
        ) {
            alignedIdx = (alignedIdx - 1).coerceAtLeast(0)
        }

        // Layout the pager once a chapter is being set
        if (pager.isGone) {
            logcat { "Pager first layout" }
            val pages = chapters.currChapter.pages
            if (pages == null) {
                pager.isVisible = true // Don't leave pager permanently invisible
                return
            }
            val pageIdx = min(alignedIdx, pages.lastIndex)
            moveToPage(pages[pageIdx])
            pager.isVisible = true
        } else {
            // Restore position to avoid stale index after chapter switch
            val requestedPage = chapters.currChapter.pages?.getOrNull(alignedIdx)
            if (requestedPage != null) {
                val newPosition = adapter.items.indexOf(requestedPage)
                if (newPosition != -1) {
                    pager.setCurrentItem(newPosition, false)
                } else {
                    val pairPosition = adapter.items.indexOfFirst {
                        it is ReaderItemPair && (it.first == requestedPage || it.second == requestedPage)
                    }
                    if (pairPosition != -1) {
                        pager.setCurrentItem(pairPosition, false)
                    }
                }
            }
        }

        pager.addOnPageChangeListener(pagerListener)
        // Manually call onPageChange to update the UI
        onPageChange(pager.currentItem)
    }

    /**
     * Tells this viewer to move to the given [page].
     */
    override fun moveToPage(page: ReaderPage) {
        val position = adapter.items.indexOf(page)
        if (position != -1) {
            val currentPosition = pager.currentItem
            pager.setCurrentItem(position, true)
            // manually call onPageChange since ViewPager listener is not triggered in this case
            if (currentPosition == position) {
                onPageChange(position)
            }
        } else {
            val pairPosition = adapter.items.indexOfFirst {
                it is ReaderItemPair &&
                    (it.first == page || it.second == page)
            }
            if (pairPosition != -1) {
                val currentPosition = pager.currentItem
                pager.setCurrentItem(pairPosition, true)
                if (currentPosition == pairPosition) {
                    onReaderPageSelected(page, checkAllowPreload(page), true)
                }
            } else {
                logcat { "Page $page not found in adapter" }
            }
        }
    }

    // Returns 2 when companion page is active (skip shown on secondary), 1 otherwise
    private fun companionPageStep(direction: Int): Int {
        if (!isCompanionPageSkipEnabled()) return 1
        val nextItem = adapter.items.getOrNull(pager.currentItem + direction)
        if (nextItem is ChapterTransition) return 1
        val targetItem = adapter.items.getOrNull(pager.currentItem + direction * 2)
        if (targetItem is ChapterTransition) return 1
        return 2
    }

    private fun isCompanionPageSkipEnabled(): Boolean {
        return activity.isCompanionPageActive() &&
            !config.sideBySideMode &&
            !isPanelMapActive()
    }

    private fun isPanelMapActive(): Boolean {
        if (!activity.readerPreferences.panelReadingPaged().get()) return false

        val state = activity.panelReadingController.state.value
        if (state.activePanel == null) return false

        return currentReaderPages().any { it.matchesPanelKey(state.key) }
    }

    /**
     * Tells this viewer to move to the next page/pair.
     */
    override fun moveToNext(): Boolean {
        if (moveToNextPanel(::moveToNextPageOrPan, activity::loadNextChapter)) return true
        return moveToNextPageOrPan()
    }

    private fun moveToNextPageOrPan(): Boolean {
        return if (pager.currentItem != adapter.count - 1) {
            val pair = adapter.items.getOrNull(pager.currentItem) as? ReaderItemPair
            val firstHolder = (pair?.first as? ReaderPage)?.let(::getPageHolder)
            val secondHolder = (pair?.second as? ReaderPage)?.let(::getPageHolder)

            if (config.navigateToPan && (firstHolder?.canPanRight() == true || secondHolder?.canPanRight() == true)) {
                if (firstHolder?.canPanRight() == true) {
                    firstHolder.panRight()
                } else {
                    secondHolder?.panRight()
                }
            } else {
                val step = companionPageStep(1)
                val target = (pager.currentItem + step).coerceAtMost(adapter.count - 1)
                pager.setCurrentItem(
                    target,
                    if (adapter.currentChapter?.pageLoader is EpubPageLoader) {
                        activity.readerPreferences.epubAnimatePages.get()
                    } else {
                        config.usePageTransitions
                    },
                )
            }
            true
        } else {
            false
        }
    }

    /**
     * Tells this viewer to move to the previous page/pair.
     */
    override fun moveToPrevious(): Boolean {
        if (moveToPreviousPanel(::moveToPreviousPageOrPan, activity::loadPreviousChapter)) return true
        return moveToPreviousPageOrPan()
    }

    private fun moveToPreviousPageOrPan(): Boolean {
        return if (pager.currentItem != 0) {
            val pair = adapter.items.getOrNull(pager.currentItem) as? ReaderItemPair
            val firstHolder = (pair?.first as? ReaderPage)?.let(::getPageHolder)
            val secondHolder = (pair?.second as? ReaderPage)?.let(::getPageHolder)

            if (config.navigateToPan && (firstHolder?.canPanLeft() == true || secondHolder?.canPanLeft() == true)) {
                if (secondHolder?.canPanLeft() == true) {
                    secondHolder.panLeft()
                } else {
                    firstHolder?.panLeft()
                }
            } else {
                val step = companionPageStep(-1)
                val target = (pager.currentItem - step).coerceAtLeast(0)
                pager.setCurrentItem(
                    target,
                    if (adapter.currentChapter?.pageLoader is EpubPageLoader) {
                        activity.readerPreferences.epubAnimatePages.get()
                    } else {
                        config.usePageTransitions
                    },
                )
            }
            true
        } else {
            false
        }
    }

    /**
     * Moves to the next page.
     */
    open fun moveToNextPage() {
        moveRight()
    }

    /**
     * Moves to the previous page.
     */
    open fun moveToPreviousPage() {
        moveLeft()
    }

    /**
     * Moves to the page at the right.
     */
    fun moveRight(onFallbackUnavailable: (() -> Unit)? = null): Boolean {
        if (this is R2LPagerViewer) {
            if (moveToPreviousPanel(::moveToNextPageOrPan, onFallbackUnavailable)) return true
        } else if (moveToNextPanel(::moveToNextPageOrPan, onFallbackUnavailable)) {
            return true
        }

        return moveToNextPageOrPan()
    }

    /**
     * Moves to the page at the left.
     */
    fun moveLeft(onFallbackUnavailable: (() -> Unit)? = null): Boolean {
        if (this is R2LPagerViewer) {
            if (moveToNextPanel(::moveToPreviousPageOrPan, onFallbackUnavailable)) return true
        } else if (moveToPreviousPanel(::moveToPreviousPageOrPan, onFallbackUnavailable)) {
            return true
        }

        return moveToPreviousPageOrPan()
    }

    /**
     * Moves to the page at the top (or previous).
     */
    protected open fun moveUp() {
        activity.loadPreviousPage()
    }

    /**
     * Moves to the page at the bottom (or next).
     */
    protected open fun moveDown() {
        activity.loadNextPage()
    }

    private var pendingRefresh: Runnable? = null

    // Main-thread only: set by configuration listeners, read by refreshAdapterInternal.
    private var needsFullAdapterReset = false

    /**
     * Resets the adapter in order to recreate all the views. Used when a image configuration is
     * changed. Debounced so rapid successive calls (e.g. during span/unspan) are coalesced.
     */
    internal fun refreshAdapter(forceFullReset: Boolean = false) {
        if (forceFullReset) {
            needsFullAdapterReset = true
        }
        pendingRefresh?.let { pager.removeCallbacks(it) }
        val runnable = Runnable {
            pendingRefresh = null
            refreshAdapterInternal()
        }
        pendingRefresh = runnable
        pager.post(runnable)
    }

    private fun refreshAdapterInternal() {
        lastReportedPosition = -1
        val currentItem = (currentPage as? ReaderItemPair)?.first ?: currentPage
        val wasTransition = adapter.items.getOrNull(pager.currentItem) is ChapterTransition

        adapter.refresh()
        activity.viewModel.state.value.viewerChapters?.let {
            adapter.setChapters(it, wasTransition)
        } ?: run {
            adapter.notifyDataSetChanged()
        }

        if (needsFullAdapterReset) {
            // Mode changed -- full reset required to rebuild view cache
            pager.adapter = null
            pager.adapter = adapter
            needsFullAdapterReset = false
        }

        if (currentItem != null) {
            when (currentItem) {
                is ReaderPage -> moveToPage(currentItem)
                is ChapterTransition -> {
                    val newIndex = adapter.items.indexOfFirst { item ->
                        val unwrapped = (item as? ReaderItemPair)?.first ?: item
                        unwrapped is ChapterTransition &&
                            // Compare type (Prev/Next) and To/From chapter IDs to ensure identity
                            unwrapped::class == currentItem::class &&
                            unwrapped.to?.chapter?.id == currentItem.to?.chapter?.id
                    }

                    if (newIndex != -1) {
                        pager.setCurrentItem(newIndex, false)
                        onPageChange(newIndex)
                    }
                }
            }
        }
    }

    /**
     * Called from the containing activity when a key [event] is received. It should return true
     * if the event was handled, false otherwise.
     */
    override fun handleKeyEvent(event: KeyEvent): Boolean {
        val isUp = event.action == KeyEvent.ACTION_UP
        val ctrlPressed = event.metaState.and(KeyEvent.META_CTRL_ON) > 0

        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (!config.volumeKeysEnabled || activity.viewModel.state.value.menuVisible) {
                    return false
                } else if (isUp) {
                    if (!config.volumeKeysInverted) moveDown() else moveUp()
                }
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (!config.volumeKeysEnabled || activity.viewModel.state.value.menuVisible) {
                    return false
                } else if (isUp) {
                    if (!config.volumeKeysInverted) moveUp() else moveDown()
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isUp) {
                    if (ctrlPressed) activity.loadNextPage() else moveRight()
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isUp) {
                    if (ctrlPressed) activity.loadPreviousPage() else moveLeft()
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> if (isUp) moveDown()
            KeyEvent.KEYCODE_DPAD_UP -> if (isUp) moveUp()
            KeyEvent.KEYCODE_PAGE_DOWN -> if (isUp) moveDown()
            KeyEvent.KEYCODE_PAGE_UP -> if (isUp) moveUp()
            KeyEvent.KEYCODE_MENU -> if (isUp) activity.toggleMenu()
            else -> return false
        }
        return true
    }

    override fun handleReaderAction(action: ReaderAction): Boolean {
        return when (pagerReaderActionRoute(action)) {
            PagerReaderActionRoute.MOVE_RIGHT -> moveRight()
            PagerReaderActionRoute.MOVE_LEFT -> moveLeft()
            PagerReaderActionRoute.MOVE_TO_NEXT -> moveToNext()
            PagerReaderActionRoute.MOVE_TO_PREVIOUS -> moveToPrevious()
            PagerReaderActionRoute.LOAD_NEXT_PAGE -> {
                activity.loadNextPage()
                true
            }
            PagerReaderActionRoute.LOAD_PREVIOUS_PAGE -> {
                activity.loadPreviousPage()
                true
            }
            null -> false
        }
    }

    /**
     * Called from the containing activity when a generic motion [event] is received. It should
     * return true if the event was handled, false otherwise.
     */
    override fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_CLASS_POINTER != 0) {
            when (event.action) {
                MotionEvent.ACTION_SCROLL -> {
                    if (event.getAxisValue(MotionEvent.AXIS_VSCROLL) < 0.0f) {
                        moveDown()
                    } else {
                        moveUp()
                    }
                    return true
                }
            }
        }
        return false
    }

    fun onPageSplit(currentPage: ReaderPage, newPage: InsertPage) {
        activity.runOnUiThread {
            // Need to insert on UI thread else images will go blank
            adapter.onPageSplit(currentPage, newPage)
        }
    }

    private fun cleanupPageSplit() {
        adapter.cleanupPageSplit()
    }

    /**
     * Checks if the current page is zoomed in
     */
    fun isCurrentPageZoomed(): Boolean {
        val pair = currentPage as? ReaderItemPair
        if (pair != null) {
            val first = (pair.first as? ReaderPage)?.let(::getPageHolder)?.isZoomed() == true
            val second = (pair.second as? ReaderPage)?.let(::getPageHolder)?.isZoomed() == true
            return first || second
        }
        val holder = (currentPage as? ReaderPage)?.let(::getPageHolder)
        return holder?.isZoomed() == true
    }

    /**
     * Called when an external pan event is received (e.g. from a dual-screen touchpad)
     */
    override fun handleExternalPan(dx: Float, dy: Float) {
        val pair = currentPage as? ReaderItemPair
        if (pair != null) {
            (pair.first as? ReaderPage)?.let(::getPageHolder)?.panBy(dx, dy)
            (pair.second as? ReaderPage)?.let(::getPageHolder)?.panBy(dx, dy)
            return
        }
        val holder = (currentPage as? ReaderPage)?.let(::getPageHolder)
        holder?.panBy(dx, dy)
    }

    /**
     * Called when zoom should be reset to default (e.g. double-tap on touchpad)
     */
    override fun handleExternalZoomReset() {
        val pair = currentPage as? ReaderItemPair
        if (pair != null) {
            (pair.first as? ReaderPage)?.let(::getPageHolder)?.resetZoom()
            (pair.second as? ReaderPage)?.let(::getPageHolder)?.resetZoom()
            return
        }
        val holder = (currentPage as? ReaderPage)?.let(::getPageHolder)
        holder?.resetZoom()
    }
}

private const val PANEL_DETECTION_GRACE_MS = 750L
