@file:UseSerializers(
    UUIDSerializer::class,
    DataSerializer::class
)

package tice.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import tice.utility.serializer.DataSerializer
import tice.utility.serializer.UUIDSerializer

@Serializable
data class ParentGroup(
    val groupId: GroupId,
    val encryptedChildGroupKey: Ciphertext
)
