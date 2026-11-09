# SKILLS - iOS-Messages work log

## Task: icon, iPhone-style UI, contacts in New Message, settings button

### Done
- Launcher icon: white speech bubble with bottom-left tail on iOS green gradient (adaptive foreground, background, monochrome layer)
  - res/drawable/ic_launcher_foreground.xml, ic_launcher_monochrome.xml, ic_launcher_background.xml
  - res/mipmap-anydpi-v26/ic_launcher.xml, ic_launcher_round.xml
- New Message screen with contacts: ui/NewMessageScreen.kt
  - live search by name or number, A-Z sections with sticky headers, A-Z side index when keyboard is hidden
  - "Send to this number" row for typed numbers, Done key opens the chat
  - Picking a contact opens the chat directly
- Settings: ui/SettingsScreen.kt, opened by the gear button next to the compose button on the list screen
  - default SMS app, sent bubble color (green/blue), notifications on/off, previews on/off, system notification settings, permissions, app settings
- List screen: ui/ConversationsScreen.kt
  - Edit menu: Select Messages, filters (All, Unread, Unknown Senders)
  - selection mode with Read All / Delete bar
  - long press menu: Pin / Unpin, Mark as Read, Delete
  - pinned conversations shown as avatar grid (3 per row)
- Chat header tap menu: Call, Add to Contacts, Copy Number (ui/ChatScreen.kt)
- Storage: data/Prefs.kt (SharedPreferences) for pins, bubble color, notification options
- Data: SmsRepository.contacts(), Conversation.known, MessagesViewModel pin/prefs/bulk actions
- Notifier respects notification and preview settings

### Files touched
- MainActivity.kt (settings overlay, new message routing, state wiring)
- MessagesViewModel.kt, data/Models.kt, data/Prefs.kt, data/SmsRepository.kt, Notifier.kt
- ui/Theme.kt (groupBg, cell, LocalSentColor, IosBlue), ui/Icons.kt (CheckMark, GearIcon)

### Not verified
- No compiler available in the sandbox: build once with the GitHub Actions workflow and send the log if anything fails

### Next ideas
- MMS attachments, message search inside a chat, mute conversation, blocked numbers

## Task: Full notifications support (feature 11 of the 1-11 roadmap)

### Done
- Notifier.kt rewritten: per-settings channels (sound / vibration / pop-up combinations), separate channel for verification codes and for delivery problems
- MessagingStyle per conversation with message history, sender Person, SIM label, private lock screen visibility
- Actions: Reply (inline RemoteInput, default SMS app only), Mark as Read, Copy Code
- Group summary notification when 2+ conversations are active
- Notifications are cleared when a chat opens, when a thread is marked read, and when a conversation becomes read elsewhere (Notifier.syncRead)
- Receivers.kt: ReplyReceiver, MarkReadReceiver, NotificationDismissedReceiver (registered in AndroidManifest.xml, VIBRATE permission added)
- Prefs.kt: notifSound, notifVibrate, notifHeadsUp, notifLockScreen, notifQuickReply, notifCopyCode, notifGroup, notifChannelVersion
- Settings screen: Lock Screen, Pop Up, Sound, Vibration, Quick Reply, Copy Code Button, Group toggles; "Sound & System Settings" opens the channel settings page

### Files touched
- Notifier.kt, Receivers.kt, AndroidManifest.xml, data/Prefs.kt, MessagesViewModel.kt, MainActivity.kt, ui/SettingsScreen.kt

### Not verified
- No compiler available in the sandbox: build with the GitHub Actions workflow and send the log if anything fails

### Remaining roadmap (in order)
1 spam filter + unknown senders + report/block, 2 scheduled messages, 3 drafts label in red, 4 location sticker, 6 OTP quick reply (done inside notifications), 7 encrypted local backup/restore, 8 default-app setup screen (fix it staying stuck after becoming default), 9 business message tabs, 10 Arabic/English RTL with language picker

## Task: Full Arabic/English with RTL and in-app language choice (feature 10)

### Done
- All UI text moved to resources: res/values/strings.xml (English) and res/values-ar/strings.xml (Arabic); notifications, toasts, dates and settings all localized
- Lang.kt: wraps a Context with the saved language (Prefs.language: system / en / ar), sets Locale default and layout direction; used by MainActivity.attachBaseContext, Notifier, Receivers, MessagesViewModel
- Language picker section in Settings (System, English, Arabic); selecting recreates the activity
- AndroidManifest supportsRtl=true
- RTL fixes: page slide direction and edge swipe back (rtlSign() in ui/Components.kt), bubble tail mirrored in BubbleShape, chevrons mirrored in ui/Icons.kt, timestamp swipe reveal direction, shadow gradient direction
- DateFmt takes a Context for Today / Yesterday / "at" and uses locale date formats
- Notification channels get localized names on every ensureChannels call
- Version bumped to 1.9 (versionCode 7)

### Files touched
- Lang.kt (new), res/values/strings.xml, res/values-ar/strings.xml, AndroidManifest.xml, app/build.gradle.kts
- MainActivity.kt, MessagesViewModel.kt, Notifier.kt, Receivers.kt, data/Prefs.kt
- ui/ChatScreen.kt, ConversationsScreen.kt, NewMessageScreen.kt, SettingsScreen.kt, SimUi.kt, Components.kt, Icons.kt, DateFmt.kt

### Not verified
- No compiler in the sandbox: build with the GitHub Actions workflow and send the log if anything fails
- CrashScreen text in MainActivity is intentionally left in English

### Remaining roadmap (requested order)
8 default-app setup screen (fix it staying stuck after becoming default), then 3 drafts red label, then 7 encrypted backup/restore, then 1, 2, 4, 9

## Task: default-app setup fix (8), drafts (3), encrypted backup/restore (7)

### Done
- 8: SmsRepository.isDefault() now also checks RoleManager.isRoleHeld(ROLE_SMS); MessagesViewModel.recheckRole() re-reads default/permission state immediately and again after 0.5s and 2s (called from onResume and from the role result); setup screen is dismissed automatically once permission and default are both granted (MainActivity LaunchedEffect)
- 3: drafts show the red "Draft:" label in the list (already present) and now also appear for numbers with no conversation yet (Prefs.allDrafts, synthetic Conversation with negative thread id, opened by address); deleting a conversation removes its draft; draft time and address stored in Prefs
- 7: data/Backup.kt exports text messages (inbox, sent, failed) to a file encrypted with AES-256-GCM, key from PBKDF2-HMAC-SHA256 (150000 iterations), gzip JSON; restore skips messages already present (address, date, type); ui/BackupUi.kt password dialog; Settings "Backup" section; file chosen through the system file picker (.imsgbak)
- MMS/photo messages are not part of the backup
- Version bumped to 2.0 (versionCode 8)

### Files touched
- data/SmsRepository.kt, data/Prefs.kt, data/Backup.kt (new), MessagesViewModel.kt, MainActivity.kt, ui/SettingsScreen.kt, ui/BackupUi.kt (new), res strings (en, ar)

### Not verified
- No compiler in the sandbox: build with GitHub Actions and send the log if anything fails

### Remaining roadmap
1 spam filter + unknown senders section + report/block, 2 scheduled messages with SIM choice, 4 location sticker, 9 business message tabs (Transactions, Offers)

