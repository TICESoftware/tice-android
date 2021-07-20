@file:UseSerializers(
    DateSerializer::class,
    UUIDSerializer::class
)

package tice.models

import androidx.room.Entity
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import tice.utility.serializer.DateSerializer
import tice.utility.serializer.UUIDSerializer
import java.util.*

@Entity(primaryKeys = ["userId", "groupId"])
@Serializable
data class LocationSharingState(
    val userId: UserId,
    val groupId: GroupId,
    var sharingEnabled: Boolean,
    var lastUpdate: Date
)
