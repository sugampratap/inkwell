package com.xnotes.platform

import android.content.Context
import com.xnotes.core.model.Rgba
import com.xnotes.core.tools.TapeConfig
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Where the tape tool's style lives between sessions: one tiny JSON file beside the settings rather
 * than a field inside them, so the roll is remembered without the settings format having to know
 * about it. Shared by every editor in the process (a split view runs two), like the settings are.
 *
 * Reading it is a few dozen bytes the first time anything asks; writing goes to a background thread
 * so a slider drag in the tape popover never touches the disk on the main thread.
 */
object TapeConfigStore {

    @Volatile private var value: TapeConfig? = null

    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "tape-config").apply { isDaemon = true } }

    private fun store(context: Context) =
        JsonStore(File(File(context.applicationContext.filesDir, "config"), "tape.json"))

    fun get(context: Context): TapeConfig = value ?: load(context).also { value = it }

    fun set(context: Context, config: TapeConfig) {
        if (value == config) return
        value = config
        val file = store(context)
        writer.execute {
            file.write(
                JSONObject()
                    .put("color", Rgba.toHex(config.color))
                    .put("pattern", config.pattern.id)
                    .put("width", config.width),
            )
        }
    }

    private fun load(context: Context): TapeConfig {
        val o = store(context).read()
        return TapeConfig.decode(
            o.optString("color", "").ifEmpty { null },
            o.optString("pattern", "").ifEmpty { null },
            if (o.has("width")) o.optDouble("width") else null,
        )
    }
}
