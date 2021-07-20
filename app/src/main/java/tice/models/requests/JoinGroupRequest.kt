package tice.models.requests

import kotlinx.serialization.Serializable
import tice.models.Certificate

@Serializable
data class JoinGroupRequest(
    val selfSignedMembershipCertificate: Certificate,
    val serverSignedAdminCertificate: Certificate? = null,
    val adminSignedMembershipCertificate: Certificate? = null
)
