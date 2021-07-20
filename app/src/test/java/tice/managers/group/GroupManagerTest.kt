package tice.managers.group

import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tice.backend.BackendType
import tice.crypto.AuthManagerType
import tice.crypto.CryptoManagerType
import tice.exceptions.GroupManagerException
import tice.managers.SignedInUserManagerType
import tice.managers.messaging.MailboxType
import tice.managers.storageManagers.GroupStorageManagerType
import tice.models.*
import tice.models.messaging.GroupUpdate
import tice.models.messaging.MessagePriority
import tice.models.messaging.Payload
import tice.models.messaging.PayloadContainer
import tice.models.responses.UpdatedETagResponse
import tice.utility.dataFromBase64
import tice.utility.serializer.MembershipSerializer
import tice.utility.toBase64String
import java.lang.ref.WeakReference
import java.util.*

internal class GroupManagerTest {

    private lateinit var groupManager: GroupManager

    private val mockGroupStorageManager: GroupStorageManagerType = mockk(relaxUnitFun = true)
    private val mockCryptoManager: CryptoManagerType = mockk(relaxUnitFun = true)
    private val mockAuthManager: AuthManagerType = mockk(relaxUnitFun = true)
    private val mockSignedInUserManager: SignedInUserManagerType = mockk(relaxUnitFun = true)
    private val mockMailbox: MailboxType = mockk(relaxUnitFun = true)
    private val mockBackend: BackendType = mockk(relaxUnitFun = true)

    private val mockSignedInUser: SignedInUser = mockk(relaxUnitFun = true)
    private val TEST_SIGNED_IN_USER_ID = UUID.randomUUID()
    private val TEST_SIGNED_IN_PUBLIC_KEY = "PublicKey".toByteArray()
    private val TEST_SIGNED_IN_PRIVATE_KEY = "PrivateKey".toByteArray()
    private val TEST_OTHER_USER_ID = UUID.randomUUID()

    private val mockGroup: Group = mockk(relaxUnitFun = true)
    private val mockSignedInMembership: Membership = mockk(relaxUnitFun = true)
    private val mockOtherMembership: Membership = mockk(relaxUnitFun = true)
    private lateinit var TEST_MEMBERSHIPS: Set<Membership>

    private val TEST_GROUP_ID = UUID.randomUUID()
    private val TEST_GROUP_KEY = "groupKey".toByteArray()
    private val TEST_GROUP_TAG1 = "GroupTag1"
    private val TEST_GROUP_TAG2 = "GroupTag2"

    private val TEST_SELF_CERTIFICATE = "selfSignedMembershipCertificate"
    private val TEST_SERVER_CERTIFICATE = "serverSignedMembershipCertificate"
    private val TEST_OTHER_SERVER_CERTIFICATE = "OTHER_SERVER_SIGNED"

    private val TEST_NOTIFICATION_RECEIPT = NotificationRecipient(TEST_OTHER_USER_ID, TEST_OTHER_SERVER_CERTIFICATE, MessagePriority.Alert)
    private val TEST_NOTIFICATION_RECEIPT_SET = listOf(TEST_NOTIFICATION_RECEIPT)

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        groupManager = GroupManager(
            mockSignedInUserManager,
            mockBackend,
            mockCryptoManager,
            mockAuthManager,
            mockGroupStorageManager,
            mockMailbox
        )

        TEST_MEMBERSHIPS = setOf(mockSignedInMembership, mockOtherMembership)

        every { mockSignedInUserManager.signedInUser } returns mockSignedInUser
        every { mockSignedInUser.userId } returns TEST_SIGNED_IN_USER_ID
        every { mockSignedInUser.publicSigningKey } returns TEST_SIGNED_IN_PUBLIC_KEY
        every { mockSignedInUser.privateSigningKey } returns TEST_SIGNED_IN_PRIVATE_KEY

        every { mockSignedInMembership.userId } returns TEST_SIGNED_IN_USER_ID
        every { mockSignedInMembership.serverSignedMembershipCertificate } returns TEST_SERVER_CERTIFICATE
        every { mockOtherMembership.admin } returns false
        every { mockOtherMembership.userId } returns TEST_OTHER_USER_ID
        every { mockOtherMembership.serverSignedMembershipCertificate } returns TEST_OTHER_SERVER_CERTIFICATE

        every { mockGroup.groupKey } returns TEST_GROUP_KEY
        every { mockGroup.groupId } returns TEST_GROUP_ID
        every { mockGroup.tag } returns TEST_GROUP_TAG1

