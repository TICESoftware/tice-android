package tice.exceptions

sealed class CryptoStorageManagerException : Exception() {
    object NoDataStored : CryptoStorageManagerException()
    object InvalidOneTimePrekey : CryptoStorageManagerException()
}
