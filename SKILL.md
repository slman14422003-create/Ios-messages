---
name: ios-messages-progress
description: Progress log for the Ios-messages Android SMS app (app.ios.messages). Read before continuing any task on this project.
---

# Ios-messages progress

## Done
- Recently Deleted: "Recover All" and "Delete All" actions (with confirmation) via TrashStore.purgeAll/restoreAll, MessagesViewModel, ConversationsScreen.
- Drawing tool in chat: DrawingSheet.kt, "Draw" entry in the plus menu, result attached as PNG through the existing attach flow.
- Auto-resend of failed SMS (last 24h) when cell service returns: data/AutoResend.kt, started in MessagesApp, toggle in notifications settings (Prefs.autoResend).
- Conversation search now matches phone numbers by digits.

## Not applicable / skipped
- No voice-recording button exists in the composer, so the hide option is not needed.
- SMS has no Tapbacks, so notification grouping of reactions does not apply.
- Not done: background MMS sending, image thumbnails after offloading, nickname search in conversation list.
- Not compiled in the sandbox: build in CI and fix any compile errors first.
