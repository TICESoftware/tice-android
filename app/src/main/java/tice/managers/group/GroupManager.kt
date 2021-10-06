package tice.managers.group

import kotlinx.serialization.json.Json
import tice.backend.BackendType
import tice.crypto.AuthManagerType
import tice.crypto.CryptoManagerType
import tice.dagger.scopes.AppScope
import tice.exceptions.GroupManagerException
import tice.managers.SignedInUserManagerType
import tice.managers.messaging.MailboxType
import tice.managers.storageManagers.GroupStorageManagerType
import tice.models.*
import tice.models.messaging.GroupUpdate
import tice.models.messaging.MessagePriority
import tice.models.messaging.Payload
import tice.models.messaging.PayloadContainer
import tice.utility.serializer.MembershipSerializer
import java.lang.ref.WeakReference
import javax.inject.Inject

@AppScope
class GroupManager @Inject constructor(
    private val signedInUserManager: SignedInUserManagerType,
    private val backend: BackendType,
    private val cryptoManager: CryptoManagerType,
    private val authManager: AuthManagerType,
    private val groupStorageManager: GroupStorageManagerType,
    private val mailbox: MailboxType
) : GroupManagerType {

    override fun registerForDelegate() {
        signedInUserManager.groupManagerDelegate = WeakReference(this)
    }

    override suspend fun addUserMember(
        group: Group,
        admin: Boolean,
        serverSignedMembershipCertificate: Certificate,
        notificationRecipients: Collection<NotificationRecipient>
    ): Pair<Membership, GroupTag> {
        val user = signedInUserManager.signedInUser
        val selfSignedMembershipCertificate =
            authManager.createUserSignedMembershipCertificate(user.userId, group.groupId, admin, user.userId, user.privateSigningKey)

        val membership = Membership(
            user.userId,
            group.groupId,
            user.publicSigningKey,
            admin,
            selfSignedMembershipCertificate,
            serverSignedMembershipCertificate
        )

        val membershipData = Json.encodeToString(MembershipSerializer, membership).toByteArray()
        val encryptedMembership = cryptoManager.encrypt(membershipData, group.groupKey)

        val tokenKey = cryptoManager.tokenKeyForGroup(group.groupKey, user)

        val response = backend.addGroupMember(
            group.groupId,
            user.userId,
            encryptedMembership,
            serverSignedMembershipCertificate,
            tokenKey,
            group.tag,
            notificationRecipients.toList()
        )

        return Pair(membership, response.groupTag)
    }

    override suspend fun leave(group: Group, notificationRecipients: Collection<NotificationRecipient>): GroupTag {
        val signedInUser = signedInUserManager.signedInUser
        val membership = groupStorageManager.loadMembership(signedInUser.userId, group.groupId)

        if (membership.admin) {
            throw GroupManagerException.LastAdminException
        }

        val tokenKey = cryptoManager.tokenKeyForGroup(group.groupKey, signedInUserManager.signedInUser)

        val response = backend.deleteGroupMember(
            group.groupId,
            group.tag,
            tokenKey,
            signedInUser.userId,
            membership.serverSignedMembershipCertificate,
            membership.serverSignedMembershipCertificate,
            notificationRecipients.toList()
        )

        return response.groupTag
    }

    override suspend fun deleteGroupMember(
        membership: Membership,
        group: Group,
        serverSignedMembershipCertificate: Certificate,
        notificationRecipients: Collection<NotificationRecipient>
    ): GroupTag {
        if (membership.admin) {
            throw GroupManagerException.LastAdminException
        }

        val user = groupStorageManager.loadUser(membership)
        val tokenKey = cryptoManager.tokenKeyForGroup(group.groupKey, user)

        val response = backend.deleteGroupMember(
            group.groupId,
            group.tag,
            tokenKey,
            membership.userId,
            membership.serverSignedMembershipCertificate,
            serverSignedMembershipCertificate,
            notificationRecipients.toList()
        )

        return response.groupTag
    }

    override suspend fun send(
        payloadContainer: PayloadContainer,
        group: Group,
        collapseId: CollapseIdentifier?,
        priority: MessagePriority
    ) {
        val signedInUser = signedInUserManager.signedInUser
        val signedInUserMembership = groupStorageManager.loadMembership(signedInUser.userId, group.groupId)
        val memberships = groupStorageManager.loadMembershipsOfGroup(group.groupId).filter { it.userId != signedInUser.userId }

        mailbox.sendToGroup(
            payloadContainer,
            memberships.toSet(),
            signedInUserMembership.serverSignedMembershipCertificate,
            priority,
            collapseId
        )
    }

    override suspend fun notificationRecipients(groupId: GroupId, priority: MessagePriority): List<NotificationRecipient> {
        val memberships = groupStorageManager.loadMembershipsOfGroup(groupId)
        val filteredMemberships = memberships.filter { it.userId != signedInUserManager.signedInUser.userId }
        return filteredMemberships.map { NotificationRecipient(it.userId, it.serverSignedMembershipCertificate, priority) }
    }

    override suspend fun sendGroupUpdateNotification(group: Group, action: GroupUpdate.Action) {
        val groupUpdate = GroupUpdate(group.groupId, action)
        val payloadContainer = PayloadContainer(Payload.PayloadType.GroupUpdateV1, groupUpdate)

        send(payloadContainer, group, null, MessagePriority.Alert)
    }
}
