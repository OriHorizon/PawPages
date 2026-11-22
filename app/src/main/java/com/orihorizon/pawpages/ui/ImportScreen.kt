package com.orihorizon.pawpages.ui

import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import androidx.core.widget.doAfterTextChanged
import com.orihorizon.pawpages.ReaderState
import com.orihorizon.pawpages.ReaderViewModel

internal class ImportScreen(
    private val context: Context,
    private val model: ReaderViewModel,
    onPick: () -> Unit
) {
    val view = context.stack()
    private val fileName = context.label("", 16f, bold = true)
    private val pick = context.action("Choose a PDF", Symbol.PDF, Palette.paper, Palette.deepRose, onPick)
    private val nameArea = context.stack()
    private val title = EditText(context).apply {
        id = View.generateViewId()
        hint = "e.g. The Secret Garden"
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        maxLines = 1
        filters = arrayOf(InputFilter.LengthFilter(120))
        background = context.shape(Palette.paper, 14, Palette.line)
        pad(14, 12, 14, 12)
        setTextColor(Palette.ink)
        setHintTextColor(Palette.secondary)
        textSize = 16f
        minimumHeight = context.dp(54)
    }
    private val error = context.label("", 14f, Palette.error)
    private val add = context.action("Add to bookshelf", Symbol.PLUS) { model.addBook() }
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleSmall)
    private var busy = false
    private var hasFile = false
    private var syncingTitle = false

    init {
        view.addView(screenHeader(context, "Add a book") { model.goBack() })
        val content = context.stack().apply { pad(22, 14, 22, 24) }
        val scroller = ScrollView(context).apply {
            isFillViewport = true
            addView(content, FrameLayout.LayoutParams(-1, -2))
        }
        view.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(context.heading("Make room for a new story", 30f).apply {
            androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
        })
        content.gap(9)
        content.addView(context.label("Choose a PDF and give it a name for your bookshelf.",
            15f, Palette.secondary))
        content.gap(22)
        content.addView(context.stack().apply {
            background = context.shape(Palette.blue, 22)
            pad(20, 20, 20, 18)
            gravity = Gravity.CENTER_HORIZONTAL
            addView(Glyph(context, Symbol.PDF, Palette.deepRose),
                LinearLayout.LayoutParams(context.dp(38), context.dp(38)))
            gap(10)
            fileName.gravity = Gravity.CENTER
            addView(fileName)
            gap(14)
            addView(pick, LinearLayout.LayoutParams(-2, -2))
        }, LinearLayout.LayoutParams(-1, -2))
        content.gap(20)
        nameArea.addView(context.label("Book title", 14f, bold = true).apply { labelFor = title.id })
        nameArea.gap(8)
        nameArea.addView(title)
        content.addView(nameArea)
        content.gap(12)
        content.addView(error)

        val footer = context.row().apply { pad(22, 8, 22, 18) }
        footer.addView(progress, LinearLayout.LayoutParams(context.dp(36), context.dp(36)).apply {
            marginEnd = context.dp(12)
        })
        footer.addView(add, LinearLayout.LayoutParams(0, -2, 1f))
        view.addView(footer)
        title.doAfterTextChanged {
            if (!syncingTitle) model.updateTitle(it.toString())
            updateAdd()
        }
    }

    fun update(state: ReaderState) {
        busy = state.busy
        hasFile = state.chosenName != null
        fileName.text = state.chosenName.orEmpty()
        fileName.visibility = if (hasFile) View.VISIBLE else View.GONE
        nameArea.visibility = if (hasFile) View.VISIBLE else View.GONE
        val value = model.draftTitle()
        if (title.text.toString() != value) {
            syncingTitle = true
            title.setText(value)
            title.setSelection(value.length)
            syncingTitle = false
        }
        title.isEnabled = !busy
        pick.isEnabled = !busy
        pick.alpha = if (busy) 0.5f else 1f
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        error.text = state.error.orEmpty()
        error.visibility = if (state.error == null) View.GONE else View.VISIBLE
        updateAdd()
    }

    private fun updateAdd() {
        add.isEnabled = hasFile && !busy && title.text.isNotBlank()
        add.alpha = if (add.isEnabled) 1f else 0.47f
    }
}

internal fun screenHeader(context: Context, title: String, onBack: () -> Unit) =
    context.row().apply {
        pad(10, 6, 12, 6)
        addView(iconButton(context, "Back to bookshelf", Symbol.BACK, onBack).first)
        addView(context.heading(title, 22f).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            pad(6, 0, 4, 0)
            androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
        }, LinearLayout.LayoutParams(0, -2, 1f))
    }

internal fun iconButton(
    context: Context, description: String, symbol: Symbol, click: () -> Unit
): Pair<FrameLayout, Glyph> {
    val icon = Glyph(context, symbol, Palette.deepRose)
    val button = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(context.dp(48), context.dp(48))
        background = context.touch(Palette.pink, 16)
        addView(icon, FrameLayout.LayoutParams(context.dp(23), context.dp(23), Gravity.CENTER))
        describeButton(description, click)
    }
    return button to icon
}
