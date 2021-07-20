@file:UseSerializers(
    UserPublicKeysSerializer::class
)

package tice.models.requests

import tice.models.UserPublicKeys
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import tice.models.DeviceId
import tice.models.VerificationCode
import tice.utility.serializer.UserPublicKeysSerializer

@Serializable
data class UpdateUserRequest(
    val publicKeys: UserPublicKeys? = null,
    val deviceId: DeviceId? = null,
    val verificationCode: VerificationCode? = null,
    val publicName: String? = null
)
