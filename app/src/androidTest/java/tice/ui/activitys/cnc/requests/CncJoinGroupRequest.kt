@file:UseSerializers(
    UUIDSerializer::class
)

package tice.ui.activitys.cnc.requests

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import tice.models.GroupId
import tice.utility.serializer.UUIDSerializer

@Serializable
data class CnCJoinGroupRequest(
    private val groupId: GroupId,
    private val groupKey: String
)