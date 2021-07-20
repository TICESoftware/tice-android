package tice.exceptions

sealed class LocationSharingException : Exception() {
    object UnknownUser : LocationSharingException()
    object UnknownGroup : LocationSharingException()
}
