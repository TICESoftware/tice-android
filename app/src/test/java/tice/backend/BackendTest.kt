package tice.backend

import com.ticeapp.TICE.BuildConfig
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.serialization.UnsafeSerializationApi
import okhttp3.Headers
import okhttp3.Response
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tice.crypto.AuthManagerType
import tice.crypto.CryptoManagerType
import tice.exceptions.BackendException
import tice.managers.SignedInUserManagerType
import tice.models.*
import tice.models.messaging.MessagePriority
import tice.models.messaging.Recipient
import tice.models.requests.*
import tice.models.responses.*
import tice.utility.toBase64URLSafeString
import java.net.URL
import java.util.*

@UnsafeSerializationApi
internal class BackendTest {

    private lateinit var backend: Backend

    private val mockHTTPRequester: HTTPRequesterType = mockk(relaxUnitFun = true)
    private val mockSignedInUserManager: SignedInUserManagerType = mockk(relaxUnitFun = true)
    private val mockAuthManager: AuthManagerType = mockk(relaxUnitFun = true)
    private val mockResponse: Response = mockk(relaxUnitFun = true)

    val TEST_DEVICEID: String = "TestID"
    val TEST_BASE_URL: String = "Base_Url"
    lateinit var TEST_STANDARD_HEADERS: Headers

    val TEST_VERIFICATIONCODE = "verificationcode"
    val TEST_USERNAME = "UserName"

    val TEST_SIGNINGKEY = "signingKey".toByteArray()
    val TEST_IDENTITYKEY = "identityKey".toByteArray()
    val TEST_SIGNINGPREKEY = "signedPrekey".toByteArray()
    val TEST_PREKEYSIGNATURE = "prekeySignature".toByteArray()
    val TEST_ONETIMEPREKEY = listOf("oneTimePrekey".toByteArray())
    val TEST_USER_UUID = UUID.randomUUID()


    val TEST_USER_PRIVATE_KEY = "privateKey".toByteArray()
    val TEST_GROUP_TAG = "groupTag"
    val TEST_GROUP_ID = UUID.randomUUID()
    val TEST_GROUP_URL = URL("https://tice.app/group/testGroup")
    val TEST_SERVER_SIGNED = "serverSignedMembershipCertificate"
    val TEST_SELF_SIGNED = "selfSignedAdminCertificate"
    val TEST_ADMIN_SIGNED = "adminSigned"

    val TEST_ENCRYPTED_SETTINGS = "encryptedSettings".toByteArray()

    val TEST_ENCRYPTED_INTERNAL_SETTINGS = "encryptedInternalSettings".toByteArray()
    val TEST_RECIPIENTS = emptyList<NotificationRecipient>()

    @BeforeEach
    fun before() {
        clearAllMocks()

        val versionCode = "42"
        val versionName = "2.0"
        TEST_STANDARD_HEADERS = Headers.Builder()
            .add("X-Platform", "Android")
            .add("X-Version", versionName)
            .add("X-Build", versionCode)
            .build()
        backend = Backend(TEST_BASE_URL, versionName, versionCode, "Android", mockHTTPRequester, mockSignedInUserManager, mockAuthManager)

        val mockSignedInUser = mockk<SignedInUser>()
        every { mockSignedInUserManager.signedInUser } returns mockSignedInUser
        every { mockSignedInUser.userId } returns TEST_USER_UUID
        every { mockSignedInUser.privateSigningKey } returns TEST_USER_PRIVATE_KEY

        every { mockAuthManager.generateAuthHeader(TEST_USER_PRIVATE_KEY, TEST_USER_UUID) } returns TEST_USER_UUID.toString()
    }

    @Test
    fun verify() = runBlockingTest {
        val urlString = "$TEST_BASE_URL/verify"
        val body = VerifyRequest(TEST_DEVICEID, Platform.Android)

        coEvery {
            mockHTTPRequester.executeRequest(
                urlString,
                HTTPRequesterType.HTTPMethod.POST,
                TEST_STANDARD_HEADERS,
                Pair(body, VerifyRequest.serializer())
            )
        } returns mockResponse

        backend.verify(TEST_DEVICEID, Platform.Android)

        coVerify(exactly = 1) {
            mockHTTPRequester.request(
                urlString,
                HTTPRequesterType.HTTPMethod.POST,
                TEST_STANDARD_HEADERS,
                body
            )
        }

        confirmVerified(mockHTTPRequester)
    }

