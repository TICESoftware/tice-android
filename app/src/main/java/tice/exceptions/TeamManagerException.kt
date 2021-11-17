package tice.exceptions

sealed class TeamManagerException : Exception() {
    object PermissionDenied : TeamManagerException()
    object UserAlreadyMember : TeamManagerException()
    object MemberLimitExceeded : TeamManagerException()
    object MeetupRunning : TeamManagerException()
}
