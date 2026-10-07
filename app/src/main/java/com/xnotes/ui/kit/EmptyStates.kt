package com.xnotes.ui.kit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk

private val EmptyTitle = InkType.title.copy(fontWeight = FontWeight.ExtraBold, lineHeight = 26.sp)
private val EmptyBody = InkType.body.copy(fontSize = 14.5.sp, lineHeight = 21.sp)

/**
 * An empty place with a picture (.empty): the [art] (200 × 150), a plain-word title, one line of what
 * to do, and the ways out ([actions], e.g. New note). Centred, at most 380dp wide.
 */
@Composable
fun InkEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    art: (@Composable () -> Unit)? = null,
    body: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val ink = LocalInk.current
    Column(modifier.widthIn(max = 380.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (art != null) {
            art()
            Spacer(Modifier.height(18.dp))
        }
        Text(title, style = EmptyTitle, color = ink.text, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        if (body != null) {
            Spacer(Modifier.height(10.dp))
            Text(body, style = EmptyBody, color = ink.text2, textAlign = TextAlign.Center)
        }
        if (actions != null) {
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), content = actions)
        }
    }
}

/** A small gap inside a panel (.ps-hint): a faint icon, a bold line and one quiet line. */
@Composable
fun InkPanelHint(icon: ImageVector, title: String, body: String? = null, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    Column(modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = ink.text3, modifier = Modifier.padding(bottom = 4.dp).size(28.dp))
        Text(title, style = InkType.rowStrong.copy(fontWeight = FontWeight.Bold), color = ink.text, textAlign = TextAlign.Center)
        if (body != null) Text(body, style = InkType.meta.copy(lineHeight = 19.sp), color = ink.text2, textAlign = TextAlign.Center)
    }
}
