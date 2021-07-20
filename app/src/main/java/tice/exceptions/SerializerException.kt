package tice.exceptions

sealed class SerializerException : Exception() {
    object InvalidPayloadType : SerializerException()
}
