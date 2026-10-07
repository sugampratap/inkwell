package com.xnotes.core.model

import com.xnotes.core.geometry.Affine
import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.pal.Renderer
import kotlin.math.cos
import kotlin.math.sin

/**
 * A pasted or inserted bitmap (spec 02 §5.2). Holds the encoded [image] source (decoded on demand
 * by the renderer) and a quarter-turn [orientation]; resize is aspect-locked.
 *
 * The image tools are non-destructive, like the turn: a [crop] of the source and a mirror
 * ([flipX], [flipY]) are recorded here and applied by every renderer in the order [ImageEdit]
 * documents, so "Reset" can always bring the original back and the stored file is never rewritten.
 *
 * A turn is stored, never baked: ink and shapes rotate by moving their own points, but pixels
 * cannot be moved without resampling them, so the rotate handle only advances [angle] and the
 * renderer places the bitmap turned. [rect] therefore stays the image's own upright box, and
 * [bounds] is the box that box sweeps out once turned.
 */
class ImageItem(
    var image: ImageData,
    var rect: Rect,
    var orientation: Int = 0,
    /** Free rotation about [rect]'s centre, radians clockwise. */
    var angle: Double = 0.0,
    /** The part of the source shown, normalised; null shows all of it. */
    var crop: ImageCrop? = null,
    /** Mirror of the (cropped) source left↔right, in its own frame, before the turn. */
    var flipX: Boolean = false,
    /** Mirror of the (cropped) source top↔bottom, in its own frame, before the turn. */
    var flipY: Boolean = false,
) : CanvasItem, Resizable {

    override val kind = KIND
    override val resizable = true
    override var locked = false

    /** The crop and mirror as one value, the form the renderers take them in. */
    val edit: ImageEdit get() = ImageEdit(crop, flipX, flipY)

    /** True when anything has been done to the image since it was placed, short of moving it. */
    val isEdited: Boolean get() = !edit.isIdentity || ImageGeometry.normOrientation(orientation) != 0 || angle != 0.0

    override fun paint(r: Renderer) {
        val e = edit
        if (e.isIdentity) r.drawImage(image, rect, orientation, angle) else r.drawImage(image, rect, orientation, angle, e)
    }

    /**
     * Turn the picture a quarter turn about its centre ([clockwise] or not), the way the image bar's
     * rotate buttons do. The box swaps its sides about the same centre, so the image keeps its size.
     */
    fun rotateQuarter(clockwise: Boolean) {
        orientation = ImageGeometry.normOrientation(orientation + if (clockwise) 90 else 270)
        val c = rect.center
        rect = Rect(c.x - rect.h / 2.0, c.y - rect.w / 2.0, rect.h, rect.w)
    }

    /** Mirror the picture as it is seen: left↔right when [horizontal], else top↔bottom. */
    fun flipShown(horizontal: Boolean) {
        val (fx, fy) = ImageGeometry.toggleDisplayFlip(orientation, flipX, flipY, horizontal)
        flipX = fx
        flipY = fy
    }

    /**
     * Swap the picture for [next] in the same frame: same box, same tilt, the new picture cropped
     * (centred) to fill it exactly, the way Samsung Notes' Replace keeps a layout intact. The old
     * crop, mirror and quarter turn belonged to the old picture and go with it.
     */
    fun replaceKeepingFrame(next: ImageData) {
        image = next
        orientation = 0
        flipX = false
        flipY = false
        crop = ImageGeometry.coverCrop(next.width, next.height, rect.w, rect.h)
    }

    /**
     * Undo every crop, mirror and turn: the whole source, upright, at the scale it is showing now and
     * about the same centre. A stretched image comes back to its own aspect.
     */
    fun resetEdits() {
        val (dw, dh) = ImageGeometry.displaySize(image.width, image.height, crop, orientation)
        val scale = if (dw > 0.0 && dh > 0.0) kotlin.math.sqrt((rect.w / dw) * (rect.h / dh)) else 1.0
        val w = image.width * scale
        val h = image.height * scale
        val c = rect.center
        rect = Rect(c.x - w / 2.0, c.y - h / 2.0, w, h)
        crop = null
        flipX = false
        flipY = false
        orientation = 0
        angle = 0.0
    }

    override fun bounds(): Rect = if (angle == 0.0) rect else Rect.bounding(corners())

    /** The turned rect's four corners, in the item's own space. */
    fun corners(): List<Pt> {
        val cs = cos(angle)
        val sn = sin(angle)
        val c = rect.center
        return listOf(
            Pt(rect.left, rect.top), Pt(rect.right, rect.top),
            Pt(rect.right, rect.bottom), Pt(rect.left, rect.bottom),
        ).map {
            val dx = it.x - c.x
            val dy = it.y - c.y
            Pt(c.x + dx * cs - dy * sn, c.y + dx * sn + dy * cs)
        }
    }

    /** [p] brought back into the upright rect's frame, so hit tests stay plain rectangle maths. */
    private fun unturn(p: Pt): Pt {
        if (angle == 0.0) return p
        val cs = cos(angle)
        val sn = sin(angle)
        val c = rect.center
        val dx = p.x - c.x
        val dy = p.y - c.y
        return Pt(c.x + dx * cs + dy * sn, c.y - dx * sn + dy * cs)
    }

    override fun translate(dx: Double, dy: Double) {
        rect = rect.translate(dx, dy)
    }

    override fun contains(p: Pt): Boolean = rect.contains(unturn(p))

    override fun centroid(): Pt = rect.center

    override fun intersectsCircle(cx: Double, cy: Double, radius: Double): Boolean =
        rect.distanceTo(unturn(Pt(cx, cy))) <= radius

    override fun geometry(): GeoHandle = RectHandle(rect)

    override fun setGeometry(handle: GeoHandle) {
        if (handle is RectHandle) rect = handle.rect
    }

    /**
     * The whole look of the image, not just where it sits: every image tool (crop, turn, mirror,
     * replace, reset) is recorded as a before/after pair of these, so each is one undo step through
     * the same [com.xnotes.core.history.TransformItems] a drag uses.
     */
    override fun snapshotGeometry(): GeometrySnapshot = ImageSnapshot(image, rect, angle, orientation, crop, flipX, flipY)

    override fun restoreGeometry(snap: GeometrySnapshot) {
        if (snap is ImageSnapshot) {
            image = snap.image
            rect = snap.rect
            angle = snap.angle
            orientation = snap.orientation
            crop = snap.crop
            flipX = snap.flipX
            flipY = snap.flipY
        }
    }

    /**
     * Scale the upright box about its mapped centre and add the transform's own turn to [angle].
     * The scale factors are the world axes' — an image already turned is stretched along its own
     * axes rather than sheared, since a bitmap has no shear to be drawn with.
     */
    override fun applyTransform(t: Affine) {
        val c = t.apply(rect.center)
        val w = rect.w * t.scaleX
        val h = rect.h * t.scaleY
        rect = Rect(c.x - w / 2.0, c.y - h / 2.0, w, h)
        if (t.determinant >= 0.0) {
            angle += t.rotationAngle
            return
        }
        // A mirror (a selection flipped as a whole). Write the map as M·X, X the left↔right mirror:
        // M is then a plain turn-and-scale, and X carried past the image's own turn reverses it. So
        // the angle is negated and turned by M, and the picture is mirrored left↔right as shown. A
        // turn of half a circle is the top↔bottom mirror in disguise, and is stored as that instead.
        val m = Affine(-t.a, -t.b, t.c, t.d, 0.0, 0.0)
        val turn = m.rotationAngle
        if (kotlin.math.abs(kotlin.math.abs(turn) - Math.PI) < 1e-9) {
            angle = -angle
            flipShown(horizontal = false)
        } else {
            angle = -angle + turn
            flipShown(horizontal = true)
        }
    }

    companion object {
        const val KIND = "image"
    }
}

/** Snapshot of an image's placement and every non-destructive edit on it. */
private data class ImageSnapshot(
    val image: ImageData,
    val rect: Rect,
    val angle: Double,
    val orientation: Int,
    val crop: ImageCrop?,
    val flipX: Boolean,
    val flipY: Boolean,
) : GeometrySnapshot
