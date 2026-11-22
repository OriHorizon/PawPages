package com.orihorizon.pawpages.pdf

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor

class PdfRenderService : Service() {
    override fun onBind(intent: Intent): IBinder = object : IPdfService.Stub() {
        override fun open(source: ParcelFileDescriptor): IPdfDocument {
            val renderer = try {
                PdfRenderer(source)
            } catch (error: Exception) {
                source.close()
                throw error
            }
            return Document(renderer)
        }
    }

    // Binder can call the document concurrently. PdfRenderer requires one open page at a time.
    private class Document(private val renderer: PdfRenderer) : IPdfDocument.Stub() {
        private var closed = false

        @Synchronized
        override fun getPageCount() = renderer.pageCount

        @Synchronized
        override fun getPageSize(index: Int): IntArray =
            renderer.openPage(index).use { intArrayOf(it.width, it.height) }

        @Synchronized
        override fun render(index: Int, width: Int, height: Int, target: Bundle) {
            require(width in 1..2000 && height in 1..6000 && width * height <= 3_010_000)
            target.sharedPixels().use { shared ->
                require(shared.size == width * height * 4)
                renderer.openPage(index).use { page ->
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val pixels = shared.mapReadWrite()
                        try {
                            bitmap.copyPixelsToBuffer(pixels)
                        } finally {
                            android.os.SharedMemory.unmap(pixels)
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }

        @Synchronized
        override fun close() {
            if (!closed) {
                closed = true
                renderer.close()
            }
        }
    }
}
