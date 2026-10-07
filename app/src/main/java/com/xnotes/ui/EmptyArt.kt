package com.xnotes.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Which picture an empty state shows. */
internal enum class EmptyArt { PAGES, SEARCH, RECENT, TRASH }

/**
 * An empty state's picture (Frame 5), drawn by the kit's [InkLineArt]: 1.5dp ink lines, sheets in the raised surface,
 * and one pale sun behind, all from B2 tokens, so it reads in Light, Dark and OLED alike. 200 × 150 dp unless
 * [modifier] sizes it (the first run draws it at 300 × 225).
 */
@Composable
internal fun EmptyIllustration(art: EmptyArt, modifier: Modifier = Modifier) {
    val picture = when (art) {
        EmptyArt.PAGES -> LineArt.PAGES
        EmptyArt.SEARCH -> LineArt.SEARCH
        EmptyArt.RECENT -> LineArt.RECENT
        EmptyArt.TRASH -> LineArt.TRASH
    }
    InkLineArt(picture, modifier.size(200.dp, 150.dp))
}
