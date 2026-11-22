package com.orihorizon.pawpages

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.orihorizon.pawpages.ui.ImportScreen
import com.orihorizon.pawpages.ui.Palette
import com.orihorizon.pawpages.ui.ReaderScreen
import com.orihorizon.pawpages.ui.ShelfScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val model: ReaderViewModel by viewModels()
    private val selectPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(model::pick)
    }
    private val selectContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(model::pick)
    }
    private lateinit var root: FrameLayout
    private var shown: Screen? = null
    private var shelf: ShelfScreen? = null
    private var importer: ImportScreen? = null
    private var reader: ReaderScreen? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        root = FrameLayout(this).apply { setBackgroundColor(Palette.background) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            insets
        }
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!model.goBack()) finish()
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.ui.collect(::show) }
                launch { model.notices.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show() } }
            }
        }
    }

    private fun openFilePicker() {
        try {
            selectPdf.launch(arrayOf("application/pdf"))
        } catch (_: ActivityNotFoundException) {
            // Some Android builds leave out the system document picker.
            try {
                selectContent.launch("application/pdf")
            } catch (_: ActivityNotFoundException) {
                model.pickerUnavailable()
            }
        }
    }

    private fun show(state: ReaderState) {
        if (shown != state.screen) {
            root.removeAllViews()
            shelf = null
            importer = null
            reader = null
            val view = when (state.screen) {
                Screen.SHELF -> ShelfScreen(this, model::showImport) { model.openBook(it) }
                    .also { shelf = it }.view
                Screen.IMPORT -> ImportScreen(this, model, ::openFilePicker)
                    .also { importer = it }.view
                Screen.READER -> ReaderScreen(this, model, lifecycleScope).also { reader = it }.view
            }
            root.addView(view, FrameLayout.LayoutParams(-1, -1))
            shown = state.screen
        }
        shelf?.update(state)
        importer?.update(state)
        reader?.update(state)
    }
}
