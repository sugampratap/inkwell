package com.xnotes.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.xnotes.R

/**
 * B2's type: Plus Jakarta Sans, with sizes and weights read off the mockups (CSS px = sp). Screens
 * use these by role; nothing in a screen should spell out a font size.
 */
object InkType {

    val Jakarta = FontFamily(
        Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
        Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
        Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
        Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
        Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
    )

    // CSS centres a line's text in its line box; so does this, untrimmed, so heights match the mockups.
    private val centred = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

    private fun style(size: Float, line: Float, weight: FontWeight, tracking: Float = 0f) = TextStyle(
        fontFamily = Jakarta,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = weight,
        letterSpacing = tracking.sp,
        lineHeightStyle = centred,
    )

    /** Library and Settings page titles (.mh h1). */
    val display = style(28f, 34f, FontWeight.ExtraBold, -0.6f)

    /** Section headings in a scrolling page (.h-sec). */
    val title = style(20f, 24f, FontWeight.Bold, -0.3f)

    /** Sheet and popover titles (.ms-h b, .pp-h b). */
    val sheetTitle = style(19f, 24f, FontWeight.ExtraBold, -0.4f)

    /** Card titles (.ins-h b) and the editor's note title. */
    val cardTitle = style(17f, 22f, FontWeight.ExtraBold, -0.3f)

    /** The New button. */
    val brandButton = style(16f, 20f, FontWeight.ExtraBold, -0.1f)

    /** List and menu rows (.row, .m-row). */
    val row = style(15f, 20f, FontWeight.Medium)

    /** Grouped-row and card titles (.gr .gt b, .cw-t). */
    val rowStrong = style(15f, 20f, FontWeight.SemiBold)

    /** Strong buttons. */
    val button = style(15f, 20f, FontWeight.Bold)

    /** Secondary and ghost buttons. */
    val buttonSmall = style(14f, 18f, FontWeight.SemiBold)

    /** Default chrome text. */
    val body = style(14f, 20f, FontWeight.Medium)

    /** Group, menu and section headers (.group-h, .m-h, .sec-t). */
    val label = style(13f, 18f, FontWeight.Bold)

    /** Subtitles, meta lines, counts (.gr .gt span, .cw-m). */
    val meta = style(13f, 18f, FontWeight.Medium)

    /** Chips. */
    val chip = style(13.5f, 18f, FontWeight.SemiBold)

    /** Tab labels and badges. */
    val small = style(12f, 16f, FontWeight.SemiBold)

    /** Nib and preset captions. */
    val tiny = style(11f, 13f, FontWeight.SemiBold)

    /** The editor header's note title (W 338): Bold, not cardTitle's ExtraBold. */
    val headerTitle = style(17f, 22f, FontWeight.Bold, -0.2f)

    /** The header's page counter "7 / 12" (W 342); callers add `.tnum()`. */
    val counter = style(13f, 18f, FontWeight.SemiBold)

    /** Hints under a control and the More menu's footer (.to-hint, .to-mfoot). */
    val hint = style(12.5f, 18f, FontWeight.Medium)

    /** A row's second line, a sheet's small print and a bookmark's page (.st-sub, .ps-hint, .pp-bm small): 12.5/17. */
    val caption = style(12.5f, 17f, FontWeight.Medium)

    /** The Insert card's wide tiles, PDF and Voice recording (.ins-b). */
    val tileWide = style(14f, 17f, FontWeight.Bold)

    /** The Insert card's grid tiles (.ins-i). */
    val tileSmall = style(13f, 16f, FontWeight.SemiBold)

    /** Material's own scale, re-set in Jakarta only: sizes stay what existing screens expect. */
    val material: Typography = Typography().run {
        copy(
            displayLarge = displayLarge.copy(fontFamily = Jakarta),
            displayMedium = displayMedium.copy(fontFamily = Jakarta),
            displaySmall = displaySmall.copy(fontFamily = Jakarta),
            headlineLarge = headlineLarge.copy(fontFamily = Jakarta),
            headlineMedium = headlineMedium.copy(fontFamily = Jakarta),
            headlineSmall = headlineSmall.copy(fontFamily = Jakarta),
            titleLarge = titleLarge.copy(fontFamily = Jakarta),
            titleMedium = titleMedium.copy(fontFamily = Jakarta),
            titleSmall = titleSmall.copy(fontFamily = Jakarta),
            bodyLarge = bodyLarge.copy(fontFamily = Jakarta),
            bodyMedium = bodyMedium.copy(fontFamily = Jakarta),
            bodySmall = bodySmall.copy(fontFamily = Jakarta),
            labelLarge = labelLarge.copy(fontFamily = Jakarta),
            labelMedium = labelMedium.copy(fontFamily = Jakarta),
            labelSmall = labelSmall.copy(fontFamily = Jakarta),
        )
    }
}

/** Tabular figures, for counts, times and sizes that change in place. */
fun TextStyle.tnum(): TextStyle = copy(fontFeatureSettings = "tnum")
