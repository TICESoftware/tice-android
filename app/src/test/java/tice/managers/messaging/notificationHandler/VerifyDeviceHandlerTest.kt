package tice.managers.messaging.notificationHandler

import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tice.backend.BackendType
import tice.exceptions.VerifyDeviceHandlerException
import tice.helper.joinAllChildren
import tice.managers.SignedInUserManagerType
import tice.managers.messaging.PostOfficeType
import tice.managers.storageManagers.DeviceIdStorageManagerType
import tice.models.Platform
import tice.models.SignedInUser
import tice.models.VerificationCode
import tice.models.messaging.Payload
import tice.models.messaging.PayloadContainerBundle
import tice.models.messaging.ResetConversation
import tice.models.messaging.VerificationMessage
import tice.utility.provider.CoroutineContextProviderType
import java.util.*

internal class VerifyDeviceHandlerTest {

    private lateinit var verifyDeviceHandler: VerifyDeviceHandler

    val mockBackend: BackendType = mockk(relaxUnitFun = true)
    val mockCoroutineContextProvider: CoroutineContextProviderType = mockk(relaxUnitFun = true)
    val mockPostOffice: PostOfficeType = mockk(relaxUnitFun = true)
    val mockDeviceIdStorageManager: DeviceIdStorageManagerType = mockk(relaxUnitFun = true)
    val mockSignedInUser: SignedInUser = mockk(relaxUnitFun = true)
    val mockSignedInUserManagerType: SignedInUserManagerType = mockk(relaxUnitFun = true)

    private val TEST_TIMEOUT = 5000L

    private val TEST_DEVICE_ID = "deviceId"
    private val TEST_DEVICE_ID_NEW = "deviceIdNew"
    private val TEST_VERIFICATION_CODE = "verificationMessage"
    private val TEST_VERIFICATION_OBJ = VerificationMessage("verificationMessage")

    private val TEST_USER_ID = UUID.randomUUID()
    private val TEST_USER_NAME = "userName"

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        verifyDeviceHandler = VerifyDeviceHandler(
            mockBackend,
            mockCoroutineContextProvider,
            mockPostOffice,
            mockDeviceIdStorageManager,
            mockSignedInUserManagerType,
            TEST_TIMEOUT
        )
    }

    @Nested
    inner class VerifyDeviceId {

        @Test
        fun success() = runBlocking {
            val TEST_PAYLOAD_CONTAINER = PayloadContainerBundle(Payload.PayloadType.VerificationMessageV1, TEST_VERIFICATION_OBJ, mockk())

            var result: VerificationCode = ""
            val verificationJob = launch {
                result = verifyDeviceHandler.verifyDeviceId(TEST_DEVICE_ID)
            }

            val receiveVerificationCodeJob = launch {
                delay(TEST_TIMEOUT / 2)
                verifyDeviceHandler.handlePayloadContainerBundle(TEST_PAYLOAD_CONTAINER)
            }

            receiveVerificationCodeJob.join()
            verificationJob.join()

            coVerify(exactly = 1) { mockBackend.verify(TEST_DEVICE_ID, Platform.Android) }
            Assertions.assertEquals(TEST_VERIFICATION_CODE, result)
        }

        @Test
        fun `failed with a TimeOut`() = runBlockingTest {
            Assertions.assertThrows(VerifyDeviceHandlerException.VerificationTimedOut::class.java) {
                runBlockingTest { verifyDeviceHandler.verifyDeviceId(TEST_DEVICE_ID) }
            }

            coVerify(exactly = 1) { mockBackend.verify(TEST_DEVICE_ID, Platform.Android) }
        }
    }

    @Nested
    inner class StartUpdatingDeviceId {

        @Test
        fun `store DeviceId without SignedInUser`() = runBlockingTest {
            every { mockSignedInUserManagerType.signedIn() } returns false
            every { mockDeviceIdStorageManager.loadDeviceId() } returns TEST_DEVICE_ID

            verifyDeviceHandler.startUpdatingDeviceId(TEST_DEVICE_ID_NEW)

            verify(exactly = 1) { mockDeviceIdStorageManager.storeDeviceId(TEST_DEVICE_ID_NEW) }
            verify(exactly = 1) { mockDeviceIdStorageManager.loadDeviceId() }
            confirmVerified(mockBackend, mockCoroutineContextProvider, mockDeviceIdStorageManager)
        }

        @Test
        fun `dont do anything because of same DeviceId`() = runBlockingTest {
            every { mockSignedInUserManagerType.signedIn() } returns false
            every { mockDeviceIdStorageManager.loadDeviceId() } returns TEST_DEVICE_ID

            verifyDeviceHandler.startUpdatingDeviceId(TEST_DEVICE_ID)

            verify(exactly = 1) { mockDeviceIdStorageManager.loadDeviceId() }
            confirmVerified(mockBackend, mockCoroutineContextProvider, mockDeviceIdStorageManager)
        }

        @Test
        fun `store DeviceId`() = runBlocking {
            val TEST_PAYLOAD_CONTAINER = PayloadContainerBundle(Payload.PayloadType.VerificationMessageV1, TEST_VERIFICATION_OBJ, mockk())

            val testJob = Job()

            every { mockDeviceIdStorageManager.loadDeviceId() } returns TEST_DEVICE_ID
            every { mockSignedInUserManagerType.signedIn() } returns true
            every { mockSignedInUserManagerType.signedInUser } returns mockk {
                every { userId } returns TEST_USER_ID
                every { publicName } returns TEST_USER_NAME
            }
            every { mockCoroutineContextProvider.IO } returns Dispatchers.IO + testJob

            verifyDeviceHandler.handlePayloadContainerBundle(TEST_PAYLOAD_CONTAINER)
            verifyDeviceHandler.startUpdatingDeviceId(TEST_DEVICE_ID_NEW)

            testJob.joinAllChildren()

            coVerify(exactly = 1) { mockBackend.verify(TEST_DEVICE_ID_NEW, Platform.Android) }
            coVerify(exactly = 1) { mockBackend.updateUser(TEST_USER_ID, null, TEST_DEVICE_ID_NEW, TEST_VERIFICATION_CODE, TEST_USER_NAME) }
            verify(exactly = 1) { mockDeviceIdStorageManager.storeDeviceId(TEST_DEVICE_ID_NEW) }
            verify(exactly = 1) { mockDeviceIdStorageManager.loadDeviceId() }
            confirmVerified(mockBackend)
        }
    }
}