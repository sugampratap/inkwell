package com.xnotes.platform

import android.content.Context
import androidx.annotation.StyleRes
import com.xnotes.R

/** Which splash theme the next launch should use, from the appearance setting. */
enum class LaunchLook(@param:StyleRes val splashTheme: Int) {
    /** The base launch theme (light): a Dark or OLED look is the saved window colour and, on 13+, its own splash. */
    SYSTEM(R.style.Theme_Xnotes_Splash),
    LIGHT(R.style.Theme_Xnotes_Splash_Light),
    DARK(R.style.Theme_Xnotes_Splash_Dark),
    OLED(R.style.Theme_Xnotes_Splash_Oled);

    companion object {
        fun of(appearance: String): LaunchLook = when (appearance) {
            "system" -> SYSTEM
            "dark" -> DARK
            "oled" -> OLED
            else -> LIGHT
        }
    }
}

/** The window colour and look the app last drew with, read before anything else loads. */
object LaunchLookStore {
    private const val FILE = "launch_look"
    private const val KEY_LOOK = "look"
    private const val KEY_BG = "bg"

    /** The saved window colour (ARGB), or null on a first launch. */
    fun background(context: Context): Int? {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return if (p.contains(KEY_BG)) p.getInt(KEY_BG, 0) else null
    }

    /** Save [look] and [bg]; true when the look changed (so the splash theme needs telling). */
    fun save(context: Context, look: LaunchLook, bg: Int): Boolean {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val changed = p.getString(KEY_LOOK, null) != look.name
        if (changed || p.getInt(KEY_BG, 0) != bg || !p.contains(KEY_BG)) p.edit().putString(KEY_LOOK, look.name).putInt(KEY_BG, bg).apply()
        return changed
    }
}
