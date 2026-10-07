package com.xnotes.gl

import android.opengl.GLES30
import com.xnotes.core.model.Rgba
import com.xnotes.core.stroke.Graphite
import java.nio.ByteBuffer

/**
 * [CoverShader] for graphite: the same clip-space quad painted through the stencil once, but each
 * fragment's alpha is the ink's times the paper's grain ([Graphite.tile]) at the world point under
 * it. The world point is recovered from the fragment's own device position and the view, so the
 * grain is anchored to the canvas's world: it holds still under a pan and scales with a zoom, the
 * same paper the Android canvas draws pencil on, texel for texel.
 *
 * Precision: the scroll is reduced modulo the tile on the CPU in double, so the shader only ever
 * adds a screen's worth of world to a number under one tile, in highp.
 *
 * The grain texture is made once per context, single-channel and mipmapped, so a zoomed-out canvas
 * samples a pre-averaged level and the grain settles into a tone instead of shimmering.
 */
class GrainCoverShader(contextGen: Int) {

    private val program = GlProgram.build(VERTEX_SRC, FRAGMENT_SRC, contextGen)
    private val texture: Int = uploadGrain()

    val contextGen: Int get() = program.contextGen

    fun release() {
        program.release()
        if (texture != 0) GLES30.glDeleteTextures(1, intArrayOf(texture), 0)
    }

    /**
     * Draw [color] at [alpha] times the grain over the clip-space rectangle, for a view scrolled to
     * [scrollX]/[scrollY] (world units at the top-left device pixel) at [zoom], on a target
     * [viewportH] device pixels tall.
     */
    fun draw(
        x0: Float, y0: Float, x1: Float, y1: Float,
        color: Rgba, alpha: Double,
        scrollX: Double, scrollY: Double, zoom: Double, viewportH: Int,
    ) {
        val tile = Graphite.TILE.toDouble()
        program.use()
        program.set("uRect", x0, y0, x1, y1)
        program.set("uColor", color.r / 255f, color.g / 255f, color.b / 255f, alpha.coerceIn(0.0, 1.0).toFloat())
        program.set("uGrainOrigin", wrap(scrollX, tile).toFloat(), wrap(scrollY, tile).toFloat())
        program.set("uInvZoom", (1.0 / zoom.coerceAtLeast(1e-6)).toFloat())
        program.set("uViewportH", viewportH.toFloat())
        program.set("uInvTile", (1.0 / tile).toFloat())
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        program.set("uGrain", 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    internal companion object {
        /** [v] modulo [m], in [0, m): the scroll's place within the tile, taken while still in double. */
        fun wrap(v: Double, m: Double): Double {
            val r = v % m
            return if (r < 0.0) r + m else r
        }

        /** The paper's grain as a mipmapped single-channel texture in the current context. */
        fun uploadGrain(): Int {
            val n = Graphite.TILE
            val bytes = Graphite.tile
            val buf = ByteBuffer.allocateDirect(bytes.size).put(bytes)
            buf.position(0)
            val name = IntArray(1)
            GLES30.glGenTextures(1, name, 0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, name[0])
            GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R8, n, n, 0, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, buf)
            GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
            return name[0]
        }

        private val VERTEX_SRC = """#version 300 es
            uniform vec4 uRect;
            void main() {
                float x = (gl_VertexID == 0 || gl_VertexID == 2) ? uRect.x : uRect.z;
                float y = (gl_VertexID == 0 || gl_VertexID == 1) ? uRect.y : uRect.w;
                gl_Position = vec4(x, y, 0.0, 1.0);
            }
        """.trimIndent()

        private val FRAGMENT_SRC = """#version 300 es
            precision highp float;
            uniform vec4 uColor;
            uniform vec2 uGrainOrigin;
            uniform float uInvZoom;
            uniform float uViewportH;
            uniform float uInvTile;
            uniform sampler2D uGrain;
            out vec4 fragColor;
            void main() {
                // Device pixels run down from the top; the window's fragment rows run up.
                vec2 device = vec2(gl_FragCoord.x, uViewportH - gl_FragCoord.y);
                vec2 world = uGrainOrigin + device * uInvZoom;
                float grain = texture(uGrain, world * uInvTile).r;
                fragColor = vec4(uColor.rgb, uColor.a * grain);
            }
        """.trimIndent()
    }
}
