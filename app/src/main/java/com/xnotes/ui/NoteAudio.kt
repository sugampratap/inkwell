package com.xnotes.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.PlaybackParams
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.xnotes.R
import com.xnotes.canvas.CanvasState
import com.xnotes.canvas.CanvasView
import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.history.AddItem
import com.xnotes.core.history.AddItems
import com.xnotes.core.history.Command
import com.xnotes.core.history.CompositeCommand
import com.xnotes.core.history.History
import com.xnotes.core.model.AudioData
import com.xnotes.core.model.AudioItem
import com.xnotes.core.model.AudioStamp
import com.xnotes.core.model.CanvasItem
import com.xnotes.core.model.Page
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.Stroke
import com.xnotes.core.pal.BlendMode
import com.xnotes.core.pal.RasterSurface
import com.xnotes.core.pal.Renderer
import com.xnotes.core.tools.Tool
import com.xnotes.platform.AndroidSurfaceFactory
import com.xnotes.settings.PlaybackSpeed
import com.xnotes.ui.icons.Ph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

/** What [NoteAudio] needs from the paged editor it serves; the editor implements it over itself. */
internal interface NoteAudioHost {
    val context: Context
    val state: CanvasState
    val history: History
    val view: CanvasView

    /** The note-asset temp dir the codec streams images and audio into (purged on launch). */
    val assetDir: File

    /** The tool the pen holds now: a tap with a selecting tool selects a chip instead of playing it. */
    val currentTool: Tool

    fun say(text: String, action: Pair<String, () -> Unit>? = null, icon: ImageVector? = null)

    /** The process-wide playback speed (Preferences.playbackSpeed), read when this pane's audio is built. */
    val playbackSpeed: Float

    /** The document changed: refresh the chrome and schedule the autosave. */
    fun contentChanged()

    /** Where an insert lands: the current page and the centre of its visible part, in page space. */
    fun insertionPoint(): Pair<Int, Pt>?

    /** Hand a touch event to the canvas's own gesture handling, as if it had never been held. */
    fun forwardTouch(ev: MotionEvent): Boolean
}

/**
 * Voice recording, audio playback and synced playback for one paged editor pane.
 *
 * **Recording** uses [MediaRecorder] (AAC in an `.m4a`) into the note-asset dir. While it runs,
 * every item the user adds is stamped with the recording and its offset into it: [History.onPush]
 * sees each edit as it is recorded, and the items an [AddItem]/[AddItems] puts down are written
 * into the document's [com.xnotes.core.model.Document.audioStamps] side table. A stroke is stamped
 * from when the pen went down, not when it lifted. Stop puts a chip ([AudioItem]) on the page.
 * There is no foreground service: when the app goes to the background the recording stops and what
 * was recorded is kept ([onAppStopped]); closing or switching the note does the same.
 *
 * **Playback** is a [MediaPlayer] driven from the chip (tap) and the floating player bar.
 *
 * **Synced playback.** Playing a recording that has stamped items replays the writing: items stamped
 * later than the playback position are taken out of the page caches (through the canvas's own
 * "lifted item" exclusion, [hides]) and drawn instead as a faint ghost layer, then come back at full
 * strength as playback reaches them. Nothing is rebuilt per frame: the ghosts are one cached raster
 * per page, and when the play head crosses a stamp only that item's region is repaired, in the page
 * cache and in the ghost layer. A tap on a stamped item while the player is open seeks to it.
 */
@Stable
class NoteAudio internal constructor(private val host: NoteAudioHost) {

    private val scope = MainScope()
    private val main = Handler(Looper.getMainLooper())

    // --- recording state (observed by the recording bar) ---

    var recordingActive by mutableStateOf(false)
        private set
    var recordingPaused by mutableStateOf(false)
        private set
    var recordingElapsedMs by mutableLongStateOf(0L)
        private set

    /** The microphone level, 0..1, for the bar's meter. */
    var recordingLevel by mutableFloatStateOf(0f)
        private set

    /** The last [TRACE_BARS] levels, one per recorder tick (120 ms), for the header's rolling trace; drawn only. */
    internal val levelHistory = LevelHistory()

