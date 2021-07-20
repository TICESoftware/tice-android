package tice.ui.models

sealed class ShortChatMessages {
    data class TextMessage(
        val senderColor: Int,
        val senderNameShort: String,
        val messageText: String
    ) : ShortChatMessages()

    data class ImageMessage(val name: String) : ShortChatMessages()
}