    @Test
    fun createUserUsingPush() = runBlockingTest {
        val urlString = "$TEST_BASE_URL/user/push"
        val createUserResponse = CreateUserResponse(TEST_USER_UUID)
        val userPublicKeys = UserPublicKeys(TEST_SIGNINGKEY, TEST_IDENTITYKEY, TEST_SIGNINGPREKEY, TEST_PREKEYSIGNATURE, TEST_ONETIMEPREKEY)
        val body = CreateUserPushRequest(userPublicKeys, Platform.Android, TEST_DEVICEID, TEST_VERIFICATIONCODE, TEST_USERNAME)

        coEvery {
            mockHTTPRequester.executeRequest(
                urlString,
                HTTPRequesterType.HTTPMethod.POST,
                TEST_STANDARD_HEADERS,
                Pair(body, CreateUserPushRequest.serializer())
            )
        } returns mockResponse

        coEvery { mockHTTPRequester.extractResponse(mockResponse, CreateUserResponse.serializer()) } returns createUserResponse

        val response = backend.createUserUsingPush(userPublicKeys, Platform.Android, TEST_DEVICEID, TEST_VERIFICATIONCODE, TEST_USERNAME)

        Assert.assertEquals(response, createUserResponse)
    }

    @Test
    fun createUserUsingCaptcha() = runBlockingTest {
        val urlString = "$TEST_BASE_URL/user/captcha"
        val createUserResponse = CreateUserResponse(TEST_USER_UUID)
        val userPublicKeys = UserPublicKeys(TEST_SIGNINGKEY, TEST_IDENTITYKEY, TEST_SIGNINGPREKEY, TEST_PREKEYSIGNATURE, TEST_ONETIMEPREKEY)
        val body = CreateUserCaptchaRequest(userPublicKeys, Platform.Android, TEST_VERIFICATIONCODE, TEST_USERNAME)

        coEvery {
            mockHTTPRequester.executeRequest(
                urlString,
                HTTPRequesterType.HTTPMethod.POST,
                TEST_STANDARD_HEADERS,
                Pair(body, CreateUserCaptchaRequest.serializer())
            )
        } returns mockResponse

        coEvery { mockHTTPRequester.extractResponse(mockResponse, CreateUserResponse.serializer()) } returns createUserResponse

        val response = backend.createUserUsingCaptcha(userPublicKeys, Platform.Android, TEST_VERIFICATIONCODE, TEST_USERNAME)

        Assert.assertEquals(response, createUserResponse)
    }

    @Test
    fun updateUser() = runBlockingTest {
        val headers = TEST_STANDARD_HEADERS.newBuilder().add("X-Authorization", TEST_USER_UUID.toString()).build()

        val userPublicKeys = UserPublicKeys(TEST_SIGNINGKEY, TEST_IDENTITYKEY, TEST_SIGNINGPREKEY, TEST_PREKEYSIGNATURE, TEST_ONETIMEPREKEY)
        val body = UpdateUserRequest(userPublicKeys, TEST_DEVICEID, TEST_VERIFICATIONCODE, TEST_USERNAME)

        coEvery {
            mockHTTPRequester.executeRequest(
                "$TEST_BASE_URL/user/$TEST_USER_UUID",
                HTTPRequesterType.HTTPMethod.PUT,
                headers,
                Pair(body, UpdateUserRequest.serializer())
            )
        } returns mockResponse

        backend.updateUser(TEST_USER_UUID, userPublicKeys, TEST_DEVICEID, TEST_VERIFICATIONCODE, TEST_USERNAME)

        coVerify(exactly = 1) {
            mockHTTPRequester.request<UpdateUserRequest, Unit>(
                "$TEST_BASE_URL/user/$TEST_USER_UUID",
                HTTPRequesterType.HTTPMethod.PUT,
                headers,
                body
            )
        }

        confirmVerified(mockHTTPRequester)
    }

