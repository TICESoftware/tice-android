package tice.managers.storageManagers

interface VersionCodeStorageManagerType {
    val outdatedVersion: Boolean
    fun storeVersionCode(versionCode: Int)
    fun getStoredVersionCode(): Int
}
