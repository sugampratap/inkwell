package com.xnotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.IconBadge
import com.xnotes.ui.kit.InkBrandButton
import com.xnotes.ui.kit.InkGhostButton
import com.xnotes.ui.kit.InkSheet
import com.xnotes.ui.kit.InkStrongButton
import com.xnotes.ui.kit.InkTextField
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import kotlinx.coroutines.delay

/** Which creation path [CreateNameSheet] is for. */
internal enum class CreateKind { CANVAS, IMPORT }

/** Hardware Enter confirms and Esc closes, in every name sheet. */
private fun nameKeys(onConfirm: () -> Unit, onDismiss: () -> Unit): (KeyEvent) -> Boolean = { ev ->
    when {
        ev.type != KeyEventType.KeyDown -> false
        ev.key == Key.Enter || ev.key == Key.NumPadEnter -> { onConfirm(); true }
        ev.key == Key.Escape -> { onDismiss(); true }
        else -> false
    }
}

/** How long a confirm with no answer yet holds off another (see [CreateNameSheet]). */
private const val CONFIRM_GUARD_MS = 3_000L

private val HintStyle = InkType.hint
private val ErrorStyle = InkType.meta.copy(lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)

/**
 * The name sheet for New canvas and Import PDF (r2_page_notebook Frame 4): the same header, field and
 * footer as New notebook in a small size, with a preview of what is made. A canvas has no pages, so a
 * line says why there is nothing to set; Create wears the marigold brand button. Import is near-black
 * and says Import. Errors stay in the sheet, under the name, in the danger colour beside an icon;
 * [locked] (a password-protected PDF) shows a lock for the preview. The name opens selected.
 */
@Composable
internal fun CreateNameSheet(
    kind: CreateKind,
    initial: String,
    folderName: String?,
    error: String?,
    locked: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val ink = LocalInk.current
    val canvas = kind == CreateKind.CANVAS
    var text by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // One confirm at a time: a double tap must not make two canvases or two imports. A new error ends
    // the attempt; the caller's work is out of reach here, so a repeat of the same error is let go
    // after [CONFIRM_GUARD_MS] (a success closes the sheet long before).
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(error) { busy = false }
    LaunchedEffect(busy) {
        if (busy) {
            delay(CONFIRM_GUARD_MS)
            busy = false
        }
    }
    val confirm = {
        if (!busy) {
            busy = true
            onConfirm(text.text.trim())
        }
    }
    val nameLabel = stringResource(R.string.name_field)
    InkSheet(
        title = stringResource(if (canvas) R.string.new_canvas else R.string.import_pdf_title),
        onDismiss = onDismiss,
        subtitle = folderName?.let { stringResource(if (canvas) R.string.new_notebook_in_folder else R.string.create_into_folder, it) },
        subtitleIcon = if (canvas) Ph.folderSimple else Ph.filePdf,
        width = 620.dp,
        showClose = false,
        footer = {
            Spacer(Modifier.weight(1f))
            InkGhostButton(stringResource(R.string.cancel), onDismiss)
            if (canvas) InkBrandButton(stringResource(R.string.create), { confirm() }, icon = Ph.plus, enabled = !busy)
            else InkStrongButton(stringResource(R.string.import_action), { confirm() }, icon = Ph.fileArrowDown, enabled = !busy)
        },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 22.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            CreatePreview(kind, locked)
            Column(Modifier.weight(1f)) {
                Text(nameLabel, style = InkType.label, color = ink.text, modifier = Modifier.padding(bottom = 10.dp))
                InkTextField(
                    text, { text = it },
                    modifier = Modifier.semantics { contentDescription = nameLabel },
                    placeholder = if (canvas) initial else null,
                    focusRequester = focus,
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    onKeyEvent = nameKeys(confirm, onDismiss),
                )
                Text(
                    if (canvas) stringResource(R.string.create_canvas_hint, initial) else stringResource(R.string.create_import_hint, folderName ?: ""),
                    style = HintStyle, color = ink.text2, modifier = Modifier.padding(start = 2.dp, top = 10.dp),
                )
                if (error != null) {
                    Row(Modifier.padding(start = 2.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Ph.warningCircle, null, tint = ink.text, modifier = Modifier.padding(top = 1.dp).size(18.dp))
                        Text(error, style = ErrorStyle, color = ink.danger)
                    }
                }
            }
        }
    }
}

