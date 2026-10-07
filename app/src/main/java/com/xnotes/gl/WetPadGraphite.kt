package com.xnotes.gl

import com.xnotes.core.infinite.InkPass

/**
 * How the front buffer lays a live pencil ([com.xnotes.core.stroke.Graphite]) so it reads exactly
 * as the committed stroke does, drawn incrementally and taken back at will.
 *
 * ### What has to come out
 *
 * The committed pencil is two passes through the paper's grain `g`, each filled once: an outer at
 * alpha `o·g` over the stroke's full width, then a pressed core at `p·g` composited *over* it. So a
 * pixel the core reaches holds `1 − (1 − o·g)(1 − p·g)`, a pixel only the outer reaches holds
 * `o·g`, and a stroke never darkens where it overlaps itself. Two strokes, on the other hand, do
 * build up where they cross: the second is composited over the first.
 *
 * ### How the pad gets there
 *
 * Every present of the pad rebuilds its damage from geometry into a scratch cleared to transparent,
 * so the pad never blends onto what it showed before: there is no accumulated state that a guess
 * drawn ahead of the nib, or a tail that retracts, could leave behind. What the scratch needs is to
 * draw each stroke's runs, which overlap each other and themselves, as if each pass were one fill.
 *
 * That is the stencil's job, per sample. Every pencil stroke in a present is given an id `k`, and
 * the stencil holds `2k` where that stroke's outer has been laid and `2k + 1` where its core has.
 * Anything else, `0` or another stroke's id, is a sample this stroke has not touched yet:
 *  - an **outer** fragment draws `o·g` only on a sample whose id is not this stroke's (either
 *    value), and marks it `2k`;
 *  - a **core** fragment on a sample marked `2k` (this stroke's outer is under it) draws `p·g`,
 *    which over `o·g` is the committed core-over-outer, and steps it to `2k + 1`;
 *  - a **core** fragment on a sample this stroke has not touched draws the whole combined value
 *    at once, and marks it `2k + 1`, so an outer arriving later finds it taken.
 *
 * Each sample therefore receives exactly one of: `o·g`, `o·g` then `p·g`, or the combined value,
 * whatever order and however many times the runs reach it, and each is composited source-over what
 * earlier strokes left there. That is the committed render, stroke by stroke, including pencil over
 * pencil building up as it does on the page, over pens and under them, in any colours. Nothing has
 * to be refused or ended for mixing inks on one pad run, and no "max" look is introduced anywhere.
 *
 * A stroke's pieces are always drawn back to back (its settled runs in order, then its tail), so
 * only "this stroke" against "anything else" ever has to be told apart. Ids therefore only need to
 * be unique within a present, and when they run out the stencil is simply cleared and they start
 * again: nothing already drawn can be confused for the stroke being drawn.
 *
 * Pure, so the rule is checked without a GL context; [GlWetPadInk] feeds these values straight to
 * `glStencilFunc` and `glStencilOp`.
 */
internal object WetPadGraphite {

    // The GL enums, by value, so the rule is testable off the device.
    const val GL_EQUAL = 0x0202
    const val GL_NOTEQUAL = 0x0205
    const val GL_KEEP = 0x1E00
    const val GL_REPLACE = 0x1E01
    const val GL_INCR = 0x1E02

    /** Ids a present can hand out before the stencil is cleared: 2k + 1 must fit in a byte. */
    const val MAX_ID = 127

    /** Which half of a pencil a pad piece is: not one at all, its outer, or its pressed core. */
    const val NONE = 0
    const val OUTER = 1
    const val CORE = 2

    /** The role a mesh part plays on the pad, from what the mesher made of it. */
    fun roleOf(pass: InkPass, under: Double): Int = when {
        pass != InkPass.GRAPHITE -> NONE
        under >= 0.0 -> CORE
        else -> OUTER
    }

    // --- the three draws: glStencilFunc(func, ref, mask), then glStencilOp(KEEP, KEEP, op) ---

    /** An outer fragment: anywhere this stroke has not been, which it then marks as its outer. */
    const val OUTER_FUNC = GL_NOTEQUAL
    const val OUTER_MASK = 0xFE
    const val OUTER_OP = GL_REPLACE
    fun outerRef(k: Int): Int = 2 * k

    /** A core fragment over this stroke's own outer: the core composited over it, as committed. */
    const val CORE_OVER_FUNC = GL_EQUAL
    const val CORE_OVER_MASK = 0xFF
    const val CORE_OVER_OP = GL_INCR
    fun coreOverRef(k: Int): Int = 2 * k

    /** A core fragment where this stroke has not been at all: outer and core in one blend. */
    const val CORE_FRESH_FUNC = GL_NOTEQUAL
    const val CORE_FRESH_MASK = 0xFE
    const val CORE_FRESH_OP = GL_REPLACE
    fun coreFreshRef(k: Int): Int = 2 * k + 1

    /** `glStencilFunc`'s test, for a sample holding [stencil]. */
    fun passes(func: Int, ref: Int, mask: Int, stencil: Int): Boolean {
        val r = ref and mask
        val s = stencil and mask
        return when (func) {
            GL_EQUAL -> r == s
            GL_NOTEQUAL -> r != s
            else -> error("unused stencil func $func")
        }
    }

    /** What a passing fragment leaves in the stencil, for `glStencilOp(KEEP, KEEP, op)`. */
    fun written(op: Int, ref: Int, stencil: Int): Int = when (op) {
        GL_KEEP -> stencil
        GL_REPLACE -> ref and 0xFF
        GL_INCR -> minOf(stencil + 1, 0xFF)
        else -> error("unused stencil op $op")
    }

    /**
     * The alpha a pad pencil draw lays at grain [g], for the pair the shader is given: `(o, 0)` for
     * an outer, `(p, 0)` for a core over its outer, `(o, p)` for a core on a fresh sample.
     */
    fun alpha(a: Double, b: Double, g: Double): Double = 1.0 - (1.0 - a * g) * (1.0 - b * g)

    /** What the committed stroke holds at a pixel: the outer, and the core over it where it reaches. */
    fun committed(o: Double, p: Double, g: Double, outer: Boolean, core: Boolean): Double {
        var a = 0.0
        if (outer) a = o * g
        if (core) a = p * g + a * (1.0 - p * g)
        return a
    }

    /**
     * The id the next pencil stroke in a present gets after [k], and whether the stencil has to be
     * cleared before it is used.
     */
    fun nextId(k: Int): Int = if (k >= MAX_ID) 1 else k + 1

    fun clearsBefore(k: Int): Boolean = k >= MAX_ID
}
