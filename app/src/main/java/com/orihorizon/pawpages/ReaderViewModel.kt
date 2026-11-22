package com.orihorizon.pawpages

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.orihorizon.pawpages.data.Book
import com.orihorizon.pawpages.data.BookStore
import com.orihorizon.pawpages.data.Mark
import com.orihorizon.pawpages.pdf.PdfConnection
import com.orihorizon.pawpages.pdf.PdfSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class Screen { SHELF, IMPORT, READER }

data class ReaderState(
    val screen: Screen = Screen.SHELF,
    val collectionReady: Boolean = false,
    val books: List<Book> = emptyList(),
    val book: Book? = null,
    val session: PdfSession? = null,
    val startPage: Int = 0,
    val marks: Map<Int, List<Mark>> = emptyMap(),
    val chosenName: String? = null,
    val busy: Boolean = false,
    val error: String? = null
)

class ReaderViewModel(application: Application, private val saved: SavedStateHandle) :
    AndroidViewModel(application) {
    private val store = BookStore(application)
    private val pdf = PdfConnection(application)
    private val state = MutableStateFlow(ReaderState())
    val ui = state.asStateFlow()
    private val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val notices = messages.asSharedFlow()
    private val writes = Mutex()
    private var readerJob: Job? = null
    private var selectionJob: Job? = null
    private var selectedUri: Uri? = saved["draftUri"]

    init {
        refreshCollection()
        when (saved.get<String>("screen")) {
            Screen.READER.name -> saved.get<String>("bookId")?.let { openBook(it, saved["page"]) }
            Screen.IMPORT.name -> {
                showImport()
                selectedUri?.let { pick(it, restore = true) }
            }
        }
    }

    private fun refreshCollection() = viewModelScope.launch {
        val books = withContext(Dispatchers.IO) { store.collection() }
        state.value = state.value.copy(books = books, collectionReady = true)
    }

    fun showImport() {
        saved["screen"] = Screen.IMPORT.name
        state.value = state.value.copy(screen = Screen.IMPORT, error = null)
    }

    fun pickerUnavailable() {
        state.value = state.value.copy(error = "This device has no file picker for PDFs.")
    }

    fun pick(uri: Uri, restore: Boolean = false) {
        if (state.value.screen != Screen.IMPORT || state.value.busy) return
        selectionJob?.cancel()
        if (!restore && uri != selectedUri) {
            releaseSelection()
            runCatching {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        selectedUri = uri
        saved["draftUri"] = uri
        if (!restore) saved.remove<String>("draftTitle")
        state.value = state.value.copy(chosenName = "PDF selected", error = null)
        selectionJob = viewModelScope.launch {
            val suggested = withContext(Dispatchers.IO) {
                runCatching { store.suggestedTitle(uri) }.getOrDefault("")
            }
            if (selectedUri == uri && state.value.screen == Screen.IMPORT) {
                if (saved.get<String>("draftTitle") == null) saved["draftTitle"] = suggested
                state.value = state.value.copy(
                    chosenName = suggested.takeIf(String::isNotBlank)?.plus(".pdf") ?: "PDF selected"
                )
            }
        }
    }

    fun draftTitle(): String = saved["draftTitle"] ?: ""
    fun updateTitle(title: String) { saved["draftTitle"] = title }

    fun addBook() {
        val uri = selectedUri ?: return
        val title = draftTitle().trim()
        if (title.isEmpty() || state.value.busy) return
        state.value = state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val imported = store.import(uri, title) { file ->
                    withContext(Dispatchers.Main.immediate) {
                        val session = pdf.open(file)
                        try {
                            session.pages
                        } finally {
                            session.close()
                        }
                    }
                }
                releaseSelection()
                showShelf()
                val books = state.value.books
                if (books.none { it.id == imported.id }) {
                    state.value = state.value.copy(books = listOf(imported) + books)
                }
                messages.tryEmit("Added to your bookshelf")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                state.value = state.value.copy(
                    busy = false,
                    error = if (error is SecurityException) {
                        "Choose an unlocked PDF that you can access."
                    } else {
                        "Couldn’t add this PDF. Check that it opens and that your device has space."
                    }
                )
            }
        }
    }

    fun openBook(id: String, restoredPage: Int? = null) {
        readerJob?.cancel()
        val previous = state.value.session
        if (previous != null) viewModelScope.launch { previous.close() }
        val known = state.value.books.firstOrNull { it.id == id }
        saved["screen"] = Screen.READER.name
        saved["bookId"] = id
        saved["page"] = restoredPage
        state.value = state.value.copy(screen = Screen.READER, book = known, session = null,
            startPage = restoredPage ?: known?.bookmark ?: 0, marks = emptyMap(), error = null)
        readerJob = viewModelScope.launch {
            var unclaimed: PdfSession? = null
            try {
                val (book, marks) = writes.withLock {
                    withContext(Dispatchers.IO) {
                        store.book(id) to store.marks(id).groupBy(Mark::page)
                    }
                }
                val opened = pdf.open(store.pdf(id))
                unclaimed = opened
                val start = (restoredPage ?: book.bookmark ?: 0)
                    .coerceIn(0, (opened.pages - 1).coerceAtLeast(0))
                state.value = state.value.copy(book = book, session = opened,
                    startPage = start, marks = marks)
                unclaimed = null
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                state.value = state.value.copy(error = "Couldn’t open this PDF.")
            } finally {
                unclaimed?.close()
            }
        }
    }

    fun retryReader() = saved.get<String>("bookId")?.let { openBook(it, saved["page"]) }

    fun resumePage(fallback: Int): Int = saved["page"] ?: fallback

    fun setVisiblePage(page: Int) {
        if (state.value.screen == Screen.READER) saved["page"] = page
    }

    fun saveBookmark(page: Int?) {
        val book = state.value.book ?: return
        viewModelScope.launch {
            writes.withLock {
                try {
                    val updated = withContext(Dispatchers.IO) { store.setBookmark(book.id, page) }
                    val current = state.value
                    state.value = current.copy(
                        book = if (current.book?.id == book.id) updated else current.book,
                        books = current.books.map { if (it.id == book.id) updated else it }
                    )
                    messages.tryEmit(if (page == null) "Bookmark removed" else "Bookmarked page ${page + 1}")
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    messages.tryEmit("Couldn’t save the bookmark")
                }
            }
        }
    }

    fun saveMark(mark: Mark, remove: Boolean = false) {
        val id = state.value.book?.id ?: return
        viewModelScope.launch {
            writes.withLock {
                try {
                    val grouped = withContext(Dispatchers.IO) {
                        store.updateMark(id, mark, remove).groupBy(Mark::page)
                    }
                    if (state.value.book?.id == id) state.value = state.value.copy(marks = grouped)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    messages.tryEmit("Couldn’t save the annotation")
                }
            }
        }
    }

    fun goBack(): Boolean = when (state.value.screen) {
        Screen.SHELF -> false
        Screen.IMPORT -> {
            if (!state.value.busy) {
                releaseSelection()
                showShelf()
            }
            true
        }
        Screen.READER -> {
            readerJob?.cancel()
            state.value.session?.let { viewModelScope.launch { it.close() } }
            showShelf()
            true
        }
    }

    private fun showShelf() {
        val previous = state.value
        saved["screen"] = Screen.SHELF.name
        saved.remove<String>("bookId")
        saved.remove<Int>("page")
        state.value = ReaderState(books = previous.books, collectionReady = previous.collectionReady)
    }

    private fun releaseSelection() {
        selectionJob?.cancel()
        selectedUri?.let {
            runCatching {
                getApplication<Application>().contentResolver.releasePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        selectedUri = null
        saved.remove<Uri>("draftUri")
        saved.remove<String>("draftTitle")
    }

    override fun onCleared() {
        pdf.close()
    }
}
