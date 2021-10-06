package tice.integration

import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import io.mockk.*
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.serialization.UnsafeSerializationApi
import okhttp3.OkHttpClient
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions
import tice.backend.Backend
import tice.backend.HTTPRequester
import tice.backend.HTTPRequesterType
import tice.crypto.AuthManager
import tice.crypto.AuthManagerType
import tice.crypto.CryptoManager
import tice.dagger.provides.ConfigModule
import tice.exceptions.BackendException
import tice.exceptions.CryptoStorageManagerException
import tice.managers.DoubleRatchetProviderType
import tice.managers.SignedInUserManagerType
import tice.managers.storageManagers.CryptoStorageManagerType
import tice.models.*
import tice.models.messaging.MessagePriority
import tice.models.responses.CreateGroupResponse
import java.util.*

@UnsafeSerializationApi
internal class BackendTestIntegration {

    private lateinit var backend: Backend

    private val hTTPRequester: HTTPRequesterType = HTTPRequester(OkHttpClient())
    private val mockSignedInUserManager: SignedInUserManagerType = mockk()
    private val mockCryptoStorageManager: CryptoStorageManagerType = mockk(relaxUnitFun = true)
    private val sodium = LazySodiumAndroid(SodiumAndroid())
    private val cryptoParams = ConfigModule.CryptoParams(100, 100, "TICE", 100, "SHA512withECDSA", 100, 5)
    private val cryptoManager = CryptoManager(sodium)
    private val doubleRatchetProvider: DoubleRatchetProviderType = mockk()
    private val authManager: AuthManagerType = AuthManager(sodium, cryptoParams)

    private val TEST_DEVICEID: String = "deviceId"
    private lateinit var TEST_BASE_URL: String

    private lateinit var TEST_VERIFICATION_CODE: String
    private val TEST_USERNAME = "UserName"

    private val TEST_GROUP_ID = UUID.randomUUID()

    private val TEST_ENCRYPTED_GROUP_SETTINGS = "groupSettings".toByteArray()
    private val TEST_ENCRYPTED_INTERNAL_SETTINGS = "internalSettings".toByteArray()

    @Before
    fun before() {
        clearAllMocks()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        TEST_BASE_URL = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData.getString("base_url")!!
        TEST_VERIFICATION_CODE = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData.getString("development_verification_code")!!

        backend = Backend(TEST_BASE_URL, "2.0", "120", "android", hTTPRequester, mockSignedInUserManager, authManager)

        coEvery { mockCryptoStorageManager.loadIdentityKeyPair() } throws CryptoStorageManagerException.NoDataStored

        coEvery { mockCryptoStorageManager.savePrekeyPair(any(), any()) }
    }

    @Test
    fun verify() = runBlockingTest {
        Assertions.assertDoesNotThrow {
            runBlockingTest {
                backend.verify(TEST_DEVICEID, Platform.Android)
            }
        }
    }

    @Test
    fun createUserUsingPush() = runBlockingTest {
        val keyPairSlot = slot<KeyPair>()

        coEvery { mockCryptoStorageManager.saveIdentityKeyPair(capture(keyPairSlot)) } answers {
            coEvery { mockCryptoStorageManager.loadIdentityKeyPair() } returns keyPairSlot.captured
            Unit
        }

        val signingKeyPair = cryptoManager.generateSigningKeyPair()

        val userPublicKeys = UserPublicKeys(
            signingKeyPair.publicKey,
            "identityKey".encodeToByteArray(),
            "signedPrekey".encodeToByteArray(),
            "prekeySignature".encodeToByteArray(),
            listOf("oneTimePrekey".encodeToByteArray())
        )

        Assertions.assertDoesNotThrow {
            runBlockingTest {
                backend.createUserUsingPush(userPublicKeys, Platform.Android, TEST_DEVICEID, TEST_VERIFICATION_CODE, TEST_USERNAME)
            }
        }
    }

    @Test
    fun createUserUsingCaptcha() = runBlockingTest {
        val keyPairSlot = slot<KeyPair>()

        coEvery { mockCryptoStorageManager.saveIdentityKeyPair(capture(keyPairSlot)) } answers {
            coEvery { mockCryptoStorageManager.loadIdentityKeyPair() } returns keyPairSlot.captured
            Unit
        }

        val signingKeyPair = cryptoManager.generateSigningKeyPair()

        val userPublicKeys = UserPublicKeys(
            signingKeyPair.publicKey,
            "identityKey".encodeToByteArray(),
            "signedPrekey".encodeToByteArray(),
            "prekeySignature".encodeToByteArray(),
            listOf("oneTimePrekey".encodeToByteArray())
        )

        val hcaptchaResponse = "10000000-aaaa-bbbb-cccc-000000000001"

        Assertions.assertDoesNotThrow {
            runBlockingTest {
                backend.createUserUsingCaptcha(userPublicKeys, Platform.Android, hcaptchaResponse, TEST_USERNAME)
            }
        }
    }