    /** Bumped on every [levelHistory] push. The trace reads it while drawing, so a new level only redraws it. */
    var levelTick by mutableIntStateOf(0)
        private set

    private var recorder: MediaRecorder? = null
    private var recFile: File? = null
    private var recId: String? = null
    private var recStart = 0L
    private var recPausedTotal = 0L
    private var recPausedSince = -1L
    private var recTicker: Job? = null

    // --- playback state (observed by the player bar) ---

    var playerItem by mutableStateOf<AudioItem?>(null)
        private set
    var playerPlaying by mutableStateOf(false)
        private set
    var playerPositionMs by mutableLongStateOf(0L)
        private set
    var playerDurationMs by mutableLongStateOf(0L)
        private set

    /** Whether the playing recording has notes synced to it (the bar says so). */
    var playerSynced by mutableStateOf(false)
        private set

    /** The player's speed (process-wide; Editor persists it): the chip shows it, [setSpeed] changes it. */
    var playbackSpeed by mutableFloatStateOf(PlaybackSpeed.coerce(host.playbackSpeed))
        private set

    /** What the current MediaPlayer was last set to; a fresh player plays at 1×. */
    private var appliedSpeed = PlaybackSpeed.NORMAL

    /** The last seek tap on a synced note, for the player's ring (AU 559-575); null once the ring has played. */
    internal var seekTap by mutableStateOf<SeekTap?>(null)
        private set
    private var seekTaps = 0L

    private var player: MediaPlayer? = null
    private var prepared = false
    private var pendingSeek = -1L
    private var playTicker: Job? = null
    private var focusRequest: AudioFocusRequest? = null

    // ---------------------------------------------------------------------------------------------
    // Recording
    // ---------------------------------------------------------------------------------------------

    /** Start a voice recording (the microphone permission is already granted). */
    fun startRecording() {
        if (recorder != null) return
        closePlayer()
        val file = runCatching { File.createTempFile("rec", ".m4a", host.assetDir) }.getOrNull()
        if (file == null) {
            host.say(host.context.getString(R.string.err_record_start), icon = Ph.warningCircle)
            return
        }
        @Suppress("DEPRECATION")
        val rec = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(host.context) else MediaRecorder()
        try {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioChannels(1)
            rec.setAudioSamplingRate(44_100)
            rec.setAudioEncodingBitRate(96_000)
            rec.setOutputFile(file.path)
            rec.prepare()
            rec.start()
        } catch (_: Throwable) {
            rec.release()
            file.delete()
            host.say(host.context.getString(R.string.err_record_start), icon = Ph.warningCircle)
            return
        }
        recorder = rec
        recFile = file
        recId = AudioItem.newRecordingId()
        recStart = SystemClock.elapsedRealtime()
        recPausedTotal = 0L
        recPausedSince = -1L
        recordingElapsedMs = 0L
        recordingPaused = false
        recordingActive = true
        host.history.onPush = ::stampPush
        levelHistory.clear()
        levelTick++
        recTicker = scope.launch {
            while (isActive) {
                recordingElapsedMs = recElapsed()
                val amp = if (recPausedSince < 0) runCatching { rec.maxAmplitude }.getOrDefault(0) else 0
                // A soft log curve: speech sits mid-scale rather than pinned at the bottom.
                recordingLevel = if (amp <= 0) 0f else (kotlin.math.ln(1.0 + amp / 600.0) / kotlin.math.ln(1.0 + 32767 / 600.0)).toFloat().coerceIn(0f, 1f)
                // One sample per tick into the trace; the trace redraws, nothing recomposes.
                levelHistory.push(recordingLevel)
                levelTick++
                delay(120)
            }
        }
    }

    private fun recElapsed(): Long {
        val now = if (recPausedSince >= 0) recPausedSince else SystemClock.elapsedRealtime()
        return (now - recStart - recPausedTotal).coerceAtLeast(0L)
    }

    fun pauseRecording() {
        val rec = recorder ?: return
        if (recPausedSince >= 0) return
        if (runCatching { rec.pause() }.isFailure) return
        recPausedSince = SystemClock.elapsedRealtime()
        recordingPaused = true
        recordingElapsedMs = recElapsed()
    }

