package app.ios.messages.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ios.messages.EdgeSwipeBack
import app.ios.messages.R
import app.ios.messages.data.BubbleConfig
import app.ios.messages.data.SimInfo
import app.ios.messages.data.Sims
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object SettingsPage {
    const val Notifications = "notifications"
    const val Display = "display"
    const val Themes = "themes"
    const val Language = "language"
    const val Sims = "sims"
    const val Storage = "storage"
    const val Privacy = "privacy"
    const val About = "about"
}

@Composable
fun SettingsScreen(
    isDefault: Boolean,
    hasPermission: Boolean,
    bubble: BubbleConfig,
    onBubble: (BubbleConfig) -> Unit,
    page: String?,
    onPage: (String?) -> Unit,
    onBack: () -> Unit,
    onMakeDefault: () -> Unit,
    onGrant: () -> Unit,
    onAppSettings: () -> Unit,
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
    val dir = rtlSign()
    val progress = remember { Animatable(if (page != null) 1f else 0f) }
    var lastPage by remember { mutableStateOf(page) }
    LaunchedEffect(page) {
        if (page != null) lastPage = page
        val goal = if (page != null) 1f else 0f
        progress.animateTo(goal, Motion.pushF(kotlin.math.abs(goal - progress.value)))
    }
    val subVisible by remember { derivedStateOf { progress.value > 0.001f } }

    Box(Modifier.fillMaxSize().background(p.groupBg)) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                translationX = -progress.value * size.width * Motion.Parallax * dir
            }
        ) {
            SettingsMain(
                isDefault = isDefault,
                sims = sims,
                language = language,
                onBack = onBack,
                onMakeDefault = onMakeDefault,
                onPage = onPage
            )
        }
        if (subVisible || page != null) {
            Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black, alpha = progress.value * Motion.PushDim) })
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = (1f - progress.value) * size.width * dir }
                    .background(p.groupBg)
                    .plainClickable {}
            ) {
                SubPage(
                    page = page ?: lastPage,
                    onBack = { onPage(null) },
                    isDefault = isDefault,
                    hasPermission = hasPermission,
                    bubble = bubble,
                    onBubble = onBubble,
                    onGrant = onGrant,
                    onAppSettings = onAppSettings,
                    onSystemNotifications = onSystemNotifications,
                    sims = sims,
                    defaultSub = defaultSub,
                    onDefaultSub = onDefaultSub,
                    onSaveSim = onSaveSim,
                    language = language,
                    onLanguage = onLanguage,
                    appearance = appearance,
                    onAppearance = onAppearance,
                    textScale = textScale,
                    onTextScale = onTextScale,
                    keepDays = keepDays,
                    onKeepDays = onKeepDays,
                    onBackup = onBackup,
                    onRestore = onRestore
                )
            }
            EdgeSwipeBack(
                progress = progress,
                enabled = page != null,
                onCommit = { onPage(null) },
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }
    }
}

