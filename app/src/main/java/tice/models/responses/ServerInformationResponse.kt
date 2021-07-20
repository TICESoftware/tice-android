package tice.models.responses

import kotlinx.serialization.Serializable
import tice.utility.serializer.DateSerializer
import java.util.*

@Serializable
data class ServerInformationResponse(
    val deployedCommit: String? = null,
    @Serializable(with = DateSerializer::class)
    val deployedAt: Date? = null,
    val minVersion: MinVersion,
    val env: String
)

@Serializable
data class MinVersion(
    val iOS: Int,
    val android: Int
)