    fun resumeRecording() {
        val rec = recorder ?: return
        if (recPausedSince < 0) return
        if (runCatching { rec.resume() }.isFailure) return
        recPausedTotal += SystemClock.elapsedRealtime() - recPausedSince
        recPausedSince = -1L
        recordingPaused = false
    }

    /** Stop the recording; with [insert] its chip goes on the page, else it is discarded. */
    fun stopRecording(insert: Boolean = true) {
        val rec = recorder ?: return
        host.history.onPush = null
        val duration = recElapsed()
        val ok = runCatching { rec.stop() }.isSuccess // throws when nothing was captured at all
        rec.release()
        recorder = null
        recTicker?.cancel()
        recTicker = null
        recordingActive = false
        recordingPaused = false
        recordingLevel = 0f
        val file = recFile
        val id = recId
        recFile = null
        recId = null
        if (file == null || id == null) return
        if (!insert || !ok || duration < MIN_RECORDING_MS) {
            file.delete()
            dropStamps(id)
            if (insert) host.say(host.context.getString(R.string.err_record_short), icon = Ph.clockCountdown)
            return
        }
        val n = host.state.document.pages.sumOf { p -> p.items.count { it is AudioItem && it.voice } } + 1
        insertChip(AudioItem(AudioData(file, "m4a"), Pt.ZERO, host.context.getString(R.string.voice_title, n), duration, voice = true, recording = id))
    }

    /** Stamp what [cmd] put on the page with the running recording and the time into it. */
    private fun stampPush(cmd: Command) {
        val id = recId ?: return
        val now = recElapsed()
        val stamps = host.state.document.audioStamps
        for ((_, item) in addedBy(cmd)) {
            if (item is AudioItem) continue
            // A stroke reaches the history at pen-up; it belongs to the moment the pen went down.
            val lead = (item as? Stroke)?.samples?.let { s ->
                if (s.size >= 2) (s[s.size - 1].t - s[0].t).toLong().coerceAtLeast(0L) else 0L
            } ?: 0L
            stamps[item] = AudioStamp(id, (now - lead).coerceAtLeast(0L))
        }
    }

    private fun addedBy(cmd: Command): List<Pair<Page, CanvasItem>> = when (cmd) {
        is AddItem, is AddItems -> cmd.touched { null }.orEmpty()
        is CompositeCommand -> cmd.parts.flatMap { addedBy(it) }
        else -> emptyList()
    }

    private fun dropStamps(id: String) {
        val stamps = host.state.document.audioStamps
        stamps.keys.filter { stamps[it]?.recording == id }.forEach { stamps.remove(it) }
    }

    // ---------------------------------------------------------------------------------------------
    // Audio files
    // ---------------------------------------------------------------------------------------------

    /** Insert > Audio file: copy the picked audio off the main thread and put its chip down. */
    fun insertAudioFile(uri: android.net.Uri) {
        scope.launch {
            val item = withContext(Dispatchers.IO) { stageAudio(uri) }
            if (item == null) host.say(host.context.getString(R.string.err_audio_read), icon = Ph.warningCircle) else insertChip(item)
        }
    }

