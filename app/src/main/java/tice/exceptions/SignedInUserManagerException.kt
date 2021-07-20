package tice.exceptions

sealed class SignedInUserManagerException : Exception() {
    object NotSignedIn : SignedInUserManagerException()
}