    @Test
    fun deleteUser() = runBlockingTest {
        val headers = TEST_STANDARD_HEADERS.newBuilder().add("X-Authorization", TEST_USER_UUID.toString()).build()

        coEvery {
            mockHTTPRequester.executeRequest<Unit>(
                "$TEST_BASE_URL/user/$TEST_USER_UUID",
                HTTPRequesterType.HTTPMethod.DELETE,
                headers,
                null
            )
        } returns mockResponse

        backend.deleteUser(TEST_USER_UUID)

        coVerify(exactly = 1) {
            mockHTTPRequester.request<Unit>(
                "$TEST_BASE_URL/user/$TEST_USER_UUID",
                HTTPRequesterType.HTTPMethod.DELETE,
                headers
            )
        }

        confirmVerified(mockHTTPRequester)
    }

    @Test
    fun getUser() = runBlockingTest {
        val headers = TEST_STANDARD_HEADERS.newBuilder().add("X-Authorization", TEST_USER_UUID.toString()).build()

        val getUserResponse = GetUserResponse(TEST_USER_UUID, "publicKey".toByteArray(), null)

        coEvery {
            mockHTTPRequester.executeRequest<Unit>(
                "$TEST_BASE_URL/user/$TEST_USER_UUID",
                HTTPRequesterType.HTTPMethod.GET,
                headers,
                null
            )
        } returns mockResponse

        coEvery { mockHTTPRequester.extractResponse(mockResponse, GetUserResponse.serializer()) } returns getUserResponse

        val response = backend.getUser(TEST_USER_UUID)

        Assert.assertEquals(response, getUserResponse)
    }

    @Test
    fun getUserPublicKeys() = runBlockingTest {
        val urlString = "$TEST_BASE_URL/user/$TEST_USER_UUID/keys"
        val headers = TEST_STANDARD_HEADERS.newBuilder().add("X-Authorization", TEST_USER_UUID.toString()).build()
        val userPublicKeysResponse = GetUserPublicKeysResponse(
            TEST_SIGNINGKEY,
            TEST_IDENTITYKEY,
            TEST_SIGNINGPREKEY,
            TEST_PREKEYSIGNATURE,
            TEST_ONETIMEPREKEY.first()
        )

        coEvery {
            mockHTTPRequester.executeRequest<GetUserPublicKeysResponse>(
                urlString,
                HTTPRequesterType.HTTPMethod.POST,
                headers,
                null
            )
        } returns mockResponse

        coEvery { mockHTTPRequester.extractResponse(mockResponse, GetUserPublicKeysResponse.serializer()) } returns userPublicKeysResponse

        val response = backend.getUserKey(TEST_USER_UUID)

        Assert.assertEquals(response, userPublicKeysResponse)
    }

    @Test
    fun createGroup() = runBlockingTest {
        val urlString = "$TEST_BASE_URL/group"
        val headers = TEST_STANDARD_HEADERS.newBuilder().add("X-Authorization", TEST_USER_UUID.toString()).build()
        val createGroupResponse = CreateGroupResponse(TEST_GROUP_URL, "certificate", "tag")

        val body = CreateGroupRequest(
            TEST_GROUP_ID,
            GroupType.Team,
            JoinMode.Open,
            PermissionMode.Everyone,
            TEST_SELF_SIGNED,
            TEST_ENCRYPTED_SETTINGS,
            TEST_ENCRYPTED_INTERNAL_SETTINGS,
            null
        )

        coEvery {
            mockHTTPRequester.executeRequest(
                urlString,
                HTTPRequesterType.HTTPMethod.POST,
                headers,
                Pair(body, CreateGroupRequest.serializer())
            )
        } returns mockResponse

        coEvery { mockHTTPRequester.extractResponse(mockResponse, CreateGroupResponse.serializer()) } returns createGroupResponse

        val response = backend.createGroup(
            TEST_GROUP_ID,
            GroupType.Team,
            JoinMode.Open,
            PermissionMode.Everyone,
            TEST_SELF_SIGNED,
            TEST_ENCRYPTED_SETTINGS,
            TEST_ENCRYPTED_INTERNAL_SETTINGS,
            null
        )

        Assert.assertEquals(response, createGroupResponse)
    }


