package tice.managers.storageManagers

interface StorageLockerType {
    fun store(storageKey: StorageKey, value: String)
    fun load(storageKey: StorageKey): String?
    fun remove(storageKey: StorageKey)

    enum class StorageKey(val value: String) {
        DEVICE_ID("deviceId"),
        GROUPS("groups"),
        SIGNED_IN_USER("signedInUser"),
        DATABASE_KEY_ENCRYPTION_IV("databaseKeyEncryptionIV"),
        ENCRYPTED_DATABASE_KEY("encryptedDatabaseKey"),
        PLAINTEXT_DATABASE_KEY("plaintextDatabaseKey"),
        CRYPTO("crypto");
    }
}
