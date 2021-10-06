package tice.utility.beekeeper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tice.utility.serializer.DateSerializer
import java.util.*

typealias Day = String

@Serializable
data class Event(
    val id: String,
    @SerialName("p")
    val product: String,
    @Serializable(with = DateSerializer::class)
    @SerialName("t")
    val timestamp: Date,
    val name: String,
    val group: String,
    val detail: String?,
    val value: Double?,
    @SerialName("prev")
    val previousEvent: String?,
    @SerialName("last")
    val previousEventTimestamp: Day?,
    val install: Day,
    val custom: List<String?>,
)

@Serializable
data class URLDispatcherError(
    val error: String
) : Exception()