    @Test
    fun getGroupInformation() = runBlockingTest {
        val urlString = "$TEST_BASE_URL/group/$TEST_GROUP_ID"
        val headers = TEST_STANDARD_HEADERS.newBuilder()
            .add("X-Authorization", TEST_USER_UUID.toString())
            .add("X-GroupTag", TEST_GROUP_TAG)
            .build()
        val groupInformationResponse = GroupInformationResponse(
            TEST_GROUP_ID,
            null,
            GroupType.Team,
            JoinMode.Open,
            PermissionMode.Everyone,
            TEST_GROUP_URL,
            TEST_ENCRYPTED_SETTINGS,
            TEST_GROUP_TAG
        )

        coEvery {
            mockHTTPRequester.executeRequest<Unit>(
                urlString,
                HTTPRequesterType.HTTPMethod.GET,
                headers,
                null
            )
        } returns mockResponse

        coEvery { mockHTTPRequester.extractResponse(mockResponse, GroupInformationResponse.serializer()) } returns groupInformationResponse

        val response = backend.getGroupInformation(TEST_GROUP_ID, TEST_GROUP_TAG)

        Assert.assertEquals(response, groupInformationResponse)
    }

    @Test
    fun getGroupInformation_NotModified() = runBlockingTest {
        val urlString = "$TEST_BASE_URL/group/$TEST_GROUP_ID"
        val headers = TEST_STANDARD_HEADERS.newBuilder()
            .add("X-Authorization", TEST_USER_UUID.toString())
            .add("X-GroupTag", TEST_GROUP_TAG)
            .build()
        val notModified = APIError(APIError.ErrorType.NOT_MODIFIED, "Not modified")

        coEvery {
            mockHTTPRequester.executeRequest<Unit>(
                urlString,
                HTTPRequesterType.HTTPMethod.GET,
                headers,
                null
            )
        } throws notModified

        assertThrows<BackendException.NotModified> { runBlocking { backend.getGroupInformation(TEST_GROUP_ID, TEST_GROUP_TAG) } }
    }

    @Test
    fun getGroupInformation_NotFound() = runBlockingTest {
        val urlString = "$TEST_BASE_URL/group/$TEST_GROUP_ID"
        val headers = TEST_STANDARD_HEADERS.newBuilder()
            .add("X-Authorization", TEST_USER_UUID.toString())
            .add("X-GroupTag", TEST_GROUP_TAG)
            .build()
        val notFound = APIError(APIError.ErrorType.NOT_FOUND, "Not found")

        coEvery {
            mockHTTPRequester.executeRequest<Unit>(
                urlString,
                HTTPRequesterType.HTTPMethod.GET,
                headers,
                null
            )
        } throws notFound

        assertThrows<BackendException.NotFound> { runBlocking { backend.getGroupInformation(TEST_GROUP_ID, TEST_GROUP_TAG) } }
    }

    @Test
    fun getGroupInternals() = runBlockingTest {
        val urlString = "$TEST_BASE_URL/group/$TEST_GROUP_ID/internals"
        val headers = TEST_STANDARD_HEADERS.newBuilder()
            .add("X-Authorization", TEST_USER_UUID.toString())
            .add("X-GroupTag", TEST_GROUP_TAG)
            .add("X-ServerSignedMembershipCertificate", TEST_SERVER_SIGNED)
            .build()
        val groupInternalsResponse = GroupInternalsResponse(
            TEST_GROUP_ID,
            null,
            GroupType.Team,
            JoinMode.Open,
            PermissionMode.Everyone,
            TEST_GROUP_URL,
            TEST_ENCRYPTED_SETTINGS,
            TEST_ENCRYPTED_INTERNAL_SETTINGS,
            emptyArray(),
            null,
            emptyArray(),
            TEST_GROUP_TAG
        )

        coEvery {
            mockHTTPRequester.executeRequest<Unit>(
                urlString,
                HTTPRequesterType.HTTPMethod.GET,
                headers,
                null
            )
        } returns mockResponse

        coEvery { mockHTTPRequester.extractResponse(mockResponse, GroupInternalsResponse.serializer()) } returns groupInternalsResponse

        val response = backend.getGroupInternals(TEST_GROUP_ID, TEST_SERVER_SIGNED, TEST_GROUP_TAG)

        Assert.assertEquals(response, groupInternalsResponse)
    }

