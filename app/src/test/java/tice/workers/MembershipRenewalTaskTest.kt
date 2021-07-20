package tice.workers

import androidx.work.ListenableWorker
import io.mockk.*
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tice.backend.BackendType
import tice.crypto.AuthManagerType
import tice.crypto.CryptoManagerType
import tice.exceptions.BackendException
import tice.exceptions.SignedInUserManagerException
import tice.managers.SignedInUserManagerType
import tice.managers.storageManagers.GroupStorageManagerType
import tice.managers.storageManagers.MembershipsDiff
import tice.models.*
import tice.models.messaging.MessagePriority
import tice.models.requests.CertificateBlacklistedRequest
import tice.models.requests.RenewCertificateResponse
import tice.models.responses.UpdatedETagResponse
import tice.utility.beekeeper.BeekeeperEvent
import tice.utility.beekeeper.BeekeeperType
import tice.utility.beekeeper.track
import tice.utility.toBase64String
import tice.utility.toBase64URLSafeString
import java.util.*

internal class MembershipRenewalTaskTest {

    private lateinit var membershipRenewalTask: MembershipRenewalTask

    private val mockMembershipRenewalConfig: MembershipRenewalConfig = mockk(relaxUnitFun = true)
    private val mockSignedInUserManager: SignedInUserManagerType = mockk(relaxUnitFun = true)
    private val mockGroupStorageManager: GroupStorageManagerType = mockk(relaxUnitFun = true)
    private val mockCryptoManager: CryptoManagerType = mockk(relaxUnitFun = true)
    private val mockAuthManager: AuthManagerType = mockk(relaxUnitFun = true)
    private val mockBackend: BackendType = mockk(relaxUnitFun = true)
    private val mockBeekeeper: BeekeeperType = mockk(relaxUnitFun = true)

    private val mockSignedInUser: SignedInUser = mockk(relaxUnitFun = true)
    private val TEST_SIGNED_IN_PUBLIC_KEY = "PublicKey".encodeToByteArray()
    private val TEST_SIGNED_IN_PRIVATE_KEY = "PrivateKey".encodeToByteArray()

    private val TEST_USER_ID = UUID.randomUUID()
    private val TEST_OTHER_USER_ID = UUID.randomUUID()
    private val TEST_GROUP_ID_1 = UUID.randomUUID()
    private val TEST_GROUP_ID_2 = UUID.randomUUID()
    private val TEST_GROUP_ID_3 = UUID.randomUUID()

    private val TEST_SELF_SIGNED_CERT_1 = "selfCert1"
    private val TEST_SELF_SIGNED_CERT_2 = "selfCert12"
    private val TEST_SELF_SIGNED_CERT_3 = "selfCert3"

    private val TEST_SERVER_SIGNED_CERT_1 = "serverCert1"
    private val TEST_SERVER_SIGNED_CERT_2 = "serverCert2"
    private val TEST_SERVER_SIGNED_CERT_3 = "serverCert3"

    private val TEST_ADMIN_SIGNED_CERT_1 = "adminCert1"
    private val TEST_ADMIN_SIGNED_CERT_2 = "adminCert2"
    private val TEST_ADMIN_SIGNED_CERT_3 = "adminCert3"

    private val TEST_ADMIN_STATE_1 = true
    private val TEST_ADMIN_STATE_2 = true
    private val TEST_ADMIN_STATE_3 = false

    private val TEST_PUBLIC_SIGN_KEY_1 = "publicSignKey1".toByteArray()
    private val TEST_PUBLIC_SIGN_KEY_2 = "publicSignKey2".toByteArray()
    private val TEST_PUBLIC_SIGN_KEY_3 = "publicSignKey3".toByteArray()

    private val TEST_RENEWED_SELF_SIGNED_CERT_1 = "renewedSelfCert1"
    private val TEST_RENEWED_SELF_SIGNED_CERT_2 = "renewedSelfCert2"

    private val TEST_RENEWED_SERVER_SIGNED_CERT_1 = "renewedServerCert1"
    private val TEST_RENEWED_SERVER_SIGNED_CERT_2 = "renewedServerCert2"

    private val TEST_SECRET_KEY_GROUP_1 = "secretKeyGroup1".toByteArray()
    private val TEST_SECRET_KEY_GROUP_2 = "secretKeyGroup2".toByteArray()

    private val TEST_GROUP_TAG_1 = "tag1"
    private val TEST_GROUP_TAG_2 = "tag2"

    private val TEST_NEW_GROUP_TAG_1 = "newTag1"
    private val TEST_NEW_GROUP_TAG_2 = "newTag2"

    private val TEST_TOKEN_KEY_1 = "tokenKey1".toByteArray()
    private val TEST_TOKEN_KEY_2 = "tokenKey2".toByteArray()

    private val TEST_ENCRYPT_NEW_MEMBERSHIP_1 = "encryptNewMembership1".toByteArray()
    private val TEST_ENCRYPT_NEW_MEMBERSHIP_2 = "encryptNewMembership2".toByteArray()

    private val TEST_MEMBERSHIP_1 = Membership(
        TEST_USER_ID,
        TEST_GROUP_ID_1,
        TEST_PUBLIC_SIGN_KEY_1,
        TEST_ADMIN_STATE_1,
        TEST_SELF_SIGNED_CERT_1,
        TEST_SERVER_SIGNED_CERT_1,
        TEST_ADMIN_SIGNED_CERT_1
    )

    private val TEST_MEMBERSHIP_2 = Membership(
        TEST_USER_ID,
        TEST_GROUP_ID_2,
        TEST_PUBLIC_SIGN_KEY_2,
        TEST_ADMIN_STATE_2,
        TEST_SELF_SIGNED_CERT_2,
        TEST_SERVER_SIGNED_CERT_2,
        TEST_ADMIN_SIGNED_CERT_2
    )

    private val TEST_MEMBERSHIP_3 = Membership(
        TEST_OTHER_USER_ID,
        TEST_GROUP_ID_3,
        TEST_PUBLIC_SIGN_KEY_3,
        TEST_ADMIN_STATE_3,
        TEST_SELF_SIGNED_CERT_3,
        TEST_SERVER_SIGNED_CERT_3,
        TEST_ADMIN_SIGNED_CERT_3
    )

    private val TEST_GROUP_1 = Team(
        TEST_GROUP_ID_1,
        TEST_SECRET_KEY_GROUP_1,
        UUID.randomUUID(),
        JoinMode.Open,
        PermissionMode.Everyone,
        TEST_GROUP_TAG_1,
        mockk(), 
        null,
        meetingPoint = null
    )

    private val TEST_GROUP_2 = Meetup(
        TEST_GROUP_ID_2,
        TEST_SECRET_KEY_GROUP_2,
        UUID.randomUUID(),
        JoinMode.Open,
        PermissionMode.Everyone,
        TEST_GROUP_TAG_2,
        mockk(),
        null
    )

    private val TEST_EXPECTED_MEMBERSHIP_1 = Membership(
        TEST_USER_ID,
        TEST_GROUP_ID_1,
        TEST_PUBLIC_SIGN_KEY_1,
        TEST_ADMIN_STATE_1,
        TEST_RENEWED_SELF_SIGNED_CERT_1,
        TEST_RENEWED_SERVER_SIGNED_CERT_1,
        null
    )

    private val TEST_EXPECTED_MEMBERSHIP_2 = Membership(
        TEST_USER_ID,
        TEST_GROUP_ID_2,
        TEST_PUBLIC_SIGN_KEY_2,
        TEST_ADMIN_STATE_2,
        TEST_RENEWED_SELF_SIGNED_CERT_2,
        TEST_RENEWED_SERVER_SIGNED_CERT_2,
        null
    )

    private val TEST_NOTIFICATION_RECIPIENTS_1 = NotificationRecipient(
        TEST_OTHER_USER_ID,
        TEST_SERVER_SIGNED_CERT_3,
        MessagePriority.Background
    )

    private val TEST_VALIDITY_THRESHOLD = 10L

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        membershipRenewalTask = MembershipRenewalTask(
            mockMembershipRenewalConfig,
            mockSignedInUserManager,
            mockGroupStorageManager,
            mockCryptoManager,
            mockAuthManager,
            mockBackend,
            mockBeekeeper
        )

        every { mockSignedInUserManager.signedInUser } returns mockSignedInUser
        every { mockSignedInUser.userId } returns TEST_USER_ID
        every { mockSignedInUser.publicSigningKey } returns TEST_SIGNED_IN_PUBLIC_KEY
        every { mockSignedInUser.privateSigningKey } returns TEST_SIGNED_IN_PRIVATE_KEY

        every { mockMembershipRenewalConfig.certificateValidityTimeRenewalThreshold } returns TEST_VALIDITY_THRESHOLD
    }

    @Test
    fun doWork_NotSignedIn() = runBlockingTest {
        every { mockSignedInUserManager.signedIn() } returns false

        val result = membershipRenewalTask.doWork()

        Assertions.assertEquals(ListenableWorker.Result.failure(), result)
        coVerify(exactly = 0) { mockGroupStorageManager.loadMembershipsOfUser(TEST_USER_ID) }
        coVerify(exactly = 1) { mockBeekeeper.track(BeekeeperEvent.membershipRenewalWorkerStarted()) }
        coVerify(exactly = 1) { mockBeekeeper.track(BeekeeperEvent.membershipRenewalWorkerCompleted()) }
        coVerify(exactly = 0) { mockBackend.renewCertificate(TEST_SERVER_SIGNED_CERT_1) }
        coVerify(exactly = 0) { mockBackend.renewCertificate(TEST_SERVER_SIGNED_CERT_2) }
        coVerify(exactly = 0) {
            mockGroupStorageManager.storeTeam(TEST_GROUP_1, MembershipsDiff.Replace(listOf(TEST_EXPECTED_MEMBERSHIP_1)))
        }
        coVerify(exactly = 0) {
            mockGroupStorageManager.storeMeetup(TEST_GROUP_2, MembershipsDiff.Replace(listOf(TEST_EXPECTED_MEMBERSHIP_2)))
        }
        coVerify(exactly = 0) {
            mockAuthManager.createUserSignedMembershipCertificate(
                TEST_USER_ID,
                TEST_GROUP_ID_2,
                TEST_ADMIN_STATE_2,
                TEST_USER_ID,
                TEST_SIGNED_IN_PRIVATE_KEY
            )
        }
        coVerify(exactly = 0) {
            mockAuthManager.createUserSignedMembershipCertificate(
                TEST_USER_ID,
                TEST_GROUP_ID_1,
                TEST_ADMIN_STATE_1,
                TEST_USER_ID,
                TEST_SIGNED_IN_PRIVATE_KEY
            )
        }
    }

    @Test
    fun doWork_success() = runBlockingTest {
        every { mockSignedInUserManager.signedIn() } returns true

        coEvery { mockGroupStorageManager.loadMembershipsOfUser(TEST_USER_ID) }
            .returns(setOf(TEST_MEMBERSHIP_1, TEST_MEMBERSHIP_2, TEST_MEMBERSHIP_3))

        every { mockAuthManager.membershipCertificateExpirationDate(TEST_SELF_SIGNED_CERT_1, TEST_PUBLIC_SIGN_KEY_1) } returns Date(Date().time + TEST_VALIDITY_THRESHOLD + 2)
        every { mockAuthManager.membershipCertificateExpirationDate(TEST_SERVER_SIGNED_CERT_1, TEST_PUBLIC_SIGN_KEY_1) } returns Date(Date().time + TEST_VALIDITY_THRESHOLD - 2)

        every { mockAuthManager.membershipCertificateExpirationDate(TEST_SELF_SIGNED_CERT_2, TEST_PUBLIC_SIGN_KEY_2) } returns Date(Date().time + TEST_VALIDITY_THRESHOLD - 2)
        every { mockAuthManager.membershipCertificateExpirationDate(TEST_SERVER_SIGNED_CERT_2, TEST_PUBLIC_SIGN_KEY_2) } returns Date(Date().time + TEST_VALIDITY_THRESHOLD + 2)

        every { mockAuthManager.membershipCertificateExpirationDate(TEST_SELF_SIGNED_CERT_3, TEST_PUBLIC_SIGN_KEY_3) } returns Date(Date().time + TEST_VALIDITY_THRESHOLD + 2)
        every { mockAuthManager.membershipCertificateExpirationDate(TEST_SERVER_SIGNED_CERT_3, TEST_PUBLIC_SIGN_KEY_3) } returns Date(Date().time + TEST_VALIDITY_THRESHOLD + 2)

        every {
            mockAuthManager.createUserSignedMembershipCertificate(
                TEST_USER_ID,
                TEST_GROUP_ID_1,
                TEST_ADMIN_STATE_1,
                TEST_USER_ID,
                TEST_SIGNED_IN_PRIVATE_KEY
            )
        } returns TEST_RENEWED_SELF_SIGNED_CERT_1

        every {
            mockAuthManager.createUserSignedMembershipCertificate(
                TEST_USER_ID,
                TEST_GROUP_ID_2,
                TEST_ADMIN_STATE_2,
                TEST_USER_ID,
                TEST_SIGNED_IN_PRIVATE_KEY
            )
        } returns TEST_RENEWED_SELF_SIGNED_CERT_2

        coEvery { mockBackend.renewCertificate(TEST_SERVER_SIGNED_CERT_1) }
            .returns(RenewCertificateResponse(TEST_RENEWED_SERVER_SIGNED_CERT_1))
        coEvery { mockBackend.renewCertificate(TEST_SERVER_SIGNED_CERT_2) }
            .returns(RenewCertificateResponse(TEST_RENEWED_SERVER_SIGNED_CERT_2))

        coEvery { mockGroupStorageManager.loadTeam(TEST_GROUP_ID_1) } returns TEST_GROUP_1
        coEvery { mockGroupStorageManager.loadTeam(TEST_GROUP_ID_2) } returns null
        coEvery { mockGroupStorageManager.loadMeetup(TEST_GROUP_ID_2) } returns TEST_GROUP_2

        val encryptDataSlot1 = slot<Data>()
        val encryptDataSlot2 = slot<Data>()
        coEvery { mockCryptoManager.encrypt(capture(encryptDataSlot1), TEST_SECRET_KEY_GROUP_1) } returns TEST_ENCRYPT_NEW_MEMBERSHIP_1
        coEvery { mockCryptoManager.encrypt(capture(encryptDataSlot2), TEST_SECRET_KEY_GROUP_2) } returns TEST_ENCRYPT_NEW_MEMBERSHIP_2

        every { mockCryptoManager.tokenKeyForGroup(TEST_SECRET_KEY_GROUP_1, mockSignedInUser) } returns TEST_TOKEN_KEY_1
        every { mockCryptoManager.tokenKeyForGroup(TEST_SECRET_KEY_GROUP_2, mockSignedInUser) } returns TEST_TOKEN_KEY_2

        coEvery { mockGroupStorageManager.loadMembershipsOfGroup(TEST_GROUP_ID_1) } returns setOf(TEST_MEMBERSHIP_1, TEST_MEMBERSHIP_3)
        coEvery { mockGroupStorageManager.loadMembershipsOfGroup(TEST_GROUP_ID_2) } returns setOf(TEST_MEMBERSHIP_2, TEST_MEMBERSHIP_3)

        coEvery {
            mockBackend.updateGroupMember(
                TEST_GROUP_ID_1,
                TEST_USER_ID,
                TEST_ENCRYPT_NEW_MEMBERSHIP_1,
                TEST_RENEWED_SERVER_SIGNED_CERT_1,
                TEST_TOKEN_KEY_1,
                TEST_GROUP_TAG_1,
                listOf(TEST_NOTIFICATION_RECIPIENTS_1)
            )
        } returns UpdatedETagResponse(TEST_NEW_GROUP_TAG_1)

        coEvery {
            mockBackend.updateGroupMember(
                TEST_GROUP_ID_2,
                TEST_USER_ID,
                TEST_ENCRYPT_NEW_MEMBERSHIP_2,
                TEST_RENEWED_SERVER_SIGNED_CERT_2,
                TEST_TOKEN_KEY_2,
                TEST_GROUP_TAG_2,
                listOf(TEST_NOTIFICATION_RECIPIENTS_1),
            )
        } returns UpdatedETagResponse(TEST_NEW_GROUP_TAG_2)

        mockkStatic("tice.utility.Base64ConvertFunctionsKt")
        val slotByteArray = slot<ByteArray>()
        every { capture(slotByteArray).toBase64String() } answers { String(slotByteArray.captured) }

        val result = membershipRenewalTask.doWork()

        TEST_GROUP_1.tag = TEST_NEW_GROUP_TAG_1
        TEST_GROUP_2.tag = TEST_NEW_GROUP_TAG_2

        Assertions.assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { mockGroupStorageManager.loadMembershipsOfUser(TEST_USER_ID) }
        coVerify(exactly = 1) { mockBeekeeper.track(BeekeeperEvent.membershipRenewalWorkerStarted()) }
        coVerify(exactly = 1) { mockBeekeeper.track(BeekeeperEvent.membershipRenewalWorkerCompleted()) }
        coVerify(exactly = 1) { mockBackend.renewCertificate(TEST_SERVER_SIGNED_CERT_1) }
        coVerify(exactly = 1) { mockBackend.renewCertificate(TEST_SERVER_SIGNED_CERT_2) }
        coVerify(exactly = 1) {
            mockGroupStorageManager.storeTeam(TEST_GROUP_1, MembershipsDiff.Replace(listOf(TEST_EXPECTED_MEMBERSHIP_1)))
        }
        coVerify(exactly = 1) {
            mockGroupStorageManager.storeMeetup(TEST_GROUP_2, MembershipsDiff.Replace(listOf(TEST_EXPECTED_MEMBERSHIP_2)))
        }
        coVerify(exactly = 1) {
            mockAuthManager.createUserSignedMembershipCertificate(
                TEST_USER_ID,
                TEST_GROUP_ID_2,
                TEST_ADMIN_STATE_2,
                TEST_USER_ID,
                TEST_SIGNED_IN_PRIVATE_KEY
            )
        }
        coVerify(exactly = 1) {
            mockAuthManager.createUserSignedMembershipCertificate(
                TEST_USER_ID,
                TEST_GROUP_ID_1,
                TEST_ADMIN_STATE_1,
                TEST_USER_ID,
                TEST_SIGNED_IN_PRIVATE_KEY
            )
        }
    }


    @Test
    fun doWork_FailedAtSecondMembership() = runBlockingTest {
        every { mockSignedInUserManager.signedIn() } returns true

        coEvery { mockGroupStorageManager.loadMembershipsOfUser(TEST_USER_ID) }
            .returns(setOf(TEST_MEMBERSHIP_1, TEST_MEMBERSHIP_2, TEST_MEMBERSHIP_3))

        every { mockAuthManager.membershipCertificateExpirationDate(TEST_SELF_SIGNED_CERT_1, TEST_PUBLIC_SIGN_KEY_1) } returns Date(Date().time + TEST_VALIDITY_THRESHOLD + 2)
        every { mockAuthManager.membershipCertificateExpirationDate(TEST_SERVER_SIGNED_CERT_1, TEST_PUBLIC_SIGN_KEY_1) } returns Date(Date().time + TEST_VALIDITY_THRESHOLD - 2)

        every { mockAuthManager.membershipCertificateExpirationDate(TEST_SELF_SIGNED_CERT_2, TEST_PUBLIC_SIGN_KEY_2) } returns Date(Date().time + TEST_VALIDITY_THRESHOLD - 2)
        every { mockAuthManager.membershipCertificateExpirationDate(TEST_SERVER_SIGNED_CERT_2, TEST_PUBLIC_SIGN_KEY_2) } returns Date(Date().time + TEST_VALIDITY_THRESHOLD + 2)

        every { mockAuthManager.membershipCertificateExpirationDate(TEST_SELF_SIGNED_CERT_3, TEST_PUBLIC_SIGN_KEY_3) } returns Date(Date().time + TEST_VALIDITY_THRESHOLD + 2)
        every { mockAuthManager.membershipCertificateExpirationDate(TEST_SERVER_SIGNED_CERT_3, TEST_PUBLIC_SIGN_KEY_3) } returns Date(Date().time + TEST_VALIDITY_THRESHOLD + 2)

        every {
            mockAuthManager.createUserSignedMembershipCertificate(
                TEST_USER_ID,
                TEST_GROUP_ID_1,
                TEST_ADMIN_STATE_1,
                TEST_USER_ID,
                TEST_SIGNED_IN_PRIVATE_KEY
            )
        } returns TEST_RENEWED_SELF_SIGNED_CERT_1

        every {
            mockAuthManager.createUserSignedMembershipCertificate(
                TEST_USER_ID,
                TEST_GROUP_ID_2,
                TEST_ADMIN_STATE_2,
                TEST_USER_ID,
                TEST_SIGNED_IN_PRIVATE_KEY
            )
        } returns TEST_RENEWED_SELF_SIGNED_CERT_2

        coEvery { mockBackend.renewCertificate(TEST_SERVER_SIGNED_CERT_1) }
            .returns(RenewCertificateResponse(TEST_RENEWED_SERVER_SIGNED_CERT_1))
        coEvery { mockBackend.renewCertificate(TEST_SERVER_SIGNED_CERT_2) }
            .returns(RenewCertificateResponse(TEST_RENEWED_SERVER_SIGNED_CERT_2))

        coEvery { mockGroupStorageManager.loadTeam(TEST_GROUP_ID_1) } returns TEST_GROUP_1
        coEvery { mockGroupStorageManager.loadTeam(TEST_GROUP_ID_2) } returns null
        coEvery { mockGroupStorageManager.loadMeetup(TEST_GROUP_ID_2) } returns TEST_GROUP_2

        val encryptDataSlot1 = slot<Data>()
        val encryptDataSlot2 = slot<Data>()
        coEvery { mockCryptoManager.encrypt(capture(encryptDataSlot1), TEST_SECRET_KEY_GROUP_1) } returns TEST_ENCRYPT_NEW_MEMBERSHIP_1
        coEvery { mockCryptoManager.encrypt(capture(encryptDataSlot2), TEST_SECRET_KEY_GROUP_2) } returns TEST_ENCRYPT_NEW_MEMBERSHIP_2

        every { mockCryptoManager.tokenKeyForGroup(TEST_SECRET_KEY_GROUP_1, mockSignedInUser) } returns TEST_TOKEN_KEY_1
        every { mockCryptoManager.tokenKeyForGroup(TEST_SECRET_KEY_GROUP_2, mockSignedInUser) } returns TEST_TOKEN_KEY_2

        coEvery { mockGroupStorageManager.loadMembershipsOfGroup(TEST_GROUP_ID_1) } returns setOf(TEST_MEMBERSHIP_1, TEST_MEMBERSHIP_3)
        coEvery { mockGroupStorageManager.loadMembershipsOfGroup(TEST_GROUP_ID_2) } returns setOf(TEST_MEMBERSHIP_2, TEST_MEMBERSHIP_3)

        coEvery {
            mockBackend.updateGroupMember(
                TEST_GROUP_ID_1,
                TEST_USER_ID,
                TEST_ENCRYPT_NEW_MEMBERSHIP_1,
                TEST_RENEWED_SERVER_SIGNED_CERT_1,
                TEST_TOKEN_KEY_1,
                TEST_GROUP_TAG_1,
                listOf(TEST_NOTIFICATION_RECIPIENTS_1)
            )
        } returns UpdatedETagResponse(TEST_NEW_GROUP_TAG_1)

        coEvery {
            mockBackend.updateGroupMember(
                TEST_GROUP_ID_2,
                TEST_USER_ID,
                TEST_ENCRYPT_NEW_MEMBERSHIP_2,
                TEST_RENEWED_SERVER_SIGNED_CERT_2,
                TEST_TOKEN_KEY_2,
                TEST_GROUP_TAG_2,
                listOf(TEST_NOTIFICATION_RECIPIENTS_1),
            )
        } throws BackendException.GroupOutdated

        mockkStatic("tice.utility.Base64ConvertFunctionsKt")
        val slotByteArray = slot<ByteArray>()
        every { capture(slotByteArray).toBase64String() } answers { String(slotByteArray.captured) }

        val result = membershipRenewalTask.doWork()

        TEST_GROUP_1.tag = TEST_NEW_GROUP_TAG_1
        TEST_GROUP_2.tag = TEST_NEW_GROUP_TAG_2

        Assertions.assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { mockGroupStorageManager.loadMembershipsOfUser(TEST_USER_ID) }
        coVerify(exactly = 1) { mockBeekeeper.track(BeekeeperEvent.membershipRenewalWorkerStarted()) }
        coVerify(exactly = 1) { mockBeekeeper.track(BeekeeperEvent.membershipRenewalWorkerCompleted()) }
        coVerify(exactly = 1) { mockBackend.renewCertificate(TEST_SERVER_SIGNED_CERT_1) }
        coVerify(exactly = 1) { mockBackend.renewCertificate(TEST_SERVER_SIGNED_CERT_2) }
        coVerify(exactly = 1) {
            mockGroupStorageManager.storeTeam(TEST_GROUP_1, MembershipsDiff.Replace(listOf(TEST_EXPECTED_MEMBERSHIP_1)))
        }
        coVerify(exactly = 0) {
            mockGroupStorageManager.storeMeetup(TEST_GROUP_2, MembershipsDiff.Replace(listOf(TEST_EXPECTED_MEMBERSHIP_2)))
        }
        coVerify(exactly = 1) {
            mockAuthManager.createUserSignedMembershipCertificate(
                TEST_USER_ID,
                TEST_GROUP_ID_2,
                TEST_ADMIN_STATE_2,
                TEST_USER_ID,
                TEST_SIGNED_IN_PRIVATE_KEY
            )
        }
        coVerify(exactly = 1) {
            mockAuthManager.createUserSignedMembershipCertificate(
                TEST_USER_ID,
                TEST_GROUP_ID_1,
                TEST_ADMIN_STATE_1,
                TEST_USER_ID,
                TEST_SIGNED_IN_PRIVATE_KEY
            )
        }
    }
}