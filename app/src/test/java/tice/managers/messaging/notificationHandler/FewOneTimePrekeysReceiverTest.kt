package tice.managers.messaging.notificationHandler

import tice.crypto.CryptoManagerType
import tice.models.UserPublicKeys
import io.mockk.*
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tice.backend.BackendType
import tice.crypto.ConversationCryptoMiddlewareType
import tice.managers.SignedInUserManagerType
import tice.managers.messaging.PostOfficeType
import tice.models.SignedInUser
import tice.models.messaging.PayloadContainerBundle
import java.util.*

internal class FewOneTimePrekeysReceiverTest {

    private lateinit var fewOneTimePrekeysReceiver: FewOneTimePrekeysReceiver

    private val mockSignedInUserManager: SignedInUserManagerType = mockk(relaxUnitFun = true)
    private val mockPostOffice: PostOfficeType = mockk(relaxUnitFun = true)
    private val mockConversationCryptoMiddleware: ConversationCryptoMiddlewareType = mockk(relaxUnitFun = true)
    private val mockBackend: BackendType = mockk(relaxUnitFun = true)

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        fewOneTimePrekeysReceiver = FewOneTimePrekeysReceiver(
            mockPostOffice,
            mockSignedInUserManager,
            mockConversationCryptoMiddleware,
            mockBackend
        )
    }

    @Test
    fun receivePayloadContainerBundle() = runBlockingTest {
        val mockPayloadContainerBundle = mockk<PayloadContainerBundle>()
        val mockSignedInUser = mockk<SignedInUser>()
        val mockUserPublicKeys = mockk<UserPublicKeys>()

        val TEST_SIGNED_IN_USER_ID = UUID.randomUUID()
        val TEST_PRIVATE_KEY = "privateKey".toByteArray()
        val TEST_PUBLIC_KEY = "publicKey".toByteArray()
        val TEST_NAME = "name"

        every { mockSignedInUserManager.signedInUser } returns mockSignedInUser
        every { mockSignedInUser.userId } returns TEST_SIGNED_IN_USER_ID
        every { mockSignedInUser.publicName } returns TEST_NAME
        every { mockSignedInUser.privateSigningKey } returns TEST_PRIVATE_KEY
        every { mockSignedInUser.publicSigningKey } returns TEST_PUBLIC_KEY

        coEvery {
            mockConversationCryptoMiddleware.renewHandshakeKeyMaterial(TEST_PRIVATE_KEY, TEST_PUBLIC_KEY)
        } returns mockUserPublicKeys

        fewOneTimePrekeysReceiver.handlePayloadContainerBundle(mockPayloadContainerBundle)

        coVerify(exactly = 1) {
            mockBackend.updateUser(
                TEST_SIGNED_IN_USER_ID,
                mockUserPublicKeys,
                null,
                null,
                TEST_NAME
            )
        }
    }
}