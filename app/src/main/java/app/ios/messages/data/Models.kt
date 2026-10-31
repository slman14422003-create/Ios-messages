package app.ios.messages.data

data class Conversation(
    val threadId: Long,
    val address: String,
    val name: String,
    val snippet: String,
    val date: Long,
    val unread: Int,
    val known: Boolean = true
)

data class Message(
    val id: Long,
    val body: String,
    val date: Long,
    val mine: Boolean,
    val read: Boolean
)

data class Contact(
    val id: Long,
    val name: String,
    val number: String
)
