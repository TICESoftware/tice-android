package tice.exceptions

sealed class SignedInUserStorageManagerException : Exception() {
    object NotSignedIn : SignedInUserStorageManagerException()
    object NotSignedInKeyPair : SignedInUserStorageManagerException()
}
