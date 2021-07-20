package tice.exceptions

sealed class DatabaseManagerException : Exception() {
    object DatabaseEncryptionIVMissing : DatabaseManagerException()
}