    @Test
    fun getGroupInternals_NotModified() = runBlockingTest {
        val urlString = "$TEST_BASE_URL/group/$TEST_GROUP_ID/internals"
        val headers = TEST_STANDARD_HEADERS.newBuilder()
            .add("X-Authorization", TEST_USER_UUID.toString())
            .add("X-GroupTag", TEST_GROUP_TAG)
            .add("X-ServerSignedMembershipCertificate", TEST_SERVER_SIGNED)
            .build()
        val notModified = APIError(APIError.ErrorType.NOT_MODIFIED, "Not modified")

        coEvery {
            mockHTTPRequester.executeRequest<Unit>(
                urlString,
                HTTPRequesterType.HTTPMethod.GET,
                headers,
                null
            )
        } throws notModified

        assertThrows<BackendException.NotModified> {
            runBlocking {
                backend.getGroupInternals(
                    TEST_GROUP_ID,
                    TEST_SERVER_SIGNED,
                    TEST_GROUP_TAG
                )
            }
        }
    }

    @Test
    fun getGroupInternals_NotFound() = runBlockingTest {
        val urlString = "$TEST_BASE_URL/group/$TEST_GROUP_ID/internals"
        val headers = TEST_STANDARD_HEADERS.newBuilder()
            .add("X-Authorization", TEST_USER_UUID.toString())
            .add("X-GroupTag", TEST_GROUP_TAG)
            .add("X-ServerSignedMembershipCertificate", TEST_SERVER_SIGNED)
            .build()
        val notFound = APIError(APIError.ErrorType.NOT_FOUND, "Not found")

        coEvery {
            mockHTTPRequester.executeRequest<Unit>(
                urlString,
                HTTPRequesterType.HTTPMethod.GET,
                headers,
                null
            )
        } throws notFound

        assertThrows<BackendException.NotFound> {
            runBlocking {
                backend.getGroupInternals(
                    TEST_GROUP_ID,
                    TEST_SERVER_SIGNED,
                    TEST_GROUP_TAG
                )
            }
        }
    }

    @Test
    fun getGroupInternals_AuthenticationFailed() = runBlockingTest {
        val urlString = "$TEST_BASE_URL/group/$TEST_GROUP_ID/internals"
        val headers = TEST_STANDARD_HEADERS.newBuilder()
            .add("X-Authorization", TEST_USER_UUID.toString())
            .add("X-GroupTag", TEST_GROUP_TAG)
            .add("X-ServerSignedMembershipCertificate", TEST_SERVER_SIGNED)
            .build()
        val authenticationFailed = APIError(APIError.ErrorType.AUTHENTICATION_FAILED, "Authentication failed")

        coEvery {
            mockHTTPRequester.executeRequest<Unit>(
                urlString,
                HTTPRequesterType.HTTPMethod.GET,
                headers,
                null
            )
        } throws authenticationFailed

        assertThrows<BackendException.Unauthorized> {
            runBlocking {
                backend.getGroupInternals(
                    TEST_GROUP_ID,
                    TEST_SERVER_SIGNED,
                    TEST_GROUP_TAG
                )
            }
        }
    }

    @Test
    fun getGroupInternals_InvalidGroupTag() = runBlockingTest {
        val urlString = "$TEST_BASE_URL/group/$TEST_GROUP_ID/internals"
        val headers = TEST_STANDARD_HEADERS.newBuilder()
            .add("X-Authorization", TEST_USER_UUID.toString())
            .add("X-GroupTag", TEST_GROUP_TAG)
            .add("X-ServerSignedMembershipCertificate", TEST_SERVER_SIGNED)
            .build()
        val invalidGroupTag = APIError(APIError.ErrorType.INVALID_GROUP_TAG, "Invalid group tag")

        coEvery {
            mockHTTPRequester.executeRequest<Unit>(
                urlString,
                HTTPRequesterType.HTTPMethod.GET,
                headers,
                null
            )
        } throws invalidGroupTag

        assertThrows<BackendException.GroupOutdated> {
            runBlocking {
                backend.getGroupInternals(
                    TEST_GROUP_ID,
                    TEST_SERVER_SIGNED,
                    TEST_GROUP_TAG
                )
            }
        }
    }

