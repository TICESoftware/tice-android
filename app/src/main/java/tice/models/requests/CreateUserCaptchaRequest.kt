package tice.models.requests

import kotlinx.serialization.Serializable
import tice.models.Platform
import tice.models.UserPublicKeys
import tice.utility.serializer.UserPublicKeysSerializer

@Serializable
data class CreateUserCaptchaRequest(
    @Serializable(with = UserPublicKeysSerializer::class)
    val publicKeys: UserPublicKeys,
    val platform: Platform,
    val verificationCode: String,
    val publicName: String?
)
