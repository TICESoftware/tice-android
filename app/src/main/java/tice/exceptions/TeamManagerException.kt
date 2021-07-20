package tice.exceptions

sealed class TeamManagerException : Exception() {
    object PermissionDenied : TeamManagerException()
    object UserAlreadyMember : TeamManagerException()
    object MeetupRunning : TeamManagerException()
}
