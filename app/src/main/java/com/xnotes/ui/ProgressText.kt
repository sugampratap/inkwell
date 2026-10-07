package com.xnotes.ui

/** Where an export has got to, for its progress line. */
internal sealed interface ExportStage {
    data object Preparing : ExportStage
    data class Step(val done: Int, val total: Int, val items: Boolean) : ExportStage
    data class Finishing(val total: Int, val items: Boolean) : ExportStage
    data class Writing(val percent: Int) : ExportStage
}

/** The progress sheets' words and bar, from what MainActivity reports. */
internal object ProgressText {

    /** A negative [total] is the PDF write, with [done] in permille; [counting] is "page" or "item" (a canvas). */
    fun exportStage(done: Int, total: Int, counting: String): ExportStage = when {
        total < 0 -> ExportStage.Writing(Math.round(done / 10f).coerceIn(0, 100))
        total == 0 -> ExportStage.Preparing
        done < total -> ExportStage.Step(done, total, counting == "item")
        else -> ExportStage.Finishing(total, counting == "item")
    }

    /** How full the bar is: pages done of all the pages, then the write's own permille. Preparing has nothing to count. */
    fun exportFraction(done: Int, total: Int): Float? = when {
        total < 0 -> (done / 1000f).coerceIn(0f, 1f)
        total == 0 -> null
        else -> (done.toFloat() / total).coerceIn(0f, 1f)
    }

    /** A multi-file import fills by files finished. */
    fun batchFraction(done: Int, total: Int): Float = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
}
