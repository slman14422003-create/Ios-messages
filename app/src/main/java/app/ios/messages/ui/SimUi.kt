package app.ios.messages.ui

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ios.messages.data.SimInfo

/** SIM cards of the device, visible to every bubble without threading it through parameters. */
val LocalSims = staticCompositionLocalOf<List<SimInfo>> { emptyList() }

/** Segmented control above the input field: one segment per SIM, filled with the SIM's color. */
@Composable
fun SimSwitch(
    sims: List<SimInfo>,
    selected: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(17.dp)
    Row(
        modifier
            .clip(shape)
            .background(p.glass)
            .border(Dp.Hairline, p.separator, shape)
            .padding(2.dp)
    ) {
        sims.forEach { s ->
            val on = s.subId == selected
            val bg by animateColorAsState(if (on) Color(s.color) else Color.Transparent, label = "sim")
            Row(
                Modifier
                    .clip(RoundedCornerShape(15.dp))
                    .background(bg)
                    .plainClickable { onPick(s.subId) }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (on) Color.White else Color(s.color))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    s.label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (on) Color.White else p.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 110.dp)
                )
            }
        }
    }
}

/** Small colored dot + SIM name shown under a message, so you can tell which SIM it used. */
@Composable
fun SimTag(sim: SimInfo, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(modifier.padding(top = 2.dp, start = 10.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Color(sim.color)))
        Spacer(Modifier.width(4.dp))
        Text(sim.label, color = p.secondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp))
    }
}

/** One-tap copy for verification codes. */
@Composable
fun OtpChip(code: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    Box(
        modifier
            .padding(top = 4.dp, start = 4.dp, end = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(p.blue.copy(alpha = 0.14f))
            .plainClickable {
                clipboard.setText(AnnotatedString(code))
                Toast.makeText(ctx, ctx.getString(R.string.code_copied), Toast.LENGTH_SHORT).show()
            }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(stringResource(R.string.copy_code_chip, code), color = p.blue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** First-run guide: permissions, then "default SMS app". Disappears by itself when both are done. */
@Composable
fun SetupScreen(
    hasPermission: Boolean,
    isDefault: Boolean,
    onGrant: () -> Unit,
    onMakeDefault: () -> Unit,
    onSkip: () -> Unit
) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .plainClickable {}
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp)
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(56.dp))
            Text(
                stringResource(R.string.welcome),
                color = p.label,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.welcome_sub),
                color = p.secondary,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(40.dp))
            SetupStep(
                n = 1,
                done = hasPermission,
                title = stringResource(R.string.allow_access),
                text = stringResource(R.string.allow_access_desc),
                action = stringResource(R.string.allow),
                onClick = onGrant
            )
            Spacer(Modifier.height(28.dp))
            SetupStep(
                n = 2,
                done = isDefault,
                title = stringResource(R.string.make_default_title),
                text = stringResource(R.string.make_default_desc),
                action = stringResource(R.string.set_default),
                onClick = onMakeDefault
            )
        }
        Text(
            stringResource(R.string.not_now),
            color = p.blue,
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .plainClickable(onSkip)
                .padding(vertical = 18.dp)
        )
    }
}

@Composable
private fun SetupStep(n: Int, done: Boolean, title: String, text: String, action: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(if (done) Color(0xFF34C759) else p.blue),
            contentAlignment = Alignment.Center
        ) {
            Text(if (done) "\u2713" else n.toString(), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = p.label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(text, color = p.secondary, fontSize = 15.sp, lineHeight = 20.sp)
            if (!done) {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(p.blue)
                        .plainClickable(onClick)
                        .padding(horizontal = 22.dp, vertical = 9.dp)
                ) { Text(action, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
