package com.xnotes.gl

import android.opengl.GLES30
import android.util.Log
import com.xnotes.core.geometry.Rect
import com.xnotes.core.infinite.InkPass
import com.xnotes.core.infinite.MeshPart
import com.xnotes.core.infinite.PixelRect
import com.xnotes.core.infinite.WetDamage
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.floor
import kotlin.math.max

/**
 * The stroke under the pen, drawn into the front buffer.
 *
 * The buffer is the one being scanned out, so a present may only touch pixels it is about to put
 * back correctly. That is the whole shape of this class: work out the damage, rebuild every pixel
 * of it from geometry, and put it down in one blit. Nothing is copied forward and nothing
 * accumulates, so a tail that retracts is not a special case.
 *
 * Ink is antialiased by a multisampled buffer the size of the damage, because the fragment shader
 * emits flat colour and no config with the mutable-render-buffer bit has samples of its own. The
 * resolve is a blit with identical source and destination rectangles, which is the form GLES3
 * allows out of a multisampled read.
 *
 * ### The pencil
 *
 * Graphite is translucent and goes through the paper's grain, so it cannot simply be laid over
 * itself the way a pen's triangles are. It is drawn by its own variant of the ink shader, and the
 * scratch carries a stencil while a pencil is on the pad, so each stroke's outer and pressed core
 * reach every sample once and in the committed order ([WetPadGraphite]). Every other ink keeps the
 * path it always had; what it pays for the pencil being possible is one flag read per present.
 *
 * ### Threads
 *
 * The main thread owns the queue and the session; the render thread owns every GL object and never
 * reads the model. What crosses is the view baked at pen down and triangles that were built
 * before they were handed over. The viewport cannot move while a stroke is down, which is what lets
 * the session be baked once.
 */
class GlWetPadInk {

    /** Everything a present needs from the main thread, baked at pen down and never revisited. */
    private class Session(
        /** Counts up per stroke, so a batch can say which view it was meshed through. */
        val serial: Long,
        val scrollX: Double,
        val scrollY: Double,
        val zoom: Double,
        val width: Int,
        val height: Int,
        /** Where the stroke is allowed to show, in view pixels: a page's paper, or the surface. */
        val clip: PixelRect,
    )

    /**
     * Triangles handed over, already meshed. A run is appended once; the tail replaces the last.
     * [stroke] counts the strokes laid on one session, which is how a pencil tells its own pieces
     * from another stroke's.
     */
    private class Batch(val serial: Long, val parts: List<MeshPart>, val settled: Boolean, val stroke: Int)

    /**
     * An uploaded slice with the pixels it can reach, so a present that misses it can skip it.
     *
     * A pencil's piece also carries its half ([WetPadGraphite.OUTER] or [WetPadGraphite.CORE]), the
     * stroke it belongs to, its own alpha and, for a core, the outer's it goes over.
     */
    private class Piece(
        val slice: BufferSlice,
        val bounds: PixelRect?,
        val role: Int = WetPadGraphite.NONE,
        val stroke: Int = 0,
        val alpha: Float = 0f,
        val under: Float = 0f,
    )

    private val pending = ConcurrentLinkedQueue<Batch>()

    @Volatile
    private var session: Session? = null

    /**
     * The session [end] put down, kept so a stroke starting on the same view can pick it up.
     *
     * Volatile because the pad drops it from whichever thread queued the release that wipes it.
     */
    @Volatile
    private var frozen: Session? = null

    /** Serials handed out, read and written on the main thread only. */
    private var serials = 0L

    /** Which stroke of the session the next batch belongs to, main thread only (see [nextStroke]). */
    private var strokeTag = 0

    /**
     * Whether this pad can lay a pencil at all: its grain shader compiled and its grain uploaded.
     * Written by the render thread, read by a stroke deciding where to go.
     */
    @Volatile
    var takesGraphite = false
        private set

    // --- render thread ---

    private var current: Session? = null
    private var contextGen = -1
    private var ink: InkShader? = null

    /** The pencil's variant of [ink], and the paper's grain it samples; null where it would not build. */
    private var grainInk: InkShader? = null
    private var grainTexture = 0

