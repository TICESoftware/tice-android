package tice.exceptions

sealed class ChatMessageInterfaceException : Exception() {
    object NotSupportedMessageType : ChatMessageInterfaceException()
}
