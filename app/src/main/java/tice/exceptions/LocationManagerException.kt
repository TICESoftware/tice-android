package tice.exceptions

sealed class LocationManagerException : Exception() {
    object UnknownUser : LocationManagerException()
}