    /** Whether the session being drawn has any pencil on it, so its presents need the stencil. */
    private var sessionGraphite = false

    private val runs = GeometryStore()
    private var runPieces: List<Piece> = emptyList()

    private val tail = GeometryStore()
    private var tailPieces: List<Piece> = emptyList()

    private var scratchFbo = 0
    private var scratchBuffer = 0
    private var resolveFbo = 0
    private var resolveBuffer = 0
    private var scratchW = 0
    private var scratchH = 0
    private var scratchSamples = 0

    /**
     * The scratch's colour with a stencil beside it, for a present with a pencil in it: made the
     * first time one needs it at this size and dropped with the scratch, so a pen never binds it.
     */
    private var stencilFbo = 0
    private var stencilBuffer = 0

    /** Set when a stencil could not be had at this size, so the pencil falls back to a max blend. */
    private var stencilFailed = false

    /** The scratch's colour renderbuffer, whichever field [framebuffer] filed it under. */
    private var scratchColour = 0

    /** Smallest scratch this stroke may use, and the largest bucket it has asked for, per axis. */
    private var floorW = SCRATCH_MIN
    private var floorH = SCRATCH_MIN
    private var peakW = SCRATCH_MIN
    private var peakH = SCRATCH_MIN

    private val damage = PixelRect()
    private val lastTail = PixelRect()

    /** The union of every damage this stroke has had, which is what the handover captures. */
    private val strokeBox = PixelRect()

    /** What the last present drew, in view pixels, for the debug readout. */
    @Volatile
    var lastDamage = ""
        private set

    /**
     * Every pixel this stroke has put ink on, in view pixels, read from the main thread.
     *
     * Four numbers rather than a rectangle because the render thread writes them and the handover
     * reads them: each is written once per present and read once per stroke, and a box that is a
     * pixel stale in either direction only widens what gets captured.
     */
    @Volatile
    var boxLeft = 0
        private set

    @Volatile
    var boxTop = 0
        private set

    @Volatile
    var boxRight = 0
        private set

    @Volatile
    var boxBottom = 0
        private set

    /** Why the last present drew nothing, for the trace. */
    @Volatile
    var why = ""
        private set

    // --- main thread ---

    /**
     * Take the stroke. The view given here is the view the whole stroke is painted through, and
     * [clip] is the only part of the surface it may reach, in view pixels: the paged canvas clips
     * live ink to the paper it is on, and a pen crossing the edge must meet it here too.
     */
    fun begin(
        scrollX: Double,
        scrollY: Double,
        zoom: Double,
        width: Int,
        height: Int,
        clip: PixelRect? = null,
    ): Boolean {
        if (width <= 0 || height <= 0 || !zoom.isFinite() || zoom <= 0.0) return false
        val box = PixelRect(0, 0, width, height)
        if (clip != null) {
            box.set(clip)
            box.clampTo(width, height)
            if (box.isEmpty) return false
        }
        frozen = null
        strokeTag = 0
        session = Session(++serials, scrollX, scrollY, zoom, width, height, box)
        return true
    }

    /**
     * Everything handed over from here on belongs to a new stroke on the same session: a stroke
     * joining the frozen one, once the last stroke's tail has been put down as a run of its own.
     */
    fun nextStroke() {
        strokeTag++
    }

    /** The stroke the next batch will be filed under, for tests. */
    internal val strokeInPlay: Int get() = strokeTag

    /**
     * Pick the frozen stroke's view back up for a stroke starting on exactly it, so the ink already
     * on the pad stays where it is and this one is laid over the top.
     *
     * Exact equality on every number, because the alternative to being the same view is being a
     * slightly wrong one, and the ink already down cannot be redrawn through the new one.
     */
    fun extend(
        scrollX: Double,
        scrollY: Double,
        zoom: Double,
        width: Int,
        height: Int,
        clip: PixelRect? = null,
    ): Boolean {
        val s = frozen ?: return false
        if (s.width != width || s.height != height) return false
        if (s.scrollX != scrollX || s.scrollY != scrollY || s.zoom != zoom) return false
        val box = PixelRect(0, 0, width, height)
        if (clip != null) {
            box.set(clip)
            box.clampTo(width, height)
        }
        if (!box.sameAs(s.clip)) return false
        frozen = null
        session = s
        return true
    }

