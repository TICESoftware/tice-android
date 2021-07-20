@file:UseSerializers(
    UUIDSerializer::class,
    DataSerializer::class
)

package tice.models.requests

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import tice.models.*
import tice.utility.serializer.DataSerializer
import tice.utility.serializer.UUIDSerializer

@Serializable
data class CreateGroupRequest(
    val groupId: GroupId,
    val type: GroupType,
    val joinMode: JoinMode,
    val permissionMode: PermissionMode,
    val selfSignedAdminCertificate: Certificate,
    val encryptedSettings: Ciphertext,
    val encryptedInternalSettings: Ciphertext,
    val parent: ParentGroup?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CreateGroupRequest

        if (groupId != other.groupId) return false
        if (type != other.type) return false
        if (joinMode != other.joinMode) return false
        if (permissionMode != other.permissionMode) return false
        if (selfSignedAdminCertificate != other.selfSignedAdminCertificate) return false
        if (!encryptedSettings.contentEquals(other.encryptedSettings)) return false
        if (!encryptedInternalSettings.contentEquals(other.encryptedInternalSettings)) return false
        if (parent != other.parent) return false

        return true
    }

    override fun hashCode(): Int {
        var result = groupId.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + joinMode.hashCode()
        result = 31 * result + permissionMode.hashCode()
        result = 31 * result + selfSignedAdminCertificate.hashCode()
        result = 31 * result + encryptedSettings.contentHashCode()
        result = 31 * result + encryptedInternalSettings.contentHashCode()
        result = 31 * result + (parent?.hashCode() ?: 0)
        return result
    }
}
