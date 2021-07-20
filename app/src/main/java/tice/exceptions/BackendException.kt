package tice.exceptions

sealed class BackendException : Exception() {
    object NotModified : BackendException()
    object Unauthorized : BackendException()
    object GroupOutdated : BackendException()
    object NotFound : BackendException()
}