    /** Ribbon that has stopped moving, added to what is already down. */
    fun appendRun(parts: List<MeshPart>) {
        if (parts.isEmpty()) return
        val s = session ?: return
        pending.add(Batch(s.serial, parts, settled = true, stroke = strokeTag))
    }

    /** The points still in play, replacing whatever was there. */
    fun setTail(parts: List<MeshPart>) {
        val s = session ?: return
        pending.add(Batch(s.serial, parts, settled = false, stroke = strokeTag))
    }

    fun end() {
        // Not an unconditional swap: ending twice would put the frozen stroke's view down as well,
        // and a stroke that could have joined it would find nothing to join.
        if (session != null) frozen = session
        session = null
    }

    /** Drop both, for a surface that is going away and taking every pixel on it. */
    fun forget() {
        session = null
        frozen = null
    }

    /** Drop only the frozen one, for a wipe: nothing that survives it can be joined. */
    fun forgetFrozen() {
        frozen = null
    }

    /** Whether a stroke is live, so the pad knows whether a beat has anything to do. */
    val active: Boolean get() = session != null

    // --- render thread ---

    fun onContextCreated(gen: Int) {
        contextGen = gen
        ink = try {
            InkShader(gen)
        } catch (e: GlShaderException) {
            Log.e(TAG, "wet pad ink shader unavailable", e)
            null
        }
        grainInk = try {
            InkShader(gen, grain = true)
        } catch (e: GlShaderException) {
            Log.e(TAG, "wet pad grain shader unavailable", e)
            null
        }
        grainTexture = if (grainInk != null) GrainCoverShader.uploadGrain() else 0
        takesGraphite = grainInk != null && grainTexture != 0
        runs.onContextCreated(gen)
        tail.onContextCreated(gen)
        scratchFbo = 0
        scratchBuffer = 0
        resolveFbo = 0
        resolveBuffer = 0
        stencilFbo = 0
        stencilBuffer = 0
        stencilFailed = false
        scratchColour = 0
        scratchW = 0
        scratchH = 0
        scratchSamples = 0
        current = null
    }

