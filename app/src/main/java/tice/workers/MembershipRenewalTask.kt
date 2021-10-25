package tice.workers

import androidx.work.ListenableWorker.Result
import kotlinx.serialization.json.Json
import tice.backend.BackendType
import tice.crypto.AuthManagerType
import tice.crypto.CryptoManagerType
import tice.exceptions.MembershipRenewalException
import tice.managers.SignedInUserManagerType
import tice.managers.storageManagers.GroupStorageManagerType
import tice.managers.storageManagers.MembershipsDiff
import tice.models.*
import tice.models.messaging.MessagePriority
import tice.utility.TrackerType
import tice.utility.getLogger
import tice.utility.serializer.MembershipSerializer
import java.util.*
import javax.inject.Inject

class MembershipRenewalTask @Inject constructor(
    private val membershipRenewalConfig: MembershipRenewalConfig,
    private val signedInUserManager: SignedInUserManagerType,
    private val groupStorageManager: GroupStorageManagerType,
    private val cryptoManager: CryptoManagerType,
    private val authManager: AuthManagerType,
    private val backend: BackendType,
    private val tracker: TrackerType
) {
    private val logger by getLogger()

    suspend fun doWork(): Result {
        logger.debug("Started backend synchronization work.")
        tracker.track(TrackerEvent.membershipRenewalWorkerStarted())

        if (!signedInUserManager.signedIn()) {
            logger.debug("Cancel membership renewal: not signed in")
            tracker.track(TrackerEvent.membershipRenewalWorkerCompleted())
            return Result.failure()
        }
        val signedInUser = signedInUserManager.signedInUser

        val memberships = groupStorageManager.loadMembershipsOfUser(signedInUser.userId)

        val renewMemberships = memberships.filter { membership ->
            val renewSelfSignedMembershipCertificate = membership.selfSignedMembershipCertificate?.let {
                authManager.membershipCertificateExpirationDate(it, membership.publicSigningKey).time - Date().time < membershipRenewalConfig.certificateValidityTimeRenewalThreshold
            } ?: false

            val renewServerSignedMembershipCertificate =
                authManager.membershipCertificateExpirationDate(membership.serverSignedMembershipCertificate, membership.publicSigningKey).time - Date().time < membershipRenewalConfig.certificateValidityTimeRenewalThreshold

            renewSelfSignedMembershipCertificate || renewServerSignedMembershipCertificate
        }

        renewMemberships.forEach { membership ->
            try {
                val renewedSelfSignedMembershipCertificate = authManager.createUserSignedMembershipCertificate(
                    membership.userId,
                    membership.groupId,
                    membership.admin,
                    signedInUser.userId,
                    signedInUser.privateSigningKey
                )

                val renewedServerSignedMembershipCertificate = backend.renewCertificate(membership.serverSignedMembershipCertificate)
                    .certificate

                val renewedMembership = Membership(
                    membership.userId,
                    membership.groupId,
                    membership.publicSigningKey,
                    membership.admin,
                    renewedSelfSignedMembershipCertificate,
                    renewedServerSignedMembershipCertificate,
                    null
                )

                val group = groupStorageManager.loadTeam(membership.groupId)
                    ?: groupStorageManager.loadMeetup(membership.groupId)
                    ?: throw MembershipRenewalException.GroupNotFoundException

                val renewedMembershipData = Json.encodeToString(MembershipSerializer, renewedMembership).toByteArray()
                val encryptedRenewedMembershipData = cryptoManager.encrypt(renewedMembershipData, group.groupKey)

                val tokenKey = cryptoManager.tokenKeyForGroup(group.groupKey, signedInUser)
                val groupMemberships = groupStorageManager.loadMembershipsOfGroup(group.groupId).filter { it.userId != signedInUser.userId }

                val notificationRecipients = groupMemberships.map {
                    NotificationRecipient(it.userId, it.serverSignedMembershipCertificate, MessagePriority.Background)
                }

                val updatedGroupTag = backend.updateGroupMember(
                    group.groupId,
                    signedInUser.userId,
                    encryptedRenewedMembershipData,
                    renewedMembership.serverSignedMembershipCertificate,
                    tokenKey,
                    group.tag,
                    notificationRecipients
                ).groupTag

                group.tag = updatedGroupTag

                when (group) {
                    is Team -> groupStorageManager.storeTeam(group, MembershipsDiff.Replace(listOf(renewedMembership)))
                    is Meetup -> groupStorageManager.storeMeetup(group, MembershipsDiff.Replace(listOf(renewedMembership)))
                }
            } catch (e: Exception) {
                logger.error("Certificate renewal failed for group: ${membership.groupId}", e)
            }
        }

        logger.debug("Completed membership renewal work.")
        tracker.track(TrackerEvent.membershipRenewalWorkerCompleted())
        return Result.success()
    }
}
