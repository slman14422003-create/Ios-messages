package app.ios.messages

/** Process-wide, memory-only state shared by the activity, the view-model and the notifier. */
object AppState {
    /** True between onResume and onPause of MainActivity. */
    @Volatile var foreground = false

    /** Prefs.key(address) of the conversation currently on screen, or null. */
    @Volatile var openKey: String? = null
}
