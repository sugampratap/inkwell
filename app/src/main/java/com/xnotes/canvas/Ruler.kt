package com.xnotes.canvas

import com.xnotes.core.geometry.Geometry
import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.model.PageSize
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Which on-ruler control a tap landed on. */
enum class RulerButton { LOCK_POS, LOCK_ANGLE }

/**
 * The on-screen straightedge: an **infinite band** fixed in screen space. It spans
 * the whole viewport along its length (so it has no ends), has a finite [thicknessPx],
 * and does not scroll or scale with the page; its graduations are scaled by the live
 * zoom at draw time so they always measure true content distance.
 *
 * All geometry is in **viewport pixels / radians**. Because the band is infinite,
 * hit-testing and snapping depend only on the perpendicular offset from the centre
 * line (the "across"), never on a length. Pure Kotlin (no Android, no rendering) so
 * the geometry is unit-tested on the JVM, like [ResizeMath].
 */
class Ruler {
    var visible = false
    var center = Pt(0.0, 0.0)   // a point on the centre line; the graduation origin
    var angleRad = 0.0          // 0 = horizontal, +x along the length
    var thicknessPx = 0.0
    var lockPosition = false
    var lockAngle = false
    var initialized = false     // false until first placed in a viewport

    /** Unit vector along the length. */
    fun direction(): Pt = Pt(cos(angleRad), sin(angleRad))

    /** Unit vector across the thickness (the [direction] normal). */
    fun normal(): Pt = direction().perp()

    /** Signed perpendicular offset of [v] from the centre line (+ on the [normal] side). */
    fun signedAcross(v: Pt): Double = Geometry.dot(v - center, normal())

    /** Signed distance of [v] along the band from [center] (the graduation coordinate). */
    fun along(v: Pt): Double = Geometry.dot(v - center, direction())

    /** True if [v] lies on the band body. The band is infinitely long, so only the across matters. */
    fun bodyContains(v: Pt): Boolean = abs(signedAcross(v)) <= thicknessPx / 2.0

    /**
     * Clamp [v] onto one long edge ([topSide] = the +normal edge), keeping its position
     * along the band — i.e. project perpendicularly onto that edge's infinite line.
     */
    fun projectToEdge(v: Pt, topSide: Boolean): Pt {
        val target = if (topSide) thicknessPx / 2.0 else -thicknessPx / 2.0
        return v - normal() * (signedAcross(v) - target)
    }

    /** The body quad covering the along-range [sMin]..[sMax] (distance from [center] along [direction]). */
    fun bodyQuad(sMin: Double, sMax: Double): List<Pt> {
        val d = direction()
        val n = normal() * (thicknessPx / 2.0)
        val a = center + d * sMin
        val b = center + d * sMax
        return listOf(a + n, b + n, b - n, a - n)
    }

    /** Visual radius of a control button (sized to sit within the body thickness). */
    fun buttonRadiusPx(): Double = thicknessPx * 0.17

    /** Centres of the control buttons, in order, straddling [center] along the centre line. */
    fun buttonCenters(): List<Pair<RulerButton, Pt>> {
        val step = buttonRadiusPx() * 2.6
        val order = listOf(RulerButton.LOCK_POS, RulerButton.LOCK_ANGLE)
        val mid = (order.size - 1) / 2.0
        val d = direction()
        return order.mapIndexed { i, b -> b to (center + d * ((i - mid) * step)) }
    }

    /** The control button within [tolerancePx] of [v], or null. */
    fun hitButton(v: Pt, tolerancePx: Double): RulerButton? =
        buttonCenters().firstOrNull { it.second.distanceTo(v) <= tolerancePx }?.first

    /** Visual radius of a rotation handle. */
    fun handleRadiusPx(): Double = thicknessPx * 0.20

    /** The two rotation handles, [distPx] along the band each side of [center] (+direction first). */
    fun handleCenters(distPx: Double): List<Pt> {
        val d = direction() * distPx
        return listOf(center + d, center - d)
    }