### Build fix (after first CI run)
- Cause: ui/*.kt were missing the imports androidx.compose.ui.res.stringResource and app.ios.messages.R (import insertion regex had not matched the first line of each file). Imports added to SimUi, NewMessageScreen, ChatScreen, SettingsScreen, Components, ConversationsScreen. All other CI errors (Text overload ambiguity, append ambiguity) were follow-ups of the same cause.
- Lesson: when adding stringResource to a file, always verify both imports exist.

## Task: Frosted-glass menus, blur overlays, smoother menu motion (v2.1)

### Done
- ui/Glass.kt (new): Backdrop snapshot via PixelCopy (half resolution), GlassBackdrop (RenderEffect blur + saturation, aligned to screen position), GlassFill (blur + translucent tint, solid fallback until the snapshot is ready), ScreenPos tracker for popup/dialog windows
- IosMenu: frosted card, transform origin computed from the real anchor position, content fades in after the card expands
- ContextOverlay: whole screen blurs + dims behind the preview, menu card is frosted glass
- IosConfirm: frosted alert with iOS pop-in scale
- IosMenuItem/IosMenuRow: translucent press highlight (old gray was invisible in dark mode), hairline under items removed
- Version 2.1 (versionCode 9)

### Files touched
- ui/Glass.kt (new), ui/Components.kt, app/build.gradle.kts

### Not verified
- No compiler in the sandbox: build with the GitHub Actions workflow and send the log if anything fails

## Task: Appearance setting (Automatic / Light / Dark) + more visible glass blur (v2.2)

### Done
- Settings: Appearance section (Automatic, Light, Dark); Prefs.appearance; LocalDark in ui/Theme.kt replaces isSystemInDarkTheme in Components.kt; status/navigation bar icons follow the choice
- Glass: lighter tint, smaller blur radius, higher saturation so the content behind menus is visible

### Not verified
- No compiler in the sandbox: build with the GitHub Actions workflow and send the log if anything fails

## Task: Live translucent bars (v2.3)

### Done
- ui/Glass.kt: LiveBlur + Modifier.liveBlurEdge (RenderNode record of the list, blurred strip redrawn under the bar every frame)
- ConversationsScreen: top bar blurs live as the list scrolls under it (fades in with the large title collapse); selection bottom bar blurs too
- ChatScreen: header is a live blur with a soft fade-out edge instead of the solid gradient

### Not verified
- No compiler in the sandbox: build with the GitHub Actions workflow and send the log if anything fails

## Task: iOS details everywhere (v2.4)

### Done
- Icons.kt: GearIcon redrawn as the SF Symbol "gearshape" (8 rounded teeth + hole)
- Components.kt: IosAlert / IosField / AlertAction (UIAlertController look, stacked buttons for 3+ actions); IosConfirm uses it; all Material AlertDialogs removed (wrong-SIM prompt, SIM rename, backup password)
- GlassCircle is now translucent (works with the blurred header behind it)
- Settings: header is a live blur that fades in while scrolling; version row shows the real versionName
- Chat: bottom input area is a live blur with soft fade instead of a solid gradient; emoji panel has its own background

### Not verified
- No compiler in the sandbox: build with the GitHub Actions workflow and send the log if anything fails

## Task: Plus menu + stronger chat blur (v2.5)

### Done
- Chat "+" button opens a glass menu (Camera, Photos, Emoji, Contact, Location); plus icon rotates 45 degrees with a spring while the menu is open
- Camera uses TakePicture + FileProvider (cache/mms); Contact inserts "name: number"; Location inserts a maps link (last known location, permission requested on demand)
- Chat header/input blur stronger: radius 32dp, tint 0.62, mask stays opaque for most of the bar and only fades at the edge
- New icons in Icons.kt: CameraIcon, PhotoIcon, ContactIcon, LocationIcon; IosMenuRow(checkColumn = false)
- Manifest: ACCESS_COARSE_LOCATION / ACCESS_FINE_LOCATION

### Not verified
- Build with the GitHub Actions workflow and send the log if anything fails

## Task: Contact card from the chat header (v2.6)

### Done
- Tapping the name/avatar in the chat header opens a glass contact card: big avatar, name, number, and three large tiles (Call, View Contact or Add Contact, Copy Number)
- View Contact is shown when the number is saved (PhoneLookup lookup URI), Add Contact otherwise
- Header name 16sp bold, avatar 58dp, larger rounded tap area; barH 128dp
- IosAlert got an optional header slot; new PhoneIcon and CopyIcon

### Not verified
- Build with the GitHub Actions workflow and send the log if anything fails

## Task: iOS features, crash hardening, smoothness (v2.7)

### Done
- Mark as Unread (long-press menu on a read conversation): SmsRepository.markUnread, MessagesViewModel.markUnread
- Block / Unblock this Caller in the contact card: data/Blocking.kt (BlockedNumberContract); SmsReceiver drops messages from blocked numbers
- Chat: scroll-to-bottom glass button, SMS length counter (chars and segments, GSM-7 vs Unicode), haptic feedback when menus open
- Crash hardening: every BroadcastReceiver body runs inside try/catch; live blur disables itself after any render error (LiveBlur.broken) and falls back to plain content; backdrop capture catches OOM and any failure
- Conversation list items use contentType

### Not verified
- Build with the GitHub Actions workflow and send the log if anything fails

## Task: Recently Deleted (v2.8)

### Done
- data/TrashStore.kt: before a conversation is deleted its SMS are copied to filesDir/trash.json and kept 30 days; list / put / restore / purge; expired items are dropped automatically
- MessagesViewModel: trash flow, loadTrash, restoreTrash, purgeTrash; deleteThreads saves to the trash first (default SMS app only)
- Conversations: "Recently Deleted" entry in the filter menu (trash icon); list shows deleted conversations with days left; tap opens Recover / Delete Permanently / Cancel
- MMS-only threads cannot be recovered (no copy is stored for them)

### Not verified
- Build with the GitHub Actions workflow and send the log if anything fails

## Task: More iOS features (v2.9)

### Done
- Search in Conversation: tile row in the contact card opens a bottom search bar; matches get a yellow outline (current match stronger), up/down arrows jump between matches, "n of m" counter
- Settings: Text Size (5 steps, scales fontScale for the whole app), Keep Messages (Forever / 1 Year / 30 Days with confirmation; applied at launch and when changed, default SMS app only), Blocked Contacts list with Unblock (shown only when something is blocked)
- Prefs: textScale, keepDays; SmsRepository.deleteOlderThan; Blocking.list

### Not verified
- Build with the GitHub Actions workflow and send the log if anything fails


## Task: v3.0 / 3.1 - notification look, sounds, local protection, Android 16

### Done
- In-app banner (ui/InAppBanner.kt) with colour, transparency and 5 entrance animations; settings in ui/NotifLookSettings.kt
- Sounds.kt: bundled send/receive tones (res/raw, synthesized placeholders - replace with your own files), tone choice, vibration patterns, Do Not Disturb override, battery shortcut
- Screenshot blocking (FLAG_SECURE, default on), security/Integrity.kt (pinned signing cert, debugger/hook checks), security/SecureStore.kt (Keystore encryption for drafts + trash), no INTERNET permission, no cloud backup
- Backup v2 (600k PBKDF2 iterations, old backups still readable); blocked numbers now also filter MMS
- compileSdk/targetSdk 36, AGP 8.9.1, Gradle 8.11.1 (workflow updated)

### Not verified
- No compiler in the sandbox: build with the GitHub Actions workflow and send the log if anything fails

### v3.2
- Banner now lives in its own window with its own blur (Window.setBackgroundBlurRadius, setting "Banner Blur"), independent from the app glass/backdrop blur; preview plays the real banner

## Task: performance, smoothness, threading, security pass (v3.3)

### Done
- Threading: SmsReceiver / MmsReceiver now work inside goAsync() on a worker thread (provider insert no longer blocks the main thread); Integrity re-check on resume runs on Dispatchers.Default and the /proc/self/maps scan is throttled to once a minute; chat items (date headers, grouping) are built on Dispatchers.Default; Sounds uses a preloaded SoundPool instead of MediaPlayer.create() on every send.
- Data: SmsRepository.withSubIds no longer scans the whole SMS table on every refresh (per-thread cache keyed by newest-message date); contact-name cache no longer holds its lock during provider queries; openChat loads that conversation's messages first, then refreshes the list.
- Keystore: SecureStore caches the SecretKey (KeyStore.getInstance + load ran on every draft read/write).
- Dates: DateFmt keeps one formatter per locale (the list built one per row).
- Blur: liveBlurEdge blurs at 1/4 resolution and reuses its Paint (about 16x fewer pixels per frame).
- Navigation: system back gesture drives the same interactive push/pop as the edge swipe (PredictiveBackHandler, chat + settings); push animation starts after at most ~110 ms + 1 frame instead of up to ~310 ms; dim layers use drawBehind (no graphics layer); notification-preference flows are collected only inside the settings overlay.
- Build: Compose stability config (compose_compiler_config.conf), baseline-prof.txt, lifecycle-runtime-ktx, LeakCanary (debug only). versionCode 23 / 3.3.

### Not verified
- No compiler in the sandbox: build with the GitHub Actions workflow and send the log if anything fails. If the build complains about baseline-prof.txt, delete that file first.

### Known, not changed
- app/release.jks and its fallback passwords are in the repository (see README / reply): move them to GitHub Secrets.

## Task: Launcher icon replaced with the supplied image (pixel-exact artwork)

### Done
- Squircle extracted from the 2048px source (241406.jpg) with a clean alpha edge, no shadow, no recolor, no redraw
- res/drawable/ic_launcher_foreground.png: 1024x1024 transparent canvas, squircle scaled to about 58dp wide so every pixel stays inside a 35dp radius (inside the 72dp visible circle of any launcher mask)
- res/mipmap-anydpi-v26/ic_launcher.xml and ic_launcher_round.xml: background is transparent, foreground used directly (old 16% inset removed, margin is baked into the png)
- res/drawable/ic_launcher_background.png removed (no longer referenced)
- Legacy mipmap-*/ic_launcher*.png regenerated from the same source with LANCZOS (unused at minSdk 31, kept consistent)
- Monochrome layer unchanged (ic_launcher_monochrome.xml)

