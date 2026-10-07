package com.xnotes.gl

import android.opengl.GLES30
import com.xnotes.core.geometry.Rect
import com.xnotes.core.model.Rgba

/**
 * Solid and outlined rectangles in device space, for the minimap panel and the markers on it.
 *
 * The minimap is drawn from item bounds rather than from the scene's own geometry. Re-running the
 * whole scene through a second transform would cost a second full pass of everything visible on a
 * canvas that may hold a great deal; a dot per item conveys where the work is at a hundredth of
 * the cost, and at minimap scale a stroke is a dot anyway.
 */
class MinimapShader(contextGen: Int) {

    private val program = GlProgram.build(VERTEX_SRC, FRAGMENT_SRC, contextGen)

    /** The markers' program: the very same fill, its corners read from a buffer of the lot. */
    private val dotProgram = GlProgram.build(DOT_VERTEX_SRC, FRAGMENT_SRC, contextGen)

    private val rectLoc = program.uniform("uRect")
    private val colorLoc = program.uniform("uColor")
    private val dotColorLoc = dotProgram.uniform("uColor")

    /** The markers' vertex buffer, its size in bytes, and the [Minimap.Dots.version] it holds. */
    private var dotBuffer = 0
    private var dotBufferBytes = 0
    private var uploadedVersion = -1
    private var staging: java.nio.FloatBuffer? = null

    val contextGen: Int get() = program.contextGen

    fun release() {
        program.release()
        dotProgram.release()
        if (dotBuffer != 0) GLES30.glDeleteBuffers(1, intArrayOf(dotBuffer), 0)
        dotBuffer = 0
    }

    /**
     * Ready the rect program for [fill] calls in a row: the program and the blend are set once for
     * all of them rather than once per rect.
     */
    fun beginRects() {
        program.use()
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
    }

    /**
     * Fill the rect at ([x], [y]) sized [w] by [h] (device pixels, y down) in the colour given as
     * 0-255 channels, after [beginRects]. The same clip corners and colour the [Rect] form uploads,
     * with nothing allocated.
     */
    fun fill(x: Double, y: Double, w: Double, h: Double, r: Int, g: Int, b: Int, a: Int, viewportW: Int, viewportH: Int) {
        if (w <= 0.0 || h <= 0.0) return
        GLES30.glUniform4f(
            rectLoc,
            (x / viewportW * 2.0 - 1.0).toFloat(),
            (1.0 - y / viewportH * 2.0).toFloat(),
            ((x + w) / viewportW * 2.0 - 1.0).toFloat(),
            (1.0 - (y + h) / viewportH * 2.0).toFloat(),
        )
        GLES30.glUniform4f(colorLoc, r / 255f, g / 255f, b / 255f, a / 255f)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    /** [outline] with nothing allocated: the same four fills, after [beginRects]. */
    fun outline(
        x: Double, y: Double, w: Double, h: Double, width: Double,
        r: Int, g: Int, b: Int, a: Int, viewportW: Int, viewportH: Int,
    ) {
        val right = x + w
        val bottom = y + h
        fill(x, y, w, width, r, g, b, a, viewportW, viewportH)
        fill(x, bottom - width, w, width, r, g, b, a, viewportW, viewportH)
        fill(x, y, width, h, r, g, b, a, viewportW, viewportH)
        fill(right - width, y, width, h, r, g, b, a, viewportW, viewportH)
    }

    /**
     * Every marker in [dots] in one draw, in the colour given as 0-255 channels. The batch is only
     * uploaded when it has changed since the last draw, so a frame that changed nothing on the map
     * costs one call. Leaves the rect program unbound: call [beginRects] before filling again.
     */
    fun drawDots(dots: com.xnotes.core.infinite.Minimap.Dots, r: Int, g: Int, b: Int, a: Int) {
        val count = dots.vertexCount
        if (count <= 0) return
        dotProgram.use()
        if (dotBuffer == 0) {
            val names = IntArray(1)
            GLES30.glGenBuffers(1, names, 0)
            dotBuffer = names[0]
            dotBufferBytes = 0
            uploadedVersion = -1
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, dotBuffer)
        if (uploadedVersion != dots.version) {
            val floats = 2 * count
            var buf = staging
            if (buf == null || buf.capacity() < floats) {
                buf = java.nio.ByteBuffer.allocateDirect(maxOf(floats, (buf?.capacity() ?: 0) * 2) * 4)
                    .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
                staging = buf
            }
            buf!!.clear()
            buf.put(dots.vertices, 0, floats)
            buf.flip()
            val bytes = floats * 4
            if (bytes > dotBufferBytes) {
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, buf.capacity() * 4, null, GLES30.GL_DYNAMIC_DRAW)
                dotBufferBytes = buf.capacity() * 4
            }
            GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, bytes, buf)
            uploadedVersion = dots.version
        }
        GLES30.glUniform4f(dotColorLoc, r / 255f, g / 255f, b / 255f, a / 255f)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glEnableVertexAttribArray(DOT_ATTRIB)
        GLES30.glVertexAttribPointer(DOT_ATTRIB, 2, GLES30.GL_FLOAT, false, 8, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, count)
        GLES30.glDisableVertexAttribArray(DOT_ATTRIB)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    /** Fill [rect] (device pixels, y down) with [color]. */
    fun fill(rect: Rect, color: Rgba, viewportW: Int, viewportH: Int) {
        if (rect.w <= 0.0 || rect.h <= 0.0) return
        program.use()
        program.set(
            "uRect",
            (rect.left / viewportW * 2.0 - 1.0).toFloat(),
            (1.0 - rect.top / viewportH * 2.0).toFloat(),
            (rect.right / viewportW * 2.0 - 1.0).toFloat(),
            (1.0 - rect.bottom / viewportH * 2.0).toFloat(),
        )
        program.set("uColor", color.r / 255f, color.g / 255f, color.b / 255f, color.a / 255f)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    /** Outline [rect] with a [width]-pixel border, as four fills. */
    fun outline(rect: Rect, width: Double, color: Rgba, viewportW: Int, viewportH: Int) {
        fill(Rect(rect.left, rect.top, rect.w, width), color, viewportW, viewportH)
        fill(Rect(rect.left, rect.bottom - width, rect.w, width), color, viewportW, viewportH)
        fill(Rect(rect.left, rect.top, width, rect.h), color, viewportW, viewportH)
        fill(Rect(rect.right - width, rect.top, width, rect.h), color, viewportW, viewportH)
    }

    companion object {
        /** Where the markers' corners come in; bound explicitly, so no lookup is needed. */
        private const val DOT_ATTRIB = 0

        private val DOT_VERTEX_SRC = """#version 300 es
            layout(location = 0) in vec2 aCorner;
            void main() {
                gl_Position = vec4(aCorner, 0.0, 1.0);
            }
        """.trimIndent()

        private val VERTEX_SRC = """#version 300 es
            uniform vec4 uRect;
            void main() {
                float x = (gl_VertexID == 0 || gl_VertexID == 2) ? uRect.x : uRect.z;
                float y = (gl_VertexID == 0 || gl_VertexID == 1) ? uRect.y : uRect.w;
                gl_Position = vec4(x, y, 0.0, 1.0);
            }
        """.trimIndent()

        private val FRAGMENT_SRC = """#version 300 es
            precision mediump float;
            uniform vec4 uColor;
            out vec4 fragColor;
            void main() { fragColor = uColor; }
        """.trimIndent()
    }
}
