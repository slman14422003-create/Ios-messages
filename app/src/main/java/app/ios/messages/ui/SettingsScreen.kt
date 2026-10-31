package app.ios.messages.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    isDefault: Boolean,
    hasPermission: Boolean,
    bubbleBlue: Boolean,
    notifications: Boolean,
    previews: Boolean,
    onBack: () -> Unit,
    onMakeDefault: () -> Unit,
    onGrant: () -> Unit,
    onAppSettings: () -> Unit,
    onBubbleBlue: (Boolean) -> Unit,
    onNotifications: (Boolean) -> Unit,
    onPreviews: (Boolean) -> Unit,
    onSystemNotifications: () -> Unit
) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize().background(p.groupBg)) {
        Column(Modifier.fillMaxWidth().background(p.groupBg).statusBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(44.dp)) {
                Row(
                    Modifier
                        .align(Alignment.CenterStart)
                        .plainClickable(onBack)
                        .padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChevronLeft(p.blue)
                    Spacer(Modifier.width(6.dp))
                    Text("Messages", color = p.blue, fontSize = 17.sp)
                }
                Text(
                    "Settings",
                    color = p.label,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            Hairline()
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
        ) {
            Section(
                "Default App",
                "Messages must be the default SMS app to send, receive and delete messages."
            ) {
                SettingRow(
                    "Default SMS App",
                    last = true,
                    onClick = if (isDefault) null else onMakeDefault
                ) {
                    Text(
                        if (isDefault) "Messages" else "Set Default",
                        color = if (isDefault) p.secondary else p.blue,
                        fontSize = 17.sp
                    )
                }
            }

            Section("Sent Bubble Color") {
                SettingRow("Green (SMS)", onClick = { onBubbleBlue(false) }) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(SentBubble))
                    Spacer(Modifier.width(12.dp))
                    if (!bubbleBlue) CheckMark(p.blue) else Spacer(Modifier.size(16.dp))
                }
                SettingRow("Blue (iMessage)", last = true, onClick = { onBubbleBlue(true) }) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(IosBlue))
                    Spacer(Modifier.width(12.dp))
                    if (bubbleBlue) CheckMark(p.blue) else Spacer(Modifier.size(16.dp))
                }
            }

            Section("Notifications") {
                SettingRow("Allow Notifications") { IosSwitch(notifications, onNotifications) }
                SettingRow("Show Previews") { IosSwitch(previews, onPreviews) }
                SettingRow("Sound & System Settings", last = true, onClick = onSystemNotifications) {
                    ChevronRight(p.tertiary)
                }
            }

            Section("Permissions") {
                SettingRow(
                    "SMS & Contacts Access",
                    onClick = if (hasPermission) null else onGrant
                ) {
                    Text(
                        if (hasPermission) "Allowed" else "Allow",
                        color = if (hasPermission) p.secondary else p.blue,
                        fontSize = 17.sp
                    )
                }
                SettingRow("App Settings", last = true, onClick = onAppSettings) {
                    ChevronRight(p.tertiary)
                }
            }

            Section("About") {
                SettingRow("Version", last = true) {
                    Text("1.5", color = p.secondary, fontSize = 17.sp)
                }
            }
        }
    }
}

@Composable
private fun Section(
    title: String,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val p = LocalPalette.current
    Text(
        title.uppercase(),
        color = p.secondary,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 32.dp, top = 24.dp, bottom = 7.dp)
    )
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(p.cell),
        content = content
    )
    if (footer != null) {
        Text(
            footer,
            color = p.secondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 7.dp)
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    last: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {}
) {
    val p = LocalPalette.current
    Box(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.iosClickable(onClick) else Modifier)
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = p.label, fontSize = 17.sp, modifier = Modifier.weight(1f))
            trailing()
        }
        if (!last) Hairline(Modifier.align(Alignment.BottomStart).padding(start = 16.dp))
    }
}

@Composable
private fun IosSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    val track by animateColorAsState(if (checked) SentBubble else p.received, label = "track")
    val x by animateDpAsState(
        if (checked) 20.dp else 0.dp,
        spring(dampingRatio = 0.62f, stiffness = 520f),
        label = "knob"
    )
    Box(
        Modifier
            .width(51.dp)
            .height(31.dp)
            .clip(CircleShape)
            .background(track)
            .plainClickable { onChange(!checked) }
            .padding(2.dp)
    ) {
        Box(
            Modifier
                .offset(x = x)
                .size(27.dp)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape)
        )
    }
}
