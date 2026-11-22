package com.orihorizon.pawpages.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.orihorizon.pawpages.R
import com.orihorizon.pawpages.ReaderState
import com.orihorizon.pawpages.data.Book

internal class ShelfScreen(
    private val context: Context,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit
) {
    val view = FrameLayout(context)
    private val books = ShelfBooks(onOpen)
    private val header = ShelfHeader()

    init {
        val body = context.stack()
        view.addView(body, FrameLayout.LayoutParams(-1, -1))
        body.addView(context.row().apply {
            pad(22, 12, 22, 8)
            addView(FrameLayout(context).apply {
                background = context.shape(Palette.pink, 13)
                addView(Glyph(context, Symbol.PAW, Palette.deepRose),
                    FrameLayout.LayoutParams(context.dp(21), context.dp(21), Gravity.CENTER))
            }, LinearLayout.LayoutParams(context.dp(40), context.dp(40)))
            addView(context.heading("Paw Pages", 22f).apply { pad(12, 0, 0, 0) })
        })
        body.addView(RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = ConcatAdapter(header, books)
            clipToPadding = false
            pad(22, 5, 22, 95)
            itemAnimator = null
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        val add = context.action("Add book", Symbol.PLUS, click = onAdd).apply {
            elevation = context.dp(5).toFloat()
        }
        view.addView(add, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
            marginEnd = context.dp(22)
            bottomMargin = context.dp(18)
        })
    }

    fun update(state: ReaderState) {
        header.update(state.collectionReady, state.books.size)
        books.submitList(state.books)
    }

    private inner class ShelfHeader : RecyclerView.Adapter<HeaderHolder>() {
        private var ready = false
        private var count = 0

        fun update(loaded: Boolean, total: Int) {
            if (ready == loaded && count == total) return
            ready = loaded
            count = total
            notifyItemChanged(0)
        }

        override fun getItemCount() = 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderHolder {
            val header = context.stack().apply {
                layoutParams = RecyclerView.LayoutParams(-1, -2)
                pad(0, 12, 0, 12)
            }
            val card = context.row().apply {
                background = context.shape(Palette.pink, 24)
                clipToOutline = true
                pad(16, 12, 8, 12)
                minimumHeight = context.dp(115)
            }
            val title = context.stack()
            title.addView(context.heading("My bookshelf", 33f).apply { androidx.core.view.ViewCompat.setAccessibilityHeading(this, true) })
            title.gap(6)
            val count = context.label("", 14f, Palette.secondary)
            title.addView(count)
            card.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(kitten(), LinearLayout.LayoutParams(context.dp(102), context.dp(84)))
            header.addView(card)
            val empty = context.stack().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                pad(0, 18, 0, 0)
            }
            empty.addView(kitten().apply {
                background = context.shape(Palette.pink, 24)
                clipToOutline = true
                scaleType = ImageView.ScaleType.CENTER_CROP
            }, LinearLayout.LayoutParams(-1, context.dp(205)))
            empty.gap(20)
            empty.addView(context.heading("Your shelf is empty", 26f))
            empty.gap(8)
            empty.addView(context.label("Add a PDF to start reading.", 15f, Palette.secondary))
            header.addView(empty, LinearLayout.LayoutParams(-1, -2))
            return HeaderHolder(header, empty, count, card.getChildAt(1))
        }

        override fun onBindViewHolder(holder: HeaderHolder, position: Int) {
            holder.count.text = when {
                !ready -> "Loading books…"
                count == 1 -> "1 book"
                else -> "$count books"
            }
            holder.empty.visibility = if (ready && count == 0) View.VISIBLE else View.GONE
            holder.thumbnail.visibility = if (ready && count == 0) View.GONE else View.VISIBLE
        }
    }

    private class HeaderHolder(
        view: View, val empty: View, val count: TextView, val thumbnail: View
    ) : RecyclerView.ViewHolder(view)

    private fun kitten() = ImageView(context).apply {
        setImageResource(R.drawable.reading_kitten)
        scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
}

private class ShelfBooks(private val open: (String) -> Unit) :
    ListAdapter<Book, ShelfBooks.Holder>(object : DiffUtil.ItemCallback<Book>() {
        override fun areItemsTheSame(oldItem: Book, newItem: Book) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Book, newItem: Book) = oldItem == newItem
    }) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context
        val card = context.row().apply {
            layoutParams = RecyclerView.LayoutParams(-1, -2).apply { bottomMargin = context.dp(10) }
            background = context.touch(Palette.paper, 20, Palette.line)
            pad(12, 10, 14, 10)
            minimumHeight = context.dp(84)
        }
        val cover = FrameLayout(context)
        cover.addView(Glyph(context, Symbol.PAW, Palette.deepRose),
            FrameLayout.LayoutParams(context.dp(20), context.dp(20), Gravity.CENTER))
        card.addView(cover, LinearLayout.LayoutParams(context.dp(46), context.dp(60)))
        val copy = context.stack().apply { pad(14, 0, 10, 0) }
        val title = context.label("", 17f, bold = true).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val details = context.label("", 13f, Palette.secondary).apply { pad(0, 5, 0, 0) }
        copy.addView(title)
        copy.addView(details)
        card.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(Glyph(context, Symbol.ARROW, Palette.deepRose),
            LinearLayout.LayoutParams(context.dp(22), context.dp(22)))
        return Holder(card, cover, title, details)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val book = getItem(position)
        val context = holder.itemView.context
        val color = coverColors[Math.floorMod(book.id.hashCode(), coverColors.size)]
        holder.cover.background = context.shape(color, 9)
        holder.title.text = book.title
        val pageCount = if (book.pageCount == 1) "1 page" else "${book.pageCount} pages"
        holder.details.text = book.bookmark?.let { "Bookmark on page ${it + 1}" } ?: pageCount
        holder.itemView.describeButton("${book.title}, ${holder.details.text}") { open(book.id) }
    }

    class Holder(view: View, val cover: View, val title: TextView, val details: TextView) :
        RecyclerView.ViewHolder(view)

    private companion object {
        val coverColors = intArrayOf(Palette.blue, Palette.pink, Palette.lilac, Palette.vanilla)
    }
}
