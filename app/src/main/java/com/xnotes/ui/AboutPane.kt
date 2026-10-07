package com.xnotes.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.xnotes.R
import com.xnotes.ui.icons.Ph
import com.xnotes.ui.kit.InkGroup
import com.xnotes.ui.kit.InkGroupRow
import com.xnotes.ui.kit.InkToast
import com.xnotes.ui.kit.pressScale
import com.xnotes.ui.theme.InkMotion
import com.xnotes.ui.theme.InkType
import com.xnotes.ui.theme.LocalInk
import com.xnotes.ui.theme.inkRounded
import com.xnotes.ui.theme.tnum
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Inkwell's own repository. */
private const val GITHUB_URL = "https://github.com/sugampratap/inkwell"

/** How long "Version copied" stays up (.st-toastw). */
private const val TOAST_MS = 1700L

/**
 * About (r2_settings Frame 5): the app's icon, name, tagline and version (tap to copy, for bug
 * reports) and a link to Inkwell's GitHub. The same pane serves the sidebar's About and Settings › About.
 */
@Composable
fun AboutPane() {
    val ink = LocalInk.current
    val ctx = LocalContext.current
    val appIcon = remember {
        runCatching { ctx.packageManager.getApplicationIcon(ctx.packageName).toBitmap(288, 288).asImageBitmap() }.getOrNull()
    }
    val version = remember {
        runCatching {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode else pi.versionCode.toLong()
            "${pi.versionName} ($code)"
        }.getOrDefault("")
    }
    // Bumped by each copy, so a second tap while the toast is up shows it again, for its full time.
    var toastNonce by remember { mutableIntStateOf(0) }
    val copied = stringResource(R.string.version_copied)

    fun open(url: String) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 460.dp).fillMaxWidth().padding(top = 28.dp, bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (appIcon != null) {
                    val shape = inkRounded(22.dp)
                    Image(appIcon, stringResource(R.string.app_name), Modifier.size(96.dp).shadow(10.dp, shape).clip(shape))
                }
                Text(stringResource(R.string.app_name), style = InkType.display, color = ink.text, modifier = Modifier.padding(top = 18.dp))
                Text(stringResource(R.string.app_tagline), style = InkType.body.copy(fontSize = 15.sp, lineHeight = 22.sp), color = ink.text2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                if (version.isNotEmpty()) {
                    VersionPill(stringResource(R.string.version_n, version), Modifier.padding(top = 14.dp)) {
                        copyVersion(ctx, version)
                        toastNonce++
                    }
                }
                Column(Modifier.fillMaxWidth().padding(top = 36.dp)) {
                    InkGroup {
                        InkGroupRow(stringResource(R.string.about_github), subtitle = stringResource(R.string.about_github_where), icon = Ph.githubLogo, onClick = { open(GITHUB_URL) }) {
                            Icon(Ph.arrowSquareOut, null, tint = ink.text3, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        RisingToast(copied, toastNonce, Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp))
    }
}

/** The version, tap to copy (.st-ver): a 34dp pill on --surface with a copy glyph. */
@Composable
private fun VersionPill(text: String, modifier: Modifier, onClick: () -> Unit) {
    val ink = LocalInk.current
    val src = remember { MutableInteractionSource() }
    Row(
        modifier
            .height(34.dp)
            .pressScale(src, 0.96f)
            .clip(CircleShape)
            .background(ink.surface)
            .clickable(src, LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, style = InkType.meta.copy(fontWeight = FontWeight.SemiBold).tnum(), color = ink.text2)
        Icon(Ph.copy, stringResource(R.string.copy_version), tint = ink.text, modifier = Modifier.size(15.dp))
    }
}

/**
 * A toast that rises 8dp and fades in, stays [TOAST_MS], then goes (transform and alpha only). Each
 * new [nonce] (0 is none yet) shows [message] again and restarts its time; once gone it leaves the tree.
 */
@Composable
private fun RisingToast(message: String, nonce: Int, modifier: Modifier) {
    val shown = remember { Animatable(0f) }
    var text by remember { mutableStateOf("") }
    LaunchedEffect(nonce) {
        if (nonce == 0) return@LaunchedEffect
        text = message
        launch { shown.animateTo(1f, InkMotion.popover()) }
        delay(TOAST_MS)
        shown.animateTo(0f, InkMotion.fade())
        text = ""
    }
    if (text.isNotEmpty()) {
        InkToast(
            text,
            modifier.graphicsLayer {
                alpha = shown.value.coerceIn(0f, 1f)
                translationY = (1f - shown.value) * 8.dp.toPx()
            },
            icon = Ph.checkCircle,
        )
    }
}

private fun copyVersion(ctx: Context, version: String) {
    runCatching {
        val clip = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val name = ctx.getString(R.string.app_name)
        clip.setPrimaryClip(ClipData.newPlainText("$name version", "$name $version"))
    }
}
