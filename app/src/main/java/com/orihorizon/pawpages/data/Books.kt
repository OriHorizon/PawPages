package com.orihorizon.pawpages.data

import android.graphics.RectF
import java.util.UUID

data class Book(
    val id: String,
    val title: String,
    val pageCount: Int,
    val bookmark: Int?,
    val importedAt: Long
)

data class Mark(
    val id: String = UUID.randomUUID().toString(),
    val page: Int,
    val left: Float,
    val top: Float,
    val right: Float = left,
    val bottom: Float = top,
    val note: String? = null
) {
    // A mark belongs to the page, independent of display size or device orientation.
    fun toPixels(width: Float, height: Float) =
        RectF(left * width, top * height, right * width, bottom * height)
}
