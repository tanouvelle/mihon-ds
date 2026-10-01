package eu.kanade.tachiyomi.ui.reader.setting

import android.os.Build
import androidx.compose.ui.graphics.BlendMode
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.reader.input.ReaderInputProfile
import eu.kanade.tachiyomi.ui.reader.input.ReaderInputProfileJson
import eu.kanade.tachiyomi.ui.reader.panel.PanelFocusEffect
import eu.kanade.tachiyomi.ui.reader.panel.PanelReadingSettings
import eu.kanade.tachiyomi.ui.reader.panel.PanelSortingAlgorithm
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.core.common.preference.getEnumSet
import tachiyomi.i18n.MR

class ReaderPreferences(
    private val preferenceStore: PreferenceStore,
    private val json: Json,
) {

    // region General

    val pageTransitions: Preference<Boolean> = preferenceStore.getBoolean("pref_enable_transitions_key", true)

    val epubReduceFlashing = preferenceStore.getBoolean("epub_reduce_flashing", true)

    val flashOnPageChange: Preference<Boolean> = preferenceStore.getBoolean("pref_reader_flash", false)

    val flashDurationMillis: Preference<Int> = preferenceStore.getInt("pref_reader_flash_duration", MILLI_CONVERSION)

    val flashPageInterval: Preference<Int> = preferenceStore.getInt("pref_reader_flash_interval", 1)

    val flashColor: Preference<FlashColor> = preferenceStore.getEnum("pref_reader_flash_mode", FlashColor.BLACK)

    val doubleTapAnimSpeed: Preference<Int> = preferenceStore.getInt("pref_double_tap_anim_speed", 500)

    val showPageNumber: Preference<Boolean> = preferenceStore.getBoolean("pref_show_page_number_key", true)

    val verticalNavigator: Preference<Set<ReadingMode>> = preferenceStore.getEnumSet(
        "pref_vertical_navigator",
        emptySet(),
    )

    val verticalNavigatorOnLeft: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_vertical_navigator_on_left",
        false,
    )

    val verticalNavigatorHeight: Preference<Int> = preferenceStore.getInt(
        "pref_vertical_navigator_height",
        65,
    )

    val showReadingMode: Preference<Boolean> = preferenceStore.getBoolean("pref_show_reading_mode", true)

    val fullscreen: Preference<Boolean> = preferenceStore.getBoolean("fullscreen", true)

    val drawUnderCutout: Preference<Boolean> = preferenceStore.getBoolean("cutout_short", true)

    val keepScreenOn: Preference<Boolean> = preferenceStore.getBoolean("pref_keep_screen_on_key", false)

    val defaultReadingMode: Preference<Int> = preferenceStore.getInt(
        "pref_default_reading_mode_key",
        ReadingMode.RIGHT_TO_LEFT.flagValue,
    )

    val defaultOrientationType: Preference<Int> = preferenceStore.getInt(
        "pref_default_orientation_type_key",
        ReaderOrientation.FREE.flagValue,
    )

    val webtoonDoubleTapZoomEnabled: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_enable_double_tap_zoom_webtoon",
        true,
    )

    val imageScaleType: Preference<Int> = preferenceStore.getInt("pref_image_scale_type_key", 1)

    val zoomStart: Preference<Int> = preferenceStore.getInt("pref_zoom_start_key", 1)

    val readerTheme: Preference<Int> = preferenceStore.getInt("pref_reader_theme_key", 1)

    /** Local EPUB typography. Changes take effect when the chapter is reopened. */
    val epubFont: Preference<Int> = preferenceStore.getInt("reader_epub_font", 0)
    val epubFontSize: Preference<Int> = preferenceStore.getInt("reader_epub_font_size", 38)
    val epubTheme: Preference<Int> = preferenceStore.getInt("reader_epub_theme", 0)
    val epubLineSpacing: Preference<Int> = preferenceStore.getInt("reader_epub_line_spacing", 150)
    val epubParagraphSpacing: Preference<Int> = preferenceStore.getInt("reader_epub_paragraph_spacing", 50)
    val epubMargin: Preference<Int> = preferenceStore.getInt("reader_epub_margin", 80)
    val epubCompactPages: Preference<Boolean> = preferenceStore.getBoolean("reader_epub_compact_pages", false)

    fun epubReadingPosition(bookKey: String): Preference<String> {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(bookKey.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return preferenceStore.getString("reader_epub_position_$digest", "")
    }


    val alwaysShowChapterTransition: Preference<Boolean> = preferenceStore.getBoolean(
        "always_show_chapter_transition",
        true,
    )

    val cropBorders: Preference<Boolean> = preferenceStore.getBoolean("crop_borders", false)

    val navigateToPan: Preference<Boolean> = preferenceStore.getBoolean("navigate_pan", true)

    fun panelReadingPaged() = preferenceStore.getBoolean("pref_panel_reading_paged", false)

    fun panelSortingAlgorithm() = preferenceStore.getEnum(
        "pref_panel_sorting_algorithm",
        PanelSortingAlgorithm.XY_CUT,
    )

    fun panelReadingTransitionMillis() = preferenceStore.getInt(
        "pref_panel_reading_transition_millis",
        PanelReadingSettings.PANEL_TRANSITION_DEFAULT_MILLIS,
    )

    fun panelReadingFocusEffect() = preferenceStore.getEnum(
        "pref_panel_reading_focus_effect",
        PanelFocusEffect.DARKEN,
    )

    fun panelReadingFocusStrength() = preferenceStore.getInt(
        "pref_panel_reading_focus_strength",
        PanelReadingSettings.PANEL_FOCUS_STRENGTH_DEFAULT,
    )

    fun panelReadingPrimaryOverlay() = preferenceStore.getBoolean("pref_panel_reading_primary_overlay", true)

    fun panelReadingSecondaryOverlay() = preferenceStore.getBoolean("pref_panel_reading_secondary_overlay", true)

    fun panelReadingFocusEffectSecondary() = preferenceStore.getEnum(
        "pref_panel_reading_focus_effect_secondary",
        PanelFocusEffect.DARKEN,
    )

    fun panelReadingDirection() = preferenceStore.getEnum(
        "pref_panel_reading_direction",
        eu.kanade.tachiyomi.ui.reader.panel.PanelReadingDirection.RIGHT_TO_LEFT,
    )

    val landscapeZoom: Preference<Boolean> = preferenceStore.getBoolean("landscape_zoom", true)

    val cropBordersWebtoon: Preference<Boolean> = preferenceStore.getBoolean("crop_borders_webtoon", false)

    val webtoonSidePadding: Preference<Int> = preferenceStore.getInt("webtoon_side_padding", WEBTOON_PADDING_MIN)

    val readerHideThreshold: Preference<ReaderHideThreshold> = preferenceStore.getEnum(
        "reader_hide_threshold",
        ReaderHideThreshold.LOW,
    )

    val folderPerManga: Preference<Boolean> = preferenceStore.getBoolean("create_folder_per_manga", false)

    val skipRead: Preference<Boolean> = preferenceStore.getBoolean("skip_read", false)

    val skipFiltered: Preference<Boolean> = preferenceStore.getBoolean("skip_filtered", true)

    val skipDupe: Preference<Boolean> = preferenceStore.getBoolean("skip_dupe", false)

    val webtoonDisableZoomOut: Preference<Boolean> = preferenceStore.getBoolean("webtoon_disable_zoom_out", false)

    fun secondaryDisplayScrollSensitivity() =
        preferenceStore.getInt("secondary_display_scroll_sensitivity", SECONDARY_DISPLAY_SCROLL_SENSITIVITY_DEFAULT)

    fun webtoonHoldScrollSpeedsLinked() = preferenceStore.getBoolean("webtoon_hold_scroll_speeds_linked", true)

    fun webtoonHoldScrollForwardSpeed() =
        preferenceStore.getInt("webtoon_hold_scroll_forward_speed", WEBTOON_HOLD_SCROLL_SPEED_DEFAULT)

    fun webtoonHoldScrollBackwardSpeed() =
        preferenceStore.getInt("webtoon_hold_scroll_backward_speed", WEBTOON_HOLD_SCROLL_SPEED_DEFAULT)

    fun readerInputProfile() = preferenceStore.getObjectFromString(
        "reader_input_profile",
        ReaderInputProfile(),
        { json.encodeToString(it) },
        { ReaderInputProfileJson.decodeOrDefault(json, it) },
    )

    // endregion

    // region Split two-page spread

    val dualPageSplitPaged: Preference<Boolean> = preferenceStore.getBoolean("pref_dual_page_split", false)

    val dualPageInvertPaged: Preference<Boolean> = preferenceStore.getBoolean("pref_dual_page_invert", false)

    val dualPageSplitWebtoon: Preference<Boolean> = preferenceStore.getBoolean("pref_dual_page_split_webtoon", false)

    val dualPageInvertWebtoon: Preference<Boolean> = preferenceStore.getBoolean("pref_dual_page_invert_webtoon", false)

    val dualPageRotateToFit: Preference<Boolean> = preferenceStore.getBoolean("pref_dual_page_rotate", false)

    val dualPageRotateToFitInvert: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_dual_page_rotate_invert",
        false,
    )

    val dualPageRotateToFitWebtoon: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_dual_page_rotate_webtoon",
        false,
    )

    val dualPageRotateToFitInvertWebtoon: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_dual_page_rotate_invert_webtoon",
        false,
    )

    // endregion

    // region Side-by-Side View (dual-screen)

    fun companionPageEnabled() = preferenceStore.getBoolean("book_mode_enabled", false)

    fun sideBySideMode() = preferenceStore.getBoolean("side_by_side_mode", false)

    fun manualHingeGap() = preferenceStore.getInt("manual_hinge_gap", 0)

    fun autoEnableSideBySide() = preferenceStore.getBoolean("auto_enable_book_mode", true)

    fun autoDisableSideBySide() = preferenceStore.getBoolean("auto_disable_book_mode", true)

    fun autoAdjustHingeGap() = preferenceStore.getBoolean("auto_adjust_hinge_gap", true)

    fun autoDisableSideBySideOnStart() = preferenceStore.getBoolean("auto_disable_book_mode_on_start", true)

    fun centerSinglePage() = preferenceStore.getBoolean("center_single_page", true)

    fun sideBySidePageOffset() = preferenceStore.getBoolean("side_by_side_page_offset", false)

    // endregion

    // region Color filter

    val customBrightness: Preference<Boolean> = preferenceStore.getBoolean("pref_custom_brightness_key", false)

    val customBrightnessValue: Preference<Int> = preferenceStore.getInt("custom_brightness_value", 0)

    val colorFilter: Preference<Boolean> = preferenceStore.getBoolean("pref_color_filter_key", false)

    val colorFilterValue: Preference<Int> = preferenceStore.getInt("color_filter_value", 0)

    val colorFilterMode: Preference<Int> = preferenceStore.getInt("color_filter_mode", 0)

    val grayscale: Preference<Boolean> = preferenceStore.getBoolean("pref_grayscale", false)

    val invertedColors: Preference<Boolean> = preferenceStore.getBoolean("pref_inverted_colors", false)

    // endregion

    // region Controls

    val readWithLongTap: Preference<Boolean> = preferenceStore.getBoolean("reader_long_tap", true)

    val readWithVolumeKeys: Preference<Boolean> = preferenceStore.getBoolean("reader_volume_keys", false)

    val readWithVolumeKeysInverted: Preference<Boolean> = preferenceStore.getBoolean(
        "reader_volume_keys_inverted",
        false,
    )

    val navigationModePager: Preference<Int> = preferenceStore.getInt("reader_navigation_mode_pager", 0)

    val navigationModeWebtoon: Preference<Int> = preferenceStore.getInt("reader_navigation_mode_webtoon", 0)

    val pagerNavInverted: Preference<TappingInvertMode> = preferenceStore.getEnum(
        "reader_tapping_inverted",
        TappingInvertMode.NONE,
    )

    val webtoonNavInverted: Preference<TappingInvertMode> = preferenceStore.getEnum(
        "reader_tapping_inverted_webtoon",
        TappingInvertMode.NONE,
    )

    val showNavigationOverlayNewUser: Preference<Boolean> = preferenceStore.getBoolean(
        "reader_navigation_overlay_new_user",
        true,
    )

    val showNavigationOverlayOnStart: Preference<Boolean> = preferenceStore.getBoolean(
        "reader_navigation_overlay_on_start",
        false,
    )

    // endregion

    enum class FlashColor {
        BLACK,
        WHITE,
        WHITE_BLACK,
    }

    enum class TappingInvertMode(
        val titleRes: StringResource,
        val shouldInvertHorizontal: Boolean = false,
        val shouldInvertVertical: Boolean = false,
    ) {
        NONE(MR.strings.tapping_inverted_none),
        HORIZONTAL(MR.strings.tapping_inverted_horizontal, shouldInvertHorizontal = true),
        VERTICAL(MR.strings.tapping_inverted_vertical, shouldInvertVertical = true),
        BOTH(MR.strings.tapping_inverted_both, shouldInvertHorizontal = true, shouldInvertVertical = true),
    }

    enum class ReaderHideThreshold(val threshold: Int) {
        HIGHEST(5),
        HIGH(13),
        LOW(31),
        LOWEST(47),
    }

    companion object {
        const val SECONDARY_DISPLAY_SCROLL_SENSITIVITY_MIN = 10
        const val SECONDARY_DISPLAY_SCROLL_SENSITIVITY_MAX = 500
        const val SECONDARY_DISPLAY_SCROLL_SENSITIVITY_DEFAULT = 100

        const val WEBTOON_HOLD_SCROLL_SPEED_MIN = 10
        const val WEBTOON_HOLD_SCROLL_SPEED_MAX = 500
        const val WEBTOON_HOLD_SCROLL_SPEED_DEFAULT = 100

        const val WEBTOON_PADDING_MIN = 0
        const val WEBTOON_PADDING_MAX = 25

        const val MILLI_CONVERSION = 100

        val TapZones = listOf(
            MR.strings.label_default,
            MR.strings.l_nav,
            MR.strings.kindlish_nav,
            MR.strings.edge_nav,
            MR.strings.right_and_left_nav,
            MR.strings.disabled_nav,
        )

        val ImageScaleType = listOf(
            MR.strings.scale_type_fit_screen,
            MR.strings.scale_type_stretch,
            MR.strings.scale_type_fit_width,
            MR.strings.scale_type_fit_height,
            MR.strings.scale_type_original_size,
            MR.strings.scale_type_smart_fit,
        )

        val ZoomStart = listOf(
            MR.strings.zoom_start_automatic,
            MR.strings.zoom_start_left,
            MR.strings.zoom_start_right,
            MR.strings.zoom_start_center,
        )

        val ColorFilterMode = buildList {
            addAll(
                listOf(
                    MR.strings.label_default to BlendMode.SrcOver,
                    MR.strings.filter_mode_multiply to BlendMode.Modulate,
                    MR.strings.filter_mode_screen to BlendMode.Screen,
                ),
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                addAll(
                    listOf(
                        MR.strings.filter_mode_overlay to BlendMode.Overlay,
                        MR.strings.filter_mode_lighten to BlendMode.Lighten,
                        MR.strings.filter_mode_darken to BlendMode.Darken,
                    ),
                )
            }
        }
    }
}
