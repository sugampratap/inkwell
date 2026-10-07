package com.xnotes

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.focusable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.IntentCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.xnotes.R
import com.xnotes.ui.Editor
import com.xnotes.ui.Toolbar
import com.xnotes.ui.PenBoxRail
import com.xnotes.ui.ToolbarAround
import com.xnotes.ui.TextFormatBar
import com.xnotes.ui.kit.LocalPenDown
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkElevation
import com.xnotes.ui.kit.inkSurface
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.XnotesTheme
import com.xnotes.ui.theme.toComposeColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.hrm.latex.renderer.export.rememberLatexExporter
import com.hrm.latex.renderer.measure.rememberLatexMeasurer

/** Minimum time the launch loader stays up, so its animation is briefly seen even
 *  when the session restores instantly. */
private const val MIN_LOADER_MS = 350L

/** Whether the display has a camera cutout (notch/hole-punch). False below API 29, which has no
 *  cutout API; such devices fall back to fullscreen by default. Also false for a context with no
 *  display of its own, which cannot answer the question. */
internal fun deviceHasDisplayCutout(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val display = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) context.display
        else @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
    }.getOrNull()
    return display?.cutout != null
}

/** The whole screen's shorter side in dp, the same in any orientation, window or split; 0 if unknown. */
internal fun deviceShortSideDp(context: Context): Double {
    val metrics = runCatching {
        DisplayMetrics().also {
            @Suppress("DEPRECATION")
            context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(it)
        }
    }.getOrNull() ?: return 0.0
    return minOf(metrics.widthPixels, metrics.heightPixels) / metrics.density.toDouble()
}

/** The standard touch action an old One UI S-Pen-button code stands in for, or -1 for anything else. */
private fun standardPenAction(action: Int): Int = when (action) {
    211 -> MotionEvent.ACTION_DOWN
    212 -> MotionEvent.ACTION_UP
    213 -> MotionEvent.ACTION_MOVE
    else -> -1
}

class MainActivity : ComponentActivity() {

