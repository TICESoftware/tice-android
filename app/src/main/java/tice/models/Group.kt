@file:UseSerializers(
    UUIDSerializer::class,
    DataSerializer::class,
    URLSerializer::class
)

package tice.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import tice.utility.serializer.DataSerializer
import tice.utility.serializer.URLSerializer
import tice.utility.serializer.UUIDSerializer
import tice.utility.toBase64URLSafeString
import java.net.URL

sealed class Group {
    abstract val groupId: GroupId
    abstract val groupKey: SecretKey
    abstract val owner: UserId
    abstract val joinMode: JoinMode
    abstract val permissionMode: PermissionMode
    abstract var tag: GroupTag
}

@Serializable
@Entity
data class Meetup(
    @PrimaryKey
    override val groupId: GroupId,
    override val groupKey: SecretKey,
    override val owner: UserId,
    override val joinMode: JoinMode,
    override val permissionMode: PermissionMode,
    override var tag: GroupTag,
    val teamId: GroupId,
    var meetingPoint: Location?
) : Group() {
    @Serializable
    data class InternalSettings(
        var location: Location? = null
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Meetup

        if (groupId != other.groupId) return false
        if (!groupKey.contentEquals(other.groupKey)) return false
        if (owner != other.owner) return false
        if (joinMode != other.joinMode) return false
        if (permissionMode != other.permissionMode) return false
        if (tag != other.tag) return false
        if (teamId != other.teamId) return false
        if (meetingPoint != other.meetingPoint) return false

        return true
    }

    override fun hashCode(): Int {
        var result = groupId.hashCode()
        result = 31 * result + groupKey.contentHashCode()
        result = 31 * result + owner.hashCode()
        result = 31 * result + joinMode.hashCode()
        result = 31 * result + permissionMode.hashCode()
        result = 31 * result + tag.hashCode()
        result = 31 * result + teamId.hashCode()
        result = 31 * result + (meetingPoint?.hashCode() ?: 0)
        return result
    }
}

@Entity
@Serializable
data class Team(
    @PrimaryKey
    override val groupId: GroupId,
    override val groupKey: SecretKey,
    override val owner: UserId,
    override val joinMode: JoinMode,
    override val permissionMode: PermissionMode,
    override var tag: GroupTag,
    val url: URL,
    var name: String? = null,
    var meetupId: GroupId? = null,
    var meetingPoint: Location?
) : Group() {
    @Serializable
    data class InternalTeamSettings(
        var meetingPoint: Location? = null
    )

    val shareURL: URL
        get() {
            val url = URL(url.toString() + "#" + groupKey.toBase64URLSafeString())
            return url
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Team

        if (groupId != other.groupId) return false
        if (!groupKey.contentEquals(other.groupKey)) return false
        if (owner != other.owner) return false
        if (joinMode != other.joinMode) return false
        if (permissionMode != other.permissionMode) return false
        if (tag != other.tag) return false
        if (url != other.url) return false
        if (name != other.name) return false
        if (meetupId != other.meetupId) return false
        if (meetingPoint != other.meetingPoint) return false

        return true
    }

    override fun hashCode(): Int {
        var result = groupId.hashCode()
        result = 31 * result + groupKey.contentHashCode()
        result = 31 * result + owner.hashCode()
        result = 31 * result + joinMode.hashCode()
        result = 31 * result + permissionMode.hashCode()
        result = 31 * result + tag.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + (meetupId?.hashCode() ?: 0)
        result = 31 * result + (meetingPoint?.hashCode() ?: 0)
        return result
    }
}