    /**
     * Paint one present into the bound front buffer, and say whether anything was written.
     *
     * [samples] is what the canvas got for its own surface, so live ink is antialiased to the same
     * standard as the ink it will become.
     */
    fun draw(surfaceW: Int, surfaceH: Int, samples: Int): Boolean {
        // A session just ended may still owe one present: the last tail, queued as it froze, which
        // takes the ink drawn ahead of the nib back off the glass (see [GlWetPad.freezeWithTail]).
        val s = session
            ?: frozen?.takeIf { it === current && pending.peek()?.serial == it.serial }
            ?: run { why = "no session"; return false }
        val program = ink ?: run { why = "no shader"; return false }
        if (program.contextGen != contextGen) { why = "stale shader"; return false }
        if (s !== current) startSession(s)
        // The view was baked at pen down. A surface that changed size under the stroke would put
        // every pixel somewhere else, so there is nothing to draw that would be right.
        if (surfaceW != s.width || surfaceH != s.height) {
            why = "session ${s.width}x${s.height}"
            return false
        }

        drain(s)
        damage.clampTo(s.clip.left, s.clip.top, s.clip.right, s.clip.bottom)
        if (damage.isEmpty) { why = "no damage, runs=${runPieces.size} tail=${tailPieces.size}"; return false }

        val dw = damage.width
        val dh = damage.height
        peakW = max(peakW, bucket(dw))
        peakH = max(peakH, bucket(dh))
        if (!ensureScratch(max(peakW, floorW), max(peakH, floorH), samples)) return false
        // What every other ink pays for the pencil: this flag.
        val grain = if (sessionGraphite) grainInk else null
        val stencilled = grain != null && ensureStencil()

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, if (stencilled) stencilFbo else scratchFbo)
        GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        if (stencilled) {
            GLES30.glStencilMask(0xFF)
            GLES30.glClearStencil(0)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_STENCIL_BUFFER_BIT)
        } else {
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        }
        GLES30.glViewport(0, 0, dw, dh)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)
        GLES30.glEnable(GLES30.GL_BLEND)
        // Separate alpha, unlike the canvas. The canvas draws into an opaque window where the alpha
        // channel is ignored; this is a translucent layer the compositor reads as premultiplied,
        // and the ordinary blend would square the alpha of a sub-pixel stroke and leave its colour
        // too bright for the coverage it claims.
        GLES30.glBlendFuncSeparate(
            GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA,
            GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA,
        )

        val camChunkX = floor(s.scrollX / GeometryStore.CHUNK_SIZE)
        val camChunkY = floor(s.scrollY / GeometryStore.CHUNK_SIZE)
        program.begin(
            camChunkX, camChunkY,
            s.scrollX - camChunkX * GeometryStore.CHUNK_SIZE,
            s.scrollY - camChunkY * GeometryStore.CHUNK_SIZE,
            s.zoom, dw.toDouble(), dh.toDouble(),
            damage.left.toDouble(), damage.top.toDouble(),
        )
        if (grain == null) {
            drawBuffer(program, runs, runPieces)
            drawBuffer(program, tail, tailPieces)
        } else {
            drawWithGraphite(program, grain, stencilled, s, camChunkX, camChunkY, dw, dh)
        }
        program.disableAttributes()

        // Out of a multisampled read GLES3 allows one shape of blit: identical rectangles. So the
        // resolve lands at the twin's origin, and the twin, which carries no such rule, goes to
        // wherever on the surface the damage is.
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, scratchFbo)
        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, resolveFbo)
        GLES30.glBlitFramebuffer(0, 0, dw, dh, 0, 0, dw, dh, GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_NEAREST)
        GLES30.glInvalidateFramebuffer(GLES30.GL_READ_FRAMEBUFFER, 1, INVALIDATE_COLOR, 0)
        if (stencilled) {
            // Nothing reads the stencil after the present, so a tiler need not write it back.
            GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, stencilFbo)
            GLES30.glInvalidateFramebuffer(GLES30.GL_READ_FRAMEBUFFER, 1, INVALIDATE_STENCIL, 0)
        }

        // The surface counts up from the bottom and the damage counts down from the top.
        val bottom = surfaceH - damage.bottom
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, resolveFbo)
        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, 0)
        GLES30.glBlitFramebuffer(
            0, 0, dw, dh,
            damage.left, bottom, damage.left + dw, bottom + dh,
            GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_NEAREST,
        )
        lastDamage = "${dw}x$dh"
        strokeBox.union(damage)
        boxLeft = strokeBox.left
        boxTop = strokeBox.top
        boxRight = strokeBox.right
        boxBottom = strokeBox.bottom
        damage.clear()
        return true
    }

    /** Wipe the whole surface, for the moment the canvas takes the stroke back. */
    fun clearSurface() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
    }

    // --- internals ---

    private fun startSession(s: Session) {
        current = s
        sessionGraphite = false
        floorW = peakW
        floorH = peakH
        peakW = SCRATCH_MIN
        peakH = SCRATCH_MIN
        runs.clear()
        tail.clear()
        runPieces = emptyList()
        tailPieces = emptyList()
        lastTail.clear()
        damage.clear()
        strokeBox.clear()
        boxLeft = 0
        boxTop = 0
        boxRight = 0
        boxBottom = 0
    }

    /**
     * Take everything the main thread has handed over for *this* stroke, and work out what it
     * dirtied.
     *
     * A present reads the session once and can still be here when the next pen down installs the
     * following one, so a batch says which view it was meshed through rather than being assumed to
     * belong to whatever is being drawn. Anything older is left over from a stroke already handed
     * over; anything newer belongs to a present that has not started yet, and waits for it.
     */
    private fun drain(s: Session) {
        while (true) {
            val batch = pending.peek() ?: break
            if (batch.serial > s.serial) break
            pending.poll()
            if (batch.serial < s.serial) continue
            if (batch.settled) {
                val added = upload(batch.parts, runs, s, batch.stroke)
                if (added.isNotEmpty()) runPieces = runPieces + added
                for (piece in added) piece.bounds?.let { damage.union(it) }
            } else {
                tail.clear()
                tailPieces = upload(batch.parts, tail, s, batch.stroke)
                // The tail is rebuilt from scratch and can retract, so where it *was* has to be
                // repainted as well as where it is.
                damage.union(lastTail)
                lastTail.clear()
                for (piece in tailPieces) {
                    val bounds = piece.bounds ?: continue
                    damage.union(bounds)
                    lastTail.union(bounds)
                }
            }
        }
    }

    /** Upload each part and record where it lands, which is both the damage and the cull's box. */
    private fun upload(parts: List<MeshPart>, into: GeometryStore, s: Session, stroke: Int): List<Piece> {
        if (parts.isEmpty()) return emptyList()
        val built = ArrayList<Piece>(parts.size)
        for (part in parts) {
            val role = WetPadGraphite.roleOf(part.pass, part.under)
            if (role == WetPadGraphite.NONE && part.pass != InkPass.OPAQUE) continue
            if (role != WetPadGraphite.NONE && grainInk == null) continue
            // A pencil's alpha comes from its draw, through the grain: its vertices carry only the
            // colour, and the sub-pixel fade the vertex shader multiplies in.
            val color = if (role == WetPadGraphite.NONE) part.color else part.color.withAlpha(255)
            val slice = into.put(part.mesh, color) ?: continue
            val pixels = PixelRect()
            val bounds = boundsOf(part.mesh.positions)
            // No box means nothing may reject it: a part whose bounds will not map is drawn every
            // present rather than dropped from all of them.
            val mapped = bounds != null &&
                WetDamage.map(bounds, s.scrollX, s.scrollY, s.zoom, OUTSET, pixels)
            val box = if (mapped) pixels else null
            if (role == WetPadGraphite.NONE) {
                built.add(Piece(slice, box))
            } else {
                sessionGraphite = true
                built.add(Piece(slice, box, role, stroke, part.color.a / 255f, part.under.toFloat()))
            }
        }
        return built
    }

    /** The content-space box of one mesh's triangles. */
    private fun boundsOf(p: DoubleArray): Rect? {
        var lo0 = Double.MAX_VALUE
        var lo1 = Double.MAX_VALUE
        var hi0 = -Double.MAX_VALUE
        var hi1 = -Double.MAX_VALUE
        var any = false
        var i = 0
        while (i < p.size) {
            val x = p[i]
            val y = p[i + 1]
            if (x < lo0) lo0 = x
            if (x > hi0) hi0 = x
            if (y < lo1) lo1 = y
            if (y > hi1) hi1 = y
            any = true
            i += 2
        }
        if (!any) return null
        return Rect(lo0, lo1, hi0 - lo0, hi1 - lo1)
    }

    /**
     * Draw the pieces that reach the damage, which is what keeps a present the size of what moved
     * rather than the size of the stroke. A long stroke is nearly all somewhere else, and a
     * rectangle test throws it out before a vertex is transformed.
     */
    private fun drawBuffer(program: InkShader, store: GeometryStore, pieces: List<Piece>) {
        if (pieces.isEmpty()) return
        if (!store.bindForDraw(contextGen)) return
        store.bindAttributes(program)
        // Runs go into a fresh buffer in order, so a whole stroke's worth land back to back and
        // collapse into one call rather than one per run.
        var start = -1
        var count = 0
        for (piece in pieces) {
            val bounds = piece.bounds
            if (bounds != null && !bounds.intersects(damage)) continue
            val slice = piece.slice
            if (start >= 0 && slice.indexOffset == start + count) {
                count += slice.indexCount
                continue
            }
            if (start >= 0) store.drawRange(start, count)
            start = slice.indexOffset
            count = slice.indexCount
        }
        if (start >= 0) store.drawRange(start, count)
    }

    /**
     * A present with a pencil in it: both stores in order, pens as [drawBuffer] draws them and each
     * pencil piece through the grain shader under the stencil state its half needs.
     *
     * Each pencil stroke gets a stencil id the first time one of its pieces comes up. A stroke's
     * pieces are always drawn back to back, its settled runs and then its tail, so an id only has
     * to tell this stroke from anything else and ids can be handed out afresh every present
     * ([WetPadGraphite]).
     */
    private fun drawWithGraphite(
        pen: InkShader,
        grain: InkShader,
        stencilled: Boolean,
        s: Session,
        camChunkX: Double,
        camChunkY: Double,
        dw: Int,
        dh: Int,
    ) {
        grain.begin(
            camChunkX, camChunkY,
            s.scrollX - camChunkX * GeometryStore.CHUNK_SIZE,
            s.scrollY - camChunkY * GeometryStore.CHUNK_SIZE,
            s.zoom, dw.toDouble(), dh.toDouble(),
            damage.left.toDouble(), damage.top.toDouble(),
        )
        // Where the camera's chunk falls in the paper's tile, so the grain is anchored to the
        // content (the page, or the canvas's world) just as the committed cover anchors it.
        val tile = com.xnotes.core.stroke.Graphite.TILE.toDouble()
        grain.setGrain(
            GrainCoverShader.wrap(camChunkX * GeometryStore.CHUNK_SIZE, tile),
            GrainCoverShader.wrap(camChunkY * GeometryStore.CHUNK_SIZE, tile),
        )
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, grainTexture)
        ids.reset()
        drawMixed(pen, grain, stencilled, runs, runPieces)
        drawMixed(pen, grain, stencilled, tail, tailPieces)
        grain.disableAttributes()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glDisable(GLES30.GL_STENCIL_TEST)
        GLES30.glBlendEquation(GLES30.GL_FUNC_ADD)
        GLES30.glBlendFuncSeparate(
            GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA,
            GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA,
        )
        // Leave the pen's program current, as a present without a pencil does.
        pen.use()
    }

    /** The stencil ids of one present, handed out stroke by stroke. */
    private class Ids {
        var id = 0
        var stroke = Int.MIN_VALUE

        fun reset() {
            id = 0
            stroke = Int.MIN_VALUE
        }
    }

    private val ids = Ids()

    /** The id of [stroke], clearing the stencil first if the ids have run out. */
    private fun idOf(stroke: Int, stencilled: Boolean): Int {
        if (stroke != ids.stroke) {
            ids.stroke = stroke
            if (stencilled && WetPadGraphite.clearsBefore(ids.id)) {
                GLES30.glStencilMask(0xFF)
                GLES30.glClear(GLES30.GL_STENCIL_BUFFER_BIT)
            }
            ids.id = WetPadGraphite.nextId(ids.id)
        }
        return ids.id
    }

    /**
     * [pieces] of [store] that reach the damage, in order, switching programs only where the ink
     * does. Without a stencil (a driver that would not attach one) a pencil is laid with a max blend
     * instead, which is exact within one stroke on the transparent scratch and differs from the
     * committed look only where two strokes cross.
     */
    private fun drawMixed(
        pen: InkShader,
        grain: InkShader,
        stencilled: Boolean,
        store: GeometryStore,
        pieces: List<Piece>,
    ) {
        if (pieces.isEmpty()) return
        if (!store.bindForDraw(contextGen)) return
        var bound: InkShader? = null
        for (piece in pieces) {
            val bounds = piece.bounds
            if (bounds != null && !bounds.intersects(damage)) continue
            val slice = piece.slice
            if (piece.role == WetPadGraphite.NONE) {
                if (bound !== pen) {
                    pen.use()
                    store.bindAttributes(pen)
                    bound = pen
                    GLES30.glDisable(GLES30.GL_STENCIL_TEST)
                    GLES30.glBlendEquation(GLES30.GL_FUNC_ADD)
                    GLES30.glBlendFuncSeparate(
                        GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA,
                        GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA,
                    )
                }
                store.drawRange(slice.indexOffset, slice.indexCount)
                continue
            }
            if (bound !== grain) {
                grain.use()
                store.bindAttributes(grain)
                bound = grain
                // The grain shader's colour comes out premultiplied.
                GLES30.glBlendFuncSeparate(
                    GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA,
                    GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA,
                )
                if (stencilled) {
                    GLES30.glEnable(GLES30.GL_STENCIL_TEST)
                    GLES30.glStencilMask(0xFF)
                    GLES30.glBlendEquation(GLES30.GL_FUNC_ADD)
                } else {
                    GLES30.glBlendEquation(GLES30.GL_MAX)
                }
            }
            val k = idOf(piece.stroke, stencilled)
            val a = piece.alpha.toDouble()
            val under = piece.under.toDouble()
            if (!stencilled) {
                if (piece.role == WetPadGraphite.OUTER) grain.setPass(a, 0.0) else grain.setPass(under, a)
                store.drawRange(slice.indexOffset, slice.indexCount)
                continue
            }
            if (piece.role == WetPadGraphite.OUTER) {
                GLES30.glStencilFunc(WetPadGraphite.OUTER_FUNC, WetPadGraphite.outerRef(k), WetPadGraphite.OUTER_MASK)
                GLES30.glStencilOp(GLES30.GL_KEEP, GLES30.GL_KEEP, WetPadGraphite.OUTER_OP)
                grain.setPass(a, 0.0)
                store.drawRange(slice.indexOffset, slice.indexCount)
                continue
            }
            // A core is two draws of the same triangles: over this stroke's own outer, and then
            // wherever this stroke has not been yet. Either order holds; this one is the common case.
            GLES30.glStencilFunc(
                WetPadGraphite.CORE_OVER_FUNC, WetPadGraphite.coreOverRef(k), WetPadGraphite.CORE_OVER_MASK,
            )
            GLES30.glStencilOp(GLES30.GL_KEEP, GLES30.GL_KEEP, WetPadGraphite.CORE_OVER_OP)
            grain.setPass(a, 0.0)
            store.drawRange(slice.indexOffset, slice.indexCount)
            GLES30.glStencilFunc(
                WetPadGraphite.CORE_FRESH_FUNC, WetPadGraphite.coreFreshRef(k), WetPadGraphite.CORE_FRESH_MASK,
            )
            GLES30.glStencilOp(GLES30.GL_KEEP, GLES30.GL_KEEP, WetPadGraphite.CORE_FRESH_OP)
            grain.setPass(under, a)
            store.drawRange(slice.indexOffset, slice.indexCount)
        }
    }

    /**
     * The stencilled twin of the scratch, sharing its colour, made the first time a pencil needs it
     * at this size. Returns false, and the pencil takes the max blend, if the driver will not have it.
     */
    private fun ensureStencil(): Boolean {
        if (stencilFbo != 0) return true
        if (stencilFailed || scratchColour == 0) return false
        val fbo = IntArray(1)
        val rb = IntArray(1)
        GLES30.glGenFramebuffers(1, fbo, 0)
        GLES30.glGenRenderbuffers(1, rb, 0)
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, rb[0])
        if (scratchSamples >= 2) {
            GLES30.glRenderbufferStorageMultisample(
                GLES30.GL_RENDERBUFFER, scratchSamples, GLES30.GL_DEPTH24_STENCIL8, scratchW, scratchH,
            )
        } else {
            GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH24_STENCIL8, scratchW, scratchH)
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
        GLES30.glFramebufferRenderbuffer(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_RENDERBUFFER, scratchColour,
        )
        GLES30.glFramebufferRenderbuffer(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_STENCIL_ATTACHMENT, GLES30.GL_RENDERBUFFER, rb[0],
        )
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "wet pad stencil incomplete: $status at ${scratchW}x$scratchH ${scratchSamples}x")
            GLES30.glDeleteFramebuffers(1, fbo, 0)
            GLES30.glDeleteRenderbuffers(1, rb, 0)
            stencilFailed = true
            return false
        }
        stencilFbo = fbo[0]
        stencilBuffer = rb[0]
        return true
    }

    /**
     * The multisampled buffer a present draws into, and the single-sampled twin it resolves to.
     *
     * A full clear is the fast path on a tiler, so the way to stop paying for a screenful of
     * samples to paint a sliver is to make the attachment smaller rather than to scissor the clear.
     * The two sides are bucketed apart because a stroke's damage is a sliver and a square around it
     * is mostly waste. Grow-only within a stroke, since reallocating mid-stroke costs a present,
     * and each stroke starts at what the last one turned out to need.
     */
    private fun ensureScratch(w: Int, h: Int, samples: Int): Boolean {
        val want = wantedSamples(samples)
        if (scratchFbo != 0 && resolveFbo != 0 && scratchW == w && scratchH == h && scratchSamples == want) {
            return true
        }
        release()
        scratchFbo = framebuffer(w, h, want) ?: return false
        // Read before the resolve's own buffer is made: without samples the scratch's colour is
        // filed where the resolve's goes, and a stencilled twin has to share it.
        scratchColour = if (want >= 2) scratchBuffer else resolveBuffer
        resolveFbo = framebuffer(w, h, 0) ?: return false
        scratchW = w
        scratchH = h
        scratchSamples = want
        return true
    }

    /** A colour-only framebuffer of renderbuffers, because nothing ever samples either of these. */
    private fun framebuffer(w: Int, h: Int, samples: Int): Int? {
        val fbo = IntArray(1)
        val rb = IntArray(1)
        GLES30.glGenFramebuffers(1, fbo, 0)
        GLES30.glGenRenderbuffers(1, rb, 0)
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, rb[0])
        if (samples >= 2) {
            GLES30.glRenderbufferStorageMultisample(GLES30.GL_RENDERBUFFER, samples, GLES30.GL_RGBA8, w, h)
        } else {
            GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_RGBA8, w, h)
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
        GLES30.glFramebufferRenderbuffer(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_RENDERBUFFER, rb[0],
        )
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "wet pad target incomplete: $status at ${w}x$h ${samples}x")
            GLES30.glDeleteFramebuffers(1, fbo, 0)
            GLES30.glDeleteRenderbuffers(1, rb, 0)
            return null
        }
        if (samples >= 2) scratchBuffer = rb[0] else resolveBuffer = rb[0]
        return fbo[0]
    }

    private fun bucket(need: Int): Int {
        var size = SCRATCH_MIN
        while (size < need + 2 && size < SCRATCH_MAX) size *= 2
        return size
    }

    private fun wantedSamples(samples: Int): Int {
        if (samples < 2) return 0
        val max = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_SAMPLES, max, 0)
        return minOf(samples, max[0].coerceAtLeast(0))
    }

    private fun release() {
        if (stencilFbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(stencilFbo), 0)
        if (stencilBuffer != 0) GLES30.glDeleteRenderbuffers(1, intArrayOf(stencilBuffer), 0)
        stencilFbo = 0
        stencilBuffer = 0
        stencilFailed = false
        // Without samples the scratch's colour is a buffer of its own that nothing else names.
        if (scratchColour != 0 && scratchColour != scratchBuffer && scratchColour != resolveBuffer) {
            GLES30.glDeleteRenderbuffers(1, intArrayOf(scratchColour), 0)
        }
        scratchColour = 0
        if (scratchFbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(scratchFbo), 0)
        if (resolveFbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(resolveFbo), 0)
        if (scratchBuffer != 0) GLES30.glDeleteRenderbuffers(1, intArrayOf(scratchBuffer), 0)
        if (resolveBuffer != 0) GLES30.glDeleteRenderbuffers(1, intArrayOf(resolveBuffer), 0)
        scratchFbo = 0
        resolveFbo = 0
        scratchBuffer = 0
        resolveBuffer = 0
        scratchW = 0
        scratchH = 0
        scratchSamples = 0
    }

    private companion object {
        const val TAG = "xnotes.gl"

        /** The largest scratch side, in pixels; a damage wider than this is clamped to it. */
        const val SCRATCH_MAX = 1024

        const val SCRATCH_MIN = 16

        /** Read only, and only from the render thread, which is the only thread with a context. */
        val INVALIDATE_COLOR = intArrayOf(GLES30.GL_COLOR_ATTACHMENT0)

        val INVALIDATE_STENCIL = intArrayOf(GLES30.GL_DEPTH_STENCIL_ATTACHMENT)

        /**
         * Pixels a damaged box grows on every side: one for its own antialiasing, and the width a
         * sub-pixel stroke is pushed back out to before its alpha is taken instead.
         */
        val OUTSET = WetDamage.OUTSET + InkShader.MIN_HALF_WIDTH_PX
    }
}