@Composable
private fun SettingsMain(
    isDefault: Boolean,
    sims: List<SimInfo>,
    language: String,
    onBack: () -> Unit,
    onMakeDefault: () -> Unit,
    onPage: (String?) -> Unit
) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            GlassCircle(44.dp, onClick = onBack) { ChevronLeft(p.label) }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
        ) {
            Text(
                stringResource(R.string.settings),
                color = p.label,
                style = IosType.largeTitle,
                modifier = Modifier.padding(start = 20.dp, bottom = 6.dp)
            )
            DefaultAppCard(isDefault, onMakeDefault)

            Group {
                NavRow(Color(0xFFFF3B30), stringResource(R.string.notifications), onClick = { onPage(SettingsPage.Notifications) }) { BellGlyph() }
                NavRow(Color(0xFF007AFF), stringResource(R.string.settings_display), onClick = { onPage(SettingsPage.Display) }) { TextGlyph() }
                NavRow(Color(0xFFAF52DE), stringResource(R.string.theme_title), onClick = { onPage(SettingsPage.Themes) }) {
                    BubbleIcon(Color.White, Modifier.size(20.dp))
                }
                NavRow(
                    Color(0xFF5856D6), stringResource(R.string.language), last = true,
                    value = stringResource(
                        when (language) {
                            "en" -> R.string.lang_english
                            "ar" -> R.string.lang_arabic
                            else -> R.string.lang_system
                        }
                    ),
                    onClick = { onPage(SettingsPage.Language) }
                ) { GlobeGlyph() }
            }

            val green = Color(0xFF34C759)
            val orange = Color(0xFFFF9500)
            val blue = Color(0xFF007AFF)
            Group {
                if (sims.isNotEmpty()) {
                    NavRow(green, stringResource(R.string.sim_cards), onClick = { onPage(SettingsPage.Sims) }) { SimGlyph(green) }
                }
                NavRow(orange, stringResource(R.string.settings_storage), onClick = { onPage(SettingsPage.Storage) }) { ArchiveGlyph(orange) }
                NavRow(blue, stringResource(R.string.privacy_security), last = true, onClick = { onPage(SettingsPage.Privacy) }) { LockGlyph() }
            }

            Group {
                NavRow(Color(0xFF8E8E93), stringResource(R.string.settings_about_perm), last = true, onClick = { onPage(SettingsPage.About) }) {
                    GearIcon(Color.White, Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun DefaultAppCard(isDefault: Boolean, onMakeDefault: () -> Unit) {
    val p = LocalPalette.current
    Group {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (isDefault) Modifier else Modifier.iosClickable(onMakeDefault))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF4ADE6B), Color(0xFF34C759)))),
                contentAlignment = Alignment.Center
            ) { BubbleIcon(Color.White, Modifier.size(32.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.default_sms_app), color = p.label, style = IosType.headline)
                Text(
                    if (isDefault) stringResource(R.string.messages) else stringResource(R.string.default_app_desc),
                    color = p.secondary,
                    style = IosType.footnote,
                    maxLines = 2
                )
            }
            if (!isDefault) {
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.set_default), color = p.blue, fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 22.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(p.cell),
        content = content
    )
}

@Composable
private fun NavRow(
    tile: Color,
    title: String,
    value: String? = null,
    last: Boolean = false,
    onClick: () -> Unit,
    glyph: @Composable BoxScope.() -> Unit
) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxWidth().iosClickable(onClick)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsTile(tile, content = glyph)
            Spacer(Modifier.width(14.dp))
            Text(title, color = p.label, style = IosType.body, modifier = Modifier.weight(1f))
            if (value != null) {
                Text(value, color = p.secondary, fontSize = 17.sp)
                Spacer(Modifier.width(8.dp))
            }
            ChevronRight(p.tertiary)
        }
        if (!last) Hairline(Modifier.align(Alignment.BottomStart).padding(start = 60.dp))
    }
}

@Composable
private fun SubPage(
    page: String?,
    onBack: () -> Unit,
    isDefault: Boolean,
    hasPermission: Boolean,
    bubble: BubbleConfig,
    onBubble: (BubbleConfig) -> Unit,
    onGrant: () -> Unit,
    onAppSettings: () -> Unit,
    onSystemNotifications: () -> Unit,
    sims: List<SimInfo>,
    defaultSub: Int,
    onDefaultSub: (Int) -> Unit,
    onSaveSim: (Int, String, Long) -> Unit,
    language: String,
    onLanguage: (String) -> Unit,
    appearance: Int,
    onAppearance: (Int) -> Unit,
    textScale: Float,
    onTextScale: (Float) -> Unit,
    keepDays: Int,
    onKeepDays: (Int) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    val p = LocalPalette.current
    val title = when (page) {
        SettingsPage.Notifications -> R.string.notifications
        SettingsPage.Display -> R.string.settings_display
        SettingsPage.Themes -> R.string.theme_title
        SettingsPage.Language -> R.string.language
        SettingsPage.Sims -> R.string.sim_cards
        SettingsPage.Storage -> R.string.settings_storage
        SettingsPage.Privacy -> R.string.privacy_security
        else -> R.string.settings_about_perm
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            GlassCircle(44.dp, onClick = onBack) { ChevronLeft(p.label) }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
        ) {
            Text(
                stringResource(title),
                color = p.label,
                style = IosType.largeTitle,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp)
            )
            when (page) {
                SettingsPage.Notifications -> SystemNotificationsSection(onSystemNotifications)
                SettingsPage.Display -> DisplayPage(appearance, onAppearance, textScale, onTextScale)
                SettingsPage.Themes -> ThemePage(bubble, onBubble)
                SettingsPage.Language -> LanguagePage(language, onLanguage)
                SettingsPage.Sims -> SimsPage(sims, defaultSub, onDefaultSub, onSaveSim)
                SettingsPage.Storage -> StoragePage(keepDays, onKeepDays, onBackup, onRestore)
                SettingsPage.Privacy -> PrivacySecuritySection()
                else -> AboutPage(hasPermission, onGrant, onAppSettings)
            }
        }
    }
}

