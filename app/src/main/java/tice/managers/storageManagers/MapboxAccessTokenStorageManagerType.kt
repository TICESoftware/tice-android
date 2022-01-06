package tice.managers.storageManagers

interface MapboxAccessTokenStorageManagerType {
    val customToken: String?
    fun requireToken(): String
    fun setCustomToken(token: String)
}
