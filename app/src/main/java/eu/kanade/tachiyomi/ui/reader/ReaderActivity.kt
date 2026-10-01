package eu.kanade.tachiyomi.ui.reader

import android.annotation.SuppressLint
import android.app.assist.AssistContent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.View.LAYER_TYPE_HARDWARE
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.core.graphics.Insets
import androidx.core.net.toUri
import androidx.core.transition.doOnEnd
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.WindowLayoutInfo
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.hippo.unifile.UniFile
import eu.kanade.core.util.ifSourcesLoaded
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.reader.DisplayRefreshHost
import eu.kanade.presentation.reader.OrientationSelectDialog
import eu.kanade.presentation.reader.ReaderContentOverlay
import eu.kanade.presentation.reader.ReaderPageActionsDialog
import eu.kanade.presentation.reader.ReaderPageIndicator
import eu.kanade.presentation.reader.ReadingModeSelectDialog
import eu.kanade.presentation.reader.appbars.ReaderAppBars
import eu.kanade.presentation.reader.components.ChapterNavigatorType
import eu.kanade.presentation.reader.settings.ReaderSettingsDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.coil.TachiyomiImageDecoder
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.databinding.ReaderActivityBinding
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel.SetAsCoverResult.AddToLibraryFirst
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel.SetAsCoverResult.Error
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel.SetAsCoverResult.Success
import eu.kanade.tachiyomi.ui.reader.input.InputBinding
import eu.kanade.tachiyomi.ui.reader.input.ReaderAction
import eu.kanade.tachiyomi.ui.reader.input.ReaderActionDispatcher
import eu.kanade.tachiyomi.ui.reader.input.ReaderActionTarget
import eu.kanade.tachiyomi.ui.reader.input.ReaderInputDefaultOptions
import eu.kanade.tachiyomi.ui.reader.input.ReaderInputEventParser
import eu.kanade.tachiyomi.ui.reader.input.ReaderInputHoldKeyDispatcher
import eu.kanade.tachiyomi.ui.reader.input.ReaderInputLayer
import eu.kanade.tachiyomi.ui.reader.input.ReaderInputMotionEventLatch
import eu.kanade.tachiyomi.ui.reader.input.ReaderInputRuntimeDispatchPolicy
import eu.kanade.tachiyomi.ui.reader.input.ReaderInputRuntimeResolver
import eu.kanade.tachiyomi.ui.reader.input.ReaderInputTrigger
import eu.kanade.tachiyomi.ui.reader.loader.EpubPageLoader
import eu.kanade.tachiyomi.ui.reader.model.EpubBookmark
import eu.kanade.tachiyomi.ui.reader.model.EpubReadingProgress
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.panel.LazyPanelDetector
import eu.kanade.tachiyomi.ui.reader.panel.LiteRtPanelDetector
import eu.kanade.tachiyomi.ui.reader.panel.PanelReadingController
import eu.kanade.tachiyomi.ui.reader.panel.PanelReadingDirection
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsViewModel
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderProgressIndicator
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.R2LPagerViewer
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.system.isNightMode
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.setComposeContent
import java.io.ByteArrayOutputStream
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.core.dualscreen.DualScreenState
import mihon.core.dualscreen.utils.FoldableUtils
import tachiyomi.core.common.Constants
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import androidx.compose.ui.graphics.Color as ComposeColor

class ReaderActivity : BaseActivity(), ReaderActionTarget {

