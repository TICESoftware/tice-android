@file:UseSerializers(
    UUIDSerializer::class,
    DataSerializer::class,
    URLSerializer::class
)

package tice.models.responses

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import tice.models.*
import tice.utility.serializer.DataSerializer
import tice.utility.serializer.URLSerializer
import tice.utility.serializer.UUIDSerializer
import java.net.URL

@Serializable
data class GroupInternalsResponse(
    val groupId: GroupId,
    val parentGroupId: GroupId? = null,
    val type: GroupType,
    val joinMode: JoinMode,
    val permissionMode: PermissionMode,
    val url: URL,
    val encryptedSettings: Ciphertext,
    val encryptedInternalSettings: Ciphertext,
    val encryptedMemberships: Array<Ciphertext>,
    val parentEncryptedGroupKey: Ciphertext? = null,
    val children: Array<GroupId>,
    val groupTag: GroupTag
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GroupInternalsResponse

        if (groupId != other.groupId) return false
        if (parentGroupId != other.parentGroupId) return false
        if (type != other.type) return false
        if (joinMode != other.joinMode) return false
        if (permissionMode != other.permissionMode) return false
        if (url != other.url) return false
        if (!encryptedSettings.contentEquals(other.encryptedSettings)) return false
        if (!encryptedInternalSettings.contentEquals(other.encryptedInternalSettings)) return false
        if (!encryptedMemberships.contentDeepEquals(other.encryptedMemberships)) return false
        if (parentEncryptedGroupKey != null) {
            if (other.parentEncryptedGroupKey == null) return false
            if (!parentEncryptedGroupKey.contentEquals(other.parentEncryptedGroupKey)) return false
        } else if (other.parentEncryptedGroupKey != null) return false
        if (!children.contentEquals(other.children)) return false
        if (groupTag != other.groupTag) return false

        return true
    }

    override fun hashCode(): Int {
        var result = groupId.hashCode()
        result = 31 * result + (parentGroupId?.hashCode() ?: 0)
        result = 31 * result + type.hashCode()
        result = 31 * result + joinMode.hashCode()
        result = 31 * result + permissionMode.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + encryptedSettings.contentHashCode()
        result = 31 * result + encryptedInternalSettings.contentHashCode()
        result = 31 * result + encryptedMemberships.contentDeepHashCode()
        result = 31 * result + (parentEncryptedGroupKey?.contentHashCode() ?: 0)
        result = 31 * result + children.contentHashCode()
        result = 31 * result + groupTag.hashCode()
        return result
    }
}
