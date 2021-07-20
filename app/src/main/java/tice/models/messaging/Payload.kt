package tice.models.messaging

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tice.models.*
import tice.utility.serializer.DataSerializer
import tice.utility.serializer.UUIDSerializer

sealed class Payload {
    @Serializable
    enum class PayloadType {

        @SerialName("verificationMessage/v1")
        VerificationMessageV1,

        @SerialName("encryptedPayloadContainer/v1")
        EncryptedPayloadContainerV1,

        @SerialName("groupInvitation/v1")
        GroupInvitationV1,

        @SerialName("groupUpdate/v1")
        GroupUpdateV1,

        @SerialName("locationUpdate/v2")
        LocationUpdateV2,

        @SerialName("fewOneTimePrekeys/v1")
        FewOneTimePrekeysV1,

        @SerialName("userUpdate/v1")
        UserUpdateV1,

        @SerialName("resetConversation/v1")
        ResetConversationV1,

        @SerialName("chatMessage/v1")
        ChatMessageV1,

        @SerialName("locationSharingUpdate/v1")
        LocationSharingUpdateV1
    }
}

@Serializable
data class VerificationMessage(
    val verificationCode: VerificationCode
) : Payload()

@Serializable
data class EncryptedPayloadContainer(
    @Serializable(with = DataSerializer::class) val ciphertext: Ciphertext,
    @Serializable(with = DataSerializer::class) val encryptedKey: Ciphertext
) : Payload()

@Serializable
data class GroupInvitation(
    @Serializable(with = UUIDSerializer::class)
    val groupId: GroupId
) : Payload()

@Serializable
data class GroupUpdate(
    @Serializable(with = UUIDSerializer::class)
    val groupId: GroupId,
    val action: Action
) : Payload() {
    @Serializable
    enum class Action {
        @SerialName("groupDeleted")
        GROUP_DELETED,

        @SerialName("memberAdded")
        MEMBER_ADDED,

        @SerialName("memberUpdated")
        MEMBER_UPDATED,

        @SerialName("memberDeleted")
        MEMBER_DELETED,

        @SerialName("settingsUpdated")
        SETTINGS_UPDATED,

        @SerialName("childGroupCreated")
        CHILD_GROUP_CREATED,

        @SerialName("childGroupDeleted")
        CHILD_GROUP_DELETED
    }
}

@Serializable
data class LocationUpdateV2(
    val location: Location,
    @Serializable(with = UUIDSerializer::class)
    val groupId: GroupId
) : Payload()

@Serializable
data class FewOneTimePrekeys(
    val remaining: Int
) : Payload()

@Serializable
data class UserUpdate(
    @Serializable(with = UUIDSerializer::class)
    val userId: UserId
) : Payload()

@Serializable
object ResetConversation : Payload()

@Serializable
data class ChatMessage(
    @Serializable(with = UUIDSerializer::class)
    val groupId: GroupId,
    val text: String? = null,
    @Serializable(with = DataSerializer::class)
    val imageData: Data? = null
) : Payload()

@Serializable
data class LocationSharingUpdate(
    @Serializable(with = UUIDSerializer::class)
    val groupId: GroupId,
    val sharingEnabled: Boolean
) : Payload()
