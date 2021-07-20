package tice.managers.storageManagers

import tice.models.SignedInUser

interface SignedInUserStorageManagerType {

    suspend fun storeSignedInUser(signedInUser: SignedInUser)
    suspend fun loadSignedInUser(): SignedInUser
    fun deleteSignedInUser()
}
