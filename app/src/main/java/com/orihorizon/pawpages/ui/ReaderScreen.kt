package com.orihorizon.pawpages.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.orihorizon.pawpages.ReaderState
import com.orihorizon.pawpages.ReaderViewModel
import com.orihorizon.pawpages.data.Mark
import com.orihorizon.pawpages.pdf.PdfSession
import kotlinx.coroutines.CoroutineScope

internal class ReaderScreen(
    private val context: Context,
    private val model: ReaderViewModel,
    private val scope: CoroutineScope
) {
    val view = context.stack()
    private val title = context.heading("Opening book…", 21f)
    private val pageText = context.label("Opening PDF…", 13f, Palette.deepRose, bold = true)
    private val guidance = context.label("", 13f, Palette.secondary)
    private val header = context.row().apply { pad(10, 6, 12, 6) }
    private val bookmarkButton = iconButton(context, "Bookmark this page", Symbol.BOOKMARK, ::bookmark)
    private val readerArea = FrameLayout(context)
    private val manager = LinearLayoutManager(context)
    private val pages = RecyclerView(context).apply {
        layoutManager = manager
        itemAnimator = null
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        clipToPadding = false
        pad(8, 10, 8, 0)
        setBackgroundColor(0xFFEDEBF3.toInt())
    }
    private val status = context.stack().apply { gravity = Gravity.CENTER; pad(20, 22, 20, 22) }
    private val loading = ProgressBar(context)
    private val statusText = context.label("Opening PDF…", 15f, Palette.secondary).apply {
        gravity = Gravity.CENTER
    }
    private val retry = context.action("Try again", Symbol.BOOK, Palette.paper, Palette.deepRose) {
        model.retryReader()
    }
    private val footer = context.stack()
    private val tools = context.row().apply { pad(8, 4, 8, 8) }
    private val modes = mutableMapOf<EditMode, View>()
    private lateinit var marksTool: View
    private var current = ReaderState()
    private var session: PdfSession? = null
    private var pageAdapter: PdfPages? = null
    private var visiblePage = 0
    private var mode = EditMode.READ

    init {
        header.addView(iconButton(context, "Back to bookshelf", Symbol.BACK) { model.goBack() }.first)
        title.apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            pad(8, 0, 8, 0)
            androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
        }
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(bookmarkButton.first)
        view.addView(header)
        val pageControl = context.row().apply {
            pad(12, 0, 12, 0)
            gravity = Gravity.CENTER
            minimumHeight = context.dp(44)
            background = context.touch(Palette.pink, 20)
            addView(pageText)
            addView(Glyph(context, Symbol.DOWN, Palette.deepRose), LinearLayout.LayoutParams(
                context.dp(15), context.dp(15)).apply { marginStart = context.dp(5) })
            describeButton("Go to a page", ::goToPageDialog)
        }
        val pageBar = context.row().apply {
            gravity = Gravity.CENTER
            pad(12, 1, 12, 8)
            addView(pageControl)
        }
        view.addView(pageBar)
        view.addView(readerArea, LinearLayout.LayoutParams(-1, 0, 1f))
        readerArea.addView(pages, FrameLayout.LayoutParams(-1, -1))
        status.addView(loading, LinearLayout.LayoutParams(context.dp(36), context.dp(36)))
        status.gap(12)
        status.addView(statusText)
        status.gap(12)
        status.addView(retry, LinearLayout.LayoutParams(-2, -2))
        readerArea.addView(status, FrameLayout.LayoutParams(-1, -1))
        guidance.gravity = Gravity.CENTER
        guidance.pad(10, 7, 10, 5)
        footer.addView(guidance)
        addTool("Read", Symbol.BOOK, EditMode.READ) { setMode(EditMode.READ) }
        addTool("Highlight", Symbol.PEN, EditMode.HIGHLIGHT) { setMode(EditMode.HIGHLIGHT) }
        addTool("Note", Symbol.NOTE, EditMode.NOTE) { setMode(EditMode.NOTE) }
        marksTool = addTool("Marks", Symbol.MARKS, null, ::showMarks)
        footer.addView(tools, LinearLayout.LayoutParams(-1, -2))
        view.addView(footer, LinearLayout.LayoutParams(-1, -2))
        setMode(EditMode.READ)
        pages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy != 0) updateVisiblePage()
            }
        })
    }

    fun update(state: ReaderState) {
        current = state
        title.text = state.book?.title ?: "Opening book…"
        bookmarkButton.second.setSymbol(if (state.book?.bookmark == null) Symbol.BOOKMARK else Symbol.SAVED)
        bookmarkButton.first.contentDescription = state.book?.bookmark
            ?.let { "Bookmark on page ${it + 1}, open bookmark options" } ?: "Bookmark this page"
        val document = state.session
        bookmarkButton.first.isEnabled = document != null
        bookmarkButton.first.alpha = if (document != null) 1f else 0.55f
        pages.visibility = if (document == null) View.GONE else View.VISIBLE
        status.visibility = if (document == null) View.VISIBLE else View.GONE
        footer.visibility = if (document == null) View.GONE else View.VISIBLE
        loading.visibility = if (state.error == null) View.VISIBLE else View.GONE
        retry.visibility = if (state.error == null) View.GONE else View.VISIBLE
        statusText.text = state.error ?: "Opening PDF…"
        if (document !== session) {
            session = document
            pageAdapter = document?.let {
                PdfPages(it, scope, { mode }, onHighlight = { mark ->
                    model.saveMark(mark)
                    setMode(EditMode.READ)
                }, onNote = { mark ->
                    setMode(EditMode.READ)
                    editNote(mark)
                })
            }
            pages.adapter = pageAdapter
            if (document != null) jumpTo(model.resumePage(state.startPage))
        }
        pageAdapter?.setMarks(state.marks)
        pageText.text = when {
            document != null -> "Page ${visiblePage + 1} of ${document.pages}"
            state.error != null -> "PDF unavailable"
            else -> "Opening PDF…"
        }
        marksTool.contentDescription = "Annotations, ${current.marks.values.sumOf { it.size }} saved"
    }

    private fun addTool(text: String, symbol: Symbol, editMode: EditMode?, click: () -> Unit): View {
        val tool = context.stack().apply {
            gravity = Gravity.CENTER
            minimumHeight = context.dp(54)
            pad(2, 7, 2, 7)
            background = context.touch(Palette.background, 14)
            addView(Glyph(context, symbol, Palette.deepRose),
                LinearLayout.LayoutParams(context.dp(21), context.dp(21)))
            gap(3)
            addView(context.label(text, 12f, Palette.ink, bold = true).apply { gravity = Gravity.CENTER })
            describeButton(text, click)
        }
        tools.addView(tool, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = context.dp(2)
            marginEnd = context.dp(2)
        })
        if (editMode != null) modes[editMode] = tool
        return tool
    }

    private fun setMode(value: EditMode) {
        mode = value
        modes.forEach { (kind, tool) ->
            tool.isSelected = kind == value
            tool.background = context.touch(if (tool.isSelected) Palette.pink else Palette.background, 14)
        }
        guidance.text = when (value) {
            EditMode.READ -> ""
            EditMode.HIGHLIGHT -> "Drag across a passage to highlight it."
            EditMode.NOTE -> "Tap a spot on the page to pin a note."
        }
        guidance.visibility = if (value == EditMode.READ) View.GONE else View.VISIBLE
    }

    private fun updateVisiblePage() {
        val document = session ?: return
        if (!pages.canScrollVertically(1) && pages.canScrollVertically(-1)) {
            reportPage(document.pages - 1)
            return
        }
        val viewportTop = pages.paddingTop
        val viewportBottom = pages.height - pages.paddingBottom
        val mostVisible = (0 until manager.childCount).mapNotNull(manager::getChildAt)
            .maxByOrNull { child -> minOf(child.bottom, viewportBottom) - maxOf(child.top, viewportTop) }
        mostVisible?.let { reportPage(manager.getPosition(it)) }
    }

    private fun reportPage(page: Int) {
        val total = session?.pages ?: return
        if (page !in 0 until total) return
        visiblePage = page
        model.setVisiblePage(page)
        pageText.text = "Page ${page + 1} of $total"
    }

    private fun jumpTo(page: Int) {
        val total = session?.pages ?: return
        if (total == 0) return
        val target = page.coerceIn(0, total - 1)
        pages.stopScroll()
        manager.scrollToPositionWithOffset(target, 0)
        reportPage(target)
    }

    private fun bookmark() {
        val saved = current.book?.bookmark
        if (session == null) return
        val page = visiblePage
        if (saved == null) {
            model.saveBookmark(page)
            return
        }
        val options = arrayOf("Go to bookmarked page ${saved + 1}", "Save page ${page + 1}",
            "Remove bookmark")
        AlertDialog.Builder(context)
            .setTitle("Bookmark")
            .setItems(options) { _, choice ->
                when (choice) {
                    0 -> jumpTo(saved)
                    1 -> model.saveBookmark(page)
                    2 -> model.saveBookmark(null)
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun goToPageDialog() {
        val total = session?.pages ?: return
        val input = editor("1–$total", (visiblePage + 1).toString(), 10).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setSelectAllOnFocus(true)
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle("Go to page")
            .setView(editFrame(input))
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Go", null)
            .create()
        showEditor(dialog, input) {
            val number = input.text.toString().toIntOrNull()
            if (number == null || number !in 1..total) input.error = "Enter a page from 1 to $total"
            else {
                jumpTo(number - 1)
                dialog.dismiss()
            }
        }
    }

    private fun showMarks() {
        val items = current.marks.toSortedMap().values.flatten()
        val dialog = AlertDialog.Builder(context).setTitle("Annotations").setNegativeButton("Close", null)
        if (items.isEmpty()) {
            dialog.setMessage("Your highlights and notes will appear here.").show()
            return
        }
        dialog.setItems(items.map { mark ->
            val summary = mark.note?.lineSequence()?.firstOrNull()?.take(80) ?: "Highlight"
            "Page ${mark.page + 1} · $summary"
        }.toTypedArray()) { _, choice ->
            val mark = items[choice]
            if (mark.note != null) editNote(mark)
            else AlertDialog.Builder(context)
                .setTitle("Highlight on page ${mark.page + 1}")
                .setPositiveButton("Go to page") { _, _ -> jumpTo(mark.page) }
                .setNeutralButton("Delete") { _, _ -> model.saveMark(mark, remove = true) }
                .setNegativeButton("Close", null)
                .show()
        }.show()
    }

    private fun editNote(mark: Mark) {
        val input = editor("Write a note…", mark.note.orEmpty(), 4000).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3
            maxLines = 8
            gravity = Gravity.TOP or Gravity.START
            setSelection(text.length)
        }
        val builder = AlertDialog.Builder(context)
            .setTitle("Note on page ${mark.page + 1}")
            .setView(editFrame(input))
            .setPositiveButton("Save note", null)
            .setNegativeButton("Cancel", null)
        if (mark.note != null) {
            builder.setNeutralButton("Options") { _, _ ->
                AlertDialog.Builder(context)
                    .setTitle("Saved note")
                    .setItems(arrayOf("Go to page ${mark.page + 1}", "Delete note")) { _, choice ->
                        if (choice == 0) jumpTo(mark.page) else model.saveMark(mark, remove = true)
                    }
                    .setNegativeButton("Close", null)
                    .show()
            }
        }
        val dialog = builder.create()
        showEditor(dialog, input) {
            val note = input.text.toString().trim()
            if (note.isEmpty()) input.error = "Write a note before saving"
            else {
                model.saveMark(mark.copy(note = note))
                dialog.dismiss()
            }
        }
    }

    private fun editor(placeholder: String, value: String, max: Int) = EditText(context).apply {
        hint = placeholder
        setText(value)
        setTextColor(Palette.ink)
        setHintTextColor(Palette.secondary)
        textSize = 16f
        typeface = Typeface.DEFAULT
        filters = arrayOf(InputFilter.LengthFilter(max))
        pad(12, 10, 12, 10)
        background = context.shape(Palette.background, 12, Palette.line)
    }

    private fun editFrame(input: EditText) = context.stack().apply {
        pad(23, 10, 23, 10)
        addView(input, LinearLayout.LayoutParams(-1, -2))
    }

    private fun showEditor(dialog: AlertDialog, input: EditText, save: () -> Unit) {
        input.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { save() }
    }
}
