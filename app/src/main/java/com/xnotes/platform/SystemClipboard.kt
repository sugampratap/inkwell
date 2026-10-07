package com.xnotes.platform

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri

/**
 * What the Android clipboard is holding, in the terms this app cares about: an image.
 *
 * Both canvases paste images and both ask the same two questions, so they ask them here rather than
 * each keeping its own copy of the answer.
 */
object SystemClipboard {

    /** An image the clipboard is holding by uri, or null when it holds something else. */
    fun imageUri(context: Context): Uri? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        val uri = clip.getItemAt(0).uri ?: return null
        val type = context.contentResolver.getType(uri)
        val isImage = type?.startsWith("image/") == true || clip.description?.hasMimeType("image/*") == true
        return if (isImage) uri else null
    }

    /** SVG markup sitting on the clipboard as plain text (copied source), as insertable bytes. */
    fun svgBytes(context: Context): ByteArray? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString() ?: return null
        val t = text.trimStart('﻿').trimStart()
        val looksSvg = t.startsWith("<svg") || ((t.startsWith("<?xml") || t.startsWith("<!")) && t.contains("<svg"))
        return if (looksSvg) text.toByteArray() else null
    }

    /** Whether there is anything here worth offering a paste for. */
    fun hasImage(context: Context): Boolean = imageUri(context) != null || svgBytes(context) != null

    /**
     * Whether the clipboard may hold an image, from its description alone.
     *
     * Reading the clip itself is announced by the system on Android 12 and later ("pasted from your
     * clipboard"), so the questions asked on every tap must not read it. The description is free to
     * ask: an image by type, or text that might be SVG source, earns the paste offer, and the paste
     * itself reads the clip, when the user has actually asked for it.
     */
    fun mayHaveImage(context: Context): Boolean {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        if (!cm.hasPrimaryClip()) return false
        val d = cm.primaryClipDescription ?: return false
        return d.hasMimeType("image/*") || d.hasMimeType("text/uri-list") || d.hasMimeType("image/svg+xml")
    }

    /** The clipboard's image as bytes, reading the uri or falling back to copied SVG source. */
    fun imageBytes(context: Context): ByteArray? {
        imageUri(context)?.let { uri ->
            runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
                .getOrNull()
                ?.let { return it }
        }
        return svgBytes(context)
    }
}
