package tice.managers.storageManagers

import io.mockk.*
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tice.models.KeyPair
import tice.models.database.*
import tice.models.messaging.conversation.ConversationState
import java.util.*
import kotlin.random.Random

internal class CryptoStorageManagerTest {

    private lateinit var cryptoStorageManager: CryptoStorageManager

    private val mockAppDatabase: AppDatabase = mockk(relaxUnitFun = true)

    private val mockIdentityKeyPairInterface: IdentityKeyPairInterface = mockk(relaxUnitFun = true)
    private val mockSigningKeyPairInterface: SigningKeyPairInterface = mockk(relaxUnitFun = true)
    private val mockGroupKeyInterface: GroupKeyInterface = mockk(relaxUnitFun = true)
    private val mockPrekeyInterface: PrekeyInterface = mockk(relaxUnitFun = true)
    private val mockOneTimePrekeyInterface: OneTimePrekeyInterface = mockk(relaxUnitFun = true)
    private val mockMembershipCertificateInterface: MembershipCertificateInterface = mockk(relaxUnitFun = true)
    private val mockConversationStateInterface: ConversationInterface = mockk(relaxUnitFun = true)
    private val mockMessageKeyCacheInterface: MessageKeyCacheInterface = mockk(relaxUnitFun = true)

    private val TEST_GROUP_KEY = "groupKey".toByteArray()
    private val TEST_PUBLIC_KEY_STRING = "publicKey"
    private val TEST_PUBLIC_KEY = TEST_PUBLIC_KEY_STRING.toByteArray()
    private val TEST_PRIVATE_KEY = "publicKey".toByteArray()
    private val TEST_KEY_PAIR = KeyPair(TEST_PRIVATE_KEY, TEST_PUBLIC_KEY)
    private val TEST_SERVER_SIGNED = "serverSignCert"

    private val TEST_GROUP_ID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        every { mockAppDatabase.identityKeyPairInterface() } returns mockIdentityKeyPairInterface
        every { mockAppDatabase.signingKeyPairInterface() } returns mockSigningKeyPairInterface
        every { mockAppDatabase.groupKeyInterface() } returns mockGroupKeyInterface
        every { mockAppDatabase.prekeyInterface() } returns mockPrekeyInterface
        every { mockAppDatabase.oneTimePrekeyInterface() } returns mockOneTimePrekeyInterface
        every { mockAppDatabase.membershipCertificateInterface() } returns mockMembershipCertificateInterface
        every { mockAppDatabase.conversationStateInterface() } returns mockConversationStateInterface
        every { mockAppDatabase.messageKeyCacheInterface() } returns mockMessageKeyCacheInterface

