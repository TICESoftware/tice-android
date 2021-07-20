@file:UseSerializers(UUIDSerializer::class)

package tice.models.responses

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import tice.models.UserId
import tice.utility.serializer.UUIDSerializer

@Serializable
data class CreateUserResponse(
    val userId: UserId
)
