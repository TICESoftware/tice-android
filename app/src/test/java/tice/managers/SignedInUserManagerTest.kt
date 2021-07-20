package tice.managers

import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tice.exceptions.SignedInUserStorageManagerException
import tice.managers.group.GroupManagerType
import tice.managers.storageManagers.GroupStorageManagerType
import tice.managers.storageManagers.SignedInUserStorageManagerType
import tice.managers.storageManagers.UserStorageManagerType
import tice.models.SignedInUser
import tice.models.Team
import tice.models.messaging.MessagePriority
import tice.models.messaging.Payload
import tice.models.messaging.PayloadContainer
import tice.models.messaging.UserUpdate
import tice.utility.provider.CoroutineContextProviderType
import java.lang.ref.WeakReference
import java.util.*

internal class SignedInUserManagerTest {

    private lateinit var signedInUserManager: SignedInUserManager

    private val mockSignedInUserStorageManager: SignedInUserStorageManagerType = mockk(relaxUnitFun = true)
    private val mockUserStorageManager: UserStorageManagerType = mockk(relaxUnitFun = true)
    private val mockGroupStorageManagerType: GroupStorageManagerType = mockk(relaxUnitFun = true)
    private val mockCoroutineContextProvider: CoroutineContextProviderType = mockk(relaxUnitFun = true)

    private lateinit var testJob: Job

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        testJob = Job()

        every { mockCoroutineContextProvider.IO } returns testJob

