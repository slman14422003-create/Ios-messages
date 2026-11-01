package app.ios.messages.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ios.messages.R
import app.ios.messages.data.Prefs
import app.ios.messages.security.Integrity
import app.ios.messages.security.applyScreenSecure
import kotlinx.coroutines.delay

private fun Context.activity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private val NotifColors = listOf(
    0L to Color(0xFF8E8E93),          // automatic: follows the sent-bubble colour
    0xFF007AFFL to Color(0xFF007AFF),
    0xFF34C759L to Color(0xFF34C759),
    0xFFFF3B30L to Color(0xFFFF3B30),
    0xFFFF9500L to Color(0xFFFF9500),
    0xFFAF52DEL to Color(0xFFAF52DE),
    0xFFFF2D55L to Color(0xFFFF2D55),
    0xFF30B0C7L to Color(0xFF30B0C7),
    0xFF48484AL to Color(0xFF48484A)
)

/** Banner colour / transparency / animation, with a live preview. */
@Composable
fun NotificationLookSection() {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var enabled by remember { mutableStateOf(prefs.bannerEnabled) }
    var color by remember { mutableLongStateOf(prefs.notifColor) }
    var alpha by remember { mutableFloatStateOf(prefs.bannerAlpha) }
    var anim by remember { mutableIntStateOf(prefs.bannerAnim) }
    var shown by remember { mutableStateOf(false) }
    var playKey by remember { mutableIntStateOf(0) }
    val argb = if (color != 0L) Color(color) else Color(prefs.notifColorArgb())
    val look = BannerLook(argb, alpha, anim, prefs.bannerSeconds)

    LaunchedEffect(playKey) {
        if (playKey > 0) {
            shown = false
            delay(60)
            shown = true
            delay(2600)
            shown = false
        }
    }

    Section(stringResource(R.string.notif_style), stringResource(R.string.notif_style_footer)) {
        SettingRow(stringResource(R.string.banner_in_app)) {
            IosSwitch(enabled) { enabled = it; prefs.bannerEnabled = it }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(stringResource(R.string.notif_color), color = p.label, fontSize = 17.sp)
            Spacer(Modifier.size(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NotifColors.forEach { (value, c) ->
                    val selected = color == value
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(c)
                            .then(if (selected) Modifier.border(3.dp, p.label.copy(alpha = 0.55f), CircleShape) else Modifier)
                            .plainClickable { color = value; prefs.notifColor = value; playKey++ },
                        contentAlignment = Alignment.Center
                    ) {
                        if (value == 0L) Text("A", color = Color.White, fontSize = 15.sp)
                    }
                }
            }
        }
        Hairline(Modifier.padding(start = 16.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.notif_opacity), color = p.label, fontSize = 17.sp, modifier = Modifier.weight(1f))
                Text("${(alpha * 100).toInt()}%", color = p.secondary, fontSize = 17.sp)
            }
            Slider(
                value = alpha,
                onValueChange = { alpha = it },
                onValueChangeFinished = { prefs.bannerAlpha = alpha; playKey++ },
                valueRange = 0.3f..1f,
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = argb, inactiveTrackColor = p.received)
            )
        }
        Hairline(Modifier.padding(start = 16.dp))
        val names = listOf(
            R.string.anim_none, R.string.anim_slide, R.string.anim_pop, R.string.anim_fade, R.string.anim_bounce
        )
        names.forEachIndexed { i, res ->
            SettingRow(stringResource(res), onClick = { anim = i; prefs.bannerAnim = i; playKey++ }) {
                if (anim == i) CheckMark(p.blue) else Spacer(Modifier.size(16.dp))
            }
        }
        SettingRow(stringResource(R.string.notif_preview), last = true, onClick = { playKey++ }) {
            Text(stringResource(R.string.notif_preview_play), color = p.blue, fontSize = 17.sp)
        }
    }
    // Live preview, outside the grouped card so the banner can overlap nothing else.
    Box(Modifier.fillMaxWidth().heightIn(min = 84.dp).padding(top = 10.dp)) {
        AnimatedVisibility(shown, enter = look.enter(), exit = look.exit()) {
            BannerCard(
                title = stringResource(R.string.notif_preview_title),
                body = stringResource(R.string.notif_preview_body),
                sub = null,
                look = look,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

/** Screenshot blocking + honest local-only / integrity status. */
@Composable
fun PrivacySecuritySection() {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var secure by remember { mutableStateOf(prefs.screenSecure) }
    val hasInternet = remember {
        ctx.packageManager.checkPermission(android.Manifest.permission.INTERNET, ctx.packageName) ==
            PackageManager.PERMISSION_GRANTED
    }
    val verdict = remember { Integrity.verdict(ctx) }

    Section(stringResource(R.string.privacy_security), stringResource(R.string.privacy_footer)) {
        SettingRow(stringResource(R.string.block_screenshots)) {
            IosSwitch(secure) {
                secure = it
                prefs.screenSecure = it
                ctx.activity()?.applyScreenSecure(it)
            }
        }
        SettingRow(stringResource(R.string.network_access)) {
            Text(
                stringResource(if (hasInternet) R.string.network_allowed else R.string.network_none),
                color = if (hasInternet) Red else p.secondary, fontSize = 17.sp
            )
        }
        SettingRow(stringResource(R.string.app_integrity), last = true) {
            Text(
                stringResource(if (verdict == Integrity.Verdict.OK) R.string.integrity_ok else R.string.integrity_bad),
                color = if (verdict == Integrity.Verdict.OK) p.secondary else Red, fontSize = 17.sp
            )
        }
    }
}