    /** Index (0 = +direction, 1 = −direction) of the handle within [tolerancePx] of [v], or null. */
    fun hitHandle(v: Pt, distPx: Double, tolerancePx: Double): Int? {
        val hs = handleCenters(distPx)
        for (i in hs.indices) if (hs[i].distanceTo(v) <= tolerancePx) return i
        return null
    }

    /** Place a sensible default ruler the first time it is shown. */
    fun placeDefault(viewportW: Double, viewportH: Double, density: Double) {
        center = Pt(viewportW / 2.0, viewportH * 0.45)
        thicknessPx = 112.0 * density
        angleRad = 0.0
        initialized = true
    }

    companion object {
        /** Magnetic pull of the horizontal/vertical orientations, in degrees. */
        const val AXIS_SNAP_DEG = 4.0

        /**
         * Snap [raw] (radians, any winding) to the nearest horizontal/vertical orientation
         * (0/90/180/270) when within [AXIS_SNAP_DEG]; otherwise return it unchanged.
         */
        fun snapToAxes(raw: Double): Double {
            val quarter = Math.PI / 2.0
            val nearest = Math.round(raw / quarter) * quarter
            return if (abs(raw - nearest) <= Math.toRadians(AXIS_SNAP_DEG)) nearest else raw
        }
    }
}

/** Pure unit conversions for the ruler's graduations and length readout. */
object RulerMath {
    /** Content-space pixels per centimetre at [dpi]. */
    fun contentPxPerCm(dpi: Int): Double = PageSize.mmToPx(10.0, dpi)

    /** Content-space pixel length to centimetres at [dpi]. */
    fun contentPxToCm(px: Double, dpi: Int): Double = PageSize.pxToMm(px, dpi) / 10.0

    /**
     * Centimetres spanned by a screen-fixed [viewportPx] length at [zoom]: the ruler
     * stays a constant size on screen, so the same screen span covers fewer page
     * centimetres the further you zoom in.
     */
    fun viewportLenToCm(viewportPx: Double, zoom: Double, dpi: Int): Double =
        contentPxToCm(viewportPx / zoom, dpi)

    // --- B2: the readouts and labels (TO Frame 7) ---

    /** Clear of the locks: no tick label within this many dp of the centre (TO 850). */
    const val TICK_CLEAR_LOCKS_DP = 56.0

    /** No tick label within this many dp of a turn handle. */
    const val TICK_CLEAR_HANDLE_DP = 30.0

    /** A handle's angle readout sits this far past the handle along the band (TO 867). */
    const val READOUT_ALONG_DP = 50.0

    /** No tick label within this many dp of an angle readout. */
    const val TICK_CLEAR_READOUT_DP = 34.0

    /** No tick label's box within this many dp of a readout pill where [pillRect] draws it. */
    const val TICK_CLEAR_PILL_DP = 4.0

    /** The turn handles' opacity while the angle is locked (.to-rh.off, TO 101). */
    const val LOCKED_HANDLE_ALPHA = 0.4

    /** The readout pill (.to-ro, TO 102): 28 dp tall, 11 dp each side of its text. */
    const val PILL_HEIGHT_DP = 28.0
    const val PILL_PAD_DP = 11.0

    /** A readout stays this many px inside the viewport, as it did. */
    const val PILL_SCREEN_MARGIN = 2.0

    /** Points on each of a pill's round ends. */
    const val PILL_SEGMENTS = 10

    /** The +direction handle's angle (TO 865): counter-clockwise from +x, rounded and then wrapped, so never 360. */
    fun ccwDegrees(phiDeg: Double): Int = (Math.round(((-phiDeg) % 360.0 + 360.0) % 360.0) % 360).toInt()

    /** The −direction handle's angle: clockwise from −x, rounded and then wrapped. */
    fun cwDegrees(phiDeg: Double): Int = (Math.round((phiDeg % 360.0 + 360.0) % 360.0) % 360).toInt()

    /** "42°". */
    fun degreeLabel(deg: Int): String = "$deg°"

    /** The edge-length readout in the tenths of a centimetre it shows, so a frame reformats it only when it changes. */
    fun lengthTenths(cm: Double): Int = Math.round(cm * 10.0).toInt()

    /** "12.4 cm", with a point in every language, as the mockup shows it. */
    fun lengthLabel(tenths: Int): String = String.format(Locale.US, "%.1f cm", tenths / 10.0)