        signedInUserManager = SignedInUserManager(
            mockSignedInUserStorageManager,
            mockUserStorageManager,
            mockGroupStorageManagerType,
            mockCoroutineContextProvider
        )
    }

    @Test
    fun testInit() = runBlocking {
        clearAllMocks()
        val testSignedInUser: SignedInUser = mockk()

        val testCoroutineDispatcher = TestCoroutineDispatcher()
        testCoroutineDispatcher.pauseDispatcher()

        every { mockCoroutineContextProvider.IO } returns testCoroutineDispatcher
        coEvery { mockSignedInUserStorageManager.loadSignedInUser() } returns testSignedInUser

        signedInUserManager.setup()

        val callWhichJoins = CoroutineScope(Dispatchers.IO).launch {
            val result = withTimeoutOrNull(2000) {
                signedInUserManager.signedInUser
            }

            assertEquals(testSignedInUser, result)
        }

        testCoroutineDispatcher.runCurrent()
        callWhichJoins.join()

        coVerify(exactly = 1) { mockSignedInUserStorageManager.loadSignedInUser() }
    }

    @Nested
    inner class `Get SignedInUser` {

        @Test
        fun `Success`() = runBlockingTest {
            val testSignedInUser: SignedInUser = mockk()

            coEvery { mockSignedInUserStorageManager.loadSignedInUser() } returns testSignedInUser
            signedInUserManager.setup()
            val resultSignedInUser = signedInUserManager.signedInUser

            assertEquals(testSignedInUser, resultSignedInUser)
        }

        @Test
        fun `Called multiple times and Fetched just once`() = runBlockingTest {
            val testSignedInUser: SignedInUser = mockk()

            coEvery { mockSignedInUserStorageManager.loadSignedInUser() } returns testSignedInUser
            signedInUserManager.setup()

            val resultSignedInUser1 = signedInUserManager.signedInUser
            val resultSignedInUser2 = signedInUserManager.signedInUser
            val resultSignedInUser3 = signedInUserManager.signedInUser

            assertEquals(testSignedInUser, resultSignedInUser1)
            assertEquals(testSignedInUser, resultSignedInUser2)
            assertEquals(testSignedInUser, resultSignedInUser3)

            coVerify(exactly = 1) { mockSignedInUserStorageManager.loadSignedInUser() }
            confirmVerified(mockSignedInUserStorageManager)
        }
    }

    @Test
    fun `stores the signedInUser`() = runBlockingTest {
        val testSignedInUser = SignedInUser(UUID.randomUUID(), "testName", "pubKey".toByteArray(), "privateKey".toByteArray())
        signedInUserManager.setup()

        signedInUserManager.storeSignedInUser(testSignedInUser)

        coVerify(exactly = 1) { mockSignedInUserStorageManager.storeSignedInUser(testSignedInUser) }
        coVerify(exactly = 1) { mockSignedInUserStorageManager.loadSignedInUser() }
        confirmVerified(mockSignedInUserStorageManager)
    }

    @Nested
    inner class ChangeSignedInUserName {

        @Test
        fun `Success`() = runBlockingTest {
            val testName = "name"
            val testNameChanged = "newName"
            val testUserId = UUID.randomUUID()
            val testSignedInUser = SignedInUser(testUserId, testName, "pubKey".toByteArray(), "privateKey".toByteArray())

            val mockTeam1 = mockk<Team>()
            val mockTeam2 = mockk<Team>()
            val teamSet = setOf(mockTeam1, mockTeam2)

            val mockGroupManager = mockk<GroupManagerType>(relaxed = true)

            coEvery { mockSignedInUserStorageManager.loadSignedInUser() } returns testSignedInUser
            coEvery { mockGroupStorageManagerType.loadTeams() } returns teamSet

            signedInUserManager.setup()

            signedInUserManager.groupManagerDelegate = WeakReference(mockGroupManager)
            signedInUserManager.changeSignedInUserName(testNameChanged)

            assertEquals(testNameChanged, testSignedInUser.publicName)

            coVerify(exactly = 1) {
                mockGroupManager.send(
                    PayloadContainer(Payload.PayloadType.UserUpdateV1, UserUpdate(testUserId)),
                    mockTeam1,
                    null,
                    MessagePriority.Alert
                )
            }
            coVerify(exactly = 1) {
                mockGroupManager.send(
                    PayloadContainer(Payload.PayloadType.UserUpdateV1, UserUpdate(testUserId)),
                    mockTeam2,
                    null,
                    MessagePriority.Alert
                )
            }
            coVerify(exactly = 1) { mockSignedInUserStorageManager.loadSignedInUser() }
            coVerify(exactly = 1) { mockSignedInUserStorageManager.storeSignedInUser(testSignedInUser) }
            confirmVerified(mockSignedInUserStorageManager)
        }

        @Test
        fun `throws NoSignedInUserException`() = runBlockingTest {
            val testNameChanged = "newName"

            coEvery { mockSignedInUserStorageManager.loadSignedInUser() } throws SignedInUserStorageManagerException.NotSignedIn
            signedInUserManager.setup()

            assertThrows(SignedInUserStorageManagerException.NotSignedIn::class.java)
            { runBlocking { signedInUserManager.changeSignedInUserName(testNameChanged) } }

            coVerify(exactly = 2) { mockSignedInUserStorageManager.loadSignedInUser() }
            confirmVerified(mockSignedInUserStorageManager)
        }
    }

    @Nested
    inner class SignIn {

        @Test
        fun `is not signed in`() {
            coEvery { mockSignedInUserStorageManager.loadSignedInUser() } throws SignedInUserStorageManagerException.NotSignedIn
            signedInUserManager.setup()

            val result = signedInUserManager.signedIn()

            assertFalse(result)
        }


        @Test
        fun `is signed in`() {
            coEvery { mockSignedInUserStorageManager.loadSignedInUser() } returns mockk()
            signedInUserManager.setup()

            val result = signedInUserManager.signedIn()

            assertTrue(result)
        }
    }

    @Test
    fun `delete SignedInUser`() = runBlockingTest {
        signedInUserManager.setup()

        signedInUserManager.deleteSignedInUser()

        coEvery { mockSignedInUserStorageManager.loadSignedInUser() } throws SignedInUserStorageManagerException.NotSignedIn

        assertThrows(SignedInUserStorageManagerException.NotSignedIn::class.java) {
            signedInUserManager.signedInUser
        }

        coVerify(exactly = 1) { mockSignedInUserStorageManager.deleteSignedInUser() }
        coVerify(exactly = 2) { mockSignedInUserStorageManager.loadSignedInUser() }
        confirmVerified(mockSignedInUserStorageManager)
    }
}