    @Test
    fun joinGroup() = runBlockingTest {
        val urlString = "$TEST_BASE_URL/group/$TEST_GROUP_ID/request"
        val headers = TEST_STANDARD_HEADERS.newBuilder()
            .add("X-Authorization", TEST_USER_UUID.toString())
            .add("X-GroupTag", TEST_GROUP_TAG)
            .build()
        val joinGroupResponse = JoinGroupResponse(TEST_SERVER_SIGNED)
        val body = JoinGroupRequest(TEST_SELF_SIGNED, TEST_SERVER_SIGNED, TEST_ADMIN_SIGNED)

        coEvery {
            mockHTTPRequester.executeRequest(
                urlString,
                HTTPRequesterType.HTTPMethod.POST,
                headers,
                Pair(body, JoinGroupRequest.serializer())
            )
        } returns mockResponse

        coEvery { mockHTTPRequester.extractResponse(mockResponse, JoinGroupResponse.serializer()) } returns joinGroupResponse

        val response = backend.joinGroup(TEST_GROUP_ID, TEST_GROUP_TAG, TEST_SELF_SIGNED, TEST_SERVER_SIGNED, TEST_ADMIN_SIGNED)

        Assert.assertEquals(response, joinGroupResponse)
    }

    @Test
    fun joinGroup_InvalidGroupTag() = runBlockingTest {
        val urlString = "$TEST_BASE_URL/group/$TEST_GROUP_ID/request"
        val headers = TEST_STANDARD_HEADERS.newBuilder()
            .add("X-Authorization", TEST_USER_UUID.toString())
            .add("X-GroupTag", TEST_GROUP_TAG)
            .build()
        val invalidGroupTag = APIError(APIError.ErrorType.INVALID_GROUP_TAG, "Invalid group tag")
        val body = JoinGroupRequest(TEST_SELF_SIGNED, TEST_SERVER_SIGNED, TEST_ADMIN_SIGNED)

        coEvery {
            mockHTTPRequester.executeRequest(
                urlString,
                HTTPRequesterType.HTTPMethod.POST,
                headers,
                Pair(body, JoinGroupRequest.serializer())
            )
        } throws invalidGroupTag

        assertThrows<BackendException.GroupOutdated> {
            runBlocking {
                backend.joinGroup(
                    TEST_GROUP_ID,
                    TEST_GROUP_TAG,
                    TEST_SELF_SIGNED,
                    TEST_SERVER_SIGNED,
                    TEST_ADMIN_SIGNED
                )
            }
        }
    }

    @Test
    fun deleteGroup() = runBlockingTest {
        val headers = TEST_STANDARD_HEADERS.newBuilder()
            .add("X-Authorization", TEST_USER_UUID.toString())
            .add("X-GroupTag", TEST_GROUP_TAG)
            .add("X-ServerSignedMembershipCertificate", TEST_SERVER_SIGNED)
            .build()
        val body = DeleteGroupRequest(TEST_SERVER_SIGNED, TEST_RECIPIENTS)

        coEvery {
            mockHTTPRequester.executeRequest(
                "$TEST_BASE_URL/group/$TEST_GROUP_ID",
                HTTPRequesterType.HTTPMethod.DELETE,
                headers,
                Pair(body, DeleteGroupRequest.serializer())
            )
        } returns mockResponse

        backend.deleteGroup(TEST_GROUP_ID, TEST_SERVER_SIGNED, TEST_GROUP_TAG, TEST_RECIPIENTS)

        coVerify(exactly = 1) {
            mockHTTPRequester.request<DeleteGroupRequest, Unit>(
                "$TEST_BASE_URL/group/$TEST_GROUP_ID",
                HTTPRequesterType.HTTPMethod.DELETE,
                headers,
                body
            )
        }

        confirmVerified(mockHTTPRequester)
    }

