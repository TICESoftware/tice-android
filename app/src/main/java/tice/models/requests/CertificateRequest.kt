package tice.models.requests

import kotlinx.serialization.Serializable
import tice.models.Certificate

@Serializable
data class CertificateRequest(
    val certificate: Certificate
)

typealias RevokeCertificateRequest = CertificateRequest
typealias CertificateBlacklistedRequest = CertificateRequest
typealias RenewCertificateRequest = CertificateRequest
typealias RenewCertificateResponse = CertificateRequest
