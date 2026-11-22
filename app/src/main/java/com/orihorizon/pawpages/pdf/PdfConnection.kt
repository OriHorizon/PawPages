package com.orihorizon.pawpages.pdf

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SharedMemory
import android.util.LruCache
import java.io.Closeable
import java.io.File
import java.io.IOException
import kotlin.math.min
import kotlin.math.floor
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class PdfConnection(private val context: Context) : Closeable {
    private val service = MutableStateFlow<IPdfService?>(null)
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service.value = IPdfService.Stub.asInterface(binder)
        }
        override fun onServiceDisconnected(name: ComponentName) {
            service.value = null
        }
        override fun onBindingDied(name: ComponentName) {
            close()
        }
        override fun onNullBinding(name: ComponentName) {
            close()
        }
    }

    suspend fun open(file: File): PdfSession {
        if (!bound) {
            bound = context.bindService(Intent(context, PdfRenderService::class.java),
                connection, Context.BIND_AUTO_CREATE)
            if (!bound) throw IOException("The PDF viewer is unavailable")
        }
        val remote = withTimeoutOrNull(10_000) { service.filterNotNull().first() }
            ?: throw IOException("The PDF viewer could not start")
        // Clean up a document even if its screen is closed during the Binder call.
        var opened: IPdfDocument? = null
        try {
            return withContext(Dispatchers.IO) {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use {
                    val document = remote.open(it)
                    opened = document
                    PdfSession(document, document.pageCount)
                }
            }
        } catch (error: Exception) {
            withContext(Dispatchers.IO + NonCancellable) { runCatching { opened?.close() } }
            throw error
        }
    }

    override fun close() {
        if (bound) {
            bound = false
            context.unbindService(connection)
        }
        service.value = null
    }
}

class PdfSession(private val document: IPdfDocument, val pages: Int) {
    private data class RenderKey(val page: Int, val width: Int)

    private val lock = Mutex()
    private val sizes = LruCache<Int, IntArray>(160)
    private val images = object : LruCache<RenderKey, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: RenderKey, value: Bitmap) = value.byteCount
    }

    fun cachedSize(page: Int): IntArray? = sizes.get(page)
    fun cachedImage(page: Int, width: Int): Bitmap? = images.get(RenderKey(page, width))

    suspend fun size(page: Int): IntArray = withContext(Dispatchers.IO) {
        lock.withLock {
            sizes.get(page) ?: cacheSize(page)
        }
    }

    suspend fun render(page: Int, width: Int): Bitmap = withContext(Dispatchers.IO) {
        lock.withLock {
            val key = RenderKey(page, width)
            images.get(key) ?: renderBitmap(page, width).also { images.put(key, it) }
        }
    }

    suspend fun close() = withContext(Dispatchers.IO + NonCancellable) {
        lock.withLock {
            images.evictAll()
            sizes.evictAll()
            runCatching { document.close() }
        }
    }

    private fun cacheSize(page: Int): IntArray = document.getPageSize(page).also {
        require(it.size == 2 && it[0] > 0 && it[1] > 0)
        sizes.put(page, it)
    }

    private fun renderBitmap(page: Int, requestedWidth: Int): Bitmap {
        val size = sizes.get(page) ?: cacheSize(page)
        val aspect = size[1].toDouble() / size[0]
        require(aspect.isFinite() && aspect > 0)
        val width = floor(min(requestedWidth.coerceIn(1, 2000).toDouble(),
            sqrt(3_000_000.0 / aspect))).toInt().coerceAtLeast(1)
        val height = (width * aspect).toInt().coerceIn(1, 6000)
        return SharedMemory.create("pdf-page", width * height * 4).use { shared ->
            document.render(page, width, height, Bundle().apply { putParcelable("pixels", shared) })
            val pixels = shared.mapReadOnly()
            try {
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                    copyPixelsFromBuffer(pixels)
                }
            } finally {
                SharedMemory.unmap(pixels)
            }
        }
    }
}

@Suppress("DEPRECATION")
internal fun Bundle.sharedPixels(): SharedMemory {
    classLoader = SharedMemory::class.java.classLoader
    return (if (Build.VERSION.SDK_INT >= 33) getParcelable("pixels", SharedMemory::class.java)
    else getParcelable<SharedMemory>("pixels")) ?: throw IOException("Missing PDF page pixels")
}
