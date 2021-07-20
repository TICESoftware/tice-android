package tice.models.responses

import kotlinx.serialization.Serializable
import tice.models.Certificate

@Serializable
data class JoinGroupResponse(val serverSignedMembershipCertificate: Certificate)