    // The editor owns the fullscreen state (persisted preference, default depends on the display
    // cutout); this activity just applies it to the window. Fullscreen draws edge to edge and lets
    // the swipe-in transient bars overlay (no resize), non-fullscreen insets under the bars.
    private var editor: Editor? = null
    // A PDF handed to us by another app ("Open with" / Share); consumed once the editor is ready.
    private var pendingPdfImport by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Jakarta is a resource font, which Compose loads synchronously on the main thread the first
        // time text asks for each weight. Every launch shows the loader and then the library, so
        // the weights load here, off the main thread while the loader shows, and the first library
        // frame doesn't block on them. This resolver shares Compose's process-wide typeface cache.
        // The loader's own label (Medium) goes first, so the loader's first frame is less likely to
        // have to decode it on the main thread.
        val fonts = createFontFamilyResolver(applicationContext)
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            runCatching {
                fonts.resolve(InkType.Jakarta, FontWeight.Medium)
                fonts.preload(InkType.Jakarta)
            }
        }
        com.xnotes.platform.FontCatalog.init(this)
        com.xnotes.platform.TemplateLibrary.init(this)
        setTheme(com.xnotes.R.style.Theme_Xnotes) // leave the launch/splash theme behind
        // The window colour the app last drew with (Dark, OLED or a Material surface), so the gap
        // between this theme swap and the loader's first frame is not white.
        com.xnotes.platform.LaunchLookStore.background(this)?.let {
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(it))
        }
        applyFullscreen(!deviceHasDisplayCutout(this)) // provisional; reconciled once the editor loads prefs
        pendingPdfImport = pdfImportUri(intent)
        setContent {
            val context = LocalContext.current
            val ed = remember { Editor(context).also { editor = it } }
            LaunchedEffect(ed.fullscreen) { applyFullscreen(ed.fullscreen) }
            SystemBarsAndLaunchLook(ed)
            LaunchedEffect(ed.noteOpen || ed.canvasOpen) { applyWritingRefresh(ed.noteOpen || ed.canvasOpen) }
            var ready by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                val start = android.os.SystemClock.uptimeMillis()
                ed.restoreSession() // heavy load off-thread; loader animates meanwhile
                val elapsed = android.os.SystemClock.uptimeMillis() - start
                if (elapsed < MIN_LOADER_MS) kotlinx.coroutines.delay(MIN_LOADER_MS - elapsed)
                ready = true
                ed.prewarmBackstage() // warm recents/explorer caches so the first backstage open is instant
            }
            XnotesTheme(ed.palette, ed.cornerStyle) {
                CompositionLocalProvider(
                    com.xnotes.ui.LocalToolbarLook provides ed.toolbarLook,
                    // One set of starred colours for every picker, on either canvas.
                    com.xnotes.ui.LocalInkFavourites provides com.xnotes.ui.InkFavourites(ed.favoriteColors, ed::toggleFavoriteColor),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (ready) EditorScreen(
                            ed,
                            fullscreen = ed.fullscreen,
                            onToggleFullscreen = ed::toggleFullscreen,
                            importPdfUri = pendingPdfImport,
                            onImportConsumed = { pendingPdfImport = null },
                        )
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !ready,
                            enter = androidx.compose.animation.EnterTransition.None,
                            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(280)),
                        ) {
                            com.xnotes.ui.XnotesLoader()
                        }
                    }
                }
            }
        }
    }

    // Stylus side-button keys (Bluetooth/USI pens report the button only this way) are caught here,
    // before Compose focus routing, so the controller sees both press and release regardless of
    // which view holds focus. Other keys fall through to the normal dispatch.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // The pen writes in whichever pane has the focus, so its button belongs to that pane too.
        if (editor?.active?.onStylusButtonKey(event) == true) return true
        return super.dispatchKeyEvent(event)
    }

    // Older Samsung builds (seen on a Tab S6 Lite, Android 13; gone by Android 15) tag a stylus
    // stroke made with the S-Pen button held with proprietary action codes instead of DOWN/MOVE/UP,
    // so the view tree never opens a touch target and the whole stroke is dropped. Rewrite them
    // here, the last point they are intact, and dispatch normally. A no-op on every other device.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val standard = standardPenAction(ev.actionMasked)
        if (standard < 0 || ev.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) {
            return super.dispatchTouchEvent(ev)
        }
        val rewritten = MotionEvent.obtain(ev)
        rewritten.action = standard
        try {
            return super.dispatchTouchEvent(rewritten)
        } finally {
            rewritten.recycle()
        }
    }

    // A PDF arriving while we're already running: singleTask reuses this instance via onNewIntent.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pdfImportUri(intent)?.let { pendingPdfImport = it }
    }

    // The PDF uri carried by an inbound VIEW ("Open with") or SEND ("Share") intent, else null.
    private fun pdfImportUri(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND ->
            if (intent.type == "application/pdf")
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else null
        else -> null
    }

    // uiMode is in configChanges, so a system dark/light flip lands here instead of recreating us.
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        val night = newConfig.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        editor?.onSystemDarkModeChanged(night == android.content.res.Configuration.UI_MODE_NIGHT_YES)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && editor?.fullscreen == true) applyFullscreen(true) // re-hide transient bars after they swipe in
    }

    override fun onPause() {
        super.onPause()
        editor?.persist()
    }

    // Leaving the foreground: a voice recording stops here (kept in the note), and playback pauses.
    override fun onStop() {
        super.onStop()
        editor?.onAppStopped()
        editor?.secondary?.onAppStopped()
    }

    @Suppress("DEPRECATION") // RUNNING_* still arrive below Android 14
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // The canvas keeps its rendered pages, so PDFium's pages and glyph caches can go while the
        // UI is hidden; they load again when something new is drawn.
        if (level >= TRIM_MEMORY_UI_HIDDEN || level == TRIM_MEMORY_RUNNING_LOW || level == TRIM_MEMORY_RUNNING_CRITICAL) {
            com.xnotes.platform.PdfiumDocument.trimAll()
        }
    }

    /**
     * Keep the system bars in the app's colours, with icons that read on them: dark icons on the
     * paper look, light ones on dark and OLED. Without this the bars keep the launch theme's.
     */
    @Suppress("DEPRECATION") // The bar colours are ignored edge to edge, and honoured below it.
    private fun applySystemBars(palette: com.xnotes.ui.theme.Palette) {
        val bg = android.graphics.Color.argb(255, palette.bg.r, palette.bg.g, palette.bg.b)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.setBackgroundColor(bg)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = !palette.isDark
        controller.isAppearanceLightNavigationBars = !palette.isDark
    }

    /**
     * The bars follow the palette, and the next launch's look follows the palette and the appearance
     * setting both: System and Dark share a palette on a dark phone, but not a splash. Its own scope,
     * so a preferences change recomposes only this.
     */
    @Composable
    private fun SystemBarsAndLaunchLook(ed: Editor) {
        val appearance = remember(ed.prefsVersion) { ed.preferences.uiAppearance }
        LaunchedEffect(ed.palette, appearance) {
            applySystemBars(ed.palette)
            rememberLaunchLook(appearance, ed.palette)
        }
    }

    /** Remember the look for the next launch: the window colour, and on Android 13+ the system splash's theme. */
    private fun rememberLaunchLook(appearance: String, palette: com.xnotes.ui.theme.Palette) {
        val look = com.xnotes.platform.LaunchLook.of(appearance)
        val bg = android.graphics.Color.argb(255, palette.bg.r, palette.bg.g, palette.bg.b)
        val changed = com.xnotes.platform.LaunchLookStore.save(this, look, bg)
        if (changed && android.os.Build.VERSION.SDK_INT >= 33) splashScreen.setSplashScreenTheme(look.splashTheme)
    }

    /**
     * Hold the panel at its fastest refresh while a note is open. An adaptive display otherwise
     * drops to 60 Hz the moment nothing moves, and the first strokes after a pause are then drawn
     * at half the rate while it ramps back up: ink a refresh behind the nib, every time the pen
     * comes down. Back to the system's choice in the library, where nothing is being written.
     */
    private fun applyWritingRefresh(writing: Boolean) {
        val lp = window.attributes
        if (!writing) {
            if (lp.preferredDisplayModeId == 0) return
            lp.preferredDisplayModeId = 0
        } else {
            @Suppress("DEPRECATION")
            val d = (if (android.os.Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay) ?: return
            val current = d.mode
            val fastest = d.supportedModes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .maxByOrNull { it.refreshRate } ?: return
            if (fastest.refreshRate <= current.refreshRate + 1f && lp.preferredDisplayModeId == fastest.modeId) return
            lp.preferredDisplayModeId = fastest.modeId
        }
        window.attributes = lp
    }

    private fun applyFullscreen(fullscreen: Boolean) {
        // Every dialog window follows this (InkDialogHost), or opening one would bring the bars back.
        com.xnotes.ui.kit.AppSystemBars.hidden = fullscreen
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (fullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun EditorScreen(
    editor: Editor,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    importPdfUri: Uri? = null,
    onImportConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    // The LaTeX renderer resolves its fonts through the composition, so this is the
    // only place it can be built; the flow reaches it through MathRendering after.
    val mathMeasurer = rememberLatexMeasurer()
    val mathExporter = rememberLatexExporter()
    val mathDensity = LocalDensity.current
    val mathText = androidx.compose.ui.text.rememberTextMeasurer()
    val mathFonts = com.xnotes.platform.LatexInternals.defaultFonts()
    LaunchedEffect(mathMeasurer, mathExporter, mathDensity, mathText, mathFonts) {
        com.xnotes.platform.MathRendering.install(mathMeasurer, mathExporter, mathDensity, mathText, mathFonts)
        editor.refreshFlowMath()
    }
    // Backstage is the root of the stack; the editor is pushed on top only when a note is open
    // (editor.noteOpen). Every launch starts on backstage.
    var backstageView by remember { mutableStateOf(com.xnotes.ui.BackstageView.HOME) }
    var showShareChooser by remember { mutableStateOf(false) }
    var guardAction by remember { mutableStateOf<GuardRequest?>(null) }
    var pendingAfterSave by remember { mutableStateOf<(() -> Unit)?>(null) }
    // The pane an image was picked for, and where a long-press menu asked it to land. Held together
    // so the picker's result lands in the pane that opened it, whatever the focus does meanwhile.
    var pendingInsert by remember { mutableStateOf<PendingInsert?>(null) }
    // The same, for the infinite canvas.
    var pendingCanvasInsert by remember { mutableStateOf<PendingInsert?>(null) }
    var pendingShareUri by remember { mutableStateOf<String?>(null) }
    // The pane whose header Share was tapped; its sheet can share just the page in view.
    var shareFromPane by remember { mutableStateOf<Editor?>(null) }
    var pendingSaveCopyUri by remember { mutableStateOf<String?>(null) }
    // A finished PDF render awaiting a SAF "Save as" destination (open-note / file / pages export).
    var pendingExportTemp by remember { mutableStateOf<java.io.File?>(null) }
    // In-flight PDF render; drives the progress dialog, null hides it.
    var exportProgress by remember { mutableStateOf<ExportProgress?>(null) }
    // The running export's coroutine and its own cancel flag. Each export gets a fresh flag so a new
    // export can abort the previous one (set its flag, then join it) without un-cancelling itself.
    var exportJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var exportCancel by remember { mutableStateOf<java.util.concurrent.atomic.AtomicBoolean?>(null) }
    // The pane whose page indices await a SAF "Save as" destination (side-panel page export).
    var pendingExportPages by remember { mutableStateOf<PendingPages?>(null) }
    val scope = rememberCoroutineScope()
    val resolver = context.contentResolver
    val rwFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    // Which pane a "Save as" writes: the focused one at the moment the picker opened.
    var savePane by remember { mutableStateOf(editor) }
    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        uri?.let {
            runCatching { resolver.takePersistableUriPermission(it, rwFlags) }
            val name = displayNameOf(resolver, it)
            runCatching { resolver.openOutputStream(it, "wt")?.use { o -> savePane.save(o, it.toString(), name) } }
                .onSuccess { val p = pendingAfterSave; pendingAfterSave = null; p?.invoke() }
                .onFailure { editor.message = context.getString(R.string.err_save_note); pendingAfterSave = null }
        }
    }

    // "Import PDF" takes one file or many. One keeps the old flow: remember the pick and show the name
    // dialog at once, with the (possibly large) copy happening at Save under the "Importing PDF…" loader.
    // Many skips naming altogether (each note takes its source file's name) and the explorer imports
    // them one at a time, so the picked list never becomes a list of open PDFs.
    val importPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        fun stemOf(u: android.net.Uri) = com.xnotes.core.util.Paths.stem(displayNameOf(resolver, u) ?: "Document")
        when {
            uris.isEmpty() -> Unit
            uris.size == 1 -> editor.requestImport(stemOf(uris[0]), uris[0].toString())
            else -> editor.requestImports(uris.map { com.xnotes.ui.PendingImport(stemOf(it), it.toString()) })
        }
        if (uris.isNotEmpty()) {
            backstageView = com.xnotes.ui.BackstageView.HOME
            editor.goHomeAll() // land on backstage to name the single pick, or to run the batch
        }
    }
    // A PDF "Save as" destination. The note is already rendered into [pendingExportTemp]
    // (off-thread, behind the progress dialog), so the picker just chooses where to copy it —
    // shared by the open-note export, the explorer-file export, and side-panel page saves.
    val savePdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        val temp = pendingExportTemp; pendingExportTemp = null
        if (temp != null) {
            if (uri != null) {
                val ok = runCatching { resolver.openOutputStream(uri)?.use { o -> temp.inputStream().use { it.copyTo(o) } } != null }.getOrDefault(false)
                editor.message = if (ok) context.getString(R.string.exported_pdf) else context.getString(R.string.err_export_pdf)
            }
            temp.delete() // discard the temp whether saved or the picker was dismissed
        }
    }

    // Save a copy of an explorer file (a note or a canvas) elsewhere.
    val saveCopyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val src = pendingSaveCopyUri; pendingSaveCopyUri = null
        if (uri != null && src != null) {
            runCatching { resolver.openOutputStream(uri)?.use { o -> editor.copyFileTo(src, o) } }
                .onFailure { editor.message = context.getString(R.string.err_save_copy) }
        }
    }

    // Save a single selected page as a PNG.
    val savePageImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        val pending = pendingExportPages; pendingExportPages = null
        val index = pending?.pages?.firstOrNull()
        if (uri != null && pending != null && index != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val ok = runCatching {
                    val png = pending.editor.pageImagePng(index) ?: return@runCatching false
                    resolver.openOutputStream(uri)?.use { it.write(png) } != null
                }.getOrDefault(false)
                if (!ok) editor.message = context.getString(R.string.err_save_image)
            }
        }
    }

    // Save several selected pages as individual PNGs into a folder the user picks.
    val savePagesImagesTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        val pending = pendingExportPages; pendingExportPages = null
        if (treeUri != null && pending != null && pending.pages.isNotEmpty()) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val stem = pending.editor.title
                val saved = runCatching {
                    val parent = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                        treeUri, android.provider.DocumentsContract.getTreeDocumentId(treeUri),
                    )
                    var n = 0
                    for (index in pending.pages) {
                        val png = pending.editor.pageImagePng(index) ?: continue
                        val name = "%s-p%02d.png".format(stem, index + 1)
                        val file = android.provider.DocumentsContract.createDocument(resolver, parent, "image/png", name) ?: continue
                        resolver.openOutputStream(file)?.use { it.write(png) }
                        n++
                    }
                    n
                }.getOrDefault(0)
                editor.message = if (saved > 0) context.resources.getQuantityString(R.plurals.saved_images, saved, saved) else context.getString(R.string.err_save_images)
            }
        }
    }

    // Several pictures may be picked at once; the editor streams each to disk off the main thread.
    val insertImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val pending = pendingInsert; pendingInsert = null
        if (uris.isNotEmpty() && pending != null) pending.editor.insertImagesFromUris(uris, pending.at)
    }

    // Images picked for the sticker library (multi-select), stored on disk by the editor.
    val addStickersLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching { resolver.openInputStream(uri)?.use { s -> editor.addSticker(s.readBytes()) } }
                .onFailure { editor.message = context.getString(R.string.err_read_image) }
        }
    }

    // A user Helix code theme (.toml), parsed + stored by the editor.
    val importCodeThemeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }
                .getOrNull()
                ?.let { editor.importCodeTheme(it, displayNameOf(resolver, uri)) }
                ?: run { editor.message = context.getString(R.string.err_read_file) }
        }
    }

    // A user page template (.xtemplate), checked and stored in the template library.
    val importTemplateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }
                .getOrNull()
                ?.let { editor.importTemplate(it) }
                ?: run { editor.message = context.getString(R.string.err_read_file) }
        }
    }

    // A user font (.ttf/.otf), stored + registered by the editor.
    val importFontLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }
                .getOrNull()
                ?.let { editor.importFont(it, displayNameOf(resolver, uri)) }
                ?: run { editor.message = context.getString(R.string.err_read_file) }
        }
    }

    // Grant a folder for the in-app explorer (a one-time system folder picker).
    val pickRootLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching { resolver.takePersistableUriPermission(it, rwFlags) }
            editor.updateBrowseRoot(it.toString())
        }
    }

    /** Open the "Save as" picker for [target], remembering which pane its result belongs to. */
    fun launchSaveAs(target: Editor) {
        savePane = target
        createLauncher.launch("${target.title}.xnote")
    }

    fun saveOrPrompt() {
        val target = editor.active
        val uri = target.currentUri
        if (uri == null) { launchSaveAs(target); return }
        // The write is off the main thread now, so the Save-As fallback fires from its callback.
        target.saveToThen(uri) { ok -> if (!ok) launchSaveAs(target) }
    }

    // The prompt is about one pane's note, so it asks about — and saves — the pane being acted on.
    fun guarded(target: Editor = editor.active, action: () -> Unit) {
        when {
            // A canvas keeps its own autosave, and dirty/autosaveUri describe the paged buffer
            // underneath it, so asking about those here would prompt over a note nobody is looking at.
            target.canvasOpen -> action()
            target.autosaveUri != null -> action() // autosaved notes are flushed on doc-swap; no prompt
            target.dirty -> guardAction = GuardRequest(target, action)
            else -> action()
        }
    }

    /** Run [action] once every open pane has settled its unsaved changes, prompting one at a time. */
    fun guardedAll(action: () -> Unit) {
        val panes = editor.openPanes
        fun step(i: Int) {
            if (i >= panes.size) action() else guarded(panes[i]) { step(i + 1) }
        }
        step(0)
    }

    // An image picked for the infinite canvas: read the bytes and hand them straight over.
    // Pictures picked for the infinite canvas: the canvas streams each to disk off the main thread.
    val insertCanvasImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        val pending = pendingCanvasInsert; pendingCanvasInsert = null
        if (uris.isEmpty() || pending == null) return@rememberLauncherForActivityResult
        pending.editor.infinite.insertImagesFromUris(uris, pending.at)
    }

    // --- Insert menu: the pane a picker or the camera was opened for, and whether it is a canvas ---
    var pendingMedia by remember { mutableStateOf<PendingMedia?>(null) }
    var cameraFile by remember { mutableStateOf<java.io.File?>(null) }

    /** Put picture [bytes] where the Insert menu inserts on [target]: the canvas's view or the page's. */
    fun insertPicture(target: PendingMedia, bytes: ByteArray) {
        if (target.canvas) {
            if (target.editor.canvasOpen && !target.editor.infinite.insertImage(bytes, null)) {
                target.editor.message = context.getString(R.string.err_read_image)
            }
        } else if (target.editor.noteOpen && !target.editor.canvasOpen) {
            target.editor.insertImageAt(bytes, target.editor.insertionPointContent())
        }
    }

    val insertPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = pendingMedia; pendingMedia = null
        if (uri != null && target != null && !target.canvas) target.editor.insertPdf(uri.toString())
    }

    val insertAudioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = pendingMedia; pendingMedia = null
        if (uri != null && target != null && !target.canvas) target.editor.media.insertAudioFile(uri)
    }

    // The camera app writes the full-size shot into our cache (a FileProvider uri), then it is
    // turned upright off the main thread and goes in as an image.
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val target = pendingMedia; pendingMedia = null
        val file = cameraFile; cameraFile = null
        if (file == null) return@rememberLauncherForActivityResult
        if (!taken || target == null) {
            file.delete()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try { runCatching { com.xnotes.platform.CameraPhoto.uprightBytes(file) }.getOrNull() } finally { file.delete() }
            }
            if (bytes == null) target.editor.message = context.getString(R.string.err_camera_photo) else insertPicture(target, bytes)
        }
    }

    // Google's document scanner (it runs in Play services) hands back one upright JPEG per page.
    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val target = pendingMedia; pendingMedia = null
        if (target == null || result.resultCode != android.app.Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val pages = com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
            .fromActivityResultIntent(result.data)?.pages?.map { it.imageUri }.orEmpty()
        if (pages.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            if (target.canvas) {
                val images = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    pages.mapNotNull { u -> runCatching { resolver.openInputStream(u)?.use { it.readBytes() } }.getOrNull() }
                }
                if (images.isEmpty()) target.editor.say(context.getString(R.string.err_scan_read), icon = com.xnotes.ui.icons.Ph.warningCircle)
                else if (target.editor.canvasOpen) insertScansOnCanvas(target.editor.infinite, images)
            } else {
                val staged = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    pages.mapNotNull { u ->
                        runCatching { resolver.openInputStream(u)?.use { it.readBytes() } }.getOrNull()?.let { target.editor.stageImage(it) }
                    }
                }
                if (staged.isEmpty()) target.editor.say(context.getString(R.string.err_scan_read), icon = com.xnotes.ui.icons.Ph.warningCircle)
                else if (target.editor.noteOpen && !target.editor.canvasOpen) target.editor.insertScannedPages(staged)
            }
        }
    }

    // Voice recording asks for the microphone the first time; a refusal explains itself.
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val target = pendingMedia; pendingMedia = null
        if (target == null) return@rememberLauncherForActivityResult
        if (granted) {
            target.editor.media.startRecording()
        } else {
            val activity = context as? android.app.Activity
            val canAskAgain = activity != null &&
                androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, android.Manifest.permission.RECORD_AUDIO)
            if (canAskAgain) {
                target.editor.say(context.getString(R.string.mic_permission_needed), icon = com.xnotes.ui.icons.Ph.microphoneSlash)
            } else {
                target.editor.say(
                    context.getString(R.string.mic_permission_denied),
                    context.getString(R.string.open_settings) to {
                        runCatching {
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(Uri.fromParts("package", context.packageName, null))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                        Unit
                    },
                    icon = com.xnotes.ui.icons.Ph.microphoneSlash,
                )
            }
        }
    }

    /** Insert menu: [kind] into [pane], whose surface (note or canvas) decides where it lands. */
    fun onInsert(pane: com.xnotes.ui.Editor, kind: com.xnotes.ui.InsertKind) {
        val target = PendingMedia(pane, canvas = pane.canvasOpen)
        when (kind) {
            com.xnotes.ui.InsertKind.IMAGE ->
                if (target.canvas) {
                    pendingCanvasInsert = PendingInsert(pane, null)
                    insertCanvasImageLauncher.launch(arrayOf("image/*"))
                } else {
                    pendingInsert = PendingInsert(pane, pane.insertionPointContent())
                    insertImageLauncher.launch(arrayOf("image/*"))
                }
            com.xnotes.ui.InsertKind.PDF -> {
                pendingMedia = target
                insertPdfLauncher.launch(arrayOf("application/pdf"))
            }
            com.xnotes.ui.InsertKind.AUDIO_FILE -> {
                pendingMedia = target
                insertAudioLauncher.launch(arrayOf("audio/*"))
            }
            com.xnotes.ui.InsertKind.VOICE -> {
                if (pane.media.recordingActive) {
                    pane.media.stopRecording(insert = true)
                } else if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    pane.media.startRecording()
                } else {
                    pendingMedia = target
                    micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                }
            }
            com.xnotes.ui.InsertKind.CAMERA -> {
                val file = runCatching {
                    val dir = java.io.File(context.cacheDir, "camera").apply { mkdirs() }
                    java.io.File.createTempFile("shot", ".jpg", dir)
                }.getOrNull()
                if (file == null) {
                    pane.message = context.getString(R.string.err_camera_photo)
                    return
                }
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                cameraFile = file
                pendingMedia = target
                try {
                    cameraLauncher.launch(uri)
                } catch (_: android.content.ActivityNotFoundException) {
                    cameraFile = null
                    pendingMedia = null
                    file.delete()
                    pane.message = context.getString(R.string.err_no_camera)
                }
            }
            com.xnotes.ui.InsertKind.SCAN -> {
                val activity = context as? android.app.Activity ?: return
                val gms = com.google.android.gms.common.GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
                if (gms != com.google.android.gms.common.ConnectionResult.SUCCESS) {
                    pane.say(context.getString(R.string.err_scanner_unavailable), icon = com.xnotes.ui.icons.Ph.warningCircle)
                    return
                }
                val options = com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.Builder()
                    .setScannerMode(com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .setResultFormats(com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                    .setGalleryImportAllowed(true)
                    .setPageLimit(SCAN_PAGE_LIMIT)
                    .build()
                com.google.mlkit.vision.documentscanner.GmsDocumentScanning.getClient(options)
                    .getStartScanIntent(activity)
                    .addOnSuccessListener { sender ->
                        pendingMedia = target
                        runCatching { scanLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build()) }
                            .onFailure { pendingMedia = null; pane.say(context.getString(R.string.err_scanner_failed), icon = com.xnotes.ui.icons.Ph.warningCircle) }
                    }
                    .addOnFailureListener { e ->
                        // The module is fetched by Play services on first use; a device that cannot get it lands here.
                        val unavailable = (e as? com.google.mlkit.common.MlKitException)?.errorCode ==
                            com.google.mlkit.common.MlKitException.UNAVAILABLE
                        pane.say(
                            context.getString(if (unavailable) R.string.err_scanner_unavailable else R.string.err_scanner_failed),
                            icon = com.xnotes.ui.icons.Ph.warningCircle,
                        )
                    }
            }
        }
    }

    /** Read the file at [uriStr] into [target]. The extension picks the surface: the two document
     *  types share the explorer but not much else. Reads off-thread behind the "Opening note…"
     *  spinner so a big embedded PDF doesn't freeze the UI. */
    suspend fun openInto(target: Editor, uriStr: String) {
        val name = displayNameOf(resolver, Uri.parse(uriStr))
        if (com.xnotes.core.util.DocumentKind.ofName(name ?: "") == com.xnotes.core.util.DocumentKind.CANVAS) {
            target.openCanvasAsync(uriStr, name)
        } else {
            target.openAsync(uriStr, name)
        }
    }

    fun openTreeFile(uriStr: String) {
        scope.launch { openInto(editor, uriStr) }
    }

    /** Open two picked files together, one per pane, and start the split focused on the first. */
    fun openSplit(firstUri: String, secondUri: String) {
        editor.splitRatio = 0.5f
        editor.focusPane(com.xnotes.ui.Pane.PRIMARY)
        val second = editor.secondaryPane()
        scope.launch {
            openInto(editor, firstUri)
            openInto(second, secondUri)
            // A file that would not open leaves no half-built pane behind; the other stays as it is.
            if (!second.noteOpen) {
                editor.abandonSecondary()
                editor.message = context.getString(R.string.err_open_second)
            }
        }
    }

    fun stemOf(uriStr: String): String =
        com.xnotes.core.util.Paths.stem(displayNameOf(resolver, Uri.parse(uriStr)) ?: "Note")

    /**
     * The kind of the stored document at [uriStr], read from its file name, falling back to a note
     * for anything unrecognizable. Every place that has to name a copy of that file goes through
     * here: spelling an extension at the call site is how an `.xcanvas` ended up shared as `.xnote`.
     */
    fun kindOf(uriStr: String): com.xnotes.core.util.DocumentKind =
        com.xnotes.core.util.DocumentKind.ofName(displayNameOf(resolver, Uri.parse(uriStr)).orEmpty())
            ?: com.xnotes.core.util.DocumentKind.NOTE

    /** What the export dialog counts for the stored document at [uriStr]: a note's pages, a canvas's items. */
    fun countingOf(uriStr: String): String = ExportProgress.countingFor(kindOf(uriStr))

    // Render a PDF off the main thread into a temp file behind a cancellable progress dialog;
    // only once it finishes does [onReady] run — opening the SAF picker or a share sheet.
    // Dismissing the dialog flips this export's cancel flag, which aborts the page loop AND the
    // write stream, so the half-written temp is discarded. A fresh export first aborts and joins any
    // previous one, so they never overlap. [shareDir] picks the cache subdir: FileProvider only
    // exposes cache/share, so shares render there; plain "save" exports use cache/export.
    fun runPdfExport(
        stem: String,
        shareDir: Boolean,
        counting: String = "page",
        render: (java.io.OutputStream, (Int, Int) -> Unit, () -> Boolean) -> Unit,
        onReady: (java.io.File) -> Unit,
    ) {
        val prevJob = exportJob
        val prevCancel = exportCancel
        val cancel = java.util.concurrent.atomic.AtomicBoolean(false)
        exportCancel = cancel // the dialog's Cancel flips THIS export's flag
        exportProgress = ExportProgress(0, 0, counting) // show the dialog at once; the render fills in the real total
        exportJob = scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Abort any still-running previous export (its write stream throws on the next buffer) and
            // wait for it to fully unwind before we touch the shared temp dir — no overlapping saves.
            prevCancel?.set(true)
            prevJob?.join()
            val dir = java.io.File(context.cacheDir, if (shareDir) "share" else "export").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() } // keep only the file this export produces
            val temp = java.io.File(dir, "$stem.pdf")
            val ok = runCatching {
                java.io.FileOutputStream(temp).use { fo ->
                    // Abort the (otherwise uninterruptible) PdfBox save the moment Cancel is tapped.
                    val o = CancellableOutputStream(fo) { cancel.get() }
                    render(o, { done, total -> if (!cancel.get()) exportProgress = ExportProgress(done, total, counting) }, { cancel.get() })
                }
            }.isSuccess
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (exportCancel === cancel) exportProgress = null // only the latest export owns the dialog
                when {
                    cancel.get() -> temp.delete()
                    ok -> onReady(temp)
                    else -> { temp.delete(); if (exportCancel === cancel) editor.message = context.getString(R.string.err_export_pdf) }
                }
            }
        }
    }

    fun launchShare(file: java.io.File, stem: String, mime: String) {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_named, stem)))
    }

    // Page images out: one goes as ACTION_SEND, several as ACTION_SEND_MULTIPLE; none is nothing to send.
    fun pageImagesIntent(uris: ArrayList<Uri>): Intent? = when {
        uris.isEmpty() -> null
        uris.size == 1 -> Intent(Intent.ACTION_SEND).apply { type = "image/png"; putExtra(Intent.EXTRA_STREAM, uris[0]); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        else -> Intent(Intent.ACTION_SEND_MULTIPLE).apply { type = "image/png"; putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    // Library Share › Images: the stored note's pages as the PNGs Share › Images makes inside it, rendered
    // off the main thread behind the export sheet (cancellable like a PDF export, and serialised with it,
    // since both clear cache/share), then sent as one image or several.
    fun shareFileAsImages(uriStr: String, stem: String) {
        val prevJob = exportJob
        val prevCancel = exportCancel
        val cancel = java.util.concurrent.atomic.AtomicBoolean(false)
        exportCancel = cancel
        exportProgress = ExportProgress(0, 0, "page", images = true)
        exportJob = scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            prevCancel?.set(true)
            prevJob?.join()
            val auth = "${context.packageName}.fileprovider"
            val files = ArrayList<java.io.File>()
            val intent = runCatching {
                val dir = java.io.File(context.cacheDir, "share").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val uris = ArrayList<Uri>()
                editor.exportFilePagesToPng(
                    uriStr,
                    onPage = { index, png ->
                        val file = java.io.File(dir, com.xnotes.ui.ShareRoute.pageImageName(stem, index))
                        files.add(file)
                        java.io.FileOutputStream(file).use { it.write(png) }
                        uris.add(androidx.core.content.FileProvider.getUriForFile(context, auth, file))
                    },
                    onProgress = { done, total -> if (!cancel.get()) exportProgress = ExportProgress(done, total, "page", images = true) },
                    isCancelled = { cancel.get() },
                )
                pageImagesIntent(uris)
            }.getOrNull()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (exportCancel === cancel) exportProgress = null // only the latest export owns the sheet
                when {
                    cancel.get() -> files.forEach { it.delete() }
                    intent != null -> runCatching { context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_named, stem))) }
                        .onFailure { editor.message = context.getString(R.string.err_share_note) }
                    else -> if (exportCancel === cancel) editor.message = context.getString(R.string.err_share_note)
                }
            }
        }
    }

    fun shareFile(uriStr: String, format: com.xnotes.ui.ShareFormat) {
        val stem = stemOf(uriStr)
        val route = com.xnotes.ui.ShareRoute.of(kindOf(uriStr), format)
        if (route == com.xnotes.ui.ShareRoute.PAGE_IMAGES) {
            shareFileAsImages(uriStr, stem)
        } else if (route == com.xnotes.ui.ShareRoute.PDF || route == com.xnotes.ui.ShareRoute.EDITABLE_PDF) {
            val editable = route == com.xnotes.ui.ShareRoute.EDITABLE_PDF
            // Render with progress, then share the finished PDF (writes into cache/share for FileProvider).
            runPdfExport(stem, shareDir = true, counting = countingOf(uriStr),
                render = { o, prog, cancel -> editor.exportFileToPdf(uriStr, o, prog, cancel, editable) },
                onReady = { temp -> runCatching { launchShare(temp, stem, "application/pdf") }.onFailure { editor.message = context.getString(R.string.err_share_note) } })
        } else {
            // Sharing the bundle itself is just a fast byte copy — no render, no dialog needed. It
            // keeps the source's own extension, so a canvas is shared as a canvas.
            runCatching {
                val dir = java.io.File(context.cacheDir, "share").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() } // keep only the file we're about to share
                val file = java.io.File(dir, "$stem${kindOf(uriStr).suffix}")
                java.io.FileOutputStream(file).use { o -> editor.copyFileTo(uriStr, o) }
                launchShare(file, stem, "application/octet-stream")
            }.onFailure { editor.message = context.getString(R.string.err_share_note) }
        }
    }

    // Share several stored files at once, each as the bundle it is (no PDF render for a batch).
    fun shareFiles(uris: List<String>) {
        if (uris.isEmpty()) return
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val intent = runCatching {
                val dir = java.io.File(context.cacheDir, "share").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val auth = "${context.packageName}.fileprovider"
                val taken = HashSet<String>()
                val out = ArrayList<Uri>()
                for (u in uris) {
                    val stem = stemOf(u)
                    val suffix = kindOf(u).suffix
                    var name = "$stem$suffix"
                    var n = 2
                    while (!taken.add(name.lowercase())) name = "$stem (${n++})$suffix"
                    val file = java.io.File(dir, name)
                    java.io.FileOutputStream(file).use { o -> editor.copyFileTo(u, o) }
                    out.add(androidx.core.content.FileProvider.getUriForFile(context, auth, file))
                }
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "application/octet-stream"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, out)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }.getOrNull()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (intent != null) context.startActivity(Intent.createChooser(intent, context.resources.getQuantityString(R.plurals.share_n_notes, uris.size, uris.size))) else editor.message = context.getString(R.string.err_share_notes)
            }
        }
    }

    // Share the selected side-panel pages: as one PDF, or as one/many PNGs (ACTION_SEND_MULTIPLE).
    fun sharePages(from: Editor, pages: List<Int>, asPdf: Boolean) {
        if (pages.isEmpty()) return
        val stem = from.title
        if (asPdf) {
            runPdfExport(stem, shareDir = true,
                render = { o, prog, cancel -> from.exportPagesToPdf(pages, o, prog, cancel) },
                onReady = { temp -> runCatching { launchShare(temp, stem, "application/pdf") }.onFailure { editor.message = context.getString(R.string.err_share_pages) } })
            return
        }
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val auth = "${context.packageName}.fileprovider"
            val intent = runCatching {
                val dir = java.io.File(context.cacheDir, "share").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val uris = ArrayList<Uri>()
                for (index in pages) {
                    val png = from.pageImagePng(index) ?: continue
                    val file = java.io.File(dir, com.xnotes.ui.ShareRoute.pageImageName(stem, index))
                    java.io.FileOutputStream(file).use { it.write(png) }
                    uris.add(androidx.core.content.FileProvider.getUriForFile(context, auth, file))
                }
                pageImagesIntent(uris)
            }.getOrNull()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (intent != null) context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_named, stem))) else editor.message = context.getString(R.string.err_share_pages)
            }
        }
    }

    /**
     * Share from inside an open pane, in [format] and over [range]. A note renders from what is on
     * screen, saved or not; a canvas is flushed to its file first and shared from there, as the
     * library shares it.
     */
    fun shareOpen(from: Editor, format: com.xnotes.ui.ShareFormat, range: com.xnotes.ui.ShareRange) {
        if (from.canvasOpen) {
            from.flushCanvasThenShare { uri ->
                if (uri != null) shareFile(uri, format) else editor.message = context.getString(R.string.err_share_note)
            }
            return
        }
        val stem = from.title
        val pages = if (range == com.xnotes.ui.ShareRange.CURRENT) listOf(from.pageIndex) else (0 until from.pageCount).toList()
        when (format) {
            com.xnotes.ui.ShareFormat.PDF, com.xnotes.ui.ShareFormat.EDITABLE_PDF -> {
                val editable = format == com.xnotes.ui.ShareFormat.EDITABLE_PDF
                runPdfExport(stem, shareDir = true,
                    render = { o, prog, cancel ->
                        if (range == com.xnotes.ui.ShareRange.ALL) from.exportPdf(o, prog, cancel, editable)
                        else from.exportPagesToPdf(pages, o, prog, cancel, editable)
                    },
                    onReady = { temp -> runCatching { launchShare(temp, stem, "application/pdf") }.onFailure { editor.message = context.getString(R.string.err_share_note) } })
            }
            com.xnotes.ui.ShareFormat.NOTE -> scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val file = runCatching {
                    val dir = java.io.File(context.cacheDir, "share").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    java.io.File(dir, "$stem${com.xnotes.core.util.DocumentKind.NOTE.suffix}").also { f ->
                        java.io.FileOutputStream(f).use { from.writeNote(it) }
                    }
                }.getOrNull()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (file != null) runCatching { launchShare(file, stem, "application/octet-stream") }
                    else editor.message = context.getString(R.string.err_share_note)
                }
            }
            com.xnotes.ui.ShareFormat.IMAGES -> sharePages(from, pages, asPdf = false)
        }
    }

    fun savePagesAsPdf(from: Editor, pages: List<Int>) {
        if (pages.isEmpty()) return
        runPdfExport(from.title, shareDir = false,
            render = { o, prog, cancel -> from.exportPagesToPdf(pages, o, prog, cancel) },
            onReady = { temp -> pendingExportTemp = temp; savePdfLauncher.launch("${from.title}.pdf") })
    }

    // One page -> a single PNG (CreateDocument); several -> a folder the user picks (one PNG per page).
    fun savePagesAsImages(from: Editor, pages: List<Int>) {
        if (pages.isEmpty()) return
        pendingExportPages = PendingPages(from, pages)
        if (pages.size == 1) savePageImageLauncher.launch(com.xnotes.ui.ShareRoute.pageImageName(from.title, pages[0]))
        else savePagesImagesTreeLauncher.launch(null)
    }

    // Export a stored file to a PDF the user saves where they like (the library's ⋮ › Export to PDF,
    // and Save PDF… in the library's share sheet).
    fun saveFilePdf(uri: String) {
        runPdfExport(stemOf(uri), shareDir = false, counting = countingOf(uri),
            render = { o, prog, cancel -> editor.exportFileToPdf(uri, o, prog, cancel) },
            onReady = { temp -> pendingExportTemp = temp; savePdfLauncher.launch("${stemOf(uri)}.pdf") })
    }

    // Save PDF… / Save images… in the share sheet inside a note: the same exports as Ctrl+E and the
    // side panel's Save as, over the pages the sheet's range covers.
    fun saveOpen(from: Editor, format: com.xnotes.ui.ShareFormat, range: com.xnotes.ui.ShareRange) {
        val pages = if (range == com.xnotes.ui.ShareRange.CURRENT) listOf(from.pageIndex) else (0 until from.pageCount).toList()
        when (format) {
            com.xnotes.ui.ShareFormat.PDF ->
                if (range == com.xnotes.ui.ShareRange.ALL) {
                    runPdfExport(from.title, shareDir = false,
                        render = { o, prog, cancel -> from.exportPdf(o, prog, cancel) },
                        onReady = { temp -> pendingExportTemp = temp; savePdfLauncher.launch("${from.title}.pdf") })
                } else savePagesAsPdf(from, pages)
            com.xnotes.ui.ShareFormat.IMAGES -> savePagesAsImages(from, pages)
            else -> Unit
        }
    }

    // Every shortcut acts on the focused pane, so the same KeyActions serve both of them.
    editor.keyActions = remember {
        Editor.KeyActions(
            newNote = { guarded { editor.active.newNote() } },
            open = { backstageView = com.xnotes.ui.BackstageView.HOME; guardedAll { editor.goHomeAll() } },
            save = { saveOrPrompt() },
            saveAs = { launchSaveAs(editor.active) },
            exportPdf = {
                val from = editor.active
                runPdfExport(from.title, shareDir = false,
                    render = { o, prog, cancel -> from.exportPdf(o, prog, cancel) },
                    onReady = { temp -> pendingExportTemp = temp; savePdfLauncher.launch("${from.title}.pdf") })
            },
            preferences = { backstageView = com.xnotes.ui.BackstageView.PREFERENCES; guardedAll { editor.goHomeAll() } },
            fullscreen = onToggleFullscreen,
        )
    }

    LaunchedEffect(editor.message) {
        editor.message?.let {
            val action = editor.messageAction
            val icon = editor.messageIcon
            editor.messageAction = null
            editor.messageIcon = null
            val result = snackbar.showSnackbar(
                com.xnotes.ui.ToastVisuals(
                    it,
                    action?.first,
                    icon,
                    if (action != null) androidx.compose.material3.SnackbarDuration.Long else androidx.compose.material3.SnackbarDuration.Short,
                ),
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) action?.second?.invoke()
            editor.message = null
        }
    }
    // The second pane raises its own messages; surface them through the same snackbar.
    val secondaryMessage = editor.secondary?.message
    LaunchedEffect(secondaryMessage) {
        if (secondaryMessage != null) {
            val icon = editor.secondary?.messageIcon
            editor.secondary?.messageIcon = null
            snackbar.showSnackbar(
                com.xnotes.ui.ToastVisuals(secondaryMessage, null, icon, androidx.compose.material3.SnackbarDuration.Short),
            )
            editor.secondary?.message = null
        }
    }

    // A PDF opened from another app ("Open with"/Share): set up a pending import and land on the
    // backstage so its name dialog appears, exactly like the in-app "Import PDF" button. With no
    // folder chosen yet, fall back to App storage so the explorer renders and the note autosaves.
    LaunchedEffect(importPdfUri) {
        val src = importPdfUri ?: return@LaunchedEffect
        onImportConsumed() // consume once; the body has no suspend point so it runs to completion
        val stem = com.xnotes.core.util.Paths.stem(displayNameOf(resolver, src) ?: "Document")
        guardedAll {
            if (editor.browseRoot == null) editor.useInternalStorage()
            editor.requestImport(stem, src.toString())
            backstageView = com.xnotes.ui.BackstageView.HOME
            editor.goHomeAll()
        }
    }

    // In fullscreen the window runs edge to edge and the swipe-in system bars are transient, so
    // zero the content insets: otherwise their inset animates 0 -> N -> 0 and resizes the canvas,
    // forcing a re-render every time the bars hide. Non-fullscreen keeps the normal bar insets.
    // A DOCKED keyboard lifts only the open panes (the bottom format bar rides right above it) and
    // the snackbar; Home stays put and the keyboard slides over it. A floating keyboard reports no
    // inset. Insets are consumed below so inner imePadding only adds what the bars don't cover.
    val contentInsets = if (fullscreen) WindowInsets(0, 0, 0, 0) else WindowInsets.systemBars
    Scaffold(
        snackbarHost = {
            // 30 dp up at rest, 16 over a pane's player or format pill (BottomStack; AU 109).
            val toastBottom = com.xnotes.ui.toastBottomFor(listOfNotNull(editor, editor.secondary))
            SnackbarHost(snackbar, Modifier.windowInsetsPadding(WindowInsets.ime.exclude(contentInsets))) { data ->
                com.xnotes.ui.kit.InkToast(
                    data.visuals.message,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = toastBottom),
                    icon = (data.visuals as? com.xnotes.ui.ToastVisuals)?.icon,
                    actionLabel = data.visuals.actionLabel,
                    onAction = { data.performAction() },
                    // SnackbarHost already puts each snackbar in a live region, with a pane title and dismiss.
                    liveRegion = false,
                )
            }
        },
        contentWindowInsets = contentInsets,
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(inner).consumeWindowInsets(contentInsets)) {
            // BASE LAYER: backstage is the root of the stack — always present underneath.
            com.xnotes.ui.Backstage(
                editor = editor,
                view = backstageView,
                onSelectView = { backstageView = it },
                onExitApp = { (context as? android.app.Activity)?.finish() },
                onImportCodeTheme = { importCodeThemeLauncher.launch(arrayOf("*/*")) },
                onImportFont = { importFontLauncher.launch(arrayOf("*/*")) },
                onImportPdf = { importPdfLauncher.launch(arrayOf("application/pdf")) },
                onOpenFile = { uri -> guarded(editor) { openTreeFile(uri) } },
                onPickRoot = { pickRootLauncher.launch(null) },
                onShareFile = { uri -> pendingShareUri = uri; showShareChooser = true },
                onSaveCopyFile = { uri -> pendingSaveCopyUri = uri; saveCopyLauncher.launch("${stemOf(uri)}${kindOf(uri).suffix}") },
                onExportFilePdf = { uri -> saveFilePdf(uri) },
                onOpenSplit = { first, second -> guardedAll { openSplit(first, second) } },
                onShareFiles = { uris -> shareFiles(uris) },
                onOpenBeside = { uri ->
                    editor.currentUri?.takeIf { !editor.isSameDocument(it, uri) }?.let { first -> { guardedAll { openSplit(first, uri) } } }
                },
            )

            // TOP LAYER: the open panes (toolbar + canvas each), pushed over backstage. Back acts on
            // the focused pane; its handlers live here so — composed after backstage — they win while
            // a note is open.
            val focused = editor.active
            if (focused.noteOpen) {
                // While a text box is open, Back commits-or-dismisses it (and hides the keyboard).
                BackHandler(enabled = focused.editingField != null) { focused.commitText() }
                // A live flow caret session ends first (flushing its typing burst).
                BackHandler(enabled = focused.flowEditingActive) { focused.flowText.endSession() }
                // Otherwise Back closes that pane (guarded for unsaved edits); the other one stays.
                BackHandler(enabled = focused.editingField == null && !focused.flowEditingActive) {
                    guarded(focused) { focused.goHome() }
                }
            }

            val actions = PaneActions(
                onToggleFullscreen = onToggleFullscreen,
                onOpenBackstage = {
                    backstageView = com.xnotes.ui.BackstageView.HOME
                    guardedAll { editor.goHomeAll() }
                },
                onClosePane = { pane -> guarded(pane) { pane.goHome() } },
                onInsertImage = { pane, at ->
                    pendingInsert = PendingInsert(pane, at)
                    insertImageLauncher.launch(arrayOf("image/*"))
                },
                onInsertCanvasImage = { pane, at ->
                    pendingCanvasInsert = PendingInsert(pane, at)
                    insertCanvasImageLauncher.launch(arrayOf("image/*"))
                },
                onInsert = { pane, kind -> onInsert(pane, kind) },
                onAddStickers = { addStickersLauncher.launch(arrayOf("image/*")) },
                onImportTemplate = { importTemplateLauncher.launch(arrayOf("*/*")) },
                onSharePages = { pane, pages, asPdf -> sharePages(pane, pages, asPdf) },
                onShareNote = { pane -> shareFromPane = pane },
                onSavePagesAsPdf = { pane, pages -> savePagesAsPdf(pane, pages) },
                onSavePagesAsImages = { pane, pages -> savePagesAsImages(pane, pages) },
            )
            SplitHost(editor, actions)
            // Drawn after the panes, so a note opening from the library grows over the editor composing beneath it.
            com.xnotes.ui.LibraryOpenTransition(editor)
        }
    }
    val sharePrefs = remember(editor.prefsVersion) { editor.preferences }
    val setHeadingBookmarks: (Boolean) -> Unit = { on -> editor.applyHomePreferences(editor.preferences.copy(pdfHeadingBookmarks = on)) }
    if (showShareChooser) {
        val shareUri = pendingShareUri
        // Remembered because resolving the kind is a SAF query, and this is composition.
        val isCanvas = remember(shareUri) { shareUri?.let { kindOf(it) == com.xnotes.core.util.DocumentKind.CANVAS } ?: false }
        com.xnotes.ui.ShareSheet(
            name = shareUri?.let { stemOf(it) } ?: "",
            isCanvas = isCanvas,
            canChooseRange = false,
            onDismiss = { showShareChooser = false; pendingShareUri = null },
            headingBookmarks = sharePrefs.pdfHeadingBookmarks,
            onHeadingBookmarks = setHeadingBookmarks,
            onSave = { _, _ ->
                showShareChooser = false
                pendingShareUri = null
                shareUri?.let { saveFilePdf(it) }
            },
        ) { format, _ ->
            showShareChooser = false
            pendingShareUri = null
            shareUri?.let { shareFile(it, format) }
        }
    }
    shareFromPane?.let { pane ->
        com.xnotes.ui.ShareSheet(
            name = pane.title,
            isCanvas = pane.canvasOpen,
            canChooseRange = true,
            onDismiss = { shareFromPane = null },
            pageCount = pane.pageCount,
            currentPage = pane.pageIndex,
            headingBookmarks = sharePrefs.pdfHeadingBookmarks,
            onHeadingBookmarks = setHeadingBookmarks,
            onSave = { format, range ->
                shareFromPane = null
                saveOpen(pane, format, range)
            },
        ) { format, range ->
            shareFromPane = null
            shareOpen(pane, format, range)
        }
    }
    guardAction?.let { request ->
        val guarded = request.editor
        val action = request.action
        com.xnotes.ui.kit.InkConfirmSheet(
            title = stringResource(R.string.unsaved_changes),
            message = stringResource(R.string.unsaved_changes_prompt, guarded.title),
            confirmLabel = stringResource(R.string.save),
            onConfirm = {
                guardAction = null
                val uri = guarded.currentUri
                if (uri != null) {
                    // Off-thread: what the prompt was guarding runs once the bytes have landed.
                    guarded.saveToThen(uri) { action() }
                } else {
                    pendingAfterSave = action
                    launchSaveAs(guarded)
                }
            },
            onDismiss = { guardAction = null },
            extra = {
                com.xnotes.ui.kit.InkGhostButton(stringResource(R.string.discard), { guardAction = null; action() }, danger = true)
            },
        )
    }
    exportProgress?.let { p ->
        PdfExportDialog(done = p.done, total = p.total, counting = p.counting, images = p.images, onCancel = {
            exportCancel?.set(true) // abort this export's page loop AND its write stream
            exportProgress = null   // hide at once; the job then discards the half-written temp
        })
    }
    // An insert can't be cancelled, but once it has run a while it can be sent to the background: the
    // sheet hides until this insert ends, and the next one shows it again.
    val insertBusy = editor.insertBusy ?: editor.secondary?.insertBusy
    var insertBackgrounded by remember(insertBusy != null) { mutableStateOf(false) }
    if (insertBusy != null && !insertBackgrounded) com.xnotes.ui.InsertBusyDialog(insertBusy, onBackground = { insertBackgrounded = true })
    if (editor.importing) {
        // A batch counts files, so its bar fills; a single import has nothing to count.
        val batch = editor.importProgress
        if (batch != null) PdfImportBatchDialog(batch.done, batch.total) { editor.cancelImportInProgress() }
        else PdfImportDialog(onCancel = { editor.cancelImportInProgress() }) // the stream-copy stops at its next buffer
    }
    // Tapping a note reads it off-thread (editor.opening). Only show the spinner once the read has run
    // long enough to matter, so opening a small note never flashes a dialog; a big PDF gets the loader.
    var showOpening by remember { mutableStateOf(false) }
    val opening = editor.opening || editor.secondary?.opening == true
    LaunchedEffect(opening) {
        if (opening) { delay(160); showOpening = opening } else showOpening = false
    }
    if (showOpening && opening) {
        SpinnerDialog(stringResource(R.string.opening_note), onCancel = {
            // Discard whichever pane's note is still being read when it returns.
            editor.cancelOpenInProgress()
            editor.secondary?.cancelOpenInProgress()
            showOpening = false // dismiss at once; opening clears as the reads unwind
        })
    }
    // A dirty note is flushed off-thread on close/pause; show the saving overlay only once the write is
    // slow enough to matter, so closing a small note never flashes a dialog.
    var showSaving by remember { mutableStateOf(false) }
    val saving = editor.savingNote || editor.secondary?.savingNote == true
    LaunchedEffect(saving) {
        if (saving) { delay(160); showSaving = saving } else showSaving = false
    }
    // Keep in background hides it for this save only: showSaving rises again when the next one starts.
    if (showSaving && saving) SavingDialog(onBackground = { showSaving = false })
}