@Composable
private fun DisplayPage(appearance: Int, onAppearance: (Int) -> Unit, textScale: Float, onTextScale: (Float) -> Unit) {
    val p = LocalPalette.current
    Section(stringResource(R.string.appearance)) {
        Box(Modifier.padding(16.dp)) {
            Segmented(
                options = listOf(
                    stringResource(R.string.appearance_auto),
                    stringResource(R.string.appearance_light),
                    stringResource(R.string.appearance_dark)
                ),
                selected = appearance,
                onSelect = onAppearance
            )
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
    PerformanceSection()
}

@Composable
private fun ThemePage(cfg: BubbleConfig, onChange: (BubbleConfig) -> Unit) {
    var hue by remember { mutableFloatStateOf(cfg.hue) }
    val live = remember(cfg, hue) { cfg.copy(hue = hue) }
    LaunchedEffect(hue) {
        delay(250)
        if (hue != cfg.hue) onChange(cfg.copy(hue = hue))
    }
    Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
        BubblePreview(remember(live) { live.style() })
    }
    Section(stringResource(R.string.settings_color), stringResource(R.string.theme_footer)) {
        ThemeSwatches(live.theme, hue, onSelect = { id -> onChange(live.copy(theme = id)) })
        if (live.theme == "custom") {
            Hairline(Modifier.padding(start = 16.dp))
            Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                HueSlider(hue, { hue = it })
            }
        }
    }
    Section(stringResource(R.string.theme_title)) {
        SettingRow(stringResource(R.string.theme_gradient)) {
            IosSwitch(live.gradient) { onChange(live.copy(gradient = it)) }
        }
        SettingRow(stringResource(R.string.theme_tails), last = true) {
            IosSwitch(live.tails) { onChange(live.copy(tails = it)) }
        }
    }
    Section(stringResource(R.string.theme_received)) {
        Box(Modifier.padding(16.dp)) {
            Segmented(
                options = listOf(
                    stringResource(R.string.recv_default),
                    stringResource(R.string.recv_tinted),
                    stringResource(R.string.recv_outline)
                ),
                selected = live.received,
                onSelect = { onChange(live.copy(received = it)) }
            )
        }
    }
    Section(stringResource(R.string.theme_background)) {
        Box(Modifier.padding(16.dp)) {
            Segmented(
                options = listOf(
                    stringResource(R.string.bg_plain),
                    stringResource(R.string.bg_tinted),
                    stringResource(R.string.bg_gradient)
                ),
                selected = live.chatBg,
                onSelect = { onChange(live.copy(chatBg = it)) }
            )
        }
    }
}

@Composable
private fun LanguagePage(language: String, onLanguage: (String) -> Unit) {
    val langTags = listOf("system", "en", "ar")
    Section(stringResource(R.string.language), stringResource(R.string.language_desc)) {
        Box(Modifier.padding(16.dp)) {
            Segmented(
                options = listOf(
                    stringResource(R.string.lang_system),
                    stringResource(R.string.lang_english),
                    stringResource(R.string.lang_arabic)
                ),
                selected = langTags.indexOf(language).coerceAtLeast(0),
                onSelect = { i -> if (language != langTags[i]) onLanguage(langTags[i]) }
            )
        }
    }
}