    private fun stageAudio(uri: android.net.Uri): AudioItem? {
        val resolver = host.context.contentResolver
        val name = runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
            }
        }.getOrNull()
        val fromName = name?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isNotEmpty() && it.length <= 5 && it.all(Char::isLetterOrDigit) }
        val fromMime = resolver.getType(uri)?.let { android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        val ext = fromName ?: fromMime ?: "m4a"
        val file = runCatching { File.createTempFile("aud", ".$ext", host.assetDir) }.getOrNull() ?: return null
        val copied = runCatching {
            resolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it, 64 * 1024) } } != null
        }.getOrDefault(false)
        val duration = if (copied) durationOf(file) else null
        if (duration == null) {
            file.delete()
            return null
        }
        val title = name?.let { com.xnotes.core.util.Paths.stem(it) }?.takeIf { it.isNotBlank() } ?: "Audio"
        return AudioItem(AudioData(file, ext), Pt.ZERO, title, duration, voice = false)
    }

    /** The file's playing time, or null when it is not audio Android can play. */
    private fun durationOf(file: File): Long? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(file.path)
            val hasAudio = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            val ms = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (hasAudio == null && ms == null) null else (ms ?: 0L)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { mmr.release() }
        }
    }

    /** Put [item] down centred on the insertion point, kept on the paper, as one undo step. */
    private fun insertChip(item: AudioItem) {
        val (index, at) = host.insertionPoint() ?: return
        val state = host.state
        val page = state.document.pages.getOrNull(index) ?: return
        val cover = state.footprint(page)
        item.pos = Pt(
            (at.x - AudioItem.WIDTH / 2.0).coerceIn(cover.left, max(cover.left, cover.right - AudioItem.WIDTH)),
            (at.y - AudioItem.HEIGHT / 2.0).coerceIn(cover.top, max(cover.top, cover.bottom - AudioItem.HEIGHT)),
        )
        page.items.add(item)
        state.appendToCache(page, item)
        host.history.push(AddItem(page, item))
        state.document.dirty = true
        host.contentChanged()
        host.view.requestRender()
    }

    // ---------------------------------------------------------------------------------------------
    // Playback
    // ---------------------------------------------------------------------------------------------

    /** A tap on a chip: play it, or pause/resume it when it is the one already in the player. */
    fun toggle(item: AudioItem) {
        if (recorder != null) {
            host.say(host.context.getString(R.string.player_busy_recording), icon = Ph.microphone)
            return
        }
        if (playerItem === item) {
            if (playerPlaying) pause() else resume()
            return
        }
        play(item, 0L)
    }

    private fun play(item: AudioItem, fromMs: Long) {
        closePlayer()
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(if (item.voice) AudioAttributes.CONTENT_TYPE_SPEECH else AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            mp.setDataSource(item.audio.file.path)
        } catch (_: Throwable) {
            mp.release()
            host.say(host.context.getString(R.string.err_audio_play), icon = Ph.warningCircle)
            return
        }
        player = mp
        prepared = false
        appliedSpeed = PlaybackSpeed.NORMAL
        pendingSeek = fromMs
        playerItem = item
        playerDurationMs = item.durationMs
        playerPositionMs = fromMs
        playerPlaying = false
        mp.setOnPreparedListener {
            if (player !== mp) return@setOnPreparedListener
            prepared = true
            mp.duration.takeIf { it > 0 }?.let { playerDurationMs = it.toLong() }
            if (pendingSeek > 0) mp.seekTo(pendingSeek.toInt())
            pendingSeek = -1L
            startPlaying()
        }
        mp.setOnCompletionListener {
            if (player !== mp) return@setOnCompletionListener
            playerPlaying = false
            playerPositionMs = playerDurationMs
            item.playing = false
            item.progress = 1.0
            repaintChip(item)
            updateSync(Long.MAX_VALUE)
            playTicker?.cancel()
            abandonFocus()
        }
        mp.setOnErrorListener { _, _, _ ->
            if (player === mp) {
                host.say(host.context.getString(R.string.err_audio_play), icon = Ph.warningCircle)
                closePlayer()
            }
            true
        }
        beginSync(item.recording, fromMs)
        runCatching { mp.prepareAsync() }.onFailure {
            host.say(host.context.getString(R.string.err_audio_play), icon = Ph.warningCircle)
            closePlayer()
        }
    }

    private fun startPlaying() {
        val mp = player ?: return
        val item = playerItem ?: return
        requestFocus()
        runCatching { mp.start() }.onFailure { return }
        playerPlaying = true
        // Only now: on a prepared but paused player setPlaybackParams would itself start playback (roadmap Part 7).
        applySpeed(justStarted = true)
        item.playing = true
        repaintChip(item)
        playTicker?.cancel()
        playTicker = scope.launch {
            var lastSync = 0L
            var lastChip = 0L
            while (isActive && player === mp) {
                val pos = runCatching { mp.currentPosition.toLong() }.getOrDefault(playerPositionMs)
                playerPositionMs = pos
                val now = SystemClock.uptimeMillis()
                // Reveal in small batches rather than per frame, so a page cache build in flight is
                // not discarded over and over by repairs while the user scrolls along.
                if (now - lastSync >= SYNC_STEP_MS) {
                    lastSync = now
                    updateSync(pos)
                }
                if (now - lastChip >= CHIP_STEP_MS && playerDurationMs > 0) {
                    lastChip = now
                    item.progress = pos.toDouble() / playerDurationMs
                    repaintChip(item)
                }
                delay(40)
            }
        }
    }

    /**
     * Set the player's speed: the chip's menu or Settings' Reset all, through Editor, for both panes. A playing player
     * changes speed at once; a paused or unprepared one only keeps it, and [startPlaying] applies it after the next
     * start.
     */
    fun setSpeed(v: Float) {
        playbackSpeed = PlaybackSpeed.coerce(v)
        applySpeed(justStarted = false)
    }

    /**
     * The speed, pitch kept natural, on the playing player (see [shouldApplySpeed]). A device that refuses it plays at
     * 1× and says nothing; the chip shows 1× (round-3 defaults, Part 7 row 8). The preference is left as chosen.
     */
    private fun applySpeed(justStarted: Boolean) {
        val mp = player ?: return
        val wanted = playbackSpeed
        if (!shouldApplySpeed(prepared, playerPlaying, appliedSpeed, wanted, justStarted)) return
        runCatching { mp.playbackParams = PlaybackParams().setSpeed(wanted).setPitch(1f) }
            .onSuccess { appliedSpeed = wanted }
            .onFailure {
                runCatching { mp.playbackParams = PlaybackParams().setSpeed(PlaybackSpeed.NORMAL).setPitch(1f) }
                appliedSpeed = PlaybackSpeed.NORMAL
                playbackSpeed = PlaybackSpeed.NORMAL
            }
    }

    fun pause() {
        val mp = player ?: return
        val item = playerItem ?: return
        if (prepared) runCatching { mp.pause() }
        playerPlaying = false
        item.playing = false
        playTicker?.cancel()
        runCatching { playerPositionMs = mp.currentPosition.toLong() }
        updateSync(playerPositionMs)
        repaintChip(item)
        abandonFocus()
    }

    fun resume() {
        val mp = player ?: return
        if (!prepared) return
        if (playerDurationMs > 0 && playerPositionMs >= playerDurationMs - 30) seekTo(0L)
        startPlaying()
    }

    /** Move the play head; the synced notes follow at once. */
    fun seekTo(ms: Long) {
        val mp = player ?: return
        val target = ms.coerceIn(0L, max(0L, playerDurationMs))
        if (prepared) runCatching { mp.seekTo(target.toInt()) } else pendingSeek = target
        playerPositionMs = target
        updateSync(target)
        playerItem?.let { item ->
            if (playerDurationMs > 0) item.progress = target.toDouble() / playerDurationMs
            repaintChip(item)
        }
    }

    /** Stop and dismiss the player bar; the notes come back in full. */
    fun closePlayer() {
        val mp = player
        val item = playerItem
        player = null
        prepared = false
        playTicker?.cancel()
        playTicker = null
        if (mp != null) runCatching { mp.release() }
        playerItem = null
        playerPlaying = false
        playerPositionMs = 0L
        if (item != null) {
            item.playing = false
            item.progress = 0.0
            repaintChip(item)
        }
        endSync()
        abandonFocus()
    }

    private fun requestFocus() {
        val am = host.context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    main.post { if (playerPlaying) pause() }
                }
            }
            .build().also { focusRequest = it }
        runCatching { am.requestAudioFocus(req) }
    }

    private fun abandonFocus() {
        val am = host.context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        focusRequest?.let { runCatching { am.abandonAudioFocusRequest(it) } }
    }

    /** Repaint just [item]'s chip in its page cache, which is all a play-state change touches. */
    private fun repaintChip(item: AudioItem) {
        val state = host.state
        val page = state.document.pages.firstOrNull { p -> p.items.any { it === item } } ?: return
        // repairRegion rather than repairInkRegions: no cache generation bump, so a page build in
        // flight is not thrown away for a progress line (the chip is in it either way).
        state.repairRegion(page, item.paintBounds().outset(2.0))
        host.view.requestRender()
    }

    // ---------------------------------------------------------------------------------------------
    // Synced playback
    // ---------------------------------------------------------------------------------------------

    private class Entry(val page: Page, val item: CanvasItem, val ms: Long)

    private class Ghost(val surface: RasterSurface, val res: Double, val cover: Rect)

    private class SyncSession(val recording: String, val entries: List<Entry>) {
        val byPage: Map<Page, List<Entry>> = entries.groupBy { it.page }

        /** entries[0 until revealed] are shown; the rest are ghosts. */
        var revealed = 0

        /** Read by the cache threads through [hides]: a concurrent set, compared by identity. */
        val hidden: MutableSet<CanvasItem> = Collections.newSetFromMap(ConcurrentHashMap())

        val ghosts = HashMap<Page, Ghost>()
        val building = HashSet<Page>()

        /** Bumped per page whenever its hidden set changes, so a ghost built against an older set is dropped. */
        val version = HashMap<Page, Int>()

        fun hasHidden(page: Page): Boolean = byPage[page]?.any { it.item in hidden } == true

        fun dropGhosts() {
            for (g in ghosts.values) g.surface.recycle()
            ghosts.clear()
            for (p in byPage.keys) version[p] = (version[p] ?: 0) + 1
        }
    }

    private var session: SyncSession? = null
    private val surfaces = AndroidSurfaceFactory()

    /** Whether the canvas should leave [item] out of its caches: a note not yet reached in playback. */
    fun hides(item: CanvasItem): Boolean = session?.hidden?.contains(item) == true

    private fun beginSync(recording: String, fromMs: Long) {
        endSync()
        val stamps = host.state.document.audioStamps
        val entries = ArrayList<Entry>()
        for (page in host.state.document.pages) {
            for (item in page.items) {
                val stamp = stamps[item] ?: continue
                if (stamp.recording == recording && item !is AudioItem) entries.add(Entry(page, item, stamp.ms))
            }
        }
        if (entries.isEmpty()) {
            playerSynced = false
            return
        }
        entries.sortBy { it.ms }
        val s = SyncSession(recording, entries)
        for (e in entries) {
            if (e.ms <= fromMs) s.revealed++ else s.hidden.add(e.item)
        }
        session = s
        playerSynced = true
        host.state.refreshAllInk()
        host.view.requestRender()
    }

    private fun updateSync(pos: Long) {
        val s = session ?: return
        val changed = ArrayList<Entry>()
        val n = s.entries.size
        while (s.revealed < n && s.entries[s.revealed].ms <= pos) {
            val e = s.entries[s.revealed++]
            s.hidden.remove(e.item)
            changed.add(e)
        }
        while (s.revealed > 0 && s.entries[s.revealed - 1].ms > pos) {
            val e = s.entries[--s.revealed]
            s.hidden.add(e.item)
            changed.add(e)
        }
        if (changed.isEmpty()) return
        val state = host.state
        if (changed.size > BULK_CHANGE) {
            // A long seek: cheaper to let the cache threads rebuild than to patch item by item.
            s.dropGhosts()
            state.refreshAllInk()
        } else {
            state.repairInkRegions(changed.map { it.page to it.item.paintBounds() })
            for (e in changed) {
                s.version[e.page] = (s.version[e.page] ?: 0) + 1
                repairGhost(s, e.page, e.item.paintBounds())
            }
        }
        host.view.requestRender()
    }

    private fun endSync() {
        val s = session ?: return
        session = null
        playerSynced = false
        s.dropGhosts()
        host.state.refreshAllInk()
        host.view.requestRender()
    }

    /** Patch [region] (page space) of [page]'s ghost layer in place: clear it, repaint what is still hidden. */
    private fun repairGhost(s: SyncSession, page: Page, region: Rect) {
        val g = s.ghosts[page] ?: return
        val r = g.surface.renderer()
        val dirty = region.outset(4.0)
        r.save()
        r.scale(g.res, g.res)
        r.translate(-g.cover.left, -g.cover.top)
        r.clipRect(dirty)
        r.clear()
        for (e in s.byPage[page].orEmpty()) {
            if (e.item in s.hidden && e.item.paintBounds().intersects(dirty)) e.item.paint(r)
        }
        r.restore()
    }

    private fun ghostFor(s: SyncSession, page: Page): Ghost? {
        s.ghosts[page]?.let { return it }
        if (!s.building.add(page)) return null
        val state = host.state
        val items = s.byPage[page].orEmpty().filter { it.item in s.hidden }.map { it.item }
        val cover = state.footprint(page)
        val longEdge = max(cover.w, cover.h)
        val res = minOf(1.0, GHOST_MAX_PX / longEdge)
        val w = ceil(cover.w * res).toInt().coerceAtLeast(1)
        val h = ceil(cover.h * res).toInt().coerceAtLeast(1)
        val version = s.version[page] ?: 0
        ghostWorker.execute {
            val surface = runCatching {
                surfaces.create(w, h, 1.0).also { surf ->
                    surf.fill(TRANSPARENT)
                    val r = surf.renderer()
                    r.scale(res, res)
                    r.translate(-cover.left, -cover.top)
                    for (item in items) item.paint(r)
                }
            }.getOrNull()
            main.post {
                s.building.remove(page)
                if (surface == null) return@post
                if (session === s && (s.version[page] ?: 0) == version) {
                    s.ghosts[page] = Ghost(surface, res, cover)
                } else {
                    surface.recycle()
                }
                host.view.requestRender()
            }
        }
        return null
    }

    /**
     * The ghost pass: each visible page's not-yet-reached notes, faint, over the page. Called from
     * the canvas's overlay hook, in viewport space.
     */
    fun drawGhosts(r: Renderer) {
        val s = session ?: return
        if (s.hidden.isEmpty()) return
        val state = host.state
        val origin = state.origin()
        val visible = state.visibleContentRect()
        val drawable = state.drawablePageRange()
        r.withSave {
            r.translate(origin.x, origin.y)
            r.scale(state.zoom, state.zoom)
            for (i in state.document.pages.indices) {
                if (i !in drawable) continue
                val pr = state.pageRects.getOrNull(i) ?: continue
                if (!pr.intersects(visible)) continue
                val page = state.document.pages[i]
                if (!s.hasHidden(page)) continue
                val g = ghostFor(s, page) ?: continue
                r.withSave {
                    r.clipRect(pr)
                    r.translate(pr.left, pr.top)
                    state.applyPageTransform(r, page)
                    r.drawRasterBlended(g.surface, g.cover, GHOST_ALPHA, BlendMode.SRC_OVER)
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Taps on chips and on synced notes
    // ---------------------------------------------------------------------------------------------

    private sealed interface Target
    private class ChipTarget(val item: AudioItem) : Target
    private class SeekTarget(val ms: Long, val page: Int) : Target

    /** A touch held back because it began on a chip (or a synced note), until it shows what it is. */
    private class Gate(val target: Target, val x: Float, val y: Float) {
        val events = ArrayList<MotionEvent>()
    }

    private var gate: Gate? = null
    private val releaseGate = Runnable { flushGate() }

    /**
     * First look at every canvas touch. A touch that starts on a chip, or on a synced note while
     * the player is open, is held: lifted where it went down it is a tap (play / seek) and the
     * canvas never sees it; moved, held, or joined by a second finger, it is handed to the canvas
     * whole, so dragging, scrolling, pinching and the long-press menu work exactly as before.
     * Returns true when the event was taken here.
     */
    fun onTouch(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            dropGate()
            val target = targetAt(ev) ?: return false
            val g = Gate(target, ev.x, ev.y)
            g.events.add(MotionEvent.obtain(ev))
            gate = g
            main.postDelayed(releaseGate, HOLD_MS)
            return true
        }
        val g = gate ?: return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                g.events.add(MotionEvent.obtain(ev))
                val slop = android.view.ViewConfiguration.get(host.context).scaledTouchSlop
                if (hypot((ev.x - g.x).toDouble(), (ev.y - g.y).toDouble()) > slop) flushGate()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                g.events.add(MotionEvent.obtain(ev))
                flushGate()
            }
            MotionEvent.ACTION_UP -> {
                dropGate()
                act(g.target, g.x, g.y)
            }
            MotionEvent.ACTION_CANCEL -> dropGate()
            else -> g.events.add(MotionEvent.obtain(ev))
        }
        return true
    }

    private fun flushGate() {
        val g = gate ?: return
        gate = null
        main.removeCallbacks(releaseGate)
        // Not recycled after: the canvas (its motion predictor among it) may keep what it is handed.
        for (e in g.events) host.forwardTouch(e)
    }

    private fun dropGate() {
        val g = gate ?: return
        gate = null
        main.removeCallbacks(releaseGate)
        for (e in g.events) e.recycle()
    }

    private fun act(target: Target, x: Float, y: Float) {
        when (target) {
            is ChipTarget -> toggle(target.item)
            is SeekTarget -> {
                seekTo(seekTargetMs(target.ms))
                // The ring marks where the tap landed (AU 559-575); a chip tap shows none.
                seekTap = SeekTap(x, y, ++seekTaps, host.state.pageAccentAt(target.page))
                if (!playerPlaying) resume()
            }
        }
    }

    /** The player's ring has played out (or the pen went down) for [tap]. */
    internal fun clearSeekTap(tap: SeekTap) {
        if (seekTap == tap) seekTap = null
    }

    private fun targetAt(ev: MotionEvent): Target? {
        if (ev.pointerCount != 1) return null
        when (host.currentTool) {
            Tool.SELECT, Tool.LASSO, Tool.SCREENSHOT -> return null
            else -> Unit
        }
        val state = host.state
        val content = state.viewportToContent(Pt(ev.x.toDouble(), ev.y.toDouble()))
        val index = state.pageIndexAtContent(content) ?: return null
        val page = state.document.pages.getOrNull(index) ?: return null
        val p = state.toPageSpace(index, content)
        page.items.lastOrNull { it is AudioItem && it.contains(p) }?.let { return ChipTarget(it as AudioItem) }
        val s = session ?: return null
        if (playerItem == null) return null
        val slop = TAP_SLOP_PX / state.zoom.coerceAtLeast(0.05)
        val stamps = state.document.audioStamps
        for (i in page.items.indices.reversed()) {
            val item = page.items[i]
            val stamp = stamps[item] ?: continue
            if (stamp.recording != s.recording) continue
            if (item.intersectsCircle(p.x, p.y, slop)) return SeekTarget(stamp.ms, index)
        }
        return null
    }

    // ---------------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------------

    /** The note is closing or being swapped: keep the recording in it, and stop playing. */
    fun onDocumentLeaving() {
        dropGate()
        stopRecording(insert = true)
        closePlayer()
    }

    /** The app went to the background: no foreground service, so the recording stops (kept). */
    fun onAppStopped() {
        if (recorder != null) {
            stopRecording(insert = true)
            host.say(host.context.getString(R.string.recording_saved_background), icon = Ph.microphone)
        }
        if (playerPlaying) pause()
    }

    private companion object {
        /** Shorter than this is a mis-tap on Record: nothing worth keeping. */
        const val MIN_RECORDING_MS = 700L

        const val SYNC_STEP_MS = 120L
        const val CHIP_STEP_MS = 400L

        /** More items crossing at once than this (a long seek) rebuild instead of patching. */
        const val BULK_CHANGE = 80

        const val GHOST_ALPHA = 0.22
        const val GHOST_MAX_PX = 2048.0

        /** Hold a touch on a chip this long before treating it as a press for the canvas. */
        const val HOLD_MS = 350L

        /** Viewport px around a tap that still hits a synced note. */
        const val TAP_SLOP_PX = 14.0

        val TRANSPARENT = Rgba(0, 0, 0, 0)

        /** One low-priority thread for ghost layers, so they never queue behind the page caches. */
        val ghostWorker: java.util.concurrent.Executor by lazy {
            Executors.newSingleThreadExecutor { r ->
                Thread({
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                    r.run()
                }, "inkwell-sync-ghosts").apply { isDaemon = true }
            }
        }
    }
}
