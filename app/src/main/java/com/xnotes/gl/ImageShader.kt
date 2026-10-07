package com.xnotes.gl

import android.opengl.GLES30
import com.xnotes.core.model.ImageCrop
import com.xnotes.core.model.ImageEdit

/**
 * A placed image: one textured quad, its corners computed on the CPU in doubles and handed over in
 * clip space.
 *
 * Images take this path rather than the geometry store's because they carry a texture rather than a
 * colour, and because an image is always four corners of an axis-aligned rectangle. Working the
 * corners out in double precision and passing clip-space coordinates sidesteps the chunk-and-offset
 * scheme entirely, and is exact however far from the origin the image sits.
 */
class ImageShader(contextGen: Int) {

    private val program = GlProgram.build(VERTEX_SRC, FRAGMENT_SRC, contextGen)

    val contextGen: Int get() = program.contextGen

    fun release() = program.release()

    /**
     * Draw [texture] over the clip-space quad given by its four corners, in the order top-left,
     * top-right, bottom-left, bottom-right. [rotationSteps] turns the picture clockwise a quarter
     * turn at a time, [edit] crops and mirrors it first (the order [ImageEdit] documents), so the
     * image tools cost nothing but a different set of texture coordinates.
     */
    fun draw(
        corners: FloatArray,
        texture: Int,
        rotationSteps: Int,
        alpha: Double = 1.0,
        edit: ImageEdit = ImageEdit.NONE,
    ) {
        if (texture == 0) return
        program.use()
        program.set("uP0", corners[0], corners[1])
        program.set("uP1", corners[2], corners[3])
        program.set("uP2", corners[4], corners[5])
        program.set("uP3", corners[6], corners[7])
        program.set("uRotation", ((rotationSteps % 4) + 4) % 4)
        val c = edit.effectiveCrop ?: ImageCrop.FULL
        program.set("uCrop", c.l.toFloat(), c.t.toFloat(), c.r.toFloat(), c.b.toFloat())
        program.set("uFlip", if (edit.flipX) 1f else 0f, if (edit.flipY) 1f else 0f)
        program.set("uAlpha", alpha.coerceIn(0.0, 1.0).toFloat())
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        program.set("uTexture", 0)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    companion object {
        private val VERTEX_SRC = """#version 300 es
            uniform vec2 uP0;
            uniform vec2 uP1;
            uniform vec2 uP2;
            uniform vec2 uP3;
            uniform int uRotation;
            uniform vec4 uCrop;
            uniform vec2 uFlip;
            out vec2 vUv;
            void main() {
                vec2 p = uP0;
                vec2 d = vec2(0.0, 0.0);
                if (gl_VertexID == 1) { p = uP1; d = vec2(1.0, 0.0); }
                else if (gl_VertexID == 2) { p = uP2; d = vec2(0.0, 1.0); }
                else if (gl_VertexID == 3) { p = uP3; d = vec2(1.0, 1.0); }
                // The quad shows the picture turned clockwise; walk each corner back through the
                // turn, then the mirror, then into the crop, to find the source pixel it shows.
                // Same mapping as ImageGeometry.displayToSource, and the paged renderer's.
                vec2 s = d;
                if (uRotation == 1) s = vec2(d.y, 1.0 - d.x);
                else if (uRotation == 2) s = vec2(1.0 - d.x, 1.0 - d.y);
                else if (uRotation == 3) s = vec2(1.0 - d.y, d.x);
                if (uFlip.x > 0.5) s.x = 1.0 - s.x;
                if (uFlip.y > 0.5) s.y = 1.0 - s.y;
                vUv = vec2(mix(uCrop.x, uCrop.z, s.x), mix(uCrop.y, uCrop.w, s.y));
                gl_Position = vec4(p, 0.0, 1.0);
            }
        """.trimIndent()

        private val FRAGMENT_SRC = """#version 300 es
            precision mediump float;
            uniform sampler2D uTexture;
            uniform float uAlpha;
            in vec2 vUv;
            out vec4 fragColor;
            void main() {
                vec4 c = texture(uTexture, vUv);
                fragColor = vec4(c.rgb, c.a * uAlpha);
            }
        """.trimIndent()
    }
}