/** A pending "unsaved changes" prompt: the pane it asks about, and what to run once it's settled. */
private class GuardRequest(val editor: Editor, val action: () -> Unit)

/** A picked image on its way into [editor], at [at] when a long-press chose the spot. */
private class PendingInsert(val editor: Editor, val at: com.xnotes.core.geometry.Pt?)

/** The pane an Insert menu picker (or the camera, scanner or mic prompt) was opened for. */
private class PendingMedia(val editor: Editor, val canvas: Boolean)

/** Samsung Notes caps a scan at about this many pages; so does the scanner here. */
private const val SCAN_PAGE_LIMIT = 20

/**
 * Several scanned pages onto the infinite canvas: a row of images, left to right, starting in the
 * middle of the view. [com.xnotes.ui.InfiniteEditor.insertImage] sizes each one to the view, so the
 * row is laid out with the same sizing to keep them from landing on top of each other.
 */
private fun insertScansOnCanvas(canvas: com.xnotes.ui.InfiniteEditor, images: List<ByteArray>) {
    val visible = canvas.viewport.visibleContentRect()
    val centre = canvas.viewport.centerContent
    val sizes = images.map { bytes ->
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        val w = o.outWidth.coerceAtLeast(1).toDouble()
        val h = o.outHeight.coerceAtLeast(1).toDouble()
        val scale = minOf(1.0, visible.w * 0.6 / w, visible.h * 0.6 / h)
        w * scale
    }
    val gap = visible.w * 0.03
    var x = centre.x - sizes.first() / 2.0
    for ((i, bytes) in images.withIndex()) {
        canvas.insertImage(bytes, com.xnotes.core.geometry.Pt(x + sizes[i] / 2.0, centre.y))
        x += sizes[i] + gap
    }
}

