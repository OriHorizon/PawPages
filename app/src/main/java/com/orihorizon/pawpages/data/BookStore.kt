package com.orihorizon.pawpages.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class BookStore(private val context: Context) {
    private val root = File(context.noBackupFilesDir, "books")

    fun collection(): List<Book> = root.listFiles()
        .orEmpty()
        .filter { it.isDirectory && isBookId(it.name) }
        .mapNotNull { runCatching { book(it.name) }.getOrNull() }
        .sortedByDescending(Book::importedAt)

    fun book(id: String): Book {
        val data = read(directory(id), "book.json") ?: throw IOException("Missing book")
        return Book(id, data.getString("title"), data.getInt("pageCount"),
            data.takeUnless { it.isNull("bookmark") }?.getInt("bookmark"),
            data.getLong("importedAt"))
    }

    fun pdf(id: String) = File(directory(id), "book.pdf")

    fun suggestedTitle(uri: Uri): String {
        val name = context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
            .orEmpty()
        return name.replace(Regex("\\.pdf$", RegexOption.IGNORE_CASE), "").trim().take(120)
    }

    suspend fun import(uri: Uri, title: String, inspect: suspend (File) -> Int): Book =
        withContext(Dispatchers.IO) {
            require(title.isNotBlank() && title.length <= 120)
            if (!root.mkdirs() && !root.isDirectory) throw IOException("Can't create bookshelf")
            val id = UUID.randomUUID().toString()
            val staging = File(root, ".$id")
            if (!staging.mkdir()) throw IOException("Can't create a draft book")
            try {
                val pdf = File(staging, "book.pdf")
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Can't read the selected file")
                input.use {
                    pdf.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            ensureActive()
                            val bytes = it.read(buffer)
                            if (bytes == -1) break
                            output.write(buffer, 0, bytes)
                        }
                        output.fd.sync()
                    }
                }
                val pages = inspect(pdf)
                if (pages < 1) throw IOException("The PDF has no pages")
                val book = Book(id, title, pages, null, System.currentTimeMillis())
                ensureActive()
                // Publish the PDF and its metadata together with a same-directory rename.
                withContext(NonCancellable) {
                    saveBook(staging, book)
                    if (!staging.renameTo(directory(id))) throw IOException("Can't save the book")
                    book
                }
            } finally {
                staging.deleteRecursively()
            }
        }

    fun setBookmark(id: String, page: Int?): Book = synchronized(writeLock) {
        val current = book(id)
        require(page == null || page in 0 until current.pageCount)
        current.copy(bookmark = page).also { saveBook(directory(id), it) }
    }

    fun marks(id: String): List<Mark> {
        val items = read(directory(id), "marks.json")?.getJSONArray("marks") ?: return emptyList()
        return List(items.length()) { index ->
            val item = items.getJSONObject(index)
            Mark(item.getString("id"), item.getInt("page"),
                item.getDouble("left").toFloat(), item.getDouble("top").toFloat(),
                item.getDouble("right").toFloat(), item.getDouble("bottom").toFloat(),
                item.takeUnless { it.isNull("note") }?.getString("note"))
        }
    }

    fun updateMark(id: String, mark: Mark, remove: Boolean): List<Mark> = synchronized(writeLock) {
        val dir = directory(id)
        if (!dir.isDirectory) throw IOException("Missing book")
        val updated = marks(id).filterNot { it.id == mark.id }.toMutableList()
        if (!remove) updated.add(mark)
        val items = JSONArray()
        updated.forEach {
            items.put(JSONObject()
                .put("id", it.id).put("page", it.page)
                .put("left", it.left.toDouble()).put("top", it.top.toDouble())
                .put("right", it.right.toDouble()).put("bottom", it.bottom.toDouble())
                .put("note", it.note ?: JSONObject.NULL))
        }
        write(dir, "marks.json", JSONObject().put("version", 1).put("marks", items))
        updated
    }

    private fun saveBook(dir: File, book: Book) = write(dir, "book.json", JSONObject()
        .put("version", 1)
        .put("title", book.title)
        .put("pageCount", book.pageCount)
        .put("bookmark", book.bookmark ?: JSONObject.NULL)
        .put("importedAt", book.importedAt))

    private fun directory(id: String): File {
        require(isBookId(id))
        return File(root, id)
    }

    private fun read(dir: File, name: String): JSONObject? = synchronized(writeLock) {
        try {
            JSONObject(AtomicFile(File(dir, name)).readFully().decodeToString())
        } catch (_: java.io.FileNotFoundException) {
            null
        }
    }

    private fun write(dir: File, name: String, data: JSONObject) = synchronized(writeLock) {
        val file = AtomicFile(File(dir, name))
        val stream = file.startWrite()
        try {
            stream.write(data.toString().encodeToByteArray())
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    private companion object {
        val writeLock = Any()
        val idPattern = Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")
        fun isBookId(id: String) = idPattern.matches(id)
    }
}
