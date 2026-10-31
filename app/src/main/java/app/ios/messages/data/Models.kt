package app.ios.messages.data

data class Conversation(
    val threadId: Long,
    val address: String,
    val name: String,
    val snippet: String,
    val date: Long,
    val unread: Int
)

data class Message(
    val id: Long,
    val body: String,
    val date: Long,
    val mine: Boolean,
    val read: Boolean
)