### Notes
- To change the icon size, only the scale of the squircle inside ic_launcher_foreground.png needs to change
- No compiler in the sandbox: icon change not built, resources only

### Files touched
- app/src/main/res/drawable/ic_launcher_foreground.png
- app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml, ic_launcher_round.xml
- app/src/main/res/mipmap-*/ic_launcher.png, ic_launcher_round.png

### Fix: black edges around the launcher icon
- Cause: transparent adaptive background is drawn black by the launcher
- res/drawable/ic_launcher_background.png recreated: vertical green gradient sampled from the squircle edge colors
- ic_launcher_foreground.png: squircle now about 73dp wide (fills the 72dp visible area like other icons), bubble stays inside the safe zone
- mipmap-anydpi-v26/ic_launcher.xml, ic_launcher_round.xml: background points to @drawable/ic_launcher_background again

## Task: contact sync, system notifications, fixes (v3.4)

### Done
- Live contact sync: MessagesViewModel observes the whole Contacts Provider (works with Google / Samsung / Xiaomi contacts apps), re-checks an address-book fingerprint on every resume, clears the name cache and reloads names, photos and the chat header. Name cache entries now expire (10 min hits, 1 min misses); a new contact is found even when the system PhoneLookup fails (last-9-digits fallback index).
- Contact photos in the list, chat header, contact sheet and New Message (ui/Components.kt Avatar + PhotoCache).
- Notification customization removed completely (banner, overlay, colors, blur, animations, tone/vibration/DND/SIM-label switches, all related prefs and strings, SYSTEM_ALERT_WINDOW and ACCESS_NOTIFICATION_POLICY permissions). Notifier now creates normal channels and Settings has one row that opens Android's notification page. Notification actions (Reply, Mark as Read, Copy Code), grouping and a hidden-content lock-screen version are always on.
- Same number written two ways (+966 / 05...) now shares one notification id and history.
- HeadlessSmsSendService now really sends "respond via message" texts (was an empty stub).
- Drafts are read in one pass during refresh; MMS snippets and crash screen are localized.