        every { mockCryptoManager.encrypt(any(), any()) } answers { arg(0) as Ciphertext }
        every { mockCryptoManager.decrypt(any(), any()) } answers { arg(0) as Ciphertext }
        every { mockCryptoManager.tokenKeyForGroup(any(), any()) } returns "token".toByteArray()
    }

    @Test
    fun `Registered at init for delegate in SignedInUserManager`() {
        val slot = slot<WeakReference<GroupManagerType>>()

        groupManager.registerForDelegate()

        verify(exactly = 1) { mockSignedInUserManager.groupManagerDelegate = capture(slot) }

        Assertions.assertEquals(groupManager, slot.captured.get())
    }

    @Test
    fun addUserMember() = runBlockingTest {
        mockkStatic("tice.utility.Base64ConvertFunctionsKt")
        val slotString = slot<String>()
        val slotByteArray = slot<ByteArray>()
        every { capture(slotString).dataFromBase64() } answers { slotString.captured.toByteArray() }
        every { capture(slotByteArray).toBase64String() } answers { String(slotByteArray.captured) }
        val IS_ADMIN = true

        val membership = Membership(
            TEST_SIGNED_IN_USER_ID,
            TEST_GROUP_ID,
            TEST_SIGNED_IN_PUBLIC_KEY,
            IS_ADMIN,
            TEST_SELF_CERTIFICATE,
            TEST_SERVER_CERTIFICATE
        )
        val membershipData = Json.encodeToString(MembershipSerializer, membership).toByteArray()

        val EXPECTED_TOKEN = "token".toByteArray()

        every {
            mockAuthManager.createUserSignedMembershipCertificate(
                TEST_SIGNED_IN_USER_ID,
                TEST_GROUP_ID,
                IS_ADMIN,
                TEST_SIGNED_IN_USER_ID,
                TEST_SIGNED_IN_PRIVATE_KEY
            )
        } returns TEST_SELF_CERTIFICATE
        coEvery { mockGroupStorageManager.loadMembershipsOfGroup(TEST_GROUP_ID) } returns TEST_MEMBERSHIPS
        coEvery {
            mockBackend.addGroupMember(
                TEST_GROUP_ID,
                TEST_SIGNED_IN_USER_ID,
                membershipData,
                TEST_SERVER_CERTIFICATE,
                EXPECTED_TOKEN,
                TEST_GROUP_TAG1,
                TEST_NOTIFICATION_RECEIPT_SET
            )
        } returns UpdatedETagResponse(TEST_GROUP_TAG2)

        val result = groupManager.addUserMember(mockGroup, IS_ADMIN, TEST_SERVER_CERTIFICATE, TEST_NOTIFICATION_RECEIPT_SET)

        Assertions.assertEquals(Pair(membership, TEST_GROUP_TAG2), result)
    }

    @Nested
    inner class Leave {

        @Test
        fun lastAdminException() = runBlockingTest {
            val IS_ADMIN = true

            coEvery { mockGroupStorageManager.loadMembership(TEST_SIGNED_IN_USER_ID, TEST_GROUP_ID) } returns mockSignedInMembership
            every { mockSignedInMembership.admin } returns IS_ADMIN

            Assertions.assertThrows(GroupManagerException.LastAdminException::class.java) {
                runBlocking { groupManager.leave(mockGroup, emptySet()) }
            }
        }

        @Test
        fun success() = runBlockingTest {
            val IS_ADMIN = false
            val EXPECTED_TOKEN = "token".toByteArray()

            coEvery { mockGroupStorageManager.loadMembership(TEST_SIGNED_IN_USER_ID, TEST_GROUP_ID) } returns mockSignedInMembership
            every { mockSignedInMembership.admin } returns IS_ADMIN

            coEvery { mockGroupStorageManager.loadMembershipsOfGroup(TEST_GROUP_ID) } returns TEST_MEMBERSHIPS
            coEvery {
                mockBackend.deleteGroupMember(
                    TEST_GROUP_ID,
                    TEST_GROUP_TAG1,
                    EXPECTED_TOKEN,
                    TEST_SIGNED_IN_USER_ID,
                    TEST_SERVER_CERTIFICATE,
                    TEST_SERVER_CERTIFICATE,
                    TEST_NOTIFICATION_RECEIPT_SET
                )
            } returns UpdatedETagResponse(TEST_GROUP_TAG2)

            val result = groupManager.leave(mockGroup, TEST_NOTIFICATION_RECEIPT_SET)

            Assertions.assertEquals(TEST_GROUP_TAG2, result)
        }
    }

    @Nested
    inner class DeleteGroupMember {

        @Test
        fun success() = runBlockingTest {
            val IS_ADMIN = false
            val EXPECTED_TOKEN = "token".toByteArray()

            coEvery { mockGroupStorageManager.loadMembership(TEST_SIGNED_IN_USER_ID, TEST_GROUP_ID) } returns mockSignedInMembership
            every { mockSignedInMembership.admin } returns IS_ADMIN

            coEvery { mockGroupStorageManager.loadMembershipsOfGroup(TEST_GROUP_ID) } returns TEST_MEMBERSHIPS
            coEvery { mockGroupStorageManager.loadUser(mockOtherMembership) } returns mockk()
            coEvery {
                mockBackend.deleteGroupMember(
                    TEST_GROUP_ID,
                    TEST_GROUP_TAG1,
                    EXPECTED_TOKEN,
                    TEST_OTHER_USER_ID,
                    TEST_OTHER_SERVER_CERTIFICATE,
                    TEST_SERVER_CERTIFICATE,
                    TEST_NOTIFICATION_RECEIPT_SET
                )
            } returns UpdatedETagResponse(TEST_GROUP_TAG2)

            val result =
                groupManager.deleteGroupMember(mockOtherMembership, mockGroup, TEST_SERVER_CERTIFICATE, TEST_NOTIFICATION_RECEIPT_SET)

            Assertions.assertEquals(TEST_GROUP_TAG2, result)
        }

        @Test
        fun isAdmin() = runBlockingTest {
            val IS_ADMIN = true

            coEvery { mockGroupStorageManager.loadMembership(TEST_SIGNED_IN_USER_ID, TEST_GROUP_ID) } returns mockSignedInMembership
            every { mockOtherMembership.admin } returns IS_ADMIN

            Assertions.assertThrows(GroupManagerException.LastAdminException::class.java) {
                runBlocking { groupManager.deleteGroupMember(mockOtherMembership, mockGroup, TEST_SERVER_CERTIFICATE, emptySet()) }
            }
        }
    }

    @Test
    fun notificationRecipients() = runBlockingTest {
        val TEST_MEMBERSHIPS = setOf(mockSignedInMembership, mockOtherMembership)

        coEvery { mockGroupStorageManager.loadMembershipsOfGroup(TEST_GROUP_ID) } returns TEST_MEMBERSHIPS

        val EXPECTED_NOTIFICATION_RECEIPT =
            NotificationRecipient(mockOtherMembership.userId, TEST_OTHER_SERVER_CERTIFICATE, MessagePriority.Alert)

        val result = groupManager.notificationRecipients(TEST_GROUP_ID, MessagePriority.Alert)

        Assertions.assertTrue(result.contains(EXPECTED_NOTIFICATION_RECEIPT))
        Assertions.assertEquals(1, result.count())
    }

    @Test
    fun send() = runBlockingTest {
        val mockPayloadContainer = mockk<PayloadContainer>()
        val membersList = setOf(mockOtherMembership)

        coEvery { mockGroupStorageManager.loadMembership(TEST_SIGNED_IN_USER_ID, TEST_GROUP_ID) } returns mockSignedInMembership
        coEvery { mockGroupStorageManager.loadMembershipsOfGroup(TEST_GROUP_ID) } returns membersList

        groupManager.send(mockPayloadContainer, mockGroup, "collapseId", MessagePriority.Alert)

        coVerify(exactly = 1) {
            mockMailbox.sendToGroup(mockPayloadContainer, membersList, TEST_SERVER_CERTIFICATE, MessagePriority.Alert, "collapseId")
        }
    }

    @Test
    fun sendGroupUpdateNotification() = runBlockingTest {
        val TEST_ACTION = GroupUpdate.Action.MEMBER_ADDED

        val membersList = setOf(mockOtherMembership)

        val EXPECTED_GROUP_UPDATE = GroupUpdate(TEST_GROUP_ID, TEST_ACTION)
        val EXPECTED_PAYLOAD_CONTAINER = PayloadContainer(Payload.PayloadType.GroupUpdateV1, EXPECTED_GROUP_UPDATE)

        coEvery { mockGroupStorageManager.loadMembership(TEST_SIGNED_IN_USER_ID, TEST_GROUP_ID) } returns mockSignedInMembership
        coEvery { mockGroupStorageManager.loadMembershipsOfGroup(TEST_GROUP_ID) } returns membersList

        groupManager.sendGroupUpdateNotification(mockGroup, TEST_ACTION)

        coVerify(exactly = 1) {
            mockMailbox.sendToGroup(EXPECTED_PAYLOAD_CONTAINER, membersList, TEST_SERVER_CERTIFICATE, MessagePriority.Alert, null)
        }
    }
}