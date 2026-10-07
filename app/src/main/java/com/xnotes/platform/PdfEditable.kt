package com.xnotes.platform

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentNameDictionary
import com.tom_roush.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification
import com.tom_roush.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.xnotes.core.model.CanvasItem
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.ShapeItem
import com.xnotes.core.model.Stroke
import com.xnotes.core.pdf.PageFrame
import com.xnotes.core.tools.Tool
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Calendar
import kotlin.math.hypot

/**
 * The two halves of an **editable PDF**: ink that any PDF app can still move, recolour or erase,
 * and the note itself riding along inside the file so Inkwell can reopen it whole.
 *
 * A flattened export burns every stroke into the page's content, which is what printing and plain
 * sending want and what no PDF app can take apart again. Here each stroke and shape becomes an Ink
 * annotation instead: the line it follows as `/InkList` (what other apps edit), and our own
 * rendering as its appearance, so it looks exactly as it does on the page until someone changes
 * it. Text and pictures stay in the page content, where they are already real text and images.
 *
 * Other apps cannot know a calligraphy nib or the brush's ends from an ink list, so the note file
 * is attached too ([attachNote]). Opening such a PDF in Inkwell ([embeddedNote]) restores the note
 * with every pen, page and layer as it was, rather than a PDF with marks on it.
 */
internal object PdfEditable {

    /** The attachment's file name, and the marker in the document info that says it is ours. */
    const val NOTE_ATTACHMENT = "Inkwell note.xnote"
    private const val INFO_KEY = "InkwellNote"

    /** Whether [item] goes into an editable PDF as an annotation rather than as page content. */
    fun takes(item: CanvasItem): Boolean = when (item) {
        is Stroke -> !item.config.neon && item.tool != Tool.LASER
        is ShapeItem -> !item.neon
        else -> false
    }

    /**
     * Add [item] to [pdfPage] as an Ink annotation drawn by [ctx]'s renderer through [frame]. [ctx]
     * must not be tagged: an appearance stream is not page content and has no place in its structure.
     */
    fun addInk(ctx: PdfExportContext, pdfPage: PDPage, item: CanvasItem, frame: PageFrame) {
        val box = frame.toUser(item.paintBounds())
        if (!(box.w > 0.0) || !(box.h > 0.0)) return
        val rect = PDRectangle(box.left.toFloat() - 1f, box.top.toFloat() - 1f, box.w.toFloat() + 2f, box.h.toFloat() + 2f)
        val ap = PDAppearanceStream(ctx.doc)
        ap.cosObject.setItem(COSName.BBOX, tenthsArray(rect))
        ap.resources = PDResources()
        PDPageContentStream(ctx.doc, ap, ap.cosObject.createOutputStream(COSName.FLATE_DECODE)).use { cs ->
            val r = PdfBoxRenderer(cs, ctx, pdfPage, frame.ox, frame.oy, frame.s, frame.turn)
            item.paint(r)
            r.endPage()
        }
        // A page of handwriting is a thousand of these, and annotation dictionaries are written as
        // plain text, so each keeps only what an editor needs: no per-stroke id or timestamp, its
        // small dictionaries inline rather than objects of their own, numbers to a tenth.
        val ann = PDAnnotationMarkup()
        ann.cosObject.setName(COSName.SUBTYPE, "Ink")
        ann.cosObject.setItem(COSName.RECT, tenthsArray(rect))
        ann.color = rgb(colorOf(item))
        ann.titlePopup = "Inkwell"
        ann.isPrinted = true
        ann.cosObject.setItem(COSName.getPDFName("InkList"), inkList(item, frame))
        val bs = COSDictionary()
        bs.setItem(COSName.W, COSFloat(tenths((widthOf(item) * frame.s).coerceAtLeast(0.1))))
        bs.isDirect = true
        ann.cosObject.setItem(COSName.BS, bs)
        // A highlighter's translucency and blend live in the appearance, as the page draws them; the
        // annotation's own opacity would apply on top of that and fade it twice.
        val apDict = PDAppearanceDictionary().apply { setNormalAppearance(ap) }
        apDict.cosObject.isDirect = true
        ann.appearance = apDict
        pdfPage.annotations.add(ann)
    }

    /**
     * The line each stroke follows, in user space: only the vertices it needs to stay within
     * [INK_LIST_TOLERANCE_PT] of the ink, each to a tenth of a point. Annotation dictionaries are
     * not compressed, so this list is written as plain text; the appearance keeps the detail.
     */
    private fun inkList(item: CanvasItem, frame: PageFrame): COSArray {
        val path = COSArray()
        fun add(xs: List<Double>) {
            val a = COSArray()
            for (v in xs) a.add(COSFloat(tenths(v)))
            path.add(a)
        }
        when (item) {
            is Stroke -> {
                val g = item.geometry()
                val n = g.pointCount
                val xs = DoubleArray(n)
                val ys = DoubleArray(n)
                for (i in 0 until n) {
                    val p = frame.toUser(g.cx(i), g.cy(i))
                    xs[i] = p.x
                    ys[i] = p.y
                }
                val keep = keepVertices(xs, ys, INK_LIST_TOLERANCE_PT)
                val out = ArrayList<Double>()
                for (i in 0 until n) if (keep[i]) { out += xs[i]; out += ys[i] }
                add(out)
            }
            is ShapeItem -> {
                val pts = item.points ?: listOf(item.start, item.end)
                add(pts.flatMap { val p = frame.toUser(it.x, it.y); listOf(p.x, p.y) })
            }
            else -> Unit
        }
        return path
    }

