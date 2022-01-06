package tice.exceptions

sealed class MapboxAccessTokenMissingException : Exception() {
    object TokenMissing : MapboxAccessTokenMissingException()
}
