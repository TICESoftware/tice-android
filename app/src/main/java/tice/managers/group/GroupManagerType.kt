package tice.managers.group

import tice.models.Membership
import tice.models.*
import tice.models.messaging.GroupUpdate
import tice.models.messaging.MessagePriority
import tice.models.messaging.PayloadContainer

interface GroupManagerType {
    suspend fun addUserMember(
        group: Group,
        admin: Boolean,
        serverSignedMembershipCertificate: Certificate,
        notificationRecipients: Collection<NotificationRecipient>
    ): Pair<Membership, GroupTag>

    suspend fun deleteGroupMember(
        membership: Membership,
        group: Group,
        serverSignedMembershipCertificate: Certificate,
        notificationRecipients: Collection<NotificationRecipient>
    ): GroupTag

    suspend fun leave(
        group: Group,
        notificationRecipients: Collection<NotificationRecipient>
    ): GroupTag

    suspend fun send(
        payloadContainer: PayloadContainer,
        group: Group,
        collapseId: CollapseIdentifier?,
        priority: MessagePriority
    )

    suspend fun notificationRecipients(
        groupId: GroupId,
        priority: MessagePriority
    ): List<NotificationRecipient>

    suspend fun sendGroupUpdateNotification(
        group: Group,
        action: GroupUpdate.Action
    )

    fun registerForDelegate()
}
