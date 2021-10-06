package tice.backend

import tice.models.*
import tice.models.messaging.MessagePriority
import tice.models.messaging.Recipient
import tice.models.requests.RenewCertificateResponse
import tice.models.responses.*

interface BackendType {

    suspend fun verify(deviceId: DeviceId, platform: Platform)

    suspend fun createUserUsingPush(
        publicKeys: UserPublicKeys,
        platform: Platform,
        deviceId: String?,
        verificationCode: VerificationCode,
        publicName: String?
    ): CreateUserResponse

    suspend fun createUserUsingCaptcha(
        publicKeys: UserPublicKeys,
        platform: Platform,
        verificationCode: VerificationCode,
        publicName: String?
    ): CreateUserResponse

    suspend fun updateUser(
        userId: UserId,
        publicKeys: UserPublicKeys?,
        deviceId: DeviceId?,
        verificationCode: VerificationCode?,
        publicName: String?
    )

    suspend fun deleteUser(userId: UserId)

    suspend fun getUser(userId: UserId): GetUserResponse

    suspend fun getUserKey(userId: UserId): GetUserPublicKeysResponse

    suspend fun createGroup(
        groupId: GroupId,
        groupType: GroupType,
        joinMode: JoinMode,
        permissionMode: PermissionMode,
        selfSignedAdminCertificate: Certificate,
        encryptedSettings: Ciphertext,
        encryptedInternalSettings: Ciphertext,
        parent: ParentGroup? = null
    ): CreateGroupResponse

    suspend fun getGroupInformation(
        groupId: GroupId,
        groupTag: GroupTag?
    ): GroupInformationResponse

    suspend fun getGroupInternals(
        groupId: GroupId,
        serverSignedMembershipCertificate: Certificate,
        groupTag: GroupTag?
    ): GroupInternalsResponse

    suspend fun joinGroup(
        groupId: GroupId,
        groupTag: GroupTag,
        selfSignedMembershipCertificate: Certificate,
        serverSignedAdminCertificate: Certificate? = null,
        adminSignedMembershipCertificate: Certificate? = null
    ): JoinGroupResponse

    suspend fun addGroupMember(
        groupId: GroupId,
        userId: UserId,
        encryptedMembershipCertificate: Ciphertext,
        serverSignedMembershipCertificate: Certificate,
        newTokenKey: SecretKey,
        groupTag: GroupTag,
        notificationRecipients: List<NotificationRecipient>
    ): UpdatedETagResponse

    suspend fun updateGroupMember(
        groupId: GroupId,
        userId: UserId,
        encryptedMembership: Ciphertext,
        serverSignedMembershipCertificate: Certificate,
        tokenKey: SecretKey,
        groupTag: GroupTag,
        notificationRecipients: List<NotificationRecipient>
    ): UpdatedETagResponse

    suspend fun deleteGroupMember(
        groupId: GroupId,
        groupTag: GroupTag,
        tokenKey: SecretKey,
        userId: UserId,
        userServerSignedMembershipCertificate: Certificate,
        ownServerSignedMembershipCertificate: Certificate,
        notificationRecipients: List<NotificationRecipient>
    ): UpdatedETagResponse

    suspend fun deleteGroup(
        groupId: GroupId,
        serverSignedMembershipCertificate: Certificate,
        groupTag: GroupTag,
        notificationRecipients: List<NotificationRecipient>
    )

    suspend fun updateGroupSettings(
        groupId: GroupId,
        encryptedSettings: Ciphertext,
        serverSignedMembershipCertificate: Certificate,
        groupTag: GroupTag,
        notificationRecipients: List<NotificationRecipient>
    ): UpdatedETagResponse

    suspend fun updateGroupInternalSettings(
        groupId: GroupId,
        encryptedInternalSettings: Ciphertext,
        serverSignedMembershipCertificate: Certificate,
        groupTag: GroupTag,
        notificationRecipients: List<NotificationRecipient>
    ): UpdatedETagResponse

    suspend fun message(
        id: MessageId,
        senderId: UserId,
        timestamp: Long,
        encryptedMessage: Ciphertext,
        serverSignedMembershipCertificate: Certificate,
        recipients: Set<Recipient>,
        priority: MessagePriority,
        collapseId: String?,
        messageTimeToLive: Long
    )

    suspend fun getMessages(): GetMessagesResponse

    suspend fun renewCertificate(certificate: Certificate): RenewCertificateResponse
}
