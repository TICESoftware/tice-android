@file:Suppress("NAME_SHADOWING")

package tice.managers.messaging

import kotlinx.serialization.json.Json
import tice.backend.BackendType
import tice.crypto.CryptoManagerType
import tice.dagger.scopes.AppScope
import tice.managers.ConversationManagerDelegate
import tice.managers.ConversationManagerType
import tice.managers.SignedInUserManagerType
import tice.models.*
import tice.models.messaging.*
import tice.utility.getLogger
import tice.utility.serializer.PayloadContainerSerializer
import java.util.*
import javax.inject.Inject
import javax.inject.Named

@AppScope
class Mailbox @Inject constructor(
    private val backend: BackendType,
    private val cryptoManager: CryptoManagerType,
    private val signedInUserManager: SignedInUserManagerType,
    private val conversationManager: ConversationManagerType,
    @Named("MAILBOX_DEFAULT_TIMEOUT") private val defaultMessageTimeToLive: Long
) : MailboxType, ConversationManagerDelegate {
    val logger by getLogger()

    override fun registerForDelegate() {
        conversationManager.setDelegate(this)
    }

    override suspend fun sendToGroup(
        payloadContainer: PayloadContainer,
        members: Set<Membership>,
        serverSignedMembershipCertificate: Certificate,
        priority: MessagePriority,
        collapseIdentifier: CollapseIdentifier?
    ) {
        val signedInUser = signedInUserManager.signedInUser

        if (members.isEmpty()) {
            logger.debug("Not sending message because set of recipients is empty.")
            return
        }

        logger.debug("Sending message of type ${payloadContainer.payloadType} to ${members.size} recipients.")

        val encryptedPayloadContainer = encrypt(payloadContainer)

        val recipients = members.map { membership ->
            val certificate = membership.serverSignedMembershipCertificate
            recipient(membership.userId, certificate, encryptedPayloadContainer.messageKey, collapseIdentifier != null)
        }.toSet()

        postRecipients(
            encryptedPayloadContainer.ciphertext,
            serverSignedMembershipCertificate,
            signedInUser.userId,
            recipients,
            priority,
            collapseIdentifier
        )
    }

    private suspend fun postRecipients(
        encryptedMessage: Data,
        serverSignedMembershipCertificate: Certificate,
        senderId: UserId,
        recipients: Set<Recipient>,
        priority: MessagePriority,
        collapseIdentifier: CollapseIdentifier? = null,
        messageTimeToLive: Long = defaultMessageTimeToLive
    ) {
        val id = MessageId.randomUUID()

        backend.message(
            id,
            senderId,
            Date().time,
            encryptedMessage,
            serverSignedMembershipCertificate,
            recipients,
            priority,
            collapseIdentifier,
            messageTimeToLive
        )
    }

    private suspend fun send(
        payloadContainer: PayloadContainer?,
        userId: UserId,
        receiverCertificate: Certificate,
        senderCertificate: Certificate,
        collapseIdentifier: CollapseIdentifier?
    ) {
        val payloadContainer = payloadContainer ?: PayloadContainer(Payload.PayloadType.ResetConversationV1, ResetConversation)
        val encryptedPayloadContainer = encrypt(payloadContainer)
        val recipient = recipient(userId, receiverCertificate, encryptedPayloadContainer.messageKey, false)

        postRecipients(
            encryptedPayloadContainer.ciphertext,
            senderCertificate,
            signedInUserManager.signedInUser.userId,
            setOf(recipient),
            MessagePriority.Background,
            collapseIdentifier
        )
    }

    private suspend fun recipient(
        userId: UserId,
        serverSignedMembershipCertificate: Certificate,
        messageKey: SecretKey,
        collapsing: Boolean
    ): Recipient {
        val encryptedData = conversationManager.encrypt(messageKey, userId, collapsing)
        val conversationInvitation = conversationManager.conversationInvitation(userId, collapsing)
        return Recipient(userId, serverSignedMembershipCertificate, encryptedData, conversationInvitation)
    }

    private fun encrypt(payloadContainer: PayloadContainer): EncryptionResult {
        val jsonObject = Json.encodeToString(PayloadContainerSerializer, payloadContainer)
        val encryptionResult = cryptoManager.encrypt(jsonObject.toByteArray())
        return EncryptionResult(encryptionResult.first, encryptionResult.second)
    }

    override suspend fun sendResetReply(
        userId: UserId,
        receiverCertificate: Certificate,
        senderCertificate: Certificate,
        collapseId: CollapseIdentifier?
    ) {
        logger.debug("Sending reset to user $userId.")
        send(null, userId, receiverCertificate, senderCertificate, collapseId)
    }
}