/** Side-panel pages of [editor] waiting on a SAF "Save as" destination. */
private class PendingPages(val editor: Editor, val pages: List<Int>)

/**
 * A running PDF export's progress: [done] of [total], and what those are. [counting] exists because
 * a canvas has no pages to count — it exports as one, and reports the items it is drawing instead —
 * so the dialog cannot assume the unit. `total < 0` marks the final write phase, where [done] is a
 * 0..1000 permille of the output bytes.
 */
private class ExportProgress(val done: Int, val total: Int, val counting: String, val images: Boolean = false) {
    companion object {
        /** What a document of [kind] counts on its way out. */
        fun countingFor(kind: com.xnotes.core.util.DocumentKind): String =
            if (kind == com.xnotes.core.util.DocumentKind.CANVAS) "item" else "page"
    }
}

/**
 * What a pane can ask the screen around it to do: open a SAF picker, run an export, or change the
 * window. Each callback takes the pane it is acting for, because in a split there are two of them
 * and a picker's result has to come back to the one that opened it.
 */
private class PaneActions(
    val onToggleFullscreen: () -> Unit,
    /** Leave for the backstage, closing every open pane. */
    val onOpenBackstage: () -> Unit,
    /** Close just this pane, leaving the other one to fill the window. */
    val onClosePane: (Editor) -> Unit,
    val onInsertImage: (Editor, com.xnotes.core.geometry.Pt?) -> Unit,
    val onInsertCanvasImage: (Editor, com.xnotes.core.geometry.Pt?) -> Unit,
    /** The header's Insert menu, for either surface. */
    val onInsert: (Editor, com.xnotes.ui.InsertKind) -> Unit,
    val onAddStickers: () -> Unit,
    val onImportTemplate: () -> Unit,
    val onSharePages: (Editor, List<Int>, Boolean) -> Unit,
    /** The header's Share: opens the share sheet for this pane. */
    val onShareNote: (Editor) -> Unit,
    val onSavePagesAsPdf: (Editor, List<Int>) -> Unit,
    val onSavePagesAsImages: (Editor, List<Int>) -> Unit,
)

