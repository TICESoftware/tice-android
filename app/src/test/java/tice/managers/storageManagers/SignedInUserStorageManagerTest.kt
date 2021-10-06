package tice.managers.storageManagers

import io.mockk.*
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tice.exceptions.SignedInUserStorageManagerException
import tice.models.KeyPair
import tice.models.SignedInUser
import tice.models.User
import java.util.*

internal class SignedInUserStorageManagerTest {

    private lateinit var signedInUserStorageManager: SignedInUserStorageManager

    private val mockStorageLocker: StorageLockerType = mockk(relaxUnitFun = true)
    private val mockCryptoStorageManager: CryptoStorageManagerType = mockk(relaxUnitFun = true)
    private val mockUserStorageManager: UserStorageManagerType = mockk(relaxUnitFun = true)

    private val TEST_USER_ID = UUID.randomUUID()
    private val TEST_NAME = "Name"
    private val TEST_PUBLIC_KEY = "publicKey".toByteArray()
    private val TEST_PRIVATE_KEY = "privateKey".toByteArray()

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        signedInUserStorageManager = SignedInUserStorageManager(
            mockStorageLocker,
            mockCryptoStorageManager,
            mockUserStorageManager
        )
    }

    @Test
    fun storeSignedInUser() = runBlockingTest {
        val TEST_SIGNED_IN_USER = SignedInUser(TEST_USER_ID, TEST_NAME, TEST_PUBLIC_KEY, TEST_PRIVATE_KEY)

        signedInUserStorageManager.storeSignedInUser(TEST_SIGNED_IN_USER)

        coVerify { mockUserStorageManager.store(User(TEST_USER_ID, TEST_PUBLIC_KEY, TEST_NAME)) }
        coVerify { mockCryptoStorageManager.saveSigningKeyPair(KeyPair(TEST_PRIVATE_KEY, TEST_PUBLIC_KEY)) }
        coVerify { mockStorageLocker.store(StorageLockerType.StorageKey.SIGNED_IN_USER, TEST_USER_ID.toString()) }
    }

    @Nested
    inner class LoadSignedInUser {

        @Test
        fun notSignedIn_storage_locker() = runBlockingTest {
            every { mockStorageLocker.load(StorageLockerType.StorageKey.SIGNED_IN_USER) }
                .returns(null)

            Assertions.assertThrows(SignedInUserStorageManagerException.NotSignedIn::class.java) {
                runBlockingTest { signedInUserStorageManager.loadSignedInUser() }
            }
        }

        @Test
        fun notSignedIn_user_storage_manager() = runBlockingTest {
            every { mockStorageLocker.load(StorageLockerType.StorageKey.SIGNED_IN_USER) }
                .returns(TEST_USER_ID.toString())

            coEvery { mockUserStorageManager.loadUser(TEST_USER_ID) }
                .returns(null)

            Assertions.assertThrows(SignedInUserStorageManagerException.NotSignedIn::class.java) {
                runBlockingTest { signedInUserStorageManager.loadSignedInUser() }
            }
        }


        @Test
        fun NotSignedInKeyPair() = runBlockingTest {
            every { mockStorageLocker.load(StorageLockerType.StorageKey.SIGNED_IN_USER) }
                .returns(TEST_USER_ID.toString())

            coEvery { mockUserStorageManager.loadUser(TEST_USER_ID) }
                .returns(User(TEST_USER_ID, TEST_PUBLIC_KEY, TEST_NAME))

            coEvery { mockCryptoStorageManager.loadSigningKeyPair() }
                .returns(null)

            Assertions.assertThrows(SignedInUserStorageManagerException.NotSignedInKeyPair::class.java) {
                runBlockingTest { signedInUserStorageManager.loadSignedInUser() }
            }
        }

        @Test
        fun success() = runBlockingTest {
            every { mockStorageLocker.load(StorageLockerType.StorageKey.SIGNED_IN_USER) }
                .returns(TEST_USER_ID.toString())

            coEvery { mockUserStorageManager.loadUser(TEST_USER_ID) }
                .returns(User(TEST_USER_ID, TEST_PUBLIC_KEY, TEST_NAME))

            coEvery { mockCryptoStorageManager.loadSigningKeyPair() }
                .returns(KeyPair(TEST_PRIVATE_KEY, TEST_PUBLIC_KEY))

            val result = signedInUserStorageManager.loadSignedInUser()

            val expected = SignedInUser(TEST_USER_ID, TEST_NAME, TEST_PUBLIC_KEY, TEST_PRIVATE_KEY)

            Assertions.assertEquals(expected, result)
        }
    }

    @Test
    fun deleteSignedInUser() {
        signedInUserStorageManager.deleteSignedInUser()

        verify { mockStorageLocker.remove(StorageLockerType.StorageKey.SIGNED_IN_USER) }
    }
}