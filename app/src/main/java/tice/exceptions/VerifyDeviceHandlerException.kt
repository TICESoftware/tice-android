package tice.exceptions

sealed class VerifyDeviceHandlerException : Exception() {
    object VerificationTimedOut : VerifyDeviceHandlerException()
    object UnexpectedPayloadType : VerifyDeviceHandlerException()
}
