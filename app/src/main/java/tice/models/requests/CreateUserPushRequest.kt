package tice.models.requests

import kotlinx.serialization.Serializable
import tice.models.DeviceId
import tice.models.Platform
import tice.models.UserPublicKeys
import tice.utility.serializer.UserPublicKeysSerializer

@Serializable
data class CreateUserPushRequest(
    @Serializable(with = UserPublicKeysSerializer::class)
    val publicKeys: UserPublicKeys,
    val platform: Platform,
    val deviceId: DeviceId?,
    val verificationCode: String,
    val publicName: String?
)
