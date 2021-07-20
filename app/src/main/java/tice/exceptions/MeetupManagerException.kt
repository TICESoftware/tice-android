package tice.exceptions

sealed class MeetupManagerException : Exception() {
    object ParentKeyMissing : MeetupManagerException()
    object PermissionDenied : MeetupManagerException()
    object MeetupAlreadyRunning : MeetupManagerException()
    object TeamNotFound : MeetupManagerException()
    object MeetupNotFound : MeetupManagerException()
}
