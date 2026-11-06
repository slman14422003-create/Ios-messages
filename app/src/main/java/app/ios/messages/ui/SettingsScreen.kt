package app.ios.messages.ui

import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import app.ios.messages.R

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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.ios.messages.data.SimInfo
import app.ios.messages.data.Sims
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
    onBack: () -> Unit,
    onMakeDefault: () -> Unit,
    onGrant: () -> Unit,
    onAppSettings: () -> Unit,
    onBubbleBlue: (Boolean) -> Unit,
    onSystemNotifications: () -> Unit,
    sims: List<SimInfo> = emptyList(),
    defaultSub: Int = -1,
    onDefaultSub: (Int) -> Unit = {},
    onSaveSim: (Int, String, Long) -> Unit = { _, _, _ -> },
    language: String = "system",
    onLanguage: (String) -> Unit = {},
    appearance: Int = 0,
    onAppearance: (Int) -> Unit = {},
    textScale: Float = 1f,
    onTextScale: (Float) -> Unit = {},
    keepDays: Int = 0,
    onKeepDays: (Int) -> Unit = {},
    onBackup: () -> Unit = {},
    onRestore: () -> Unit = {}
) {
    val p = LocalPalette.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var pendingKeep by remember { mutableStateOf<Int?>(null) }
    var blockedList by remember { mutableStateOf(app.ios.messages.data.Blocking.list(ctx)) }
    var editing by remember { mutableStateOf<SimInfo?>(null) }
    editing?.let { sim ->
        SimEditDialog(sim, onDismiss = { editing = null }, onSave = { name, color ->
            onSaveSim(sim.subId, name, color)
            editing = null
        })
    }
    val scroll = rememberScrollState()
    val live = remember { LiveBlur() }
    var headerH by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val blurPx = with(androidx.compose.ui.platform.LocalDensity.current) { 22.dp.toPx() }
    val topInset = WindowInsets.statusBars
        .asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize().background(p.groupBg)) {
        Column(
            Modifier
                .fillMaxSize()
                .liveBlurEdge(live, false, { headerH }, { (scroll.value / 24f).coerceIn(0f, 1f) }, p.groupBg, p.bar.copy(alpha = 0.7f), blurPx)
                .verticalScroll(scroll)
                .padding(top = topInset + 44.dp)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
        ) {
            Section(
                stringResource(R.string.default_app),
                stringResource(R.string.default_app_desc)
            ) {
                SettingRow(
                    stringResource(R.string.default_sms_app),
                    last = true,
                    onClick = if (isDefault) null else onMakeDefault
                ) {
                    Text(
                        if (isDefault) stringResource(R.string.messages) else stringResource(R.string.set_default),
                        color = if (isDefault) p.secondary else p.blue,
                        fontSize = 17.sp
                    )
                }
            }

            if (sims.isNotEmpty()) {
                Section(
                    stringResource(R.string.sim_cards),
                    stringResource(R.string.sim_cards_desc)
                ) {
                    sims.forEach { sim ->
                        SettingRow(sim.label, onClick = { editing = sim }) {
                            Box(Modifier.size(14.dp).clip(CircleShape).background(Color(sim.color)))
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.sim_n, sim.slot + 1), color = p.secondary, fontSize = 17.sp)
                            Spacer(Modifier.width(8.dp))
                            ChevronRight(p.tertiary)
                        }
                    }
                    val options = listOf(-1) + sims.map { it.subId }
                    val current = if (defaultSub in options) defaultSub else -1
                    SettingRow(
                        stringResource(R.string.default_sim),
                        last = true,
                        onClick = { onDefaultSub(options[(options.indexOf(current) + 1) % options.size]) }
                    ) {
                        Text(
                            if (current == -1) stringResource(R.string.automatic) else sims.first { it.subId == current }.label,
                            color = p.secondary,
                            fontSize = 17.sp
                        )
                    }
                }
            }

            Section(stringResource(R.string.appearance)) {
                listOf(
                    0 to stringResource(R.string.appearance_auto),
                    1 to stringResource(R.string.appearance_light),
                    2 to stringResource(R.string.appearance_dark)
                ).forEachIndexed { i, (v, label) ->
                    SettingRow(label, last = i == 2, onClick = { onAppearance(v) }) {
                        if (appearance == v) CheckMark(p.blue) else Spacer(Modifier.size(16.dp))
                    }
                }
            }

            Section(stringResource(R.string.text_size), stringResource(R.string.text_size_desc)) {
                listOf(
                    0.85f to stringResource(R.string.size_small),
                    1f to stringResource(R.string.size_default),
                    1.15f to stringResource(R.string.size_large),
                    1.3f to stringResource(R.string.size_larger),
                    1.5f to stringResource(R.string.size_largest)
                ).forEachIndexed { i, (v, label) ->
                    SettingRow(label, last = i == 4, onClick = { onTextScale(v) }) {
                        if (kotlin.math.abs(textScale - v) < 0.01f) CheckMark(p.blue) else Spacer(Modifier.size(16.dp))
                    }
                }
            }

            Section(stringResource(R.string.keep_messages), stringResource(R.string.keep_messages_desc)) {
                listOf(
                    0 to stringResource(R.string.keep_forever),
                    365 to stringResource(R.string.keep_year),
                    30 to stringResource(R.string.keep_30)
                ).forEachIndexed { i, (v, label) ->
                    SettingRow(label, last = i == 2, onClick = {
                        if (v != keepDays) {
                            if (v == 0) onKeepDays(0) else pendingKeep = v
                        }
                    }) {
                        if (keepDays == v) CheckMark(p.blue) else Spacer(Modifier.size(16.dp))
                    }
                }
            }

            if (blockedList.isNotEmpty()) {
                Section(stringResource(R.string.blocked_contacts), stringResource(R.string.blocked_desc)) {
                    blockedList.forEachIndexed { i, num ->
                        SettingRow(num, last = i == blockedList.lastIndex, onClick = {
                            app.ios.messages.data.Blocking.unblock(ctx, num)
                            blockedList = app.ios.messages.data.Blocking.list(ctx)
                        }) {
                            Text(stringResource(R.string.unblock), color = p.blue, fontSize = 17.sp)
                        }
                    }
                }
            }

            Section(stringResource(R.string.language), stringResource(R.string.language_desc)) {
                listOf(
                    "system" to stringResource(R.string.lang_system),
                    "en" to stringResource(R.string.lang_english),
                    "ar" to stringResource(R.string.lang_arabic)
                ).forEachIndexed { i, (tag, label) ->
                    SettingRow(label, last = i == 2, onClick = { if (language != tag) onLanguage(tag) }) {
                        if (language == tag) CheckMark(p.blue) else Spacer(Modifier.size(16.dp))
                    }
                }
            }

            Section(stringResource(R.string.sent_bubble_color)) {
                SettingRow(stringResource(R.string.green_sms), onClick = { onBubbleBlue(false) }) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(SentBubble))
                    Spacer(Modifier.width(12.dp))
                    if (!bubbleBlue) CheckMark(p.blue) else Spacer(Modifier.size(16.dp))
                }
                SettingRow(stringResource(R.string.blue_imessage), last = true, onClick = { onBubbleBlue(true) }) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(IosBlue))
                    Spacer(Modifier.width(12.dp))
                    if (bubbleBlue) CheckMark(p.blue) else Spacer(Modifier.size(16.dp))
                }
            }

            SystemNotificationsSection(onSystemNotifications)
            PerformanceSection()
            PrivacySecuritySection()

            Section(stringResource(R.string.backup_title), stringResource(R.string.backup_desc)) {
                SettingRow(stringResource(R.string.backup_export), onClick = onBackup) { ChevronRight(p.tertiary) }
                SettingRow(stringResource(R.string.backup_restore), last = true, onClick = onRestore) { ChevronRight(p.tertiary) }
            }

            Section(stringResource(R.string.permissions)) {
                SettingRow(
                    stringResource(R.string.sms_contacts_access),
                    onClick = if (hasPermission) null else onGrant
                ) {
                    Text(
                        if (hasPermission) stringResource(R.string.allowed) else stringResource(R.string.allow),
                        color = if (hasPermission) p.secondary else p.blue,
                        fontSize = 17.sp
                    )
                }
                SettingRow(stringResource(R.string.app_settings), last = true, onClick = onAppSettings) {
                    ChevronRight(p.tertiary)
                }
            }

            Section(stringResource(R.string.about)) {
                SettingRow(stringResource(R.string.version), last = true) {
                    Text(
                        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "",
                        color = p.secondary,
                        fontSize = 17.sp
                    )
                }
            }
        }
        pendingKeep?.let { d ->
            IosConfirm(
                title = stringResource(R.string.keep_confirm, if (d == 30) stringResource(R.string.keep_30) else stringResource(R.string.keep_year)),
                confirmLabel = stringResource(R.string.delete),
                onConfirm = { pendingKeep = null; onKeepDays(d) },
                onDismiss = { pendingKeep = null }
            )
        }
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { headerH = it.height.toFloat() }
                .statusBarsPadding()
        ) {
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
                    Text(stringResource(R.string.messages), color = p.blue, fontSize = 17.sp)
                }
                Text(
                    stringResource(R.string.settings),
                    color = p.label,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            Hairline(Modifier.graphicsLayer { alpha = (scroll.value / 24f).coerceIn(0f, 1f) })
        }
    }
}

@Composable
internal fun Section(
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
internal fun SettingRow(
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
internal fun IosSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    val track by animateColorAsState(if (checked) SentBubble else p.received, Motion.fade(250), label = "track")
    val x by animateDpAsState(
        if (checked) 20.dp else 0.dp,
        Motion.knob(),
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


@Composable
private fun SimEditDialog(sim: SimInfo, onDismiss: () -> Unit, onSave: (String, Long) -> Unit) {
    var name by remember { mutableStateOf(sim.label) }
    var color by remember { mutableStateOf(sim.color) }
    IosAlert(
        title = stringResource(R.string.sim_dialog_title, sim.slot + 1, sim.carrier),
        actions = listOf(
            AlertAction(stringResource(R.string.cancel), onClick = onDismiss),
            AlertAction(stringResource(R.string.save), bold = true, onClick = { onSave(name.ifBlank { sim.carrier }, color) })
        ),
        onDismiss = onDismiss
    ) {
        IosField(name, { if (it.length <= 16) name = it }, stringResource(R.string.sim_name_hint))
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sims.palette.forEach { c ->
                Box(
                    Modifier
                        .size(if (c == color) 28.dp else 24.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .plainClickable { color = c }
                )
            }
        }
    }
}
