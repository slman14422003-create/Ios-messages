# iOS-Messages

SMS/messaging app for **Android 12+ (API 31)** written in **Kotlin + Jetpack Compose**, styled after iOS Messages.
تطبيق رسائل SMS للأندرويد 12+ بتصميم واجهة iOS (Kotlin + Compose).

## Features
- Conversation list: large collapsing title, search field, unread blue dot, swipe-to-delete, contact names/avatars
- Chat: iOS bubbles with tails, grouped bubbles, time separators, "Sent as Text Message", long-press Copy/Delete
- New message screen ("To:" field), translucent nav bar, iOS light/dark colors
- Can be set as the **default SMS app** (receive, send, notifications, delete)

## Build
Open in Android Studio (Koala+), or push to GitHub: the **Build APK** workflow produces `app-debug.apk` as an artifact.
Local: `gradle assembleDebug` (Gradle 8.9, JDK 17).

## Notes
- SMS only (no MMS/attachments, no iMessage). Bubbles are green like iOS SMS — change `SentBubble` in `ui/Theme.kt` for blue.
- SF Pro is not redistributable, so the system font is used.
- Design references: UX case study "Redesigning iMessage" (uxdesign.cc), Apple HIG, iOS 26 Messages.