    @Test
    fun updateUser() = runBlockingTest {
        val signedInUser = createTempUser()

        every { mockSignedInUserManager.signedInUser } returns signedInUser

        Assertions.assertDoesNotThrow {
            runBlockingTest {
                backend.updateUser(signedInUser.userId, null, TEST_DEVICEID, TEST_VERIFICATION_CODE, TEST_USERNAME)
            }
        }
    }

    @Test
    fun deleteUser() = runBlockingTest {
        val signedInUser = createTempUser()

        every { mockSignedInUserManager.signedInUser } returns signedInUser

        Assertions.assertDoesNotThrow {
            runBlockingTest {
                backend.deleteUser(signedInUser.userId)
            }
        }
    }

    @Test
    fun getUser() = runBlockingTest {
        val signedInUser = createTempUser()
        every { mockSignedInUserManager.signedInUser } returns signedInUser

        Assertions.assertDoesNotThrow {
            runBlockingTest {
                backend.getUser(signedInUser.userId)
            }
        }
    }

    @Test
    fun getUserPublicKeys() = runBlockingTest {
        val signedInUser = createTempUser()
        every { mockSignedInUserManager.signedInUser } returns signedInUser

        Assertions.assertDoesNotThrow {
            runBlockingTest {
                backend.getUserKey(signedInUser.userId)
            }
        }
    }

    @Test
    fun createGroup() = runBlockingTest {
        val signedInUser = createTempUser()
        every { mockSignedInUserManager.signedInUser } returns signedInUser

        val selfSignedAdminCertificate = createSelfSigned(signedInUser)

        Assertions.assertDoesNotThrow {
            runBlockingTest {
                backend.createGroup(
                    TEST_GROUP_ID,
                    GroupType.Team,
                    JoinMode.Open,
                    PermissionMode.Everyone,
                    selfSignedAdminCertificate,
                    TEST_ENCRYPTED_GROUP_SETTINGS,
                    TEST_ENCRYPTED_INTERNAL_SETTINGS,
                    null
                )
            }
        }
    }


    @Test
    fun getGroupInformation() = runBlockingTest {
        val signedInUser = createTempUser()
        every { mockSignedInUserManager.signedInUser } returns signedInUser

        val selfSignedAdminCertificate = createSelfSigned(signedInUser)
        val teamResponse = createTempTeam(signedInUser, selfSignedAdminCertificate)

        Assertions.assertThrows(BackendException.NotModified::class.java) {
            runBlockingTest {
                backend.getGroupInformation(TEST_GROUP_ID, teamResponse.first.tag)
            }
        }
    }

    @Test
    fun getGroupInternals() = runBlockingTest {
        val signedInUser = createTempUser()
        every { mockSignedInUserManager.signedInUser } returns signedInUser

        val selfSignedAdminCertificate = createSelfSigned(signedInUser)
        val teamResponse = createTempTeam(signedInUser, selfSignedAdminCertificate)

        Assertions.assertThrows(BackendException.NotModified::class.java) {
            runBlockingTest {
                backend.getGroupInternals(
                    teamResponse.first.groupId,
                    teamResponse.second.serverSignedAdminCertificate,
                    teamResponse.first.tag
                )
            }
        }
    }

    @Test
    fun joinGroup() = runBlockingTest {
        val signedInUser = createTempUser()
        every { mockSignedInUserManager.signedInUser } returns signedInUser

        val selfSignedAdminCertificate = createSelfSigned(signedInUser)
        val teamResponse = createTempTeam(signedInUser, selfSignedAdminCertificate)

        Assertions.assertDoesNotThrow {
            runBlockingTest {
                backend.joinGroup(
                    teamResponse.first.groupId,
                    teamResponse.first.tag,
                    selfSignedAdminCertificate,
                    teamResponse.second.serverSignedAdminCertificate,
                    selfSignedAdminCertificate
                )
            }
        }
    }


    @Test
    fun deleteGroup() = runBlockingTest {
        val signedInUser = createTempUser()
        every { mockSignedInUserManager.signedInUser } returns signedInUser

        val selfSignedAdminCertificate = createSelfSigned(signedInUser)
        val teamResponse = createTempTeam(signedInUser, selfSignedAdminCertificate)

        Assertions.assertDoesNotThrow {
            runBlockingTest {
                backend.deleteGroup(
                    teamResponse.first.groupId,
                    teamResponse.second.serverSignedAdminCertificate,
                    teamResponse.first.tag,
                    emptyList()
                )
            }
        }
    }

