package app.ios.messages

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

/** What the in-app banner needs to draw one incoming message. */
data class BannerEvent(
    val id: Long,
    val address: String,
    val title: String,
    val body: String,
    val simLabel: String?
)

/** Process-wide, memory-only state shared by the activity, the view-model and the notifier. */
object AppState {
    /** True between onResume and onPause of MainActivity. */
    @Volatile var foreground = false

    /** Prefs.key(address) of the conversation currently on screen, or null. */
    @Volatile var openKey: String? = null

    private var counter = 0L
    @Synchronized fun nextId(): Long = ++counter

    val banners = MutableSharedFlow<BannerEvent>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
}
