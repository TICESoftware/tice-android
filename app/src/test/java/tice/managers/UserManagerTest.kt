package ticeTest.managers

import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tice.backend.BackendType
import tice.exceptions.UnexpectedPayloadTypeException
import tice.managers.SignedInUserManagerType
import tice.managers.UserManager
import tice.managers.messaging.PostOffice
import tice.managers.storageManagers.UserStorageManagerType
import tice.models.SignedInUser
import tice.models.User
import tice.models.messaging.*
import tice.models.responses.GetUserResponse
import java.util.*

internal class UserManagerTest {

    private lateinit var userManager: UserManager

    private val mockBackend: BackendType = mockk(relaxUnitFun = true)
    private val mockSignedInUserManager: SignedInUserManagerType = mockk(relaxUnitFun = true)
    private val mockPostOffice: PostOffice = mockk(relaxUnitFun = true)
    private val mockUserStorageManager: UserStorageManagerType = mockk(relaxUnitFun = true)

    private val mockSignedInUser: SignedInUser = mockk(relaxUnitFun = true)
    private val TEST_SIGNED_IN_USER_ID = UUID.randomUUID()

    private val TEST_UUID = UUID.randomUUID()
    private val TEST_NAME = "name"
    private val TEST_KEY = "key".toByteArray()

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        userManager = UserManager(
            mockPostOffice,
            mockBackend,
            mockSignedInUserManager,
            mockUserStorageManager
        )

        every { mockSignedInUserManager.signedInUser } returns mockSignedInUser
        every { mockSignedInUser.userId } returns TEST_SIGNED_IN_USER_ID
    }

    @Test
    fun init_RegisterEnvelopeReceiver() = runBlockingTest {
        userManager.registerEnvelopeReceiver()

        verify(exactly = 1) { mockPostOffice.registerEnvelopeReceiver(Payload.PayloadType.UserUpdateV1, userManager) }
    }

    @Test
    fun getUser_signedInUser() = runBlockingTest {
        val resultUser = userManager.getUser(TEST_SIGNED_IN_USER_ID)

        Assertions.assertEquals(mockSignedInUser, resultUser)
    }

    @Test
    fun getUser() = runBlockingTest {
        val mockOtherUser = mockk<User>()
        coEvery { mockUserStorageManager.loadUser(TEST_UUID) } returns mockOtherUser

        val resultUser = userManager.getUser(TEST_UUID)

        Assertions.assertEquals(mockOtherUser, resultUser)
    }

    @Test
    fun getOrFetchUser_UserAvailable() = runBlockingTest {
        val mockOtherUser = mockk<User>()
        coEvery { mockUserStorageManager.loadUser(TEST_UUID) } returns mockOtherUser

        val resultUser = userManager.getOrFetchUser(TEST_UUID)

        Assertions.assertEquals(mockOtherUser, resultUser)
        confirmVerified(mockBackend)
    }

    @Test
    fun getOrFetchUser_NoUserAvailable() = runBlockingTest {
        val getUserResponse = GetUserResponse(TEST_UUID, TEST_KEY, TEST_NAME)

        coEvery { mockUserStorageManager.loadUser(TEST_UUID) } returns null
        coEvery { mockBackend.getUser(TEST_UUID) } returns getUserResponse

        val EXPECTED_NEW_USER = User(TEST_UUID, TEST_KEY, TEST_NAME)

        val resultUser = userManager.getOrFetchUser(TEST_UUID)

        Assertions.assertEquals(EXPECTED_NEW_USER, resultUser)
        coVerify(exactly = 1) { mockBackend.getUser(TEST_UUID) }

    }

    @Test
    fun receivePayloadContainerBundle_WrongPayloadType() = runBlockingTest {
        val payloadContainerBundle =
            PayloadContainerBundle(Payload.PayloadType.EncryptedPayloadContainerV1, ResetConversation, mockk<PayloadMetaInfo>())

        Assertions.assertThrows(UnexpectedPayloadTypeException::class.java) {
            runBlocking {
                userManager.handlePayloadContainerBundle(payloadContainerBundle)
            }
        }
    }

    @Test
    fun receivePayloadContainerBundle() = runBlockingTest {
        val mockMetaInfo = mockk<PayloadMetaInfo>()
        val payloadBundle = PayloadContainerBundle(Payload.PayloadType.UserUpdateV1, UserUpdate(TEST_UUID), mockMetaInfo)
        val getUserResponse = GetUserResponse(TEST_UUID, TEST_KEY, TEST_NAME)

        coEvery { mockBackend.getUser(TEST_UUID) } returns getUserResponse

        val EXPECTED_NEW_USER = User(TEST_UUID, TEST_KEY, TEST_NAME)

        userManager.handlePayloadContainerBundle(payloadBundle)

        coVerify(exactly = 1) { mockUserStorageManager.store(EXPECTED_NEW_USER) }
    }

    @Test
    fun receivePayloadContainerBundle_FetchThrows() = runBlockingTest {
        val mockMetaInfo = mockk<PayloadMetaInfo>()
        val payloadBundle = PayloadContainerBundle(Payload.PayloadType.UserUpdateV1, UserUpdate(TEST_UUID), mockMetaInfo)

        coEvery { mockBackend.getUser(TEST_UUID) } throws Exception()

        userManager.handlePayloadContainerBundle(payloadBundle)

        confirmVerified(mockUserStorageManager)
    }
}