    companion object {
        fun newIntent(context: Context, mangaId: Long?, chapterId: Long?): Intent {
            return Intent(context, ReaderActivity::class.java).apply {
                putExtra("manga", mangaId)
                putExtra("chapter", chapterId)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }

    val readerPreferences = Injekt.get<ReaderPreferences>()
    val preferences = Injekt.get<BasePreferences>()

    lateinit var binding: ReaderActivityBinding

    val viewModel by viewModels<ReaderViewModel>()
    private var assistUrl: String? = null

    /**
     * Configuration at reader level, like background color or forced orientation.
     */
    private var config: ReaderConfig? = null

    private var menuToggleToast: Toast? = null
    private var readingModeToast: Toast? = null
    private val displayRefreshHost = DisplayRefreshHost()
    private val panelDetector by lazy {
        LazyPanelDetector {
            LiteRtPanelDetector(this)
        }
    }
    val panelReadingController by lazy {
        PanelReadingController(
            scope = lifecycleScope,
            detector = panelDetector,
            isEnabled = { isPanelReadingActive() },
            readingDirection = {
                readerPreferences.panelReadingDirection().get()
            },
            context = this,
        )
    }

    private val windowInsetsController by lazy { WindowInsetsControllerCompat(window, window.decorView) }

    private var loadingIndicator: ReaderProgressIndicator? = null
    private var controlsPresentation: ReaderControlsPresentation? = null
    private var readerPresentation: ReaderPresentation? = null
    private val readerInputMotionEventLatch = ReaderInputMotionEventLatch()
    private val readerInputRuntimeResolver by lazy {
        ReaderInputRuntimeResolver(
            profileProvider = { readerPreferences.readerInputProfile().get() },
            defaultOptionsProvider = {
                ReaderInputDefaultOptions(
                    volumeKeysEnabled = readerPreferences.readWithVolumeKeys.get(),
                    volumeKeysInverted = readerPreferences.readWithVolumeKeysInverted.get(),
                )
            },
            layerProvider = ::currentReaderInputLayer,
        )
    }
    private val readerInputHoldKeyDispatcher by lazy {
        ReaderInputHoldKeyDispatcher(
            scope = lifecycleScope,
            resolve = { binding, trigger ->
                readerInputRuntimeResolver.resolve(binding, trigger)
            },
            dispatch = { action ->
                ReaderActionDispatcher.dispatch(action, this)
            },
            stop = ::stopReaderInputAction,
        )
    }

    // Dual-screen state
    private var companionPageEnabled = false
    private var isDeviceFoldable = false
    private var foldableStartupSettled = false
    val isPanelCorrectionMode = MutableStateFlow(false)

    // Hinge gap state (ephemeral, not persisted to disk on every rotation)
    private val activeHingeGap = kotlinx.coroutines.flow.MutableStateFlow(0)

    fun isCompanionPageEnabled(): Boolean = companionPageEnabled

    // True when secondary display is showing the companion page (pager advances by 2).
    fun isCompanionPageActive(): Boolean {
        return companionPageEnabled &&
            !isPanelReadingActive() &&
            readerPresentation != null
    }

    fun isPanelReadingActive(): Boolean {
        return ReaderPanelReadingMode.isActive(
            panelReadingEnabled = readerPreferences.panelReadingPaged().get(),
            readingModePreference = viewModel.getMangaReadingMode(resolveDefault = true),
        )
    }

    private fun currentReaderInputLayer(): ReaderInputLayer? {
        if (isPanelReadingActive()) return ReaderInputLayer.GUIDED_READING
        return when (ReadingMode.fromPreference(viewModel.getMangaReadingMode(resolveDefault = true))) {
            ReadingMode.WEBTOON,
            ReadingMode.CONTINUOUS_VERTICAL,
            -> ReaderInputLayer.WEBTOON
            ReadingMode.LEFT_TO_RIGHT,
            ReadingMode.RIGHT_TO_LEFT,
            ReadingMode.VERTICAL,
            -> ReaderInputLayer.PAGED
            ReadingMode.DEFAULT -> null
        }
    }

    fun togglePanelReading(): Boolean {
        val enabled = !readerPreferences.panelReadingPaged().get()
        readerPreferences.panelReadingPaged().set(enabled)
        panelReadingController.setEnabledState(
            ReaderPanelReadingMode.isActive(
                panelReadingEnabled = enabled,
                readingModePreference = viewModel.getMangaReadingMode(resolveDefault = true),
            ),
        )
        return enabled
    }

    fun getHingeGap(): Int {
        if (!isDeviceFoldable && readerPreferences.autoAdjustHingeGap().get()) {
            return 0
        }
        return activeHingeGap.value
    }

    fun setCompanionPage(enabled: Boolean) {
        companionPageEnabled = enabled
        readerPreferences.companionPageEnabled().set(enabled)
        recreatePresentation()
    }

    /**
     * Hides the secondary-display windows without forgetting them.
     *
     * The references are deliberately kept: onResume recreates any presentation that is
     * non-null but no longer showing, so leaving and returning to the app restores the
     * companion exactly as it was.
     */
    private fun dismissCompanionPresentations() {
        controlsPresentation?.dismiss()
        readerPresentation?.dismiss()
    }

    private fun recreatePresentation() {
        controlsPresentation?.dismiss()
        controlsPresentation = null
        readerPresentation?.dismiss()
        readerPresentation = null

        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val secondaryId = preferences.secondaryDisplayId().get()

        var presentationDisplay = if (secondaryId != -1 && secondaryId != Display.DEFAULT_DISPLAY) {
            displayManager.getDisplay(secondaryId)
        } else {
            null
        }

        // Fallback to auto-detect
        if (presentationDisplay == null) {
            presentationDisplay = displayManager.displays.find { it.displayId != Display.DEFAULT_DISPLAY }
        }

        val dualScreenEnabled = preferences.enableDualScreenMode().get()

        // Take down the companion activity, which owns the second screen when we are not
        // reading. It is singleInstance, so when it exists this reaches onNewIntent and
        // finishes it; when it does not, Android creates one just to run that finish.
        //
        // The launch display matters. Without it that throwaway activity appears on the
        // primary display, on top of the reader, which stops the reader; onResume then sees a
        // presentation that is no longer showing and calls back into here, which starts the
        // same activity again. That loop ran about once a second and left the companion black,
        // because its Presentation was torn down and rebuilt before it could draw.
        if (dualScreenEnabled) {
            try {
                val intent = Intent(this, eu.kanade.tachiyomi.ui.main.DualScreenActivity::class.java)
                intent.action = eu.kanade.tachiyomi.ui.main.DualScreenActivity.ACTION_FINISH
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                val options = android.app.ActivityOptions.makeBasic()
                presentationDisplay?.displayId?.let { options.setLaunchDisplayId(it) }
                startActivity(intent, options.toBundle())
            } catch (_: Exception) {
            }
        }

        if (presentationDisplay != null && dualScreenEnabled) {
            try {
                when (
                    ReaderSecondaryPresentationSelector.mode(
                        companionPageEnabled = companionPageEnabled,
                        panelReadingEnabled = isPanelReadingActive(),
                    )
                ) {
                    SecondaryPresentationMode.PAGE -> {
                        readerPresentation = ReaderPresentation(this, presentationDisplay, this)
                        readerPresentation?.show()
                    }
                    SecondaryPresentationMode.CONTROLS -> {
                        controlsPresentation = ReaderControlsPresentation(this, presentationDisplay, this)
                        controlsPresentation?.show()
                    }
                }
            } catch (e: WindowManager.InvalidDisplayException) {
                logcat(LogPriority.WARN) { "Secondary display disconnected before show(): ${e.message}" }
                readerPresentation = null
                controlsPresentation = null
            }
        }
    }

    var isScrollingThroughPages = false
        private set

    /**
     * Called when the activity is created. Initializes the presenter and configuration.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        registerSecureActivity(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                R.anim.shared_axis_x_push_enter,
                R.anim.shared_axis_x_push_exit,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.shared_axis_x_push_enter, R.anim.shared_axis_x_push_exit)
        }

        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        super.onCreate(savedInstanceState)

        binding = ReaderActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.setComposeOverlay()

        companionPageEnabled = readerPreferences.companionPageEnabled().get()
        recreatePresentation()

        // Collected on lifecycleScope rather than repeatOnLifecycle: the request arrives
        // after this activity has already been stopped, which is the whole point of it.
        //
        // Hence the explicit state check instead. The watcher decides the app has left the
        // foreground by counting started activities, and that count dips to zero for a
        // moment whenever the reader is relaunched -- long enough to fire if the next
        // chapter is slow to start. Acting on that while the reader is still on screen
        // blanks the companion mid-read, so the reader ignores a request that arrives
        // while it is visible.
        lifecycleScope.launch {
            DualScreenState.companionCloseRequests.collect {
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    dismissCompanionPresentations()
                }
            }
        }

        if (!viewModel.hasValidArgs) {
            finish()
            return
        }

        NotificationReceiver.dismissNotification(
            this,
            viewModel.mangaId.hashCode(),
            Notifications.ID_NEW_CHAPTERS,
        )

        config = ReaderConfig()
        setMenuVisibility(viewModel.state.value.menuVisible)

        panelReadingController.setEnabledState(isPanelReadingActive())

        readerPreferences.panelReadingPaged().changes()
            .drop(1)
            .distinctUntilChanged()
            .onEach {
                val enabled = isPanelReadingActive()
                panelReadingController.setEnabledState(enabled)
                recreatePresentation()
                // Unconditional: border cropping is suppressed while guided reading is on, so
                // pages have to be re-decoded when it is turned off as well as on.
                (viewModel.state.value.viewer as? PagerViewer)?.refreshAdapter(forceFullReset = true)
            }
            .launchIn(lifecycleScope)

        activeHingeGap.value = readerPreferences.manualHingeGap().get()

        readerPreferences.manualHingeGap().changes()
            .onEach { activeHingeGap.value = it }
            .launchIn(lifecycleScope)

        // Foldable state observation
        FoldableUtils.windowLayoutInfoFlow(this)
            .onEach { info ->
                val isSpanned = FoldableUtils.isSpanned(info)
                val foldingFeature = FoldableUtils.getFoldingFeature(info)
                val currentSideBySide = readerPreferences.sideBySideMode().get()

                if (foldingFeature != null) {
                    isDeviceFoldable = true
                }

                logcat { "Spanned: $isSpanned, IsFoldable: $isDeviceFoldable" }

                if (isDeviceFoldable && foldableStartupSettled) {
                    if (isSpanned && !currentSideBySide && readerPreferences.autoEnableSideBySide().get()) {
                        readerPreferences.sideBySideMode().set(true)
                    } else if (!isSpanned && currentSideBySide && readerPreferences.autoDisableSideBySide().get()) {
                        readerPreferences.sideBySideMode().set(false)
                    }
                }

                // Hinge gap management
                if (!isSpanned) {
                    if (isDeviceFoldable && readerPreferences.autoAdjustHingeGap().get()) {
                        if (activeHingeGap.value != 0) {
                            activeHingeGap.value = 0
                            (viewModel.state.value.viewer as? eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewer)?.refreshAdapter()
                        }
                    }
                } else if (readerPreferences.autoAdjustHingeGap().get()) {
                    val hingeBounds = FoldableUtils.getHingeBounds(info)
                    if (hingeBounds != null) {
                        val gap = if (hingeBounds.width() > hingeBounds.height()) hingeBounds.height() else hingeBounds.width()
                        if (gap > 0 && activeHingeGap.value != gap) {
                            activeHingeGap.value = gap
                            (viewModel.state.value.viewer as? eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewer)?.refreshAdapter()
                        }
                    }
                }
            }
            .launchIn(lifecycleScope)

        if (readerPreferences.autoDisableSideBySideOnStart().get() &&
            readerPreferences.sideBySideMode().get()) {
            lifecycleScope.launch {
                kotlinx.coroutines.delay(600L)
                val info = FoldableUtils.windowLayoutInfoFlow(this@ReaderActivity).first()
                val isSpanned = FoldableUtils.isSpanned(info)
                if (!isSpanned) {
                    logcat { "Auto-disabling Side-by-Side View on start (single screen detected)" }
                    readerPreferences.sideBySideMode().set(false)
                }
                foldableStartupSettled = true
            }
        } else {
            foldableStartupSettled = true
        }

        // Finish when incognito mode is disabled
        preferences.incognitoMode.changes()
            .drop(1)
            .onEach { if (!it) finish() }
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.initError }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach(::setInitialChapterError)
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.isLoadingAdjacentChapter }
            .distinctUntilChanged()
            .onEach(::setProgressDialog)
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.manga }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach { updateViewer() }
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.viewerChapters }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach(::setChapters)
            .launchIn(lifecycleScope)

        preferences.swapPresentationRotation().changes()
            .onEach {
                controlsPresentation?.setupRotation()
                readerPresentation?.setupRotation()
            }
            .launchIn(lifecycleScope)

        viewModel.eventFlow
            .onEach { event ->
                when (event) {
                    ReaderViewModel.Event.ReloadViewerChapters -> {
                        viewModel.state.value.viewerChapters?.let(::setChapters)
                    }
                    ReaderViewModel.Event.EpubLayoutError -> toast(MR.strings.epub_apply_error)
                    ReaderViewModel.Event.PageChanged -> {
                        displayRefreshHost.flash()
                    }
                    is ReaderViewModel.Event.SetOrientation -> {
                        setOrientation(event.orientation)
                    }
                    is ReaderViewModel.Event.SavedImage -> {
                        onSaveImageResult(event.result)
                    }
                    is ReaderViewModel.Event.ShareImage -> {
                        onShareImageResult(event.uri, event.page)
                    }
                    is ReaderViewModel.Event.CopyImage -> {
                        onCopyImageResult(event.uri)
                    }
                    is ReaderViewModel.Event.SetCoverResult -> {
                        onSetAsCoverResult(event.result)
                    }
                }
            }
            .launchIn(lifecycleScope)
    }

    private fun ReaderActivityBinding.setComposeOverlay(): Unit = composeOverlay.setComposeContent {
        val state by viewModel.state.collectAsState()
        val showPageNumber by readerPreferences.showPageNumber.collectAsState()
        val sideBySideMode by readerPreferences.sideBySideMode().collectAsState()
        val panelReadingPreferenceEnabled by readerPreferences.panelReadingPaged().collectAsState()
        val panelState by panelReadingController.state.collectAsState()
        val panelReadingEnabled = ReaderPanelReadingMode.isActive(
            panelReadingEnabled = panelReadingPreferenceEnabled,
            readingModePreference = viewModel.getMangaReadingMode(resolveDefault = true),
        )

        val settingsViewModel = remember {
            ReaderSettingsViewModel(
                readerState = viewModel.state,
                onChangeReadingMode = viewModel::setMangaReadingMode,
                onChangeOrientation = viewModel::setMangaOrientationType,
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (!state.menuVisible && showPageNumber) {
                if (sideBySideMode) {
                    val readingMode = ReadingMode.fromPreference(viewModel.getMangaReadingMode())
                    val isRtl = readingMode == ReadingMode.RIGHT_TO_LEFT

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        ReaderPageIndicator(
                            currentPage = if (isRtl) (state.currentPage + 1).coerceAtMost(state.totalPages) else state.currentPage,
                            totalPages = state.totalPages,
                        )
                        ReaderPageIndicator(
                            currentPage = if (isRtl) state.currentPage else (state.currentPage + 1).coerceAtMost(state.totalPages),
                            totalPages = state.totalPages,
                        )
                    }
                } else {
                    ReaderPageIndicator(
                        currentPage = state.currentPage,
                        totalPages = state.totalPages,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding(),
                    )
                }
            }

            if (
                panelReadingEnabled &&
                !state.menuVisible &&
                state.dialog == null &&
                panelState.panelCount > 0 &&
                panelState.panelIndex >= 0
            ) {
                PanelReadingIndicator(
                    panelIndex = panelState.panelIndex,
                    panelCount = panelState.panelCount,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding(),
                )
            }

            ContentOverlay(state = state)

            if (controlsPresentation == null) {
                AppBars(state = state)
            }
        }

        val onDismissRequest = viewModel::closeDialog
        when (state.dialog) {
            is ReaderViewModel.Dialog.Loading -> {
                AlertDialog(
                    onDismissRequest = {},
                    confirmButton = {},
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator()
                            Text(stringResource(MR.strings.loading))
                        }
                    },
                )
            }
            is ReaderViewModel.Dialog.Settings -> {
                val epub = state.currentChapter?.pageLoader as? EpubPageLoader
                val offsets = epub?.bookmarks?.collectAsState()?.value.orEmpty()
                val epubBookmarks = offsets.mapNotNull { value ->
                    val offset = value.toLongOrNull() ?: return@mapNotNull null
                    val page = epub?.pageForOffset(offset) ?: return@mapNotNull null
                    EpubBookmark(offset, page)
                }.sortedBy { it.offset }
                val returnPage = state.epubReturnPosition?.takeIf {
                    it.first == state.currentChapter?.chapter?.url
                }?.second?.let { epub?.pageForOffset(it) }
                ReaderSettingsDialog(
                    onDismissRequest = onDismissRequest,
                    onShowMenus = { setMenuVisibility(true) },
                    onHideMenus = { setMenuVisibility(false) },
                    viewModel = settingsViewModel,
                    onApplyEpubLayout = viewModel::applyEpubLayout,
                    epubContents = epub?.contents,
                    epubPreferences = epub?.preferences,
                    epubCurrentPage = (state.currentPage - 1).coerceAtLeast(0),
                    epubPageCount = state.currentChapter?.pages?.size ?: 0,
                    epubBookmarks = epubBookmarks,
                    onAddEpubBookmark = {
                        epub?.let {
                            val offset = it.offsetForPage((state.currentPage - 1).coerceAtLeast(0))
                            it.bookmarks.set(it.bookmarks.get() + offset.toString())
                        }
                    },
                    onRemoveEpubBookmark = { offset ->
                        epub?.let { it.bookmarks.set(it.bookmarks.get() - offset.toString()) }
                    },
                    epubCanReturn = returnPage != null,
                    onReturnEpubPage = {
                        returnPage?.let(::moveToPageIndex)
                        viewModel.clearEpubReturnPosition()
                    },
                    onSelectEpubPage = { index ->
                        if (index != (state.currentPage - 1).coerceAtLeast(0)) {
                            viewModel.rememberEpubReturnPosition()
                            moveToPageIndex(index)
                        }
                    },
                )
            }
            is ReaderViewModel.Dialog.ReadingModeSelect -> {
                ReadingModeSelectDialog(
                    onDismissRequest = onDismissRequest,
                    viewModel = settingsViewModel,
                    onChange = { stringRes ->
                        menuToggleToast?.cancel()
                        if (!readerPreferences.showReadingMode.get()) {
                            menuToggleToast = toast(stringRes)
                        }
                    },
                )
            }
            is ReaderViewModel.Dialog.OrientationModeSelect -> {
                OrientationSelectDialog(
                    onDismissRequest = onDismissRequest,
                    viewModel = settingsViewModel,
                    onChange = { stringRes ->
                        menuToggleToast?.cancel()
                        menuToggleToast = toast(stringRes)
                    },
                )
            }
            is ReaderViewModel.Dialog.PageActions -> {
                ReaderPageActionsDialog(
                    onDismissRequest = onDismissRequest,
                    onSetAsCover = viewModel::setAsCover,
                    onShare = viewModel::shareImage,
                    onSave = viewModel::saveImage,
                )
            }
            null -> {}
        }
    }

    @Composable
    private fun PanelReadingIndicator(
        panelIndex: Int,
        panelCount: Int,
        modifier: Modifier = Modifier,
    ) {
        if (panelIndex < 0 || panelCount <= 0) return

        val text = stringResource(MR.strings.panel_indicator, panelIndex + 1, panelCount)
        val style = TextStyle(
            color = ComposeColor(235, 235, 235).copy(alpha = 0.92f),
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
            fontWeight = FontWeight.SemiBold,
        )
        val strokeStyle = style.copy(
            color = ComposeColor(45, 45, 45).copy(alpha = 0.82f),
            drawStyle = Stroke(width = 3f),
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier,
        ) {
            Text(
                text = text,
                style = strokeStyle,
            )
            Text(
                text = text,
                style = style,
            )
        }
    }

    /**
     * Called when the activity is destroyed. Cleans up the viewer, configuration and any view.
     */
    override fun onDestroy() {
        panelReadingController.cancel()
        panelDetector.close()
        controlsPresentation?.dismiss()
        controlsPresentation = null
        readerPresentation?.dismiss()
        readerPresentation = null
        readerInputHoldKeyDispatcher.cancel()
        super.onDestroy()
        viewModel.state.value.viewer?.destroy()
        config = null
        menuToggleToast?.cancel()
        readingModeToast?.cancel()
    }

    override fun onPause() {
        readerInputHoldKeyDispatcher.cancel()
        lifecycleScope.launchNonCancellable {
            viewModel.updateHistory()
        }
        super.onPause()
    }

    /**
     * Set menu visibility again on activity resume to apply immersive mode again if needed.
     * Helps with rotations.
     */
    override fun onResume() {
        super.onResume()
        val controls = controlsPresentation
        val reader = readerPresentation
        if ((controls != null && !controls.isShowing) ||
            (reader != null && !reader.isShowing)) {
            recreatePresentation()
        }

        viewModel.restartReadTimer()
        setMenuVisibility(viewModel.state.value.menuVisible)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        DualScreenState.triggerRotationUpdate()
    }

    /**
     * Called when the window focus changes. It sets the menu visibility to the last known state
     * to apply immersive mode again if needed.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setMenuVisibility(viewModel.state.value.menuVisible)
        } else {
            readerInputHoldKeyDispatcher.cancel()
            // Keep read timer alive when focus shifts to our own secondary display
            if (controlsPresentation?.isShowing == true || readerPresentation?.isShowing == true) {
                viewModel.restartReadTimer()
            }
        }
    }

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        assistUrl?.let { outContent.webUri = it.toUri() }
    }

    /**
     * Called when the user clicks the back key or the button on the toolbar. The call is
     * delegated to the presenter.
     */
    override fun finish() {
        viewModel.onActivityFinish()

        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val secondaryId = preferences.secondaryDisplayId().get()

        var presentationDisplay = if (secondaryId != -1 && secondaryId != Display.DEFAULT_DISPLAY) {
            displayManager.getDisplay(secondaryId)
        } else {
            null
        }
        if (presentationDisplay == null) {
            presentationDisplay = displayManager.displays.find { it.displayId != Display.DEFAULT_DISPLAY }
        }

        if (presentationDisplay != null && preferences.enableDualScreenMode().get()) {
            val intent = Intent(this, eu.kanade.tachiyomi.ui.main.DualScreenActivity::class.java)
            val options = android.app.ActivityOptions.makeBasic()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                options.setLaunchDisplayId(presentationDisplay.displayId)
            }
            try {
                startActivity(intent, options.toBundle())
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to restart DualScreenActivity: ${e.message}" }
            }
        }

        super.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE,
                R.anim.shared_axis_x_pop_enter,
                R.anim.shared_axis_x_pop_exit,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.shared_axis_x_pop_enter, R.anim.shared_axis_x_pop_exit)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_N) {
            loadNextChapter()
            return true
        } else if (keyCode == KeyEvent.KEYCODE_P) {
            loadPreviousChapter()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Dispatches a key event. If the viewer doesn't handle it, call the default implementation.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (
            ReaderInputRuntimeDispatchPolicy.shouldResolve(
                ReaderInputEventParser.keyBinding(event.keyCode, event.metaState),
                viewModel.state.value.menuVisible,
            ) &&
            readerInputHoldKeyDispatcher.handleKeyEvent(event)
        ) {
            return true
        }