@Composable
private fun SimsPage(
    sims: List<SimInfo>,
    defaultSub: Int,
    onDefaultSub: (Int) -> Unit,
    onSaveSim: (Int, String, Long) -> Unit
) {
    val p = LocalPalette.current
    var editing by remember { mutableStateOf<SimInfo?>(null) }
    editing?.let { sim ->
        SimEditDialog(sim, onDismiss = { editing = null }, onSave = { name, color ->
            onSaveSim(sim.subId, name, color)
            editing = null
        })
    }
    if (sims.isEmpty()) return
    Section(stringResource(R.string.sim_cards), stringResource(R.string.sim_cards_desc)) {
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

@Composable
private fun StoragePage(keepDays: Int, onKeepDays: (Int) -> Unit, onBackup: () -> Unit, onRestore: () -> Unit) {
    val p = LocalPalette.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var pendingKeep by remember { mutableStateOf<Int?>(null) }
    var blockedList by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(Unit) {
        blockedList = withContext(Dispatchers.IO) { app.ios.messages.data.Blocking.list(ctx) }
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
    Section(stringResource(R.string.backup_title), stringResource(R.string.backup_desc)) {
        SettingRow(stringResource(R.string.backup_export), onClick = onBackup) { ChevronRight(p.tertiary) }
        SettingRow(stringResource(R.string.backup_restore), last = true, onClick = onRestore) { ChevronRight(p.tertiary) }
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
    pendingKeep?.let { d ->
        IosConfirm(
            title = stringResource(R.string.keep_confirm, if (d == 30) stringResource(R.string.keep_30) else stringResource(R.string.keep_year)),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { pendingKeep = null; onKeepDays(d) },
            onDismiss = { pendingKeep = null }
        )
    }
}

@Composable
private fun AboutPage(hasPermission: Boolean, onGrant: () -> Unit, onAppSettings: () -> Unit) {
    val p = LocalPalette.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
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

@Composable
internal fun Section(
    title: String,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val p = LocalPalette.current
    // Same metrics as the phone app: caps header, 20dp card, footnote footer.
    Text(
        title.uppercase(),
        color = p.secondary,
        style = IosType.footnote,
        modifier = Modifier.padding(start = 28.dp, top = 22.dp, bottom = 8.dp)
    )
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(p.cell),
        content = content
    )
    if (footer != null) {
        Text(
            footer,
            color = p.secondary,
            style = IosType.footnote,
            modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 8.dp)
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
            Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = p.label, style = IosType.body, modifier = Modifier.weight(1f))
            trailing()
        }
        if (!last) Hairline(Modifier.align(Alignment.BottomStart).padding(start = 16.dp))
    }
}

/** iOS switch: green when on, the system fill colour when off (identical to the phone app's switch). */
@Composable
internal fun IosSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    val dark = LocalDark.current
    val on = if (dark) Color(0xFF30D158) else Color(0xFF34C759)
    val track by animateColorAsState(if (checked) on else p.fieldFill, Motion.fade(220), label = "track")
    val x by animateDpAsState(
        if (checked) 20.dp else 0.dp,
        spring(dampingRatio = 0.7f, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium),
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
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape)
        )
    }
}

/** Three-way control used for Appearance and Language, same as the phone app. */
@Composable
internal fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    val dark = LocalDark.current
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(10.dp)).background(p.fieldFill)
    ) {
        val w = maxWidth / options.size
        val x by animateDpAsState(
            w * selected,
            spring(dampingRatio = 0.8f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
            label = "segment"
        )
        Box(
            Modifier
                .offset(x = x)
                .width(w)
                .fillMaxHeight()
                .padding(2.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (dark) Color(0xFF636366) else Color.White)
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                val src = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .springPress(src, 0.97f)
                        .clickable(interactionSource = src, indication = null) { onSelect(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = p.label,
                        style = IosType.callout,
                        fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
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