    /** [r] as a PDF rectangle array, to a tenth of a point, grown outward so rounding never clips. */
    private fun tenthsArray(r: PDRectangle): COSArray = COSArray().apply {
        add(COSFloat(tenths(kotlin.math.floor(r.lowerLeftX * 10.0) / 10.0)))
        add(COSFloat(tenths(kotlin.math.floor(r.lowerLeftY * 10.0) / 10.0)))
        add(COSFloat(tenths(kotlin.math.ceil(r.upperRightX * 10.0) / 10.0)))
        add(COSFloat(tenths(kotlin.math.ceil(r.upperRightY * 10.0) / 10.0)))
    }

    /** [v] to a tenth, trailing ".0" dropped, as PDF number text. */
    private fun tenths(v: Double): String {
        val t = Math.round(v * 10.0)
        val sign = if (t < 0) "-" else ""
        val a = kotlin.math.abs(t)
        return if (a % 10 == 0L) "$sign${a / 10}" else "$sign${a / 10}.${a % 10}"
    }

    /** Ramer-Douglas-Peucker: the vertices a polyline needs to stay within [tol] of itself. */
    internal fun keepVertices(xs: DoubleArray, ys: DoubleArray, tol: Double): BooleanArray {
        val n = xs.size
        val keep = BooleanArray(n)
        if (n == 0) return keep
        keep[0] = true
        keep[n - 1] = true
        val stack = ArrayDeque<Int>()
        stack.addLast(0)
        stack.addLast(n - 1)
        while (stack.isNotEmpty()) {
            val b = stack.removeLast()
            val a = stack.removeLast()
            if (b - a < 2) continue
            val dx = xs[b] - xs[a]
            val dy = ys[b] - ys[a]
            val len = hypot(dx, dy)
            var worst = -1
            var worstD = tol
            for (i in a + 1 until b) {
                val d = if (len < 1e-9) hypot(xs[i] - xs[a], ys[i] - ys[a]) else kotlin.math.abs((xs[i] - xs[a]) * dy - (ys[i] - ys[a]) * dx) / len
                if (d > worstD) { worstD = d; worst = i }
            }
            if (worst >= 0) {
                keep[worst] = true
                stack.addLast(a); stack.addLast(worst)
                stack.addLast(worst); stack.addLast(b)
            }
        }
        return keep
    }

    private fun colorOf(item: CanvasItem): Rgba = when (item) {
        is Stroke -> item.config.rgba
        is ShapeItem -> item.strokeRgba
        else -> Rgba(0, 0, 0, 255)
    }

    private fun widthOf(item: CanvasItem): Double = when (item) {
        is Stroke -> item.config.baseWidth
        is ShapeItem -> item.strokeWidth
        else -> 1.0
    }

    private fun rgb(c: Rgba) = PDColor(floatArrayOf(c.r / 255f, c.g / 255f, c.b / 255f), PDDeviceRGB.INSTANCE)

    /** Attach the note [write] produces to [doc] as [NOTE_ATTACHMENT], and mark the file as ours. */
    fun attachNote(doc: PDDocument, write: (OutputStream) -> Unit, scratch: File) {
        scratch.outputStream().use(write)
        val embedded = scratch.inputStream().use { PDEmbeddedFile(doc, it) }
        embedded.subtype = "application/octet-stream"
        embedded.size = scratch.length().toInt()
        embedded.modDate = Calendar.getInstance()
        val spec = PDComplexFileSpecification()
        spec.file = NOTE_ATTACHMENT
        spec.fileUnicode = NOTE_ATTACHMENT
        spec.embeddedFile = embedded
        spec.embeddedFileUnicode = embedded
        spec.fileDescription = "The editable Inkwell note this PDF was made from"
        val tree = PDEmbeddedFilesNameTreeNode()
        tree.names = mapOf(NOTE_ATTACHMENT to spec)
        val catalog = doc.documentCatalog
        val names = catalog.names ?: PDDocumentNameDictionary(catalog).also { catalog.names = it }
        names.embeddedFiles = tree
        doc.documentInformation.setCustomMetadataValue(INFO_KEY, "1")
        doc.documentInformation.producer = "Inkwell"
    }

    /**
     * The note an Inkwell editable PDF at [pdf] carries, copied to [into], or false when it is any
     * other PDF. Cheap on the miss: only the trailer, the info and the name tree are read.
     */
    fun embeddedNote(pdf: File, into: File): Boolean = try {
        PDDocument.load(pdf, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            if (doc.documentInformation.getCustomMetadataValue(INFO_KEY) != "1") return false
            val spec = doc.documentCatalog.names?.embeddedFiles?.names?.get(NOTE_ATTACHMENT) ?: return false
            val file = spec.embeddedFileUnicode ?: spec.embeddedFile ?: return false
            file.createInputStream().use { input: InputStream -> into.outputStream().use { input.copyTo(it) } }
            into.length() > 0
        }
    } catch (_: Throwable) {
        false
    }

    /** How far (points) the ink list may cut across the line it follows: 0.07 mm. */
    private const val INK_LIST_TOLERANCE_PT = 0.2
}