        ReaderInputEventParser.bindingFromKeyEvent(event)?.let { binding ->
            if (dispatchResolvedReaderInput(binding)) {
                return true
            }
        }

        val handled = viewModel.state.value.viewer?.handleKeyEvent(event) ?: false
        return handled || super.dispatchKeyEvent(event)
    }

    /**
     * Dispatches a generic motion event. If the viewer doesn't handle it, call the default
     * implementation.
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        readerInputMotionEventLatch.bindingFromMotionEvent(event)?.let { binding ->
            if (dispatchResolvedReaderInput(binding)) {
                return true
            }
        }

        val handled = viewModel.state.value.viewer?.handleGenericMotionEvent(event) ?: false
        return handled || super.dispatchGenericMotionEvent(event)
    }

    private fun dispatchResolvedReaderInput(binding: InputBinding): Boolean {
        if (!ReaderInputRuntimeDispatchPolicy.shouldResolve(binding, viewModel.state.value.menuVisible)) {
            return false
        }

        val resolution = readerInputRuntimeResolver.resolveResult(binding, ReaderInputTrigger.PRESS)
        val action = resolution.action
        if (action != null && ReaderActionDispatcher.dispatch(action, this)) {
            return true
        }
        return resolution.isOwnedBinding
    }

    @Composable
    private fun ContentOverlay(state: ReaderViewModel.State) {
        val flashOnPageChange by readerPreferences.flashOnPageChange.collectAsState()

        val colorOverlayEnabled by readerPreferences.colorFilter.collectAsState()
        val colorOverlay by readerPreferences.colorFilterValue.collectAsState()
        val colorOverlayMode by readerPreferences.colorFilterMode.collectAsState()
        val colorOverlayBlendMode = remember(colorOverlayMode) {
            ReaderPreferences.ColorFilterMode.getOrNull(colorOverlayMode)?.second
        }

        ReaderContentOverlay(
            brightness = state.brightnessOverlayValue,
            color = colorOverlay.takeIf { colorOverlayEnabled },
            colorBlendMode = colorOverlayBlendMode,
        )

        val reduceEpubFlashing by readerPreferences.epubReduceFlashing.collectAsState()
        val isEpub = state.currentChapter?.pageLoader is EpubPageLoader
        if (flashOnPageChange && !(isEpub && reduceEpubFlashing)) {
            DisplayRefreshHost(hostState = displayRefreshHost)
        }
    }

    @Composable
    fun AppBars(state: ReaderViewModel.State) {
        if (!ifSourcesLoaded()) {
            return
        }

        val isHttpSource = viewModel.getSource() is HttpSource
        val dsModeEnabled by preferences.enableDualScreenMode().collectAsState()

        val cropBorderPaged by readerPreferences.cropBorders.collectAsState()
        val cropBorderWebtoon by readerPreferences.cropBordersWebtoon.collectAsState()
        val panelReadingPreferenceEnabled by readerPreferences.panelReadingPaged().collectAsState()
        val isPagerType = ReadingMode.isPagerType(viewModel.getMangaReadingMode())
        val cropEnabled = if (isPagerType) cropBorderPaged else cropBorderWebtoon
        val panelReadingEnabled = ReaderPanelReadingMode.isActive(
            panelReadingEnabled = panelReadingPreferenceEnabled,
            readingModePreference = viewModel.getMangaReadingMode(resolveDefault = true),
        )
        val panelCorrectionMode by isPanelCorrectionMode.collectAsState()

        val verticalNavigatorModes by readerPreferences.verticalNavigator.collectAsState()
        val verticalNavigator = verticalNavigatorModes.contains(
            ReadingMode.fromPreference(viewModel.getMangaReadingMode()),
        )
        val verticalNavigatorOnLeft by readerPreferences.verticalNavigatorOnLeft.collectAsState()
        val verticalNavigatorHeight by readerPreferences.verticalNavigatorHeight.collectAsState()

        val epubProgress = (state.currentChapter?.pageLoader as? EpubPageLoader)?.let {
            EpubReadingProgress.calculate(it.contents, state.currentPage - 1, state.currentChapter?.pages?.size ?: 0)
        }
        val epubTitle = epubProgress?.let {
            val title = it.chapter?.title ?: stringResource(MR.strings.epub_front_matter)
            "$title · " + stringResource(MR.strings.epub_reading_progress, it.page, it.pages, it.percent)
        }
        ReaderAppBars(
            visible = state.menuVisible,

            mangaTitle = state.manga?.title,
            chapterTitle = epubTitle ?: state.currentChapter?.chapter?.name,
            navigateUp = onBackPressedDispatcher::onBackPressed,
            onClickTopAppBar = ::openMangaScreen,
            bookmarked = state.bookmarked,
            onToggleBookmarked = viewModel::toggleChapterBookmark,
            onOpenInWebView = ::openChapterInWebView.takeIf { isHttpSource },
            onOpenInBrowser = ::openChapterInBrowser.takeIf { isHttpSource },
            onShare = ::shareChapter.takeIf { isHttpSource },

            chapterNavigatorType = if (!verticalNavigator) {
                if (state.viewer is R2LPagerViewer) {
                    ChapterNavigatorType.HORIZONTAL_RTL
                } else {
                    ChapterNavigatorType.HORIZONTAL_LTR
                }
            } else {
                if (verticalNavigatorOnLeft) {
                    ChapterNavigatorType.VERTICAL_LEFT
                } else {
                    ChapterNavigatorType.VERTICAL_RIGHT
                }
            },
            verticalNavigatorHeight = verticalNavigatorHeight / 100f,
            onNextChapter = ::loadNextChapter,
            enabledNext = state.viewerChapters?.nextChapter != null,
            onPreviousChapter = ::loadPreviousChapter,
            enabledPrevious = state.viewerChapters?.prevChapter != null,
            currentPage = state.currentPage,
            totalPages = state.totalPages,
            onPageIndexChange = {
                isScrollingThroughPages = true
                moveToPageIndex(it)
            },
            onPageIndexChangeFinished = {
                isScrollingThroughPages = false
            },

            readingMode = ReadingMode.fromPreference(
                viewModel.getMangaReadingMode(resolveDefault = false),
            ),
            onClickReadingMode = viewModel::openReadingModeSelectDialog,
            orientation = ReaderOrientation.fromPreference(
                viewModel.getMangaOrientation(resolveDefault = false),
            ),
            onClickOrientation = viewModel::openOrientationModeSelectDialog,
            cropEnabled = cropEnabled,
            onClickCropBorder = {
                val enabled = viewModel.toggleCropBorders()
                menuToggleToast?.cancel()
                menuToggleToast = toast(if (enabled) MR.strings.on else MR.strings.off)
            },
            onClickSettings = viewModel::openSettingsDialog,
            companionPageEnabled = companionPageEnabled,
            onClickDualScreenMode = if (dsModeEnabled) { { setCompanionPage(!companionPageEnabled) } } else null,
            panelReadingEnabled = panelReadingEnabled,
            onClickPanelReading = if (isPagerType && panelReadingPreferenceEnabled) {
                {
                    val enabled = togglePanelReading()
                    menuToggleToast?.cancel()
                    menuToggleToast = toast(if (enabled) MR.strings.on else MR.strings.off)
                }
            } else {
                null
            },
            isPanelCorrectionMode = panelCorrectionMode,
            onClickPanelCorrection = {
                isPanelCorrectionMode.value = !panelCorrectionMode
            },
        )
    }

    /**
     * Sets the visibility of the menu according to [visible].
     */
    private fun setMenuVisibility(visible: Boolean) {
        viewModel.showMenus(visible)
        if (visible) {
            readerPresentation?.hideLocalMenu()
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        } else if (readerPreferences.fullscreen.get()) {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    /**
     * Called from the presenter when a manga is ready. Used to instantiate the appropriate viewer.
     */
    private fun updateViewer() {
        val prevViewer = viewModel.state.value.viewer
        val newViewer = ReadingMode.toViewer(viewModel.getMangaReadingMode(), this)

        if (window.sharedElementEnterTransition is MaterialContainerTransform) {
            // Wait until transition is complete to avoid crash on API 26
            window.sharedElementEnterTransition.doOnEnd {
                setOrientation(viewModel.getMangaOrientation())
            }
        } else {
            setOrientation(viewModel.getMangaOrientation())
        }

        // Destroy previous viewer if there was one
        if (prevViewer != null) {
            prevViewer.destroy()
            binding.viewerContainer.removeAllViews()
        }
        viewModel.onViewerLoaded(newViewer)
        updateViewerInset(readerPreferences.fullscreen.get(), readerPreferences.drawUnderCutout.get())
        binding.viewerContainer.addView(newViewer.getView())

        if (readerPreferences.showReadingMode.get()) {
            showReadingModeToast(viewModel.getMangaReadingMode())
        }

        loadingIndicator = ReaderProgressIndicator(this)
        binding.readerContainer.addView(loadingIndicator)

        panelReadingController.setEnabledState(isPanelReadingActive())
        recreatePresentation()

        startPostponedEnterTransition()
    }

    fun openMangaScreen() {
        viewModel.manga?.id?.let { id ->
            if (preferences.enableDualScreenMode().get()) {
                mihon.core.dualscreen.DualScreenState.openScreen(eu.kanade.tachiyomi.ui.manga.MangaScreen(id))
                finish()
            } else {
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        action = Constants.SHORTCUT_MANGA
                        putExtra(Constants.MANGA_EXTRA, id)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    },
                )
            }
        }
    }

    fun openChapterInWebView() {
        val manga = viewModel.manga ?: return
        val source = viewModel.getSource() ?: return
        assistUrl?.let {
            val intent = WebViewActivity.newIntent(this@ReaderActivity, it, source.id, manga.title)
            startActivity(intent)
        }
    }

    fun openChapterInBrowser() {
        assistUrl?.let {
            openInBrowser(it.toUri(), forceDefaultBrowser = false)
        }
    }

    fun shareChapter() {
        assistUrl?.let {
            val intent = it.toUri().toShareIntent(this, type = "text/plain")
            startActivity(intent)
        }
    }

    private fun showReadingModeToast(mode: Int) {
        try {
            readingModeToast?.cancel()
            readingModeToast = toast(ReadingMode.fromPreference(mode).stringRes)
        } catch (_: ArrayIndexOutOfBoundsException) {
            logcat(LogPriority.ERROR) { "Unknown reading mode: $mode" }
        }
    }

    /**
     * Called from the presenter whenever a new [viewerChapters] have been set. It delegates the
     * method to the current viewer, but also set the subtitle on the toolbar, and
     * hides or disables the reader prev/next buttons if there's a prev or next chapter
     */
    @SuppressLint("RestrictedApi")
    private fun setChapters(viewerChapters: ViewerChapters) {
        binding.readerContainer.removeView(loadingIndicator)
        viewModel.state.value.viewer?.setChapters(viewerChapters)

        lifecycleScope.launchIO {
            viewModel.getChapterUrl()?.let { url ->
                assistUrl = url
            }
        }
    }

    /**
     * Called from the presenter if the initial load couldn't load the pages of the chapter. In
     * this case the activity is closed and a toast is shown to the user.
     */
    private fun setInitialChapterError(error: Throwable) {
        logcat(LogPriority.ERROR, error)
        finish()
        toast(error.message)
    }

    /**
     * Called from the presenter whenever it's loading the next or previous chapter. It shows or
     * dismisses a non-cancellable dialog to prevent user interaction according to the value of
     * [show]. This is only used when the next/previous buttons on the toolbar are clicked; the
     * other cases are handled with chapter transitions on the viewers and chapter preloading.
     */
    private fun setProgressDialog(show: Boolean) {
        if (show) {
            viewModel.showLoadingDialog()
        } else {
            viewModel.closeDialog()
        }
    }

    /**
     * Moves the viewer to the given page [index]. It does nothing if the viewer is null or the
     * page is not found.
     */
    fun moveToPageIndex(index: Int) {
        val viewer = viewModel.state.value.viewer ?: return
        val currentChapter = viewModel.state.value.currentChapter ?: return
        val page = currentChapter.pages?.getOrNull(index) ?: return
        viewer.moveToPage(page)
    }

    /**
     * Tells the presenter to load the next chapter and mark it as active. The progress dialog
     * should be automatically shown.
     */
    fun loadNextChapter() {
        lifecycleScope.launch {
            viewModel.loadNextChapter()
            moveToPageIndex(0)
        }
    }

    /**
     * Tells the presenter to load the previous chapter and mark it as active. The progress dialog
     * should be automatically shown.
     */
    fun loadPreviousChapter() {
        lifecycleScope.launch {
            viewModel.loadPreviousChapter()
            moveToPageIndex(0)
        }
    }

    /**
     * Loads the next page. If at the end of the chapter, loads the next chapter.
     */
    fun loadNextPage() {
        val viewer = viewModel.state.value.viewer ?: return
        if (!viewer.moveToNext()) {
            loadNextChapter()
        }
    }

    /**
     * Loads the previous page. If at the start of the chapter, loads the previous chapter.
     */
    fun loadPreviousPage() {
        val viewer = viewModel.state.value.viewer ?: return
        if (!viewer.moveToPrevious()) {
            loadPreviousChapter()
        }
    }

    fun handleExternalScroll(dy: Float) {
        val sensitivity = readerPreferences.secondaryDisplayScrollSensitivity().get()
        val scaledDistance = ReaderExternalScrollSensitivity.scaleDistance(dy, sensitivity)
        viewModel.state.value.viewer?.handleExternalScroll(scaledDistance)
    }

    fun handleExternalFling(vy: Float) {
        viewModel.state.value.viewer?.handleExternalFling(vy)
    }

    fun handleExternalScale(scaleFactor: Float) {
        viewModel.state.value.viewer?.handleExternalScale(scaleFactor)
    }

    fun handleExternalPan(dx: Float, dy: Float) {
        viewModel.state.value.viewer?.handleExternalPan(dx, dy)
    }

    fun handleExternalZoomReset() {
        viewModel.state.value.viewer?.handleExternalZoomReset()
    }

    fun isZoomed(): Boolean {
        val viewer = viewModel.state.value.viewer
        return when (viewer) {
            is eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer -> {
                viewer.recycler.currentScale > 1f
            }
            is eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewer -> {
                viewer.isCurrentPageZoomed()
            }
            else -> false
        }
    }

    /**
     * Called from the viewer whenever a [page] is marked as active. It updates the values of the
     * bottom menu and delegates the change to the presenter.
     */
    fun onPageSelected(page: ReaderPage) {
        viewModel.onPageSelected(page)
    }

    /**
     * Called from the viewer whenever a [page] is long clicked. A bottom sheet with a list of
     * actions to perform is shown.
     */
    fun onPageLongTap(page: ReaderPage) {
        viewModel.openPageDialog(page)
    }

    /**
     * Called from the viewer when the given [chapter] should be preloaded. It should be called when
     * the viewer is reaching the beginning or end of a chapter or the transition page is active.
     */
    fun requestPreloadChapter(chapter: ReaderChapter) {
        lifecycleScope.launchIO { viewModel.preload(chapter) }
    }

    /**
     * Called from the viewer to toggle the visibility of the menu. It's implemented on the
     * viewer because each one implements its own touch and key events.
     * @param fromSecondaryScreen If true, toggle menu on the secondary screen only
     */
    fun toggleMenu(fromSecondaryScreen: Boolean = false) {
        if (fromSecondaryScreen) {
            readerPresentation?.toggleMenu()
        } else {
            setMenuVisibility(!viewModel.state.value.menuVisible)
        }
    }

    override fun handleActivityAction(action: ReaderAction): Boolean {
        return when (action) {
            ReaderAction.NEXT_CHAPTER -> {
                loadNextChapter()
                true
            }
            ReaderAction.PREVIOUS_CHAPTER -> {
                loadPreviousChapter()
                true
            }
            ReaderAction.TOGGLE_MENU -> {
                toggleMenu()
                true
            }
            ReaderAction.TOGGLE_COMPANION_PAGE -> {
                setCompanionPage(!isCompanionPageEnabled())
                true
            }
            ReaderAction.TOGGLE_GUIDED_READING -> {
                togglePanelReading()
                true
            }
            ReaderAction.OPEN_READER_SETTINGS -> {
                viewModel.openSettingsDialog()
                true
            }
            else -> false
        }
    }

    override fun handleViewerAction(action: ReaderAction): Boolean {
        return viewModel.state.value.viewer?.handleReaderAction(action) ?: false
    }

    private fun stopReaderInputAction(action: ReaderAction) {
        viewModel.state.value.viewer?.stopReaderAction(action)
    }

    /**
     * Called from the viewer to show the menu.
     * @param onSecondaryScreen If true, show menu on the secondary screen
     */
    fun showMenu(onSecondaryScreen: Boolean = false) {
        if (onSecondaryScreen) {
            readerPresentation?.showMenu()
        } else {
            if (!viewModel.state.value.menuVisible) {
                setMenuVisibility(true)
            }
        }
    }

    /**
     * Called from the viewer to hide the menu.
     * @param onSecondaryScreen If true, hide menu on the secondary screen
     */
    fun hideMenu(onSecondaryScreen: Boolean = false) {
        if (onSecondaryScreen) {
            readerPresentation?.hideMenu()
        } else {
            if (viewModel.state.value.menuVisible) {
                setMenuVisibility(false)
            }
        }
    }

    /**
     * Called from the viewer to show the menu.
     */
    fun showMenu() {
        if (!viewModel.state.value.menuVisible) {
            setMenuVisibility(true)
        }
    }

    /**
     * Called from the viewer to hide the menu.
     */
    fun hideMenu() {
        if (viewModel.state.value.menuVisible) {
            setMenuVisibility(false)
        }
    }

    /**
     * Called from the presenter when a page is ready to be shared. It shows Android's default
     * sharing tool.
     */
    private fun onShareImageResult(uri: Uri, page: ReaderPage) {
        val manga = viewModel.manga ?: return
        val chapter = page.chapter.chapter

        val intent = uri.toShareIntent(
            context = applicationContext,
            message = stringResource(MR.strings.share_page_info, manga.title, chapter.name, page.number),
        )
        startActivity(intent)
    }

    private fun onCopyImageResult(uri: Uri) {
        val clipboardManager = applicationContext.getSystemService<ClipboardManager>() ?: return
        val clipData = ClipData.newUri(applicationContext.contentResolver, "", uri)
        clipboardManager.setPrimaryClip(clipData)
    }

    /**
     * Called from the presenter when a page is saved or fails. It shows a message or logs the
     * event depending on the [result].
     */
    private fun onSaveImageResult(result: ReaderViewModel.SaveImageResult) {
        when (result) {
            is ReaderViewModel.SaveImageResult.Success -> {
                toast(MR.strings.picture_saved)
            }
            is ReaderViewModel.SaveImageResult.Error -> {
                logcat(LogPriority.ERROR, result.error)
            }
        }
    }

    /**
     * Called from the presenter when a page is set as cover or fails. It shows a different message
     * depending on the [result].
     */
    private fun onSetAsCoverResult(result: ReaderViewModel.SetAsCoverResult) {
        toast(
            when (result) {
                Success -> MR.strings.cover_updated
                AddToLibraryFirst -> MR.strings.notification_first_add_to_library
                Error -> MR.strings.notification_cover_update_failed
            },
        )
    }

    /**
     * Forces the user preferred [orientation] on the activity.
     */
    private fun setOrientation(orientation: Int) {
        val newOrientation = ReaderOrientation.fromPreference(orientation)
        if (newOrientation.flag != requestedOrientation) {
            requestedOrientation = newOrientation.flag
        }
    }

    /**
     * Updates viewer inset depending on fullscreen reader preferences.
     */
    private fun updateViewerInset(fullscreen: Boolean, drawUnderCutout: Boolean) {
        if (!::binding.isInitialized) return
        val view = binding.viewerContainer

        view.applyInsetsPadding(ViewCompat.getRootWindowInsets(view), fullscreen, drawUnderCutout)
        ViewCompat.setOnApplyWindowInsetsListener(view) { view, windowInsets ->
            view.applyInsetsPadding(windowInsets, fullscreen, drawUnderCutout)
            windowInsets
        }
    }

    private fun View.applyInsetsPadding(
        windowInsets: WindowInsetsCompat?,
        fullscreen: Boolean,
        drawUnderCutout: Boolean,
    ) {
        val insets = when {
            !fullscreen -> windowInsets?.getInsets(WindowInsetsCompat.Type.systemBars())
            !drawUnderCutout -> windowInsets?.getInsets(WindowInsetsCompat.Type.displayCutout())
            else -> null
        }
            ?: Insets.NONE

        setPadding(insets.left, insets.top, insets.right, insets.bottom)
    }

    /**
     * Class that handles the user preferences of the reader.
     */
    private inner class ReaderConfig {

        private fun getCombinedPaint(grayscale: Boolean, invertedColors: Boolean): Paint {
            return Paint().apply {
                colorFilter = ColorMatrixColorFilter(
                    ColorMatrix().apply {
                        if (grayscale) {
                            setSaturation(0f)
                        }
                        if (invertedColors) {
                            postConcat(
                                ColorMatrix(
                                    floatArrayOf(
                                        -1f, 0f, 0f, 0f, 255f,
                                        0f, -1f, 0f, 0f, 255f,
                                        0f, 0f, -1f, 0f, 255f,
                                        0f, 0f, 0f, 1f, 0f,
                                    ),
                                ),
                            )
                        }
                    },
                )
            }
        }

        private val grayBackgroundColor = Color.rgb(0x20, 0x21, 0x25)

        /*
         * Initializes the reader subscriptions.
         */
        init {
            readerPreferences.readerTheme.changes()
                .onEach { theme ->
                    binding.readerContainer.setBackgroundColor(
                        when (theme) {
                            0 -> Color.WHITE
                            2 -> grayBackgroundColor
                            3 -> automaticBackgroundColor()
                            else -> Color.BLACK
                        },
                    )
                }
                .launchIn(lifecycleScope)

            preferences.displayProfile.changes()
                .onEach { setDisplayProfile(it) }
                .launchIn(lifecycleScope)

            readerPreferences.keepScreenOn.changes()
                .onEach(::setKeepScreenOn)
                .launchIn(lifecycleScope)

            readerPreferences.customBrightness.changes()
                .onEach(::setCustomBrightness)
                .launchIn(lifecycleScope)

            combine(
                readerPreferences.grayscale.changes(),
                readerPreferences.invertedColors.changes(),
            ) { grayscale, invertedColors -> grayscale to invertedColors }
                .onEach { (grayscale, invertedColors) ->
                    setLayerPaint(grayscale, invertedColors)
                }
                .launchIn(lifecycleScope)

            combine(
                readerPreferences.fullscreen.changes(),
                readerPreferences.drawUnderCutout.changes(),
            ) { fullscreen, drawUnderCutout -> fullscreen to drawUnderCutout }
                .onEach { (fullscreen, drawUnderCutout) ->
                    updateViewerInset(fullscreen, drawUnderCutout)
                }
                .launchIn(lifecycleScope)
        }

        /**
         * Picks background color for [ReaderActivity] based on light/dark theme preference
         */
        private fun automaticBackgroundColor(): Int {
            return if (baseContext.isNightMode()) {
                grayBackgroundColor
            } else {
                Color.WHITE
            }
        }

        /**
         * Sets the display profile to [path].
         */
        private fun setDisplayProfile(path: String) {
            val file = UniFile.fromUri(baseContext, path.toUri())
            if (file != null && file.exists()) {
                val inputStream = file.openInputStream()
                val outputStream = ByteArrayOutputStream()
                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                val data = outputStream.toByteArray()
                SubsamplingScaleImageView.setDisplayProfile(data)
                TachiyomiImageDecoder.displayProfile = data
            }
        }

        /**
         * Sets the keep screen on mode according to [enabled].
         */
        private fun setKeepScreenOn(enabled: Boolean) {
            if (enabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        /**
         * Sets the custom brightness overlay according to [enabled].
         */
        private fun setCustomBrightness(enabled: Boolean) {
            if (enabled) {
                readerPreferences.customBrightnessValue.changes()
                    .sample(0.1.seconds)
                    .onEach(::setCustomBrightnessValue)
                    .launchIn(lifecycleScope)
            } else {
                setCustomBrightnessValue(0)
            }
        }

        /**
         * Sets the brightness of the screen. Range is [-75, 100].
         * From -75 to -1 a semi-transparent black view is overlaid with the minimum brightness.
         * From 1 to 100 it sets that value as brightness.
         * 0 sets system brightness and hides the overlay.
         */
        private fun setCustomBrightnessValue(value: Int) {
            // Calculate and set reader brightness.
            val readerBrightness = when {
                value > 0 -> {
                    value / 100f
                }
                value < 0 -> {
                    0.01f
                }
                else -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            window.attributes = window.attributes.apply { screenBrightness = readerBrightness }

            viewModel.setBrightnessOverlayValue(value)
        }
        private fun setLayerPaint(grayscale: Boolean, invertedColors: Boolean) {
            val paint = if (grayscale || invertedColors) getCombinedPaint(grayscale, invertedColors) else null
            binding.viewerContainer.setLayerType(LAYER_TYPE_HARDWARE, paint)
        }
    }
}
