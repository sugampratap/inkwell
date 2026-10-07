package com.xnotes.ui

import androidx.annotation.StringRes
import com.xnotes.R

/**
 * The Settings screen's map: its categories, in the order the category list shows them, and every
 * row each one holds. The screen draws its rows from [SettingId] (a row's title and description
 * come from here), so the search over this catalogue can never offer a setting the screen does not
 * show, or miss one it does.
 *
 * The order follows Samsung Notes and Starnote: everyday look and feel first, then the pen, then
 * the page and the library, with the options most people never touch gathered in Advanced at the
 * bottom, just above About.
 */
enum class SettingsCategory(@StringRes val title: Int, @StringRes val summary: Int) {
    GENERAL(R.string.settings_cat_general, R.string.settings_cat_general_summary),
    APPEARANCE(R.string.settings_cat_appearance, R.string.settings_cat_appearance_summary),
    WRITING(R.string.settings_cat_writing, R.string.settings_cat_writing_summary),
    PAGES(R.string.settings_cat_pages, R.string.settings_cat_pages_summary),
    TEXT(R.string.settings_cat_text, R.string.settings_cat_text_summary),
    LIBRARY(R.string.settings_cat_library, R.string.settings_cat_library_summary),
    EXPORT(R.string.settings_cat_export, R.string.settings_cat_export_summary),
    ADVANCED(R.string.settings_cat_advanced, R.string.settings_cat_advanced_summary),
    ABOUT(R.string.settings_cat_about, R.string.settings_cat_about_summary),
}

/** One row of the Settings screen: the category it lives in, its title and its one-line description. */
enum class SettingId(val category: SettingsCategory, @StringRes val title: Int, @StringRes val description: Int) {
    // General
    START_FULLSCREEN(SettingsCategory.GENERAL, R.string.settings_fullscreen, R.string.settings_fullscreen_desc),
    HOME_OPENS_TO(SettingsCategory.GENERAL, R.string.pref_home_opens_to, R.string.settings_home_opens_to_desc),
    FILENAME_TEMPLATE(SettingsCategory.GENERAL, R.string.settings_filename, R.string.settings_filename_desc),

    // Appearance
    THEME(SettingsCategory.APPEARANCE, R.string.settings_theme, R.string.settings_theme_desc),
    CORNERS(SettingsCategory.APPEARANCE, R.string.pref_corners, R.string.settings_corners_desc),
    COLOUR_MODE(SettingsCategory.APPEARANCE, R.string.material_tone, R.string.settings_colour_mode_desc),
    COLOUR_STYLE(SettingsCategory.APPEARANCE, R.string.material_style, R.string.settings_colour_style_desc),
    CONTRAST(SettingsCategory.APPEARANCE, R.string.settings_contrast, R.string.settings_contrast_desc),

    // Writing & S Pen
    FINGER_DRAWS(SettingsCategory.WRITING, R.string.settings_finger_draws, R.string.settings_finger_draws_desc),
    LOCKED_TWO_FINGER_SCROLL(SettingsCategory.WRITING, R.string.settings_locked_two_finger_scroll, R.string.settings_locked_two_finger_scroll_desc),
    DETECT_SHAPES(SettingsCategory.WRITING, R.string.settings_detect_shapes, R.string.settings_detect_shapes_desc),
    PEN_BOX(SettingsCategory.WRITING, R.string.settings_pen_box, R.string.settings_pen_box_desc),
    PEN_BUTTON_HOLD(SettingsCategory.WRITING, R.string.settings_pen_button_hold, R.string.settings_pen_button_hold_desc),
    PEN_BUTTON_HOVER(SettingsCategory.WRITING, R.string.settings_pen_button_hover, R.string.settings_pen_button_hover_desc),
    STYLUS_DOUBLE_TAP(SettingsCategory.WRITING, R.string.pref_stylus_double_tap, R.string.settings_stylus_double_tap_desc),
    STYLUS_BUTTON_TAP(SettingsCategory.WRITING, R.string.pref_stylus_button_tap, R.string.settings_stylus_button_tap_desc),
    REDMI_BUTTON_1(SettingsCategory.WRITING, R.string.settings_redmi_1, R.string.settings_redmi_desc),
    REDMI_BUTTON_2(SettingsCategory.WRITING, R.string.settings_redmi_2, R.string.settings_redmi_desc),
    TWO_FINGER_TAP(SettingsCategory.WRITING, R.string.pref_two_finger_tap, R.string.settings_two_finger_tap_desc),
    THREE_FINGER_TAP(SettingsCategory.WRITING, R.string.pref_three_finger_tap, R.string.settings_three_finger_tap_desc),
    ZOOM_LOCK_PAN(SettingsCategory.WRITING, R.string.settings_zoom_lock_pan, R.string.settings_zoom_lock_pan_desc),

