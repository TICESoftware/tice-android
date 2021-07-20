package tice.managers.storageManagers

import tice.models.KeyPair
import tice.dagger.scopes.AppScope
import tice.exceptions.SignedInUserStorageManagerException
import tice.models.SignedInUser
import tice.models.User
import java.util.*
import javax.inject.Inject

@AppScope
class SignedInUserStorageManager @Inject constructor(
    private val storageLocker: StorageLockerType,
    private val cryptoStorageManager: CryptoStorageManagerType,
    private val userStorageManager: UserStorageManagerType
) : SignedInUserStorageManagerType {

    override suspend fun storeSignedInUser(signedInUser: SignedInUser) {
        val userIdString = signedInUser.userId.toString()
        val userRepresentation = User(
            signedInUser.userId,
            signedInUser.publicSigningKey,
            signedInUser.publicName
        )

        userStorageManager.store(userRepresentation)
        cryptoStorageManager.saveSigningKeyPair(KeyPair(signedInUser.privateSigningKey, signedInUser.publicSigningKey))
        storageLocker.store(StorageLockerType.StorageKey.SIGNED_IN_USER, userIdString)
    }

    override suspend fun loadSignedInUser(): SignedInUser {
        val signedInUserIdString = storageLocker.load(StorageLockerType.StorageKey.SIGNED_IN_USER)
            ?: throw SignedInUserStorageManagerException.NotSignedIn
        val signedInUserId = UUID.fromString(signedInUserIdString)

        val userRepresentation = userStorageManager.loadUser(signedInUserId)
            ?: throw SignedInUserStorageManagerException.NotSignedIn

        val keyPair = cryptoStorageManager.loadSigningKeyPair()
            ?: throw SignedInUserStorageManagerException.NotSignedInKeyPair

        return SignedInUser(
            userRepresentation.userId,
            userRepresentation.publicName,
            keyPair.publicKey,
            keyPair.privateKey
        )
    }

    override fun deleteSignedInUser() {
        storageLocker.remove(StorageLockerType.StorageKey.SIGNED_IN_USER)
    }
}