### Not verified
- No compiler in the sandbox: build once with the GitHub Actions workflow and send the log if anything fails.

## Task: iPhone-style chat header and glass effects borrowed from the phone project

### Done
- ui/Glass.kt: added Modifier.springPress (bouncy press spring from the phone app) and Modifier.liquidGlass (soft shadow, translucent fill, bright top edge gradient)
- ui/Components.kt: GlassCircle now uses liquidGlass + springPress; new GlassPill capsule; Avatar takes an optional tint gradient
- ui/ChatScreen.kt: header matches the iOS 26 reference (glass back circle, larger tinted avatar, contact name inside a glass pill with chevron); every time header now shows "Text Message • SMS" above the date
- ui/ConversationsScreen.kt: Edit button in a glass pill, settings and compose buttons in glass circles, header height 52dp
- res/values and values-ar: new string sms_service

### Decision
- The phone project's BlurView module was not copied: it needs a separate BlurTarget view wrapping the content, which cannot work for bars that live inside the same Compose tree. The existing RenderNode live blur (liveBlurEdges) already covers bars; only the pure Compose effects were ported

### Not verified
- No compiler in the sandbox: build with the GitHub Actions workflow and send the log if anything fails

### Next ideas
- Apply GlassPill/GlassCircle to the new message and settings screens, tune avatar tint colors against the reference, optional ScreenBlur dimming behind context menus