    // Toolbar
    TOOLBAR_STYLE(SettingsCategory.GENERAL, R.string.pref_toolbar_style, R.string.settings_toolbar_style_desc),
    TOOLBAR_POSITION(SettingsCategory.GENERAL, R.string.pref_toolbar_position, R.string.settings_toolbar_position_desc),
    TOOLBAR_SIZE(SettingsCategory.GENERAL, R.string.pref_toolbar_size, R.string.settings_toolbar_size_desc),
    TOOLBAR_COLOURS(SettingsCategory.GENERAL, R.string.settings_toolbar_colours, R.string.settings_toolbar_colours_desc),
    CUSTOMISE_TOOLBAR(SettingsCategory.GENERAL, R.string.settings_customise_toolbar, R.string.settings_customise_toolbar_desc),
    SELECTION_BAR(SettingsCategory.GENERAL, R.string.settings_selection_bar, R.string.settings_selection_bar_desc),

    // Pages & paper
    PAGE_SIZE(SettingsCategory.PAGES, R.string.pref_default_page_size, R.string.settings_page_size_desc),
    ORIENTATION(SettingsCategory.PAGES, R.string.pref_orientation, R.string.settings_orientation_desc),
    CUSTOM_SIZE(SettingsCategory.PAGES, R.string.settings_custom_size, R.string.settings_custom_size_desc),
    PAGE_COLOUR(SettingsCategory.PAGES, R.string.pref_page_colour, R.string.settings_page_colour_desc),
    PAGE_FOLLOWS_THEME(SettingsCategory.PAGES, R.string.settings_page_follows_theme, R.string.settings_page_follows_theme_desc),
    HIDE_BORDERS(SettingsCategory.PAGES, R.string.settings_hide_borders, R.string.settings_hide_borders_desc),
    SIDE_MARGIN(SettingsCategory.PAGES, R.string.settings_side_margin, R.string.settings_side_margin_desc),
    MIN_ZOOM(SettingsCategory.PAGES, R.string.settings_min_zoom, R.string.settings_min_zoom_desc),
    MAX_ZOOM(SettingsCategory.PAGES, R.string.settings_max_zoom, R.string.settings_max_zoom_desc),

    // Text & fonts
    MARKDOWN(SettingsCategory.TEXT, R.string.settings_markdown, R.string.settings_markdown_desc),
    SLASH_COMMANDS(SettingsCategory.TEXT, R.string.settings_slash, R.string.settings_slash_desc),
    CODE_LANGUAGE(SettingsCategory.TEXT, R.string.settings_code_language, R.string.settings_code_language_desc),
    CODE_THEME(SettingsCategory.TEXT, R.string.settings_code_theme, R.string.settings_code_theme_desc),
    FONTS(SettingsCategory.TEXT, R.string.settings_fonts, R.string.settings_fonts_desc),