/** Neither pane of a split may be squeezed below this share of the split axis. */
private const val MIN_PANE_RATIO = 0.18f

/** How wide the divider is to a finger. Mostly empty around the line, so it is easy to catch. */
private val DIVIDER = 16.dp

/** The accent line drawn down the middle of the divider, the same weight as a pane's focus line. */
private val DIVIDER_LINE = 2.dp

/**
 * Makes the open panes opaque to touch. Compose stops hit-testing lower siblings once a higher one
 * is hit, so this node — hit anywhere over the panes — keeps a tap that no child handled from
 * reaching the backstage composed underneath. It never consumes, so children still see every event.
 */
private val SwallowTouches = Modifier.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Initial)
    }
}

/**
 * Lays the open panes over the backstage. Both open is a split: side by side in landscape, stacked
 * in portrait, with a draggable divider between them. One open is that pane full-screen, and none
 * leaves the backstage showing.
 *
 * The panes are placed by offset and size inside one Box rather than by a Row that becomes a Column,
 * so a pane keeps its composition node — and with it its canvas view, raster caches and GL context —
 * when the split opens, closes or the device turns.
 */
@Composable
private fun SplitHost(editor: Editor, actions: PaneActions) {
    val second = editor.secondary
    val split = editor.noteOpen && second?.noteOpen == true
    // A pane that closed on its own leaves the other one full-screen; once neither is open the
    // second editor is released, freeing its canvas and GL surfaces.
    LaunchedEffect(editor.noteOpen, second?.noteOpen) {
        if (second != null && !second.noteOpen) editor.releaseClosedSecondary()
    }
    if (!editor.noteOpen && second?.noteOpen != true) return

    val sideBySide = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val ratio = editor.splitRatio.coerceIn(MIN_PANE_RATIO, 1f - MIN_PANE_RATIO)
    // Filled before the keyboard's inset is taken, so the strip a keyboard leaves under the panes
    // shows the editor's own background rather than the library underneath it.
    val under = com.xnotes.ui.theme.LocalPalette.current.bg.toComposeColor()
    BoxWithConstraints(Modifier.fillMaxSize().background(under).then(SwallowTouches).imePadding()) {
        val fullW = maxWidth
        val fullH = maxHeight
        val full = if (sideBySide) fullW else fullH
        // The panes meet along the split axis and take all of it; the divider is not a gap between
        // them but a handle floating over the seam, so there is no strip of its own colour to show
        // beside the toolbars. Across the axis both run full.
        val firstExtent = if (split) full * ratio else full
        val secondExtent = if (split) full - firstExtent else full

        /** Sizes a pane to [extent] along the split axis and the whole window across it. */
        fun paneSize(extent: Dp) = Modifier.size(
            if (sideBySide) extent else fullW,
            if (sideBySide) fullH else extent,
        )

        /** Offsets a pane [along] the split axis from the top-left of the editor area. */
        fun paneOffset(along: Dp) = Modifier.offset(
            x = if (sideBySide) along else 0.dp,
            y = if (sideBySide) 0.dp else along,
        )

        if (editor.noteOpen) {
            EditorPane(
                editor = editor,
                app = editor,
                actions = actions,
                closable = split,
                modifier = paneSize(firstExtent),
            )
        }
        if (second?.noteOpen == true) {
            EditorPane(
                editor = second,
                app = editor,
                actions = actions,
                closable = split,
                modifier = paneOffset(if (split) firstExtent else 0.dp).then(paneSize(secondExtent)),
            )
        }
        // Composed last so it sits over both panes and catches the drag before either of them.
        if (split) {
            SplitDivider(
                sideBySide = sideBySide,
                extentPx = with(LocalDensity.current) { full.toPx() },
                ratio = editor.splitRatio,
                onRatio = { editor.splitRatio = it },
                modifier = paneOffset((firstExtent - DIVIDER / 2).coerceAtLeast(0.dp))
                    .then(paneSize(DIVIDER)),
            )
        }
    }
}

