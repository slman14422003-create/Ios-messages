package app.ios.messages.data

data class BubbleConfig(
    val theme: String = "green",
    val hue: Float = 210f,
    val received: Int = 0,
    val tails: Boolean = true,
    val chatBg: Int = 0,
    val gradient: Boolean = true
)
