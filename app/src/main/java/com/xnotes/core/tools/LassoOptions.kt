package com.xnotes.core.tools

import com.xnotes.core.model.CanvasItem
import com.xnotes.core.model.ImageItem
import com.xnotes.core.model.ShapeItem
import com.xnotes.core.model.Stroke
import com.xnotes.core.model.TextItem

/** How the lasso is drawn: a free loop, or a box dragged corner to corner. */
enum class LassoShape(val id: String) {
    FREEFORM("free"),
    RECTANGLE("rect");

    companion object {
        fun fromId(id: String?): LassoShape = entries.firstOrNull { it.id == id } ?: FREEFORM
    }
}

/**
 * Which kinds of object the lasso picks up, like Samsung Notes' "Select" filter: everything, or
 * only the handwriting, the pictures, the text boxes or the shapes inside the loop. Lets a page of
 * ink over a photo be restyled without dragging the photo along, or the photo moved from under it.
 */
enum class LassoFilter(val id: String) {
    ALL("all"),
    HANDWRITING("handwriting"),
    IMAGES("images"),
    TEXT("text"),
    SHAPES("shapes");

    fun accepts(item: CanvasItem): Boolean = when (this) {
        ALL -> true
        HANDWRITING -> item is Stroke
        IMAGES -> item is ImageItem
        TEXT -> item is TextItem
        // Tape is laid like a shape and picked up with them.
        SHAPES -> item is ShapeItem || item is com.xnotes.core.model.TapeItem
    }

    companion object {
        fun fromId(id: String?): LassoFilter = entries.firstOrNull { it.id == id } ?: ALL
    }
}

/**
 * The lasso tool's settings, kept in the app preferences (`lasso_shape`, `lasso_filter`,
 * `lasso_tap_select`) and shared by both editors. [tapSelect] makes a tap with the lasso pick the
 * object under it, which is the quickest way to grab one thing from a crowded page.
 */
data class LassoOptions(
    val shape: LassoShape = LassoShape.FREEFORM,
    val filter: LassoFilter = LassoFilter.ALL,
    val tapSelect: Boolean = true,
)