/**
 * One pane: its toolbar over its canvas, with the menus and overlays that belong to that document.
 * A paged note and an infinite canvas get different chrome, since the paged toolbar is mostly pages,
 * viewing modes and text, none of which mean anything on a canvas.
 *
 * [app] is the primary editor, which owns the split; [editor] is this pane's own. Touching anywhere
 * in the pane gives it the focus, so the keyboard and the file actions follow the pen.
 */
@Composable
private fun EditorPane(
    editor: Editor,
    app: Editor,
    actions: PaneActions,
    closable: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val focusRequester = remember { FocusRequester() }
    val focused = app.active === editor
    // This pane owns the keyboard while it is the focused one; (re)grab it as that changes.
    LaunchedEffect(focused, editor.noteOpen) {
        if (focused && editor.noteOpen) runCatching { focusRequester.requestFocus() }
    }
    val takeFocus = Modifier.pointerInput(editor) {
        awaitPointerEventScope {
            while (true) {
                // Initial pass and never consumed: the pane notices the touch, the canvas still gets it.
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press) app.focusPane(editor.pane)
            }
        }
    }
    Column(
        modifier = modifier
            .background(palette.bg.toComposeColor())
            .then(takeFocus)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { ke ->
                ke.type == KeyEventType.KeyDown && editor.handleKeyDown(ke.nativeKeyEvent)
            },
    ) {
        val onClose = if (closable) ({ actions.onClosePane(editor) }) else null
        // The pane's toolbar, recorded by its ToolbarFrame, so the header's cards can keep clear of it.
        val paneBar = remember { com.xnotes.ui.kit.PopoverEdge() }
        // In a split, a hairline over the toolbar marks the pane the pen and the keyboard are in.
        if (closable) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background((if (focused) palette.accent else palette.border).toComposeColor()),
            )
        }
        if (editor.canvasOpen) {
            val canvas = editor.infinite
            // Remembered: a new lambda each recomposition would change the static local and recompose the pane.
            val penDownOf = remember(canvas) { { canvas.penDown } }
            // The pane's canvas, for the colour picker's eyedropper (Part 5).
            CompositionLocalProvider(LocalPenDown provides penDownOf, com.xnotes.ui.LocalCanvasHost provides canvas, com.xnotes.ui.LocalPaneBar provides paneBar) {
                val bar: @Composable () -> Unit = {
                    com.xnotes.ui.InfiniteToolbar(
                        canvas,
                        onOpenBackstage = actions.onOpenBackstage,
                        onInsertImage = { actions.onInsertCanvasImage(editor, null) },
                    )
                }
                com.xnotes.ui.CanvasHeader(
                    canvas,
                    onOpenBackstage = actions.onOpenBackstage,
                    onShare = { actions.onShareNote(editor) },
                    onClosePane = onClose,
                    onInsert = { kind -> actions.onInsert(editor, kind) },
                )
                ToolbarAround(bar, onCover = canvas::setToolbarCover) { floatingBar ->
                    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                        AndroidView(
                            factory = { detached(canvas.surfaces) },
                            modifier = Modifier.fillMaxSize(),
                            update = { canvas.view.publish() },
                        )
                        PenBoxRail(canvas)
                        floatingBar()
                        com.xnotes.ui.SelectionMenu(canvas)
                        com.xnotes.ui.LongPressMenu(canvas, onInsertImageAt = { c -> actions.onInsertCanvasImage(editor, c) })
                        com.xnotes.ui.CanvasDebugOverlay(canvas)
                    }
                }
            }
        } else {
            val penDownOf = remember(editor) { { editor.penDown } }
            // The pane's canvas, for the colour picker's eyedropper (Part 5).
            CompositionLocalProvider(LocalPenDown provides penDownOf, com.xnotes.ui.LocalCanvasHost provides editor, com.xnotes.ui.LocalPaneBar provides paneBar) {
                val bar: @Composable () -> Unit = {
                    Toolbar(
                        editor,
                        onToggleFullscreen = actions.onToggleFullscreen,
                        onOpenBackstage = actions.onOpenBackstage,
                        onInsertImage = { actions.onInsertImage(editor, null) },
                        onAddStickers = actions.onAddStickers,
                        onImportTemplate = actions.onImportTemplate,
                    )
                }
                com.xnotes.ui.NoteHeader(
                    editor,
                    onOpenBackstage = actions.onOpenBackstage,
                    onToggleFullscreen = actions.onToggleFullscreen,
                    onImportTemplate = actions.onImportTemplate,
                    onShare = { actions.onShareNote(editor) },
                    onClosePane = onClose,
                    onInsert = { kind -> actions.onInsert(editor, kind) },
                    onInsertStickyNote = { editor.insertStickyNote() },
                    onInsertTable = { editor.openTablePicker(null) },
                    recorder = { com.xnotes.ui.RecorderCapsule(editor) },
                )
                ToolbarAround(bar, onCover = editor::setToolbarCover) { floatingBar ->
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (editor.sidebarVisible) {
                            com.xnotes.ui.SidePanel(
                                editor,
                                onSharePages = { pages, asPdf -> actions.onSharePages(editor, pages, asPdf) },
                                onSavePagesAsPdf = { pages -> actions.onSavePagesAsPdf(editor, pages) },
                                onSavePagesAsImages = { pages -> actions.onSavePagesAsImages(editor, pages) },
                            )
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                            AndroidView(
                                factory = { detached(editor.surfaces) },
                                modifier = Modifier.fillMaxSize(),
                                update = { editor.view.requestRender() }, // repaint on (re)attach so a push never flashes blank
                            )
                            editor.editingField?.let { field ->
                                com.xnotes.ui.TextEditorOverlay(editor, field)
                            }
                            PenBoxRail(editor)
                            floatingBar()
                            com.xnotes.ui.AudioBars(editor) // recorder / player, under the menus; the player keeps 10 dp over the format pill
                            com.xnotes.ui.SelectionMenu(editor)
                            com.xnotes.ui.ScreenshotMenu(editor)
                            TextFormatBar(editor) // floating format pill: over the canvas, under the text menus
                            com.xnotes.ui.TextStyleBar(editor)
                            com.xnotes.ui.TableObjectBar(editor)
                            com.xnotes.ui.LongPressMenu(editor, onInsertImageAt = { c -> actions.onInsertImage(editor, c) })
                            com.xnotes.ui.FlowEditMenu(editor)
                            com.xnotes.ui.PdfSelectionMenu(editor)
                            com.xnotes.ui.MarkupNotePeek(editor)
                            com.xnotes.ui.MarkupMenu(editor)
                            com.xnotes.ui.MarkupNoteDialog(editor)
                            com.xnotes.ui.SlashMenu(editor)
                            com.xnotes.ui.FlowTableMenu(editor)
                            com.xnotes.ui.TableChrome(editor)
                            ZoomLockHint(editor)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The handle over the seam between two split panes. Dragging it moves the boundary along the split
 * axis, keeping both panes at least [MIN_PANE_RATIO] of it; double-tapping puts it back in the
 * middle. Only the line is painted — the margin either side of it is what the finger catches, and it
 * stays transparent so the panes it lies over show through.
 */
@Composable
private fun SplitDivider(
    sideBySide: Boolean,
    extentPx: Float,
    ratio: Float,
    onRatio: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    // Read inside the long-lived drag gesture, which does not restart as the ratio moves.
    val ratioNow = rememberUpdatedState(ratio)
    val onRatioNow = rememberUpdatedState(onRatio)
    var dragged by remember { mutableStateOf(0f) }
    Box(
        modifier = modifier
            .pointerInput(sideBySide, extentPx) {
                detectDragGestures(
                    onDragStart = { dragged = ratioNow.value },
                    onDrag = { change, drag ->
                        change.consume()
                        if (extentPx <= 0f) return@detectDragGestures
                        val along = if (sideBySide) drag.x else drag.y
                        dragged = (dragged + along / extentPx).coerceIn(MIN_PANE_RATIO, 1f - MIN_PANE_RATIO)
                        onRatioNow.value(dragged)
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onRatioNow.value(0.5f) })
            },
        contentAlignment = Alignment.Center,
    ) {
        // The line itself: thin, accent, and running the whole way, so it closes the frame the two
        // panes' focus lines start along their toolbars. The empty margin either side of it is what
        // the finger actually catches.
        Box(
            Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (sideBySide) (DIVIDER - DIVIDER_LINE) / 2 else 0.dp,
                    vertical = if (sideBySide) 0.dp else (DIVIDER - DIVIDER_LINE) / 2,
                )
                .clip(RoundedCornerShape(DIVIDER_LINE / 2))
                .background(palette.accent.toComposeColor()),
        )
    }
}

/**
 * Hands a reused View back to [AndroidView]. A pane's canvas can move to a fresh composition node
 * when the split opens or closes, and the new node parents the view itself, so it has to leave the
 * old parent first rather than throw.
 */
private fun <T : android.view.View> detached(view: T): T =
    view.also { (it.parent as? android.view.ViewGroup)?.removeView(it) }

/**
 * Wraps a PDF export's output stream and throws the instant [cancelled] turns true, so PdfBox's
 * otherwise-uninterruptible `save()` (which writes the whole document in one call) aborts promptly
 * when the user taps Cancel, instead of running to completion on a background thread.
 */
private class CancellableOutputStream(
    private val out: java.io.OutputStream,
    private val cancelled: () -> Boolean,
) : java.io.OutputStream() {
    override fun write(b: Int) { if (cancelled()) throw java.io.InterruptedIOException(); out.write(b) }
    override fun write(b: ByteArray, off: Int, len: Int) {
        if (cancelled()) throw java.io.InterruptedIOException()
        out.write(b, off, len)
    }
    override fun flush() { out.flush() }
    override fun close() { out.close() }
}

/**
 * "Exporting to PDF…" sheet shown while a (possibly large) note is flattened to a PDF off the main
 * thread. The bar fills by pages (or canvas items) done, then by the final write's own percentage
 * ("Writing the PDF… 40%"); Preparing has nothing to count, so the bar slides. Dismissing it (Cancel,
 * back, or tapping outside) aborts the export via [onCancel]. With [images] it is the library's
 * Share › Images instead ("Exporting images…"), counting the pages rendered.
 */
@Composable
private fun PdfExportDialog(done: Int, total: Int, counting: String, onCancel: () -> Unit, images: Boolean = false) {
    val line = when (val s = com.xnotes.ui.ProgressText.exportStage(done, total, counting)) {
        com.xnotes.ui.ExportStage.Preparing -> stringResource(R.string.preparing)
        is com.xnotes.ui.ExportStage.Step -> stringResource(if (s.items) R.string.export_item_progress else R.string.export_page_progress, s.done, s.total)
        is com.xnotes.ui.ExportStage.Finishing -> pluralStringResource(if (s.items) R.plurals.export_writing_items else R.plurals.export_writing_pages, s.total, s.total)
        is com.xnotes.ui.ExportStage.Writing -> stringResource(R.string.export_writing_percent, stringResource(R.string.writing_pdf), s.percent)
    }
    val title = stringResource(if (images) R.string.exporting_images else R.string.exporting_pdf)
    com.xnotes.ui.kit.InkProgressSheet(title, line, com.xnotes.ui.ProgressText.exportFraction(done, total), onCancel)
}

/**
 * Indeterminate progress sheet: a [title], a "This may take a moment." line, a sliding bar and a
 * Cancel button. Shared by the "Importing…" (PDF/note copy) and "Opening note…" (off-thread read)
 * flows, neither of which has a meaningful percentage. Dismissing it (Cancel, back, or tapping
 * outside) calls [onCancel].
 */
@Composable
private fun SpinnerDialog(title: String, onCancel: () -> Unit) {
    com.xnotes.ui.kit.InkProgressSheet(title, stringResource(R.string.may_take_moment), fraction = null, onCancel = onCancel)
}

/**
 * Non-cancelable "Saving your notes…" sheet shown while a dirty note is flushed off the main thread
 * on close or pause, so quitting a large note shows progress instead of a frozen (ANR) screen. A save
 * that hangs offers "Keep in background" after a while, so the app is never locked behind it: the
 * write goes on, and whatever waits on it (the note closing) still happens when it lands.
 */
@Composable
private fun SavingDialog(onBackground: () -> Unit) {
    // Not cancellable: no Cancel, and neither Back nor the scrim closes it until the offer shows.
    com.xnotes.ui.kit.InkProgressSheet(
        stringResource(R.string.saving_notes), stringResource(R.string.may_take_moment), fraction = null, onCancel = null,
        onBackground = onBackground,
    )
}

/**
 * Indeterminate "Importing PDF…"/"Importing note…" sheet shown while a picked PDF (or `.xnote`) is
 * streamed into a new note off the main thread. Import has no natural page-by-page progress — the
 * cost is copying the (possibly large) source bytes — so its bar slides.
 */
@Composable
private fun PdfImportDialog(onCancel: () -> Unit) = SpinnerDialog(stringResource(R.string.importing_pdf), onCancel)

/**
 * Determinate "Importing PDFs…" sheet for a multi-file import: a bar filled by files finished plus
 * a running "%d of %d imported" line, so the count stays visible for the whole batch. Cancel stops
 * after the file in flight and keeps the ones already imported.
 */
@Composable
private fun PdfImportBatchDialog(done: Int, total: Int, onCancel: () -> Unit) {
    com.xnotes.ui.kit.InkProgressSheet(
        stringResource(R.string.importing_pdfs),
        stringResource(R.string.import_progress, done, total),
        com.xnotes.ui.ProgressText.batchFraction(done, total),
        onCancel,
    )
}

/**
 * Transient zoom-lock affordance, centred just below the toolbar. Appears when a pinch snaps to
 * fit-to-width ([Editor.zoomLockHint] bumps) and auto-dismisses after a moment. Tapping toggles the
 * zoom lock, so the same chip both locks and unlocks; each tap re-arms the dismiss timer so it
 * lingers long enough to tap again.
 */
@Composable
private fun BoxScope.ZoomLockHint(editor: Editor) {
    val ink = LocalInk.current
    var visible by remember { mutableStateOf(false) }
    // Bumped on the initial fit-width snap and on every tap; (re)starts the auto-dismiss timer below.
    var armToken by remember { mutableStateOf(0) }
    LaunchedEffect(editor.zoomLockHint) {
        if (editor.zoomLockHint > 0) {
            visible = true
            armToken++
        }
    }
    LaunchedEffect(armToken) {
        if (armToken > 0) {
            delay(2500)
            visible = false
        }
    }
    // Breaking past the fit-width magnet dismisses the hint at once.
    LaunchedEffect(editor.zoomLockHintDismiss) {
        if (editor.zoomLockHintDismiss > 0) visible = false
    }
    val locked = editor.zoomLocked
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.TopCenter).padding(com.xnotes.ui.LocalToolbarCover.current).padding(top = 8.dp),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Row(
            modifier = Modifier
                .inkSurface(androidx.compose.foundation.shape.RoundedCornerShape(percent = 50), InkElevation.FLOAT)
                .clickable { editor.toggleZoomLock(); armToken++ }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                // A toggle that is on takes Fill, as the toolbar's zoom-lock button (Toolbar.kt).
                if (locked) Ph.lockSimpleFill else Ph.lockSimpleOpen,
                contentDescription = if (locked) stringResource(R.string.unlock_zoom) else stringResource(R.string.lock_zoom_fit_width),
                tint = ink.text,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (locked) stringResource(R.string.unlock_zoom) else stringResource(R.string.lock_zoom),
                style = InkType.chip,
                color = ink.text,
            )
        }
    }
}

/** Queries the storage provider for a document's user-visible file name, if available. */
private fun displayNameOf(resolver: android.content.ContentResolver, uri: Uri): String? = runCatching {
    resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (i >= 0) c.getString(i) else null
        } else null
    }
}.getOrNull()
