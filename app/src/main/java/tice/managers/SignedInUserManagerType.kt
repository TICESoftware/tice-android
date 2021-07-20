package tice.managers

import tice.managers.group.GroupManagerType
import tice.models.SignedInUser
import java.lang.ref.WeakReference

interface SignedInUserManagerType {

    val signedInUser: SignedInUser
    var groupManagerDelegate: WeakReference<GroupManagerType>

    fun signedIn(): Boolean
    suspend fun storeSignedInUser(newSignedInUser: SignedInUser)
    suspend fun changeSignedInUserName(publicName: String)
    suspend fun deleteSignedInUser()
    fun setup()
}
