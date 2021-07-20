package tice.managers.messaging.notificationHandler

import tice.backend.BackendType
import tice.crypto.ConversationCryptoMiddlewareType
import tice.dagger.scopes.AppScope
import tice.managers.SignedInUserManagerType
import tice.managers.messaging.PostOfficeType
import tice.models.messaging.Payload
import tice.models.messaging.PayloadContainerBundle
import tice.utility.getLogger
import javax.inject.Inject

@AppScope
class FewOneTimePrekeysReceiver @Inject constructor(
    private val postOffice: PostOfficeType,
    private val signedInUserManager: SignedInUserManagerType,
    private val conversationCryptoMiddleware: ConversationCryptoMiddlewareType,
    private val backend: BackendType
) : PayloadReceiver {
    private val logger by getLogger()

    override fun registerEnvelopeReceiver() {
        postOffice.registerEnvelopeReceiver(Payload.PayloadType.FewOneTimePrekeysV1, this)
    }

    override suspend fun handlePayloadContainerBundle(bundle: PayloadContainerBundle) {
        val user = signedInUserManager.signedInUser
        val userPublicKeys = conversationCryptoMiddleware.renewHandshakeKeyMaterial(user.privateSigningKey, user.publicSigningKey)

        try {
            backend.updateUser(
                user.userId,
                userPublicKeys,
                null,
                null,
                user.publicName
            )
        } catch (e: Exception) {
            logger.error("Updating one-time prekeys failed.", e)
        }
    }
}