        cryptoStorageManager = CryptoStorageManager(mockAppDatabase, TEST_PUBLIC_KEY_STRING)
    }

    @Test
    fun saveIdentityKeyPair() = runBlockingTest {
        val expectedEntity = IdentityKeyPairEntity(TEST_KEY_PAIR.publicKey, TEST_KEY_PAIR.privateKey)

        cryptoStorageManager.saveIdentityKeyPair(TEST_KEY_PAIR)

        coVerify(exactly = 1) { mockIdentityKeyPairInterface.insert(expectedEntity) }
    }

    @Test
    fun loadIdentityKeyPair() = runBlockingTest {
        val TEST_ENTITY = IdentityKeyPairEntity(TEST_KEY_PAIR.publicKey, TEST_KEY_PAIR.privateKey)

        coEvery { mockIdentityKeyPairInterface.get() } returns TEST_ENTITY

        val result = cryptoStorageManager.loadIdentityKeyPair()

        assertEquals(KeyPair(TEST_PRIVATE_KEY, TEST_PUBLIC_KEY), result)
    }

    @Test
    fun saveSigningKeyPair() = runBlockingTest {
        val expectedEntity = SigningKeyPairEntity(TEST_PUBLIC_KEY, TEST_PRIVATE_KEY)

        cryptoStorageManager.saveSigningKeyPair(TEST_KEY_PAIR)

        coVerify(exactly = 1) { mockSigningKeyPairInterface.insert(expectedEntity) }
    }

    @Test
    fun loadSigningKeyPair() = runBlockingTest {
        val TEST_ENTITY = SigningKeyPairEntity(TEST_KEY_PAIR.publicKey, TEST_KEY_PAIR.privateKey)

        coEvery { mockSigningKeyPairInterface.getOne() } returns TEST_ENTITY

        val result = cryptoStorageManager.loadSigningKeyPair()

        assertEquals(KeyPair(TEST_PRIVATE_KEY, TEST_PUBLIC_KEY), result)
    }

    @Test
    fun savePrekeyPair() = runBlockingTest {
        val TEST_SIGNATURE = "signature".toByteArray()

        cryptoStorageManager.savePrekeyPair(TEST_KEY_PAIR, TEST_SIGNATURE)

        coVerify(exactly = 1) { mockPrekeyInterface.insert(TEST_PUBLIC_KEY, TEST_PRIVATE_KEY, TEST_SIGNATURE) }
    }

    @Test
    fun loadPrekeyPair() = runBlockingTest {
        val TEST_SIGNATURE = "signature".toByteArray()
        val TEST_ENTITY = PrekeyEntity(TEST_KEY_PAIR.publicKey, TEST_KEY_PAIR.privateKey, TEST_SIGNATURE)

        coEvery { mockPrekeyInterface.getOne() } returns TEST_ENTITY

        val result = cryptoStorageManager.loadPrekeyPair()

        assertEquals(KeyPair(TEST_PRIVATE_KEY, TEST_PUBLIC_KEY), result)
    }

    @Test
    fun loadPrekeySignature() = runBlockingTest {
        val TEST_SIGNATURE = "signature".toByteArray()
        val TEST_ENTITY = PrekeyEntity(TEST_KEY_PAIR.publicKey, TEST_KEY_PAIR.privateKey, TEST_SIGNATURE)

        coEvery { mockPrekeyInterface.getOne() } returns TEST_ENTITY

        val result = cryptoStorageManager.loadPrekeySignature()

        assertEquals(TEST_SIGNATURE, result)
    }

    @Test
    fun saveOneTimePrekeyPairs() = runBlockingTest {
        val TEST_KEY_PAIR_2 = KeyPair("private".toByteArray(), "public".toByteArray())

        cryptoStorageManager.saveOneTimePrekeyPairs(listOf(TEST_KEY_PAIR, TEST_KEY_PAIR_2))

        val expected = listOf(
            OneTimePrekeyEntity(TEST_KEY_PAIR.publicKey, TEST_KEY_PAIR.privateKey),
            OneTimePrekeyEntity(TEST_KEY_PAIR_2.publicKey, TEST_KEY_PAIR_2.privateKey)
        )

        coVerify(exactly = 1) { mockOneTimePrekeyInterface.insert(expected) }
    }

    @Test
    fun loadPrivateOneTimePrekey() = runBlockingTest {
        val TEST_ENTITY_1 = OneTimePrekeyEntity("pubKey_wrong".toByteArray(), "priKey_wrong".toByteArray())
        val TEST_ENTITY_2 = OneTimePrekeyEntity("pubKey_wrong".toByteArray(), "priKey_wrong".toByteArray())
        val TEST_ENTITY_3 = OneTimePrekeyEntity("pubKey_wrong".toByteArray(), "priKey_wrong".toByteArray())
        val TEST_ENTITY_4 = OneTimePrekeyEntity(TEST_PUBLIC_KEY, TEST_PRIVATE_KEY)
        val TEST_ENTITY_5 = OneTimePrekeyEntity("pubKey_wrong".toByteArray(), "priKey_wrong".toByteArray())

        val entityList = listOf(TEST_ENTITY_1, TEST_ENTITY_2, TEST_ENTITY_3, TEST_ENTITY_4, TEST_ENTITY_5)

        coEvery { mockOneTimePrekeyInterface.loadAll() } returns entityList

        val result = cryptoStorageManager.loadPrivateOneTimePrekey(TEST_PUBLIC_KEY)

        assertEquals(TEST_PRIVATE_KEY, result)

    }

    @Test
    fun deleteOneTimePrekeyPair() = runBlockingTest {
        cryptoStorageManager.deleteOneTimePrekeyPair(TEST_PUBLIC_KEY)

        coVerify(exactly = 1) { mockOneTimePrekeyInterface.deleteGroupKey(TEST_PUBLIC_KEY) }
    }

    @Test
    fun save() = runBlockingTest {
        cryptoStorageManager.save(TEST_GROUP_KEY, TEST_GROUP_ID)

        coVerify(exactly = 1) { mockGroupKeyInterface.insert(TEST_GROUP_ID, TEST_GROUP_KEY) }
    }

    @Test
    fun loadGroupKey() = runBlockingTest {
        coEvery { mockGroupKeyInterface.getGroupKey(TEST_GROUP_ID) } returns TEST_GROUP_KEY

        cryptoStorageManager.loadGroupKey(TEST_GROUP_ID)

        coVerify(exactly = 1) { mockGroupKeyInterface.getGroupKey(TEST_GROUP_ID) }
    }

    @Test
    fun removeGroupKey() = runBlockingTest {
        cryptoStorageManager.removeGroupKey(TEST_GROUP_ID)

        coVerify(exactly = 1) { mockGroupKeyInterface.deleteGroupKey(TEST_GROUP_ID) }
    }

    @Test
    fun loadServerPublicSigningKey() = runBlockingTest {
        val result = cryptoStorageManager.loadServerPublicSigningKey()

        assertArrayEquals(TEST_PUBLIC_KEY, result)
    }

    @Test
    fun saveServerSignedMembershipCertificate() = runBlockingTest {
        cryptoStorageManager.saveServerSignedMembershipCertificate(TEST_SERVER_SIGNED, TEST_GROUP_ID)

        coVerify(exactly = 1) { mockMembershipCertificateInterface.insert(TEST_GROUP_ID, TEST_SERVER_SIGNED) }
    }

    @Test
    fun loadServerSignedMembershipCertificate() = runBlockingTest {
        coEvery { mockMembershipCertificateInterface.getMembershipCertificate(TEST_GROUP_ID) } returns TEST_SERVER_SIGNED

        val result = cryptoStorageManager.loadServerSignedMembershipCertificate(TEST_GROUP_ID)

        assertEquals(TEST_SERVER_SIGNED, result)
    }

    @Test
    fun removeServerSignedMembershipCertificate() = runBlockingTest {
        cryptoStorageManager.removeServerSignedMembershipCertificate(TEST_GROUP_ID)

        coVerify(exactly = 1) { mockMembershipCertificateInterface.deleteMembershipCertificate(TEST_GROUP_ID) }
    }

    @Test
    fun saveConversationState() = runBlockingTest {
        val TEST_USER_ID = UUID.randomUUID()
        val TEST_CONVERSATION_ID = UUID.randomUUID()
        val TEST_ROOT_KEY = "rootKey".toByteArray()
        val TEST_ROOT_CHAIN_PUBLIC_KEY = "rootChainPublicKey".toByteArray()
        val TEST_ROOT_CHAIN_PRIVATE_KEY = "rootChainPrivateKey".toByteArray()
        val TEST_ROOT_CHAIN_REMOTE_PUBLIC_KEY = "rootChainRemotePublicKey".toByteArray()
        val TEST_SENDING_CHAIN_KEY = "sendingChainKey".toByteArray()
        val TEST_RECEIVING_CHAIN_KEY = "sendingChainKey".toByteArray()
        val TEST_SEND_MESSAGE_NUMBER = Random.nextInt()
        val TEST_RECEIVED_MESSAGE_NUMBER = Random.nextInt()
        val TEST_CHAIN_MESSAGE_LENGHT = Random.nextInt()

        val TEST_CONVERSATION_STATE = ConversationState(
            TEST_USER_ID,
            TEST_CONVERSATION_ID,
            TEST_ROOT_KEY,
            TEST_ROOT_CHAIN_PUBLIC_KEY,
            TEST_ROOT_CHAIN_PRIVATE_KEY,
            TEST_ROOT_CHAIN_REMOTE_PUBLIC_KEY,
            TEST_SENDING_CHAIN_KEY,
            TEST_RECEIVING_CHAIN_KEY,
            TEST_SEND_MESSAGE_NUMBER,
            TEST_RECEIVED_MESSAGE_NUMBER,
            TEST_CHAIN_MESSAGE_LENGHT
        )

        cryptoStorageManager.saveConversationState(TEST_CONVERSATION_STATE)

        val expectedEntity = ConversationStateEntity(
            TEST_USER_ID,
            TEST_CONVERSATION_ID,
            TEST_ROOT_KEY,
            TEST_ROOT_CHAIN_PUBLIC_KEY,
            TEST_ROOT_CHAIN_PRIVATE_KEY,
            TEST_ROOT_CHAIN_REMOTE_PUBLIC_KEY,
            TEST_SENDING_CHAIN_KEY,
            TEST_RECEIVING_CHAIN_KEY,
            TEST_SEND_MESSAGE_NUMBER,
            TEST_RECEIVED_MESSAGE_NUMBER,
            TEST_CHAIN_MESSAGE_LENGHT
        )

        coVerify(exactly = 1) { mockConversationStateInterface.insert(expectedEntity) }
    }

    @Test
    fun loadConversationState() = runBlockingTest {
        val TEST_USER_ID = UUID.randomUUID()
        val TEST_CONVERSATION_ID = UUID.randomUUID()
        val TEST_ROOT_KEY = "rootKey".toByteArray()
        val TEST_ROOT_CHAIN_PUBLIC_KEY = "rootChainPublicKey".toByteArray()
        val TEST_ROOT_CHAIN_PRIVATE_KEY = "rootChainPrivateKey".toByteArray()
        val TEST_ROOT_CHAIN_REMOTE_PUBLIC_KEY = "rootChainRemotePublicKey".toByteArray()
        val TEST_SENDING_CHAIN_KEY = "sendingChainKey".toByteArray()
        val TEST_RECEIVING_CHAIN_KEY = "sendingChainKey".toByteArray()
        val TEST_SEND_MESSAGE_NUMBER = Random.nextInt()
        val TEST_RECEIVED_MESSAGE_NUMBER = Random.nextInt()
        val TEST_CHAIN_MESSAGE_LENGHT = Random.nextInt()

        val TEST_CONVERSATION_STATE_ENTITY = ConversationStateEntity(
            TEST_USER_ID,
            TEST_CONVERSATION_ID,
            TEST_ROOT_KEY,
            TEST_ROOT_CHAIN_PUBLIC_KEY,
            TEST_ROOT_CHAIN_PRIVATE_KEY,
            TEST_ROOT_CHAIN_REMOTE_PUBLIC_KEY,
            TEST_SENDING_CHAIN_KEY,
            TEST_RECEIVING_CHAIN_KEY,
            TEST_SEND_MESSAGE_NUMBER,
            TEST_RECEIVED_MESSAGE_NUMBER,
            TEST_CHAIN_MESSAGE_LENGHT
        )

        coEvery { mockConversationStateInterface.get(TEST_USER_ID, TEST_CONVERSATION_ID) } returns TEST_CONVERSATION_STATE_ENTITY

        val result = cryptoStorageManager.loadConversationState(TEST_USER_ID, TEST_CONVERSATION_ID)

        val expected = ConversationState(
            TEST_USER_ID,
            TEST_CONVERSATION_ID,
            TEST_ROOT_KEY,
            TEST_ROOT_CHAIN_PUBLIC_KEY,
            TEST_ROOT_CHAIN_PRIVATE_KEY,
            TEST_ROOT_CHAIN_REMOTE_PUBLIC_KEY,
            TEST_SENDING_CHAIN_KEY,
            TEST_RECEIVING_CHAIN_KEY,
            TEST_SEND_MESSAGE_NUMBER,
            TEST_RECEIVED_MESSAGE_NUMBER,
            TEST_CHAIN_MESSAGE_LENGHT
        )

        assertEquals(expected, result)
        coVerify(exactly = 1) { mockConversationStateInterface.get(TEST_USER_ID, TEST_CONVERSATION_ID) }
    }

    @Test
    fun loadConversationStates() = runBlockingTest {
        val TEST_USER_ID_1 = UUID.randomUUID()
        val TEST_CONVERSATION_ID_1 = UUID.randomUUID()
        val TEST_ROOT_KEY_1 = "rootKey".toByteArray()
        val TEST_ROOT_CHAIN_PUBLIC_KEY_1 = "rootChainPublicKey".toByteArray()
        val TEST_ROOT_CHAIN_PRIVATE_KEY_1 = "rootChainPrivateKey".toByteArray()
        val TEST_ROOT_CHAIN_REMOTE_PUBLIC_KEY_1 = "rootChainRemotePublicKey".toByteArray()
        val TEST_SENDING_CHAIN_KEY_1 = "sendingChainKey".toByteArray()
        val TEST_RECEIVING_CHAIN_KEY_1 = "sendingChainKey".toByteArray()
        val TEST_SEND_MESSAGE_NUMBER_1 = Random.nextInt()
        val TEST_RECEIVED_MESSAGE_NUMBER_1 = Random.nextInt()
        val TEST_CHAIN_MESSAGE_LENGHT_1 = Random.nextInt()

        val TEST_USER_ID_2 = UUID.randomUUID()
        val TEST_CONVERSATION_ID_2 = UUID.randomUUID()
        val TEST_ROOT_KEY_2 = "rootKey".toByteArray()
        val TEST_ROOT_CHAIN_PUBLIC_KEY_2 = "rootChainPublicKey".toByteArray()
        val TEST_ROOT_CHAIN_PRIVATE_KEY_2 = "rootChainPrivateKey".toByteArray()
        val TEST_ROOT_CHAIN_REMOTE_PUBLIC_KEY_2 = "rootChainRemotePublicKey".toByteArray()
        val TEST_SENDING_CHAIN_KEY_2 = "sendingChainKey".toByteArray()
        val TEST_RECEIVING_CHAIN_KEY_2 = "sendingChainKey".toByteArray()
        val TEST_SEND_MESSAGE_NUMBER_2 = Random.nextInt()
        val TEST_RECEIVED_MESSAGE_NUMBER_2 = Random.nextInt()
        val TEST_CHAIN_MESSAGE_LENGHT_2 = Random.nextInt()

        val TEST_CONVERSATION_ENTITY_1 = ConversationStateEntity(
            TEST_USER_ID_1,
            TEST_CONVERSATION_ID_1,
            TEST_ROOT_KEY_1,
            TEST_ROOT_CHAIN_PUBLIC_KEY_1,
            TEST_ROOT_CHAIN_PRIVATE_KEY_1,
            TEST_ROOT_CHAIN_REMOTE_PUBLIC_KEY_1,
            TEST_SENDING_CHAIN_KEY_1,
            TEST_RECEIVING_CHAIN_KEY_1,
            TEST_SEND_MESSAGE_NUMBER_1,
            TEST_RECEIVED_MESSAGE_NUMBER_1,
            TEST_CHAIN_MESSAGE_LENGHT_1
        )

        val TEST_CONVERSATION_ENTITY_2 = ConversationStateEntity(
            TEST_USER_ID_2,
            TEST_CONVERSATION_ID_2,
            TEST_ROOT_KEY_2,
            TEST_ROOT_CHAIN_PUBLIC_KEY_2,
            TEST_ROOT_CHAIN_PRIVATE_KEY_2,
            TEST_ROOT_CHAIN_REMOTE_PUBLIC_KEY_2,
            TEST_SENDING_CHAIN_KEY_2,
            TEST_RECEIVING_CHAIN_KEY_2,
            TEST_SEND_MESSAGE_NUMBER_2,
            TEST_RECEIVED_MESSAGE_NUMBER_2,
            TEST_CHAIN_MESSAGE_LENGHT_2
        )

        coEvery { mockConversationStateInterface.getAll() } returns listOf(TEST_CONVERSATION_ENTITY_1, TEST_CONVERSATION_ENTITY_2)

        val result = cryptoStorageManager.loadConversationStates()

        val expected1 = ConversationState(
            TEST_USER_ID_1,
            TEST_CONVERSATION_ID_1,
            TEST_ROOT_KEY_1,
            TEST_ROOT_CHAIN_PUBLIC_KEY_1,
            TEST_ROOT_CHAIN_PRIVATE_KEY_1,
            TEST_ROOT_CHAIN_REMOTE_PUBLIC_KEY_1,
            TEST_SENDING_CHAIN_KEY_1,
            TEST_RECEIVING_CHAIN_KEY_1,
            TEST_SEND_MESSAGE_NUMBER_1,
            TEST_RECEIVED_MESSAGE_NUMBER_1,
            TEST_CHAIN_MESSAGE_LENGHT_1
        )

        val expected2 = ConversationState(
            TEST_USER_ID_2,
            TEST_CONVERSATION_ID_2,
            TEST_ROOT_KEY_2,
            TEST_ROOT_CHAIN_PUBLIC_KEY_2,
            TEST_ROOT_CHAIN_PRIVATE_KEY_2,
            TEST_ROOT_CHAIN_REMOTE_PUBLIC_KEY_2,
            TEST_SENDING_CHAIN_KEY_2,
            TEST_RECEIVING_CHAIN_KEY_2,
            TEST_SEND_MESSAGE_NUMBER_2,
            TEST_RECEIVED_MESSAGE_NUMBER_2,
            TEST_CHAIN_MESSAGE_LENGHT_2
        )

        assertEquals(listOf(expected1, expected2), result)
        coVerify(exactly = 1) { mockConversationStateInterface.getAll() }
    }

    @Test
    fun messageKeyCache() = runBlockingTest {
        val TEST_CONVERSATION_ID = UUID.randomUUID()

        val result1 = cryptoStorageManager.messageKeyCache(TEST_CONVERSATION_ID)
        val result2 = cryptoStorageManager.messageKeyCache(TEST_CONVERSATION_ID)

        assertEquals(result1, result2)
    }

    @Test
    fun removeAllData() = runBlockingTest {
        val TEST_CONVERSATION_ID_1 = UUID.randomUUID()
        val TEST_CONVERSATION_ID_2 = UUID.randomUUID()

        cryptoStorageManager.messageKeyCache(TEST_CONVERSATION_ID_1)
        cryptoStorageManager.messageKeyCache(TEST_CONVERSATION_ID_2)

        cryptoStorageManager.removeAllData()

        coVerify(exactly = 1) { mockIdentityKeyPairInterface.delete() }
        coVerify(exactly = 1) { mockSigningKeyPairInterface.deleteAll() }
        coVerify(exactly = 1) { mockGroupKeyInterface.deleteAll() }
        coVerify(exactly = 1) { mockPrekeyInterface.deleteAll() }
        coVerify(exactly = 1) { mockOneTimePrekeyInterface.deleteAll() }
        coVerify(exactly = 1) { mockMembershipCertificateInterface.deleteAll() }
        coVerify(exactly = 1) { mockConversationStateInterface.deleteAll() }
        coVerify(exactly = 2) { mockMessageKeyCacheInterface.deleteAll() }
    }
}
