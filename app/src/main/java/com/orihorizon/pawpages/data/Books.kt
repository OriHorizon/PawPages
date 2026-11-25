package com.orihorizon.pawpages.data

import android.graphics.RectF
import java.util.UUID

/**
 * The bookshelf entry for one imported PDF. The PDF itself is stored separately.
 *
 * @property id Stable identifier used to find the book's local files.
 * @property title User-facing title chosen during import.
 * @property pageCount Number of pages reported by the PDF renderer.
 * @property bookmark Saved page index, starting at zero. `null` means there is no bookmark.
 * @property importedAt Import time in milliseconds since the Unix epoch, used to sort the shelf.
 */
data class Book(
    val id: String,
    val title: String,
    val pageCount: Int,
    val bookmark: Int?,
    val importedAt: Long
)

/**
 * A highlight or a pinned note on one page. Coordinates are fractions of the displayed
 * page's width and height, measured from the top-left corner. This keeps a mark aligned with
 * its passage when the page is displayed at a different size.
 *
 * @property id Stable identifier for editing or deleting the mark.
 * @property page Zero-based page index in the book.
 * @property left Horizontal fraction of the highlight's left edge or the note's anchor.
 * @property top Vertical fraction of the highlight's top edge or the note's anchor.
 * @property right Horizontal fraction of the highlight's right edge. Defaults to [left] for a pin.
 * @property bottom Vertical fraction of the highlight's bottom edge. Defaults to [top] for a pin.
 * @property note Text of a saved note. A saved mark with `null` here is a highlight.
 */
data class Mark(
    val id: String = UUID.randomUUID().toString(),
    val page: Int,
    val left: Float,
    val top: Float,
    val right: Float = left,
    val bottom: Float = top,
    val note: String? = null
) {
    /**
     * Converts the stored highlight bounds to coordinates for drawing on the current page.
     *
     * @param width Displayed page width in pixels.
     * @param height Displayed page height in pixels.
     * @return Rectangle in pixels relative to the page's top-left corner.
     */
    fun toPixels(width: Float, height: Float) =
        RectF(left * width, top * height, right * width, bottom * height)
}
