package app.ios.messages.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ios.messages.R
import app.ios.messages.Sounds
import app.ios.messages.data.Prefs
import app.ios.messages.security.Integrity
import app.ios.messages.security.applyScreenSecure

private fun Context.activity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Notifications are managed by Android: one row that opens the system page, plus the app's own send sound. */
@Composable
fun SystemNotificationsSection(onOpenSystem: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var sent by remember { mutableStateOf(prefs.sentSound) }
    Section(stringResource(R.string.notifications), stringResource(R.string.notifications_system_footer)) {
        SettingRow(stringResource(R.string.notifications_system), onClick = onOpenSystem) { ChevronRight(p.tertiary) }
        SettingRow(stringResource(R.string.sent_sound), last = true) {
            IosSwitch(sent) { sent = it; prefs.sentSound = it; if (it) Sounds.playSent(ctx) }
        }
    }
}

/** Smooth mode (cheap static bars instead of live blur) for lower-end phones. */
@Composable
fun PerformanceSection() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var smooth by remember { mutableStateOf(prefs.smoothMode) }
    Section(stringResource(R.string.performance_title)) {
        SettingRow(stringResource(R.string.smooth_mode), last = true) {
            IosSwitch(smooth) { smooth = it; prefs.smoothMode = it; Perf.cheapBlur = it }
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
