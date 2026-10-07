package com.xnotes.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk

/**
 * B2's text field (.field) on a [TextFieldValue], for fields that open with their text selected
 * (names): [height] tall (48dp), the theme's small corners, a 1dp --line ring that becomes a 2dp
 * near-black ring while focused. An error is said under the field by the caller, never by colouring
 * it. [leading] sits before the text (a colour dot, an icon); [trailing] after it (a clear button).
 * A field with no visible label is given a name by the caller (`semantics { contentDescription }`).
 */
@Composable
fun InkTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    height: Dp = 48.dp,
    shape: Shape = MaterialTheme.shapes.small,
    textStyle: TextStyle = InkType.row.copy(fontWeight = FontWeight.SemiBold),
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onKeyEvent: ((KeyEvent) -> Boolean)? = null,
) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (onKeyEvent != null) Modifier.onPreviewKeyEvent(onKeyEvent) else Modifier),
        singleLine = true,
        textStyle = textStyle.copy(color = ink.text),
        cursorBrush = SolidColor(ink.solid),
        interactionSource = src,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxSize()
                    .background(ink.raised, shape)
                    .border(if (focused) 2.dp else 1.dp, if (focused) ink.solid else ink.line, shape)
                    .padding(start = 14.dp, end = if (trailing != null) 6.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                leading?.invoke()
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.text.isEmpty() && placeholder != null) {
                        Text(placeholder, style = textStyle, color = ink.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
                trailing?.invoke()
            }
        },
    )
}

/**
 * The search field (.field with a magnifier): a pill by default (the side panel, Trash), or the
 * theme's small corners with [pill] false (Settings). A clear button shows while there is text;
 * clearing hands [onValueChange] an empty value. The keyboard's action is Search ([onSearch]).
 */
@Composable
fun InkSearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    pill: Boolean = true,
    height: Dp = 44.dp,
    focusRequester: FocusRequester? = null,
    onSearch: () -> Unit = {},
    onKeyEvent: ((KeyEvent) -> Boolean)? = null,
) {
    val ink = LocalInk.current
    InkTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        height = height,
        shape = if (pill) CircleShape else MaterialTheme.shapes.small,
        textStyle = InkType.row.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold),
        leading = { Icon(Ph.magnifyingGlass, null, tint = ink.text2, modifier = Modifier.size(18.dp)) },
        trailing = if (value.text.isEmpty()) null else {
            { InkIconButton(Ph.x, stringResource(R.string.kit_clear), { onValueChange(TextFieldValue("")) }, size = 32.dp, iconSize = 16.dp, tint = ink.text2) }
        },
        focusRequester = focusRequester,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        onKeyEvent = onKeyEvent,
    )
}
