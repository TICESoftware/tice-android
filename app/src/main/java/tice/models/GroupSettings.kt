@file:UseSerializers(
    UUIDSerializer::class
)

package tice.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import tice.utility.serializer.UUIDSerializer

@Serializable
data class GroupSettings(
    val owner: UserId,
    val name: String? = null
)
