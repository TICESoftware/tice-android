package tice.exceptions

sealed class GroupNotificationReceiverException : Exception() {
    object InvalidGroupAction : GroupNotificationReceiverException()
    object GroupNotFound : GroupNotificationReceiverException()
}
