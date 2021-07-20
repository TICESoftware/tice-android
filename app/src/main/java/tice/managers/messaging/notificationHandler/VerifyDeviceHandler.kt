package tice.managers.messaging.notificationHandler

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import tice.backend.BackendType
import tice.dagger.scopes.AppScope
import tice.exceptions.VerifyDeviceHandlerException
import tice.managers.SignedInUserManagerType
import tice.managers.messaging.PostOfficeType
import tice.managers.storageManagers.DeviceIdStorageManagerType
import tice.models.DeviceId
import tice.models.Platform
import tice.models.VerificationCode
import tice.models.messaging.Payload
import tice.models.messaging.PayloadContainerBundle
import tice.models.messaging.VerificationMessage
import tice.utility.getLogger
import tice.utility.provider.CoroutineContextProviderType
import javax.inject.Inject
import javax.inject.Named

@AppScope
class VerifyDeviceHandler @Inject constructor(
    private val backend: BackendType,
    private val coroutineContextProvider: CoroutineContextProviderType,
    private val postOffice: PostOfficeType,
    private val deviceIdStorageManager: DeviceIdStorageManagerType,
    private val signedInUserManager: SignedInUserManagerType,
    @Named("POST_OFFICE_TIMEOUT") private val POST_OFFICE_TIMEOUT: Long
) : VerifyDeviceHandlerType, PayloadReceiver {
    private val logger by getLogger()

    private val verificationCodeChannel: Channel<VerificationCode> = Channel(Channel.CONFLATED)

    override fun registerEnvelopeReceiver() {
        postOffice.registerEnvelopeReceiver(Payload.PayloadType.VerificationMessageV1, this)
    }

    override suspend fun verifyDeviceId(deviceId: DeviceId): VerificationCode {
        backend.verify(deviceId, Platform.Android)

        return withTimeoutOrNull(POST_OFFICE_TIMEOUT) {
            verificationCodeChannel.receive()
        } ?: run {
            throw VerifyDeviceHandlerException.VerificationTimedOut
        }
    }

    override fun startUpdatingDeviceId(deviceId: DeviceId) {
        if (deviceId == deviceIdStorageManager.loadDeviceId()) {
            logger.debug("deviceId did not change: [$deviceId]")
            return
        }

        deviceIdStorageManager.storeDeviceId(deviceId)

        if (signedInUserManager.signedIn()) {
            CoroutineScope(coroutineContextProvider.IO).launch {
                val signedInUser = signedInUserManager.signedInUser

                try {
                    val verificationCode = verifyDeviceId(deviceId)
                    backend.updateUser(signedInUser.userId, null, deviceId, verificationCode, signedInUser.publicName)
                    logger.debug("Updated the DeviceId to: [$deviceId]")
                } catch (e: Exception) {
                    logger.error("Updating DeviceId failed", e)
                }
            }
        }
    }

    override suspend fun handlePayloadContainerBundle(bundle: PayloadContainerBundle) {
        if (bundle.payload !is VerificationMessage) throw VerifyDeviceHandlerException.UnexpectedPayloadType

        verificationCodeChannel.send(bundle.payload.verificationCode)
    }
}
