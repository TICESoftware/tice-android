package tice.models.requests

import kotlinx.serialization.Serializable
import tice.models.DeviceId
import tice.models.Platform

@Serializable
data class VerifyRequest(
    val deviceId: DeviceId,
    val platform: Platform
)
