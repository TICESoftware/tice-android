package tice.exceptions

sealed class ConversationManagerException : Exception() {
    object ConversationNotInitialized : ConversationManagerException()
    object CertificateMissing : ConversationManagerException()
    object InvalidConversation : ConversationManagerException()
    object ConversationResynced : ConversationManagerException()
}
