@file:UseSerializers(
    DateSerializer::class
)

package tice.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import tice.utility.serializer.DateSerializer
import java.util.*

@Serializable
data class Location(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val horizontalAccuracy: Float,
    val verticalAccuracy: Float,
    val timestamp: Date
)
