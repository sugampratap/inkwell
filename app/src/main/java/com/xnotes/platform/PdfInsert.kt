package com.xnotes.platform

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * The file work behind Insert > PDF, kept off the main thread by its callers.
 *
 * A note holds one source PDF and its pages point into it by index, so inserting a second PDF into
 * a note that already has one means building a combined file: the note's PDF first, page for page,
 * then the inserted one's pages. The note's own pages keep their indices; only the new ones point
 * past the old page count. PdfBox does the joining (page dictionaries, resources, annotations and
 * the outline are carried across as they are, so the pages stay real vector PDF the markup tool and
 * the export see exactly as they would see an imported PDF).
 */
object PdfInsert {

    /** PdfBox's in-RAM scratch cap; the rest spills to temp files, as the exporter does. */
    private const val SCRATCH_MAIN_MEM_BYTES = 32L * 1024 * 1024

    /** Why an insert could not go ahead, for the message the user sees. */
    enum class Failure { PASSWORD, UNREADABLE, MERGE }

    class Probe(val pageCount: Int, val sizes: FloatArray)

    /**
     * Open [file] with PDFium (the renderer the note will use) and read its page sizes, or say why
     * it will not do. A file with a user password cannot be shown, so it is refused here rather than
     * inserted as blank pages.
     */
    fun probe(file: File): Pair<Probe?, Failure?> {
        val source = PdfSource.open(file)
        try {
            if (source.pageCount <= 0) {
                val failure = when (source.openError) {
                    PdfOpenError.PASSWORD, PdfOpenError.SECURITY -> Failure.PASSWORD
                    else -> Failure.UNREADABLE
                }
                return null to failure
            }
            val sizes = source.allPageSizePoints() ?: FloatArray(0)
            return Probe(source.pageCount, sizes) to null
        } finally {
            source.close()
        }
    }

    /**
     * Write [first] followed by every page of [second] into [out]. Security is dropped on both
     * (an owner password only restricts editing, and the note already annotates the first file),
     * because PdfBox will not write an encrypted document it did not encrypt. Throws on failure;
     * [out] is then left for the caller to delete.
     */
    fun merge(context: Context, first: File, second: File, out: File) {
        PDFBoxResourceLoader.init(context.applicationContext)
        val mem = MemoryUsageSetting.setupMixed(SCRATCH_MAIN_MEM_BYTES).setTempDir(context.cacheDir)
        PDDocument.load(first, mem).use { dest ->
            PDDocument.load(second, mem).use { src ->
                dest.isAllSecurityToBeRemoved = true
                src.isAllSecurityToBeRemoved = true
                PDFMergerUtility().appendDocument(dest, src)
                BufferedOutputStream(FileOutputStream(out), 256 * 1024).use { dest.save(it) }
            }
        }
    }
}
