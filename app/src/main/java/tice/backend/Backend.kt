package tice.backend

import kotlinx.serialization.UnsafeSerializationApi
import okhttp3.Headers
import tice.crypto.AuthManagerType
import tice.dagger.provides.ConfigModule
import tice.dagger.scopes.AppScope
import tice.exceptions.BackendException
import tice.managers.SignedInUserManagerType
import tice.models.*
import tice.models.messaging.MessagePriority
import tice.models.messaging.Recipient
import tice.models.requests.*
import tice.models.responses.*
import tice.utility.serializer.DateSerializer
import tice.utility.toBase64URLSafeString
import java.util.*
import javax.inject.Inject
import javax.inject.Named

@UnsafeSerializationApi
@AppScope
class Backend @Inject constructor(
    @Named(ConfigModule.BASE_URL) private val BASE_URL: String,
    @Named(ConfigModule.VERSION) private val VERSION: String,
    @Named(ConfigModule.VERSION_CODE) private val VERSION_CODE: String,
    @Named(ConfigModule.PLATFORM) private val PLATFORM: String,
    private val httpRequester: HTTPRequesterType,
    private val signedInUserManager: SignedInUserManagerType,
    private val authManagerType: AuthManagerType
) : BackendType {

    enum class HeaderKeys(val value: String) {
        AUTHORIZATION("X-Authorization"),
        GROUP_TAG("X-GroupTag"),
        SERVER_SIGNED_MEMBERSHIP_CERTIFICATE("X-ServerSignedMembershipCertificate"),
        PLATFORM("X-Platform"),
        VERSION("X-Version"),
        BUILD("X-Build");
    }

    private val standardHeaders = Headers.Builder()
        .add(HeaderKeys.PLATFORM.value, PLATFORM)
        .add(HeaderKeys.VERSION.value, VERSION)
        .add(HeaderKeys.BUILD.value, VERSION_CODE)
        .build()

    private val authenticationHeader: Headers
        get() {
            val signedInUser = signedInUserManager.signedInUser
            val certificate = authManagerType.generateAuthHeader(signedInUser.privateSigningKey, signedInUser.userId)

            return Headers.Builder().add(HeaderKeys.AUTHORIZATION.value, certificate).build()
        }

    override suspend fun verify(deviceId: DeviceId, platform: Platform) {
        val body = VerifyRequest(deviceId, Platform.Android)

        return httpRequester.request(
            "$BASE_URL/verify",
            HTTPRequesterType.HTTPMethod.POST,
            standardHeaders,
            body
        )
    }

    override suspend fun createUserUsingPush(
        publicKeys: UserPublicKeys,
        platform: Platform,
        deviceId: String?,
        verificationCode: VerificationCode,
        publicName: String?
    ): CreateUserResponse {
        val body = CreateUserPushRequest(publicKeys, platform, deviceId, verificationCode, publicName)

        return httpRequester.request(
            "$BASE_URL/user/push",
            HTTPRequesterType.HTTPMethod.POST,
            standardHeaders,
            body
        )
    }

    override suspend fun createUserUsingCaptcha(
        publicKeys: UserPublicKeys,
        platform: Platform,
        verificationCode: VerificationCode,
        publicName: String?
    ): CreateUserResponse {
        val body = CreateUserCaptchaRequest(publicKeys, platform, verificationCode, publicName)

        return httpRequester.request(
            "$BASE_URL/user/captcha",
            HTTPRequesterType.HTTPMethod.POST,
            standardHeaders,
            body
        )
    }

    override suspend fun updateUser(
        userId: UserId,
        publicKeys: UserPublicKeys?,
        deviceId: DeviceId?,
        verificationCode: VerificationCode?,
        publicName: String?
    ) {
        val headers = Headers.Builder()
            .addAll(standardHeaders)
            .addAll(authenticationHeader)
            .build()

        val body = UpdateUserRequest(publicKeys, deviceId, verificationCode, publicName)

        return httpRequester.request(
            "$BASE_URL/user/$userId",
            HTTPRequesterType.HTTPMethod.PUT,
            headers,
            body
        )
    }

    override suspend fun deleteUser(userId: UserId) {
        val headers = Headers.Builder()
            .addAll(standardHeaders)
            .addAll(authenticationHeader)
            .build()

        return httpRequester.request(
            "$BASE_URL/user/$userId",
            HTTPRequesterType.HTTPMethod.DELETE,
            headers
        )
    }

    override suspend fun getUser(userId: UserId): GetUserResponse {
        val headers = Headers.Builder()
            .addAll(standardHeaders)
            .addAll(authenticationHeader)
            .build()

        return httpRequester.request(
            "$BASE_URL/user/$userId",
            HTTPRequesterType.HTTPMethod.GET,
            headers
        )
    }

    override suspend fun getUserKey(userId: UserId): GetUserPublicKeysResponse {
        val headers = Headers.Builder()
            .addAll(standardHeaders)
            .addAll(authenticationHeader)
            .build()

        return httpRequester.request(
            "$BASE_URL/user/$userId/keys",
            HTTPRequesterType.HTTPMethod.POST,
            headers
        )
    }

    override suspend fun createGroup(
        groupId: GroupId,
        groupType: GroupType,
        joinMode: JoinMode,
        permissionMode: PermissionMode,
        selfSignedAdminCertificate: Certificate,
        encryptedSettings: Ciphertext,
        encryptedInternalSettings: Ciphertext,
        parent: ParentGroup?
    ): CreateGroupResponse {
        val headers = Headers.Builder()
            .addAll(standardHeaders)
            .addAll(authenticationHeader)
            .build()

        val body = CreateGroupRequest(
            groupId,
            groupType,
            joinMode,
            permissionMode,
            selfSignedAdminCertificate,
            encryptedSettings,
            encryptedInternalSettings,
            parent
        )

        return httpRequester.request(
            "$BASE_URL/group",
            HTTPRequesterType.HTTPMethod.POST,
            headers,
            body
        )
    }

    override suspend fun getGroupInformation(groupId: GroupId, groupTag: GroupTag?): GroupInformationResponse {
        val headers = Headers.Builder()
            .addAll(standardHeaders)
            .addAll(authenticationHeader)

        groupTag?.let {
            headers.add(HeaderKeys.GROUP_TAG.value, groupTag.toString())
        }

        return try {
            httpRequester.request(
                "$BASE_URL/group/$groupId",
                HTTPRequesterType.HTTPMethod.GET,
                headers.build()
            )
        } catch (apiError: APIError) {
            when (apiError.type) {
                APIError.ErrorType.NOT_MODIFIED -> throw BackendException.NotModified
                APIError.ErrorType.NOT_FOUND -> throw BackendException.NotFound
                else -> throw apiError
            }
        }
    }

    override suspend fun getGroupInternals(
        groupId: GroupId,
        serverSignedMembershipCertificate: Certificate,
        groupTag: GroupTag?
    ): GroupInternalsResponse {
        val headers = Headers.Builder()
            .addAll(standardHeaders)
            .addAll(authenticationHeader)
            .add(HeaderKeys.GROUP_TAG.value, groupTag.toString())
            .add(HeaderKeys.SERVER_SIGNED_MEMBERSHIP_CERTIFICATE.value, serverSignedMembershipCertificate)
            .build()

        return try {
            httpRequester.request(
                "$BASE_URL/group/$groupId/internals",
                HTTPRequesterType.HTTPMethod.GET,
                headers
            )
        } catch (apiError: APIError) {
            when (apiError.type) {
                APIError.ErrorType.NOT_MODIFIED -> throw BackendException.NotModified
                APIError.ErrorType.AUTHENTICATION_FAILED -> throw BackendException.Unauthorized
                APIError.ErrorType.NOT_FOUND -> throw BackendException.NotFound
                APIError.ErrorType.INVALID_GROUP_TAG -> throw BackendException.GroupOutdated
                else -> throw apiError
            }
        }
    }

    override suspend fun joinGroup(
        groupId: GroupId,
        groupTag: GroupTag,
        selfSignedMembershipCertificate: Certificate,
        serverSignedAdminCertificate: Certificate?,
        adminSignedMembershipCertificate: Certificate?
    ): JoinGroupResponse {
        val headers = Headers.Builder()
            .addAll(standardHeaders)
            .addAll(authenticationHeader)
            .add(HeaderKeys.GROUP_TAG.value, groupTag)
            .build()

        val body = JoinGroupRequest(selfSignedMembershipCertificate, serverSignedAdminCertificate, adminSignedMembershipCertificate)

        return try {
            httpRequester.request(
                "$BASE_URL/group/$groupId/request",
                HTTPRequesterType.HTTPMethod.POST,
                headers,
                body
            )
        } catch (apiError: APIError) {
            when (apiError.type) {
                APIError.ErrorType.INVALID_GROUP_TAG -> throw BackendException.GroupOutdated
                else -> throw apiError
            }
        }
    }

    override suspend fun addGroupMember(
        groupId: GroupId,
        userId: UserId,
        encryptedMembershipCertificate: Ciphertext,
        serverSignedMembershipCertificate: Certificate,
        newTokenKey: SecretKey,
        groupTag: GroupTag,
        notificationRecipients: List<NotificationRecipient>
    ): UpdatedETagResponse {
        val headers = Headers.Builder()
            .addAll(standardHeaders)
            .addAll(authenticationHeader)
            .add(HeaderKeys.GROUP_TAG.value, groupTag)
            .add(HeaderKeys.SERVER_SIGNED_MEMBERSHIP_CERTIFICATE.value, serverSignedMembershipCertificate)
            .build()

        val tokenKeyString = newTokenKey.toBase64URLSafeString()

        val body = AddGroupMemberRequest(encryptedMembershipCertificate, userId, tokenKeyString, notificationRecipients)

        return httpRequester.request(
            "$BASE_URL/group/$groupId/member",
            HTTPRequesterType.HTTPMethod.POST,
            headers,
            body
        )
    }

    override suspend fun updateGroupMember(
        groupId: GroupId,
        userId: UserId,
        encryptedMembership: Ciphertext,
        serverSignedMembershipCertificate: Certificate,
        tokenKey: SecretKey,
        groupTag: GroupTag,
        notificationRecipients: List<NotificationRecipient>
    ): UpdatedETagResponse {
        val headers = Headers.Builder()
            .addAll(standardHeaders)
            .addAll(authenticationHeader)
            .add(HeaderKeys.GROUP_TAG.value, groupTag)
            .build()

        val body = UpdateGroupMemberRequest(encryptedMembership, userId, notificationRecipients)

        return httpRequester.request(
            "$BASE_URL/group/$groupId/member/${tokenKey.toBase64URLSafeString()}",
            HTTPRequesterType.HTTPMethod.PUT,
            headers,
            body
        )
    }

    override suspend fun deleteGroupMember(
        groupId: GroupId,
        groupTag: GroupTag,
        tokenKey: SecretKey,
        userId: UserId,
        userServerSignedMembershipCertificate: Certificate,
        ownServerSignedMembershipCertificate: Certificate,
        notificationRecipients: List<NotificationRecipient>
    ): UpdatedETagResponse {
        val headers = Headers.Builder()
            .addAll(standardHeaders)
            .addAll(authenticationHeader)
            .add(HeaderKeys.SERVER_SIGNED_MEMBERSHIP_CERTIFICATE.value, ownServerSignedMembershipCertificate)
            .add(HeaderKeys.GROUP_TAG.value, groupTag)
            .build()

        val tokenKeyString = tokenKey.toBase64URLSafeString()

        val body = DeleteGroupMemberRequest(userId, userServerSignedMembershipCertificate, notificationRecipients)

        return httpRequester.request(
            "$BASE_URL/group/$groupId/member/$tokenKeyString",
            HTTPRequesterType.HTTPMethod.DELETE,
            headers,
            body
        )
    }

    override suspend fun deleteGroup(
        groupId: GroupId,
        serverSignedMembershipCertificate: Certificate,
        groupTag: GroupTag,
        notificationRecipients: List<NotificationRecipient>
    ) {
        val headers = Headers.Builder()
            .addAll(standardHeaders)
            .addAll(authenticationHeader)
            .add(HeaderKeys.GROUP_TAG.value, groupTag)
            .add(HeaderKeys.SERVER_SIGNED_MEMBERSHIP_CERTIFICATE.value, serverSignedMembershipCertificate)
            .build()

        val body = DeleteGroupRequest(serverSignedMembershipCertificate, notificationRecipients)

        return httpRequester.request(
            "$BASE_URL/group/$groupId",
            HTTPRequesterType.HTTPMethod.DELETE,
            headers,
            body
        )
    }

    override suspend fun updateGroupSettings(
        groupId: GroupId,
        encryptedSettings: Ciphertext,
        serverSignedMembershipCertificate: Certificate,
        groupTag: GroupTag,
        notificationRecipients: List<NotificationRecipient>
    ): UpdatedETagResponse {
        val headers = Headers.Builder()
            .addAll(standardHeaders)
            .addAll(authenticationHeader)
            .add(HeaderKeys.SERVER_SIGNED_MEMBERSHIP_CERTIFICATE.value, serverSignedMembershipCertificate)
            .add(HeaderKeys.GROUP_TAG.value, groupTag)
            .build()

        val body = UpdateGroupInformationRequest(encryptedSettings, notificationRecipients)

        return httpRequester.request(
            "$BASE_URL/group/$groupId",
            HTTPRequesterType.HTTPMethod.PUT,
            headers,
            body
        )
    }

    override suspend fun updateGroupInternalSettings(
        groupId: GroupId,
        encryptedInternalSettings: Ciphertext,
        serverSignedMembershipCertificate: Certificate,
        groupTag: GroupTag,
        notificationRecipients: List<NotificationRecipient>
    ): UpdatedETagResponse {
        val headers = Headers.Builder()
            .addAll(standardHeaders)
            .addAll(authenticationHeader)
            .add(HeaderKeys.SERVER_SIGNED_MEMBERSHIP_CERTIFICATE.value, serverSignedMembershipCertificate)
            .add(HeaderKeys.GROUP_TAG.value, groupTag)
            .build()

        val body = UpdateGroupInternalsRequest(encryptedInternalSettings, notificationRecipients)

        return httpRequester.request(
            "$BASE_URL/group/$groupId/internals",
            HTTPRequesterType.HTTPMethod.PUT,
            headers,
            body
        )
    }

    override suspend fun message(
        id: MessageId,
        senderId: UserId,
        timestamp: Long,
        encryptedMessage: Ciphertext,
        serverSignedMembershipCertificate: Certificate,
        recipients: Set<Recipient>,
        priority: MessagePriority,
        collapseId: String?,
        messageTimeToLive: Long
    ) {
        val formattedTimestamp = DateSerializer.formatter.format(Date(timestamp))

        val headers = Headers.Builder()
            .addAll(standardHeaders)
            .addAll(authenticationHeader)
            .build()

        val body = SendMessageRequest(
            id,
            senderId,
            formattedTimestamp,
            encryptedMessage,
            serverSignedMembershipCertificate,
            recipients,
            priority,
            collapseId,
            messageTimeToLive
        )

        return httpRequester.request(
            "$BASE_URL/message",
            HTTPRequesterType.HTTPMethod.POST,
            headers,
            body
        )
    }

    override suspend fun getMessages(): GetMessagesResponse {
        val headers = Headers.Builder()
            .addAll(standardHeaders)
            .addAll(authenticationHeader)
            .build()

        return httpRequester.request(
            "$BASE_URL/message",
            HTTPRequesterType.HTTPMethod.GET,
            headers
        )
    }

    override suspend fun renewCertificate(certificate: Certificate): RenewCertificateResponse {
        val headers = Headers.Builder()
            .addAll(standardHeaders)
            .build()
        val body = RenewCertificateRequest(certificate)

        return httpRequester.request(
            "$BASE_URL/certificates/renew",
            HTTPRequesterType.HTTPMethod.POST,
            headers,
            body
        )
    }
}