    @Test
    fun updateGroupInternalsSettings() = runBlockingTest {
        val urlString = "$TEST_BASE_URL/group/$TEST_GROUP_ID/internals"
        val encryptedMembershipCertificate = TEST_SERVER_SIGNED.toByteArray()
        val headers = TEST_STANDARD_HEADERS.newBuilder()
            .add("X-Authorization", TEST_USER_UUID.toString())
            .add("X-ServerSignedMembershipCertificate", TEST_SERVER_SIGNED)
            .add("X-GroupTag", TEST_GROUP_TAG)
            .build()

        val updatedETagResponse = UpdatedETagResponse(TEST_GROUP_TAG)
        val body = UpdateGroupInternalsRequest(encryptedMembershipCertificate, TEST_RECIPIENTS)

        coEvery {
            mockHTTPRequester.executeRequest(
                urlString,
                HTTPRequesterType.HTTPMethod.PUT,
                headers,
                Pair(body, UpdateGroupInternalsRequest.serializer())
            )
        } returns mockResponse

        coEvery { mockHTTPRequester.extractResponse(mockResponse, UpdatedETagResponse.serializer()) } returns updatedETagResponse

        val response = backend.updateGroupInternalSettings(
            TEST_GROUP_ID,
            encryptedMembershipCertificate,
            TEST_SERVER_SIGNED,
            TEST_GROUP_TAG,
            TEST_RECIPIENTS
        )

        Assert.assertEquals(response, updatedETagResponse)
    }

    @Test
    fun updateGroupInformation() = runBlockingTest {
        val urlString = "$TEST_BASE_URL/group/$TEST_GROUP_ID"
        val encryptedMembershipCertificate = TEST_SERVER_SIGNED.toByteArray()
        val headers = TEST_STANDARD_HEADERS.newBuilder()
            .add("X-Authorization", TEST_USER_UUID.toString())
            .add("X-ServerSignedMembershipCertificate", TEST_SERVER_SIGNED)
            .add("X-GroupTag", TEST_GROUP_TAG)
            .build()

        val updatedETagResponse = UpdatedETagResponse(TEST_GROUP_TAG)
        val body = UpdateGroupInformationRequest(encryptedMembershipCertificate, TEST_RECIPIENTS)

        coEvery {
            mockHTTPRequester.executeRequest(
                urlString,
                HTTPRequesterType.HTTPMethod.PUT,
                headers,
                Pair(body, UpdateGroupInformationRequest.serializer())
            )
        } returns mockResponse

        coEvery { mockHTTPRequester.extractResponse(mockResponse, UpdatedETagResponse.serializer()) } returns updatedETagResponse

        val response = backend.updateGroupSettings(
            TEST_GROUP_ID,
            encryptedMembershipCertificate,
            TEST_SERVER_SIGNED,
            TEST_GROUP_TAG,
            TEST_RECIPIENTS
        )

        Assert.assertEquals(response, updatedETagResponse)
    }

    @Test
    fun sendMessage() = runBlockingTest {
        val urlString = "$TEST_BASE_URL/message"

        val messageId = UUID.randomUUID()
        val timestamp = 0.toLong()
        val encryptedMessage = "encryptMessage".toByteArray()
        val recipients: Set<Recipient> = emptySet()
        val priority = MessagePriority.Alert
        val collapseId = "collapseID"
        val ttl = 2L
        val headers = TEST_STANDARD_HEADERS.newBuilder()
            .add("X-Authorization", TEST_USER_UUID.toString())
            .build()

        val formattedTimestamp = "1970-01-01T00:00:00.000+0000"
        val body = SendMessageRequest(
            messageId,
            TEST_USER_UUID,
            formattedTimestamp,
            encryptedMessage,
            TEST_SERVER_SIGNED,
            recipients,
            priority,
            collapseId,
            ttl
        )

        coEvery {
            mockHTTPRequester.executeRequest(
                urlString,
                HTTPRequesterType.HTTPMethod.POST,
                headers,
                Pair(body, SendMessageRequest.serializer())
            )
        } returns mockResponse

        backend.message(
            messageId,
            TEST_USER_UUID,
            timestamp,
            encryptedMessage,
            TEST_SERVER_SIGNED,
            recipients,
            priority,
            collapseId,
            ttl
        )

        coVerify(exactly = 1) {
            mockHTTPRequester.request<SendMessageRequest, Unit>(
                urlString,
                HTTPRequesterType.HTTPMethod.POST,
                headers,
                body
            )
        }

        confirmVerified(mockHTTPRequester)
    }