## Task: SIM chip overlap, live blur bars, notification hang

### Done
- ui/ChatScreen.kt: the SIM chip (e.g. Syriatel) now sits inside the header column right under the name pill instead of being pinned to the bottom of the bar, so it can no longer touch the pill or the "Text Message • SMS" line; header height 142dp
- data/Prefs.kt: smoothMode now uses a new key (smooth_mode2) with default false, so the list and chat bars use real live blur by default (the old default was static bars); the switch in Privacy settings still turns cheap bars back on
- Notifier.kt (notification hang hardening):
  - dynamic shortcut is pushed at most once per conversation every 30 minutes (it used to be pushed on every message, an expensive system call)
  - contact photo is decoded at most about 128px before becoming the notification icon (was full size)
  - group summary count comes from the in-memory history instead of reading all active notifications; syncRead skips the active-notification binder call when nothing was posted
  - notify and summary calls wrapped in try/catch with a log line

### Not verified
- No compiler and no device in the sandbox: the notification hang cause is not confirmed. If it still happens, send a logcat taken while a message arrives (filter: IosMessages, Messages, ANR)

### Next ideas
- If the hang remains, test with Settings > smooth mode on to rule out live blur cost, and check the receiver thread with StrictMode

## Task: pinned blurred top bar in the conversation list, prettier buttons and capsules

### Done
- ui/ConversationsScreen.kt: the bar now fades in after about 10dp of scroll (barAmount) instead of over the full title height, so the large title never shows half-blurred behind the buttons; the large title fades out as it scrolls, the small centered title fades in later, the hairline follows the bar
- Edit capsule is 42dp tall, settings and compose circles are 42dp, spacing evened out
- ui/Glass.kt: glassFill(dark) with brighter fills, stronger top sheen, soft bottom shade and a brighter rim for liquidGlass
- ui/Components.kt: GlassCircle and GlassPill use glassFill

### Not verified
- No compiler or device in the sandbox: build with the GitHub Actions workflow and send the log if anything fails

### Next ideas
- Restyle the search field and pinned avatar tiles as glass, add a soft fade under the bar (fade = true) if a harder edge is not wanted
