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