    @Test
    fun getMessages() = runBlockingTest {
        val urlString = "$TEST_BASE_URL/message"
        val headers = TEST_STANDARD_HEADERS.newBuilder()
            .add("X-Authorization", TEST_USER_UUID.toString())
            .build()
        val getMessagesResponse = GetMessagesResponse(emptyArray())

        coEvery {
            mockHTTPRequester.executeRequest<Unit>(
                urlString,
                HTTPRequesterType.HTTPMethod.GET,
                headers,
                null
            )
        } returns mockResponse

        coEvery { mockHTTPRequester.extractResponse(mockResponse, GetMessagesResponse.serializer()) } returns getMessagesResponse

        val response = backend.getMessages()

        Assert.assertEquals(response, getMessagesResponse)
    }

    @Test
    fun renewCertificate() = runBlockingTest {
        val TEST_CERT = "Cert"
        val TEST_NEW_CERT = "newCert"
        val urlString = "$TEST_BASE_URL/certificates/renew"
        val headers = TEST_STANDARD_HEADERS.newBuilder()
            .build()
        val TEST_RESPONSE = RenewCertificateResponse(TEST_NEW_CERT)
        val expectedBody = RenewCertificateRequest(TEST_CERT)

        coEvery {
            mockHTTPRequester.request<RenewCertificateRequest, RenewCertificateResponse>(
                urlString,
                HTTPRequesterType.HTTPMethod.POST,
                headers,
                expectedBody
            )
        } returns TEST_RESPONSE

        coEvery { mockHTTPRequester.extractResponse(mockResponse, RenewCertificateResponse.serializer()) } returns TEST_RESPONSE

        val response = backend.renewCertificate(TEST_CERT)

        Assert.assertEquals(response, TEST_RESPONSE)
    }

    @Test
    fun addGroupMember() = runBlockingTest {
        val TEST_ENCRYPTED_DATA = "Data".toByteArray()
        val TEST_NEW_TAG = "newTag"
        val TEST_TOKEN_KEY = "tokenKey".toByteArray()
        val TEST_NOTIFICATION_RECIPIENTS = listOf<NotificationRecipient>()
        val TEST_RESPONSE = UpdatedETagResponse(TEST_NEW_TAG)

        mockkStatic("tice.utility.Base64ConvertFunctionsKt")
        val slotByteArray = slot<ByteArray>()
        every { capture(slotByteArray).toBase64URLSafeString() } answers { String(slotByteArray.captured) }

        val urlString = "$TEST_BASE_URL/group/$TEST_GROUP_ID/member/${TEST_TOKEN_KEY.toBase64URLSafeString()}"
        val headers = TEST_STANDARD_HEADERS.newBuilder()
            .add("X-Authorization", TEST_USER_UUID.toString())
            .add("X-GroupTag", TEST_GROUP_TAG)
            .build()

        val expectedBody = UpdateGroupMemberRequest(TEST_ENCRYPTED_DATA, TEST_USER_UUID, TEST_NOTIFICATION_RECIPIENTS)

        coEvery {
            mockHTTPRequester.request<UpdateGroupMemberRequest, UpdatedETagResponse>(
                urlString,
                HTTPRequesterType.HTTPMethod.PUT,
                headers,
                expectedBody
            )
        } returns TEST_RESPONSE

        coEvery { mockHTTPRequester.extractResponse(mockResponse, UpdatedETagResponse.serializer()) } returns TEST_RESPONSE

        val response = backend.updateGroupMember(
            TEST_GROUP_ID,
            TEST_USER_UUID,
            TEST_ENCRYPTED_DATA,
            TEST_SERVER_SIGNED,
            TEST_TOKEN_KEY,
            TEST_GROUP_TAG,
            TEST_NOTIFICATION_RECIPIENTS
        )

        Assert.assertEquals(response, TEST_RESPONSE)
    }
}