    // Library & files
    NOTES_FOLDER(SettingsCategory.LIBRARY, R.string.settings_notes_folder, R.string.settings_notes_folder_desc),
    HOME_LAYOUT(SettingsCategory.LIBRARY, R.string.pref_default_layout, R.string.settings_default_layout_desc),
    SWITCHER_LAYOUTS(SettingsCategory.LIBRARY, R.string.pref_switcher_layouts, R.string.settings_switcher_layouts_desc),
    SIDEBAR_SECTIONS(SettingsCategory.LIBRARY, R.string.pref_sidebar_sections, R.string.settings_sidebar_sections_desc),
    DATES(SettingsCategory.LIBRARY, R.string.pref_dates, R.string.settings_dates_desc),
    TAPPING_FILE(SettingsCategory.LIBRARY, R.string.pref_tapping_file, R.string.settings_tapping_file_desc),
    PER_FOLDER_VIEWS(SettingsCategory.LIBRARY, R.string.settings_per_folder, R.string.settings_per_folder_desc),
    CREATE_BUTTON(SettingsCategory.LIBRARY, R.string.settings_create_button, R.string.settings_create_button_desc),
    FOLDER_COUNTS(SettingsCategory.LIBRARY, R.string.settings_folder_counts, R.string.settings_folder_counts_desc),
    EXTENSIONS(SettingsCategory.LIBRARY, R.string.settings_extensions, R.string.settings_extensions_desc),
    COLOUR_NAMES(SettingsCategory.LIBRARY, R.string.settings_tag_names, R.string.settings_colour_names_desc),
    KEEP_DELETED(SettingsCategory.LIBRARY, R.string.pref_keep_deleted, R.string.settings_keep_deleted_desc),

    // Export & sharing
    PDF_BOOKMARKS(SettingsCategory.EXPORT, R.string.settings_pdf_bookmarks, R.string.settings_pdf_bookmarks_desc),

    // Advanced
    CACHE_RESOLUTION(SettingsCategory.ADVANCED, R.string.settings_cache, R.string.settings_cache_desc),
    FRONT_BUFFERING(SettingsCategory.ADVANCED, R.string.settings_front_buffering, R.string.settings_front_buffering_desc),
    DEBUG_OVERLAY(SettingsCategory.ADVANCED, R.string.settings_debug_overlay, R.string.settings_debug_overlay_desc),
    RESET_ALL(SettingsCategory.ADVANCED, R.string.settings_reset_all, R.string.settings_reset_all_desc),
}

/** A setting as the search sees it: its words already resolved from resources. */
data class SettingSearchEntry(val id: SettingId, val title: String, val description: String, val categoryTitle: String)

/**
 * The entries matching [query], best first. Every word typed has to appear somewhere in the
 * entry's title, description or category name (case and accents aside), so "side button" finds
 * both "Press and hold" (by its description) and "Stylus side button (tap)". A title hit
 * ranks above a description-only one, and a title that starts with the query ranks first of all.
 */
fun searchSettings(query: String, entries: List<SettingSearchEntry>): List<SettingSearchEntry> {
    val words = fold(query).split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    val whole = words.joinToString(" ")
    return entries
        .mapNotNull { e ->
            val title = fold(e.title)
            val hay = title + " " + fold(e.description) + " " + fold(e.categoryTitle)
            if (!words.all { it in hay }) return@mapNotNull null
            val rank = when {
                title.startsWith(whole) -> 0
                words.all { it in title } -> 1
                else -> 2
            }
            rank to e
        }
        .sortedBy { it.first } // stable, so equal ranks keep the screen's own order
        .map { it.second }
}

/** Lower-case, strip accents and collapse runs of whitespace and punctuation into single spaces. */
private fun fold(s: String): String {
    val decomposed = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
    val sb = StringBuilder(decomposed.length)
    var space = false
    for (ch in decomposed) {
        when {
            Character.getType(ch) == Character.NON_SPACING_MARK.toInt() -> Unit
            ch.isLetterOrDigit() || ch == '#' || ch == '/' || ch == '.' -> { sb.append(ch); space = false }
            !space && sb.isNotEmpty() -> { sb.append(' '); space = true }
        }
    }
    return sb.toString().trimEnd()
}

/** The rarely used stylus mappings fold under "Other styluses" (r2_settings Frame 3); search still finds them, and opening one unfolds the group. */
internal object SettingsFold {
    val OTHER_STYLUSES: Set<SettingId> = setOf(SettingId.STYLUS_DOUBLE_TAP, SettingId.STYLUS_BUTTON_TAP, SettingId.REDMI_BUTTON_1, SettingId.REDMI_BUTTON_2)

    /** How many of them do something: the fold's "2 set" / "None set". */
    fun othersSet(p: com.xnotes.settings.Preferences): Int =
        listOf(p.stylusDoubleTap, p.stylusButtonTap, p.stylusButton1Tap, p.stylusButton2Tap).count { it != "none" }
}