    /**
     * Whether the cm label at [alongPx] from the centre shows: not over the locks (|x| ≤ 56 dp), a handle (|x − dist| ≤
     * 30 dp) or its angle readout (|x − dist − 50| ≤ 34 dp), TO 850. [handleDistPx] is the handles' distance.
     */
    fun tickLabelShown(alongPx: Double, handleDistPx: Double, density: Double): Boolean {
        val x = abs(alongPx) / density
        val dist = handleDistPx / density
        return x > TICK_CLEAR_LOCKS_DP && abs(x - dist) > TICK_CLEAR_HANDLE_DP && abs(x - dist - READOUT_ALONG_DP) > TICK_CLEAR_READOUT_DP
    }

    /**
     * [tickLabelShown], and the label's box ([w]×[h] px centred on ([cx], [cy]), viewport px) also keeps
     * [TICK_CLEAR_PILL_DP] clear of both angle readouts as drawn, [pill0] and [pill1] from [pillRect]. A handle near the
     * screen edge has its pill pushed back on screen over the band, away from the spot TO 850 clears.
     */
    fun tickLabelShown(
        alongPx: Double,
        handleDistPx: Double,
        density: Double,
        cx: Double,
        cy: Double,
        w: Double,
        h: Double,
        pill0: Rect,
        pill1: Rect,
    ): Boolean = tickLabelShown(alongPx, handleDistPx, density) &&
        labelClearOf(pill0, cx, cy, w, h, density) && labelClearOf(pill1, cx, cy, w, h, density)

    /** Whether a [w]×[h] box centred on ([cx], [cy]) stays [TICK_CLEAR_PILL_DP] clear of [pill]. */
    fun labelClearOf(pill: Rect, cx: Double, cy: Double, w: Double, h: Double, density: Double): Boolean {
        val m = TICK_CLEAR_PILL_DP * density
        return cx + w / 2.0 < pill.left - m || cx - w / 2.0 > pill.right + m ||
            cy + h / 2.0 < pill.top - m || cy - h / 2.0 > pill.bottom + m
    }

    /** The turn handles' opacity: 40% while the angle is locked (they refuse to turn it). */
    fun handleAlpha(lockAngle: Boolean): Double = if (lockAngle) LOCKED_HANDLE_ALPHA else 1.0

    /** The pill round text [textW] px wide, centred on ([cx], [cy]) and kept on screen, in viewport px. */
    fun pillRect(cx: Double, cy: Double, textW: Double, density: Double, viewportW: Double, viewportH: Double): Rect {
        val w = textW + 2.0 * PILL_PAD_DP * density
        val h = PILL_HEIGHT_DP * density
        val m = PILL_SCREEN_MARGIN
        val left = (cx - w / 2.0).coerceIn(m, (viewportW - w - m).coerceAtLeast(m))
        val top = (cy - h / 2.0).coerceIn(m, (viewportH - h - m).coerceAtLeast(m))
        return Rect(left, top, w, h)
    }

    /**
     * A [w]×[h] pill's outline from its top-left corner, as one polygon (the renderer has no round rect): the right end
     * from top to bottom, then the left end from bottom to top. One polygon, so a translucent fill never doubles up.
     */
    fun pillPoints(w: Double, h: Double, segments: Int = PILL_SEGMENTS): List<Pt> {
        val r = h / 2.0
        val right = max(w - r, r)
        val pts = ArrayList<Pt>(2 * (segments + 1))
        for (i in 0..segments) {
            val a = Math.toRadians(-90.0 + 180.0 * i / segments)
            pts.add(Pt(right + r * cos(a), r + r * sin(a)))
        }
        for (i in 0..segments) {
            val a = Math.toRadians(90.0 + 180.0 * i / segments)
            pts.add(Pt(r + r * cos(a), r + r * sin(a)))
        }
        return pts
    }

    /** A FontSpec point size for [dp] on screen: points are 150 dpi page px (`POINTS_TO_PX` = 150/72), drawn 1:1 here. */
    fun pointsForDp(dp: Double, density: Double): Double = dp * density * 72.0 / 150.0
}
