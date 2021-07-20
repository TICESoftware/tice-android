package tice.exceptions

sealed class HTTPRequesterException : Exception() {
    data class EmptyErrorResponse(val statusCode: Int) : HTTPRequesterException()
    object EmptyResponse : HTTPRequesterException()
}