/** What is being made (.pn-cvprev): a dotted canvas card, a PDF card, or a lock for a protected PDF; with its caption. */
@Composable
private fun CreatePreview(kind: CreateKind, locked: Boolean) {
    val ink = LocalInk.current
    val shape = MaterialTheme.shapes.medium
    Column(Modifier.width(120.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .then(if (kind == CreateKind.CANVAS || !locked) Modifier.shadow(6.dp, shape) else Modifier)
                .clip(shape)
                .background(if (kind == CreateKind.IMPORT && locked) ink.surface else if (kind == CreateKind.CANVAS) Color(0xFFFBFAF7) else Color.White)
                .then(
                    if (kind == CreateKind.CANVAS) Modifier.drawBehind {
                        // The canvas's dot grid (.pn-cnv): navy dots every 12dp.
                        val step = 12.dp.toPx()
                        val dot = Color(0xFF1F2A44).copy(alpha = 0.22f)
                        var y = step / 2
                        while (y < size.height) {
                            var x = step / 2
                            while (x < size.width) { drawCircle(dot, 1.dp.toPx(), Offset(x, y)); x += step }
                            y += step
                        }
                    } else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                kind == CreateKind.IMPORT && locked -> IconBadge(Ph.lockSimple)
                kind == CreateKind.IMPORT -> IconBadge(Ph.filePdf)
                else -> Unit
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(if (kind == CreateKind.CANVAS) Ph.infinity else Ph.filePdf, null, tint = ink.text, modifier = Modifier.size(15.dp))
            Text(
                stringResource(
                    when {
                        kind == CreateKind.CANVAS -> R.string.create_preview_canvas
                        locked -> R.string.create_preview_locked
                        else -> R.string.create_preview_pdf
                    },
                ),
                style = HintStyle.copy(fontWeight = FontWeight.SemiBold), color = ink.text2,
            )
        }
    }
}

/**
 * The B2 name sheet behind `NameDialog` (new folder, rename, Name this colour): the field (with an
 * optional colour dot), the error or a [note] under it, and Cancel and the action; [onRemove] adds a
 * red "Remove name" at the left. Enter confirms, Esc closes.
 */
@Composable
internal fun InkNameSheet(
    title: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    confirmLabel: String,
    placeholder: String?,
    confirmEnabled: Boolean,
    error: String?,
    focus: FocusRequester,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onRemove: (() -> Unit)?,
    leadingColor: Color?,
    note: String?,
) {
    val ink = LocalInk.current
    InkSheet(
        title = title,
        onDismiss = onDismiss,
        width = 480.dp,
        footer = {
            if (onRemove != null) InkGhostButton(stringResource(R.string.remove_name), onRemove, danger = true)
            Spacer(Modifier.weight(1f))
            InkGhostButton(stringResource(R.string.cancel), onDismiss)
            InkStrongButton(confirmLabel, onConfirm, enabled = confirmEnabled)
        },
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val label = stringResource(R.string.name_field)
            InkTextField(
                value, onValueChange,
                modifier = Modifier.semantics { contentDescription = label },
                placeholder = placeholder,
                leading = leadingColor?.let { c -> { Box(Modifier.size(14.dp).background(c, CircleShape)) } },
                focusRequester = focus,
                keyboardActions = KeyboardActions(onDone = { onConfirm() }),
                onKeyEvent = nameKeys(onConfirm, onDismiss),
            )
            if (error != null) Text(error, style = ErrorStyle, color = ink.danger)
            if (note != null) Text(note, style = InkType.body.copy(lineHeight = 21.sp), color = ink.text2)
        }
    }
}