    @Test
    fun updateGroupInternalsSettings() = runBlockingTest {
        val signedInUser = createTempUser()
        every { mockSignedInUserManager.signedInUser } returns signedInUser

        val selfSignedAdminCertificate = createSelfSigned(signedInUser)
        val teamResponse = createTempTeam(signedInUser, selfSignedAdminCertificate)

        Assertions.assertDoesNotThrow {
            runBlockingTest {
                backend.updateGroupInternalSettings(
                    teamResponse.first.groupId,
                    TEST_ENCRYPTED_GROUP_SETTINGS,
                    teamResponse.second.serverSignedAdminCertificate,
                    teamResponse.first.tag,
                    emptyList()
                )
            }
        }
    }

    @Test
    fun updateGroupInformation() = runBlockingTest {
        val signedInUser = createTempUser()
        every { mockSignedInUserManager.signedInUser } returns signedInUser

        val selfSignedAdminCertificate = createSelfSigned(signedInUser)
        val teamResponse = createTempTeam(signedInUser, selfSignedAdminCertificate)

        Assertions.assertDoesNotThrow {
            runBlockingTest {
                backend.updateGroupSettings(
                    teamResponse.first.groupId,
                    TEST_ENCRYPTED_GROUP_SETTINGS,
                    teamResponse.second.serverSignedAdminCertificate,
                    teamResponse.first.tag,
                    emptyList()
                )
            }
        }
    }

    @Test
    fun sendMessage() = runBlockingTest {
        val signedInUser = createTempUser()
        every { mockSignedInUserManager.signedInUser } returns signedInUser

        val selfSignedAdminCertificate = createSelfSigned(signedInUser)
        val teamResponse = createTempTeam(signedInUser, selfSignedAdminCertificate)

        Assertions.assertDoesNotThrow {
            runBlockingTest {
                backend.message(
                    UUID.randomUUID(),
                    teamResponse.first.groupId,
                    Date().time,
                    "encryptedMessage".toByteArray(),
                    teamResponse.second.serverSignedAdminCertificate,
                    emptySet(),
                    MessagePriority.Alert,
                    null,
                    200000L
                )
            }
        }
    }

    @Test
    fun getMessages() = runBlockingTest {
        val signedInUser = createTempUser()
        every { mockSignedInUserManager.signedInUser } returns signedInUser

        backend.getMessages()
    }

    private suspend fun createTempUser(): SignedInUser {
        val keyPairSlot = slot<KeyPair>()

        coEvery { mockCryptoStorageManager.saveIdentityKeyPair(capture(keyPairSlot)) } answers {
            coEvery { mockCryptoStorageManager.loadIdentityKeyPair() } returns keyPairSlot.captured
            Unit
        }

        val signingKeyPair = cryptoManager.generateSigningKeyPair()

        val userPublicKeys = UserPublicKeys(
            signingKeyPair.publicKey,
            "identityKey".encodeToByteArray(),
            "signedPrekey".encodeToByteArray(),
            "prekeySignature".encodeToByteArray(),
            listOf("oneTimePrekey".encodeToByteArray())
        )

        val createUserResponse = backend.createUserUsingPush(userPublicKeys, Platform.Android, TEST_DEVICEID, TEST_VERIFICATION_CODE, TEST_USERNAME)

        return SignedInUser(
            createUserResponse.userId,
            TEST_USERNAME,
            signingKeyPair.publicKey,
            signingKeyPair.privateKey
        )
    }

    private fun createSelfSigned(signedInUser: SignedInUser): Certificate {
        return authManager.createUserSignedMembershipCertificate(
            signedInUser.userId,
            TEST_GROUP_ID,
            true,
            signedInUser.userId,
            signedInUser.privateSigningKey
        )
    }

    private suspend fun createTempTeam(signedInUser: SignedInUser, certificate: Certificate): Pair<Team, CreateGroupResponse> {
        val groupKey = cryptoManager.generateGroupKey()
        val response = backend.createGroup(
            TEST_GROUP_ID,
            GroupType.Team,
            JoinMode.Open,
            PermissionMode.Everyone,
            certificate,
            TEST_ENCRYPTED_GROUP_SETTINGS,
            TEST_ENCRYPTED_GROUP_SETTINGS,
            null
        )

        return Pair(
            Team(
                TEST_GROUP_ID,
                groupKey,
                signedInUser.userId,
                JoinMode.Open,
                PermissionMode.Everyone,
                response.groupTag,
                response.url,
                "groupName",
                null,
                null
            ), response
        )
    }
}
