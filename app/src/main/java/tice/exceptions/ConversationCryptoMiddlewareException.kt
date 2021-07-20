package tice.exceptions

sealed class ConversationCryptoMiddlewareException : Exception() {
    object OneTimePrekeyMissingException : ConversationCryptoMiddlewareException()
    object ConversationNotInitializedException : ConversationCryptoMiddlewareException()
}
