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
