package tice.managers.messaging.notificationHandler

import tice.models.DeviceId
import tice.models.VerificationCode

interface VerifyDeviceHandlerType {

    fun startUpdatingDeviceId(deviceId: DeviceId)
    suspend fun verifyDeviceId(deviceId: DeviceId): VerificationCode
}
