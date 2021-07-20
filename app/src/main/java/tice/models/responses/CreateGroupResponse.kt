@file:UseSerializers(
    URLSerializer::class
)

package tice.models.responses

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import tice.models.Certificate
import tice.models.GroupTag
import tice.utility.serializer.URLSerializer
import java.net.URL

@Serializable
data class CreateGroupResponse(
    val url: URL,
    val serverSignedAdminCertificate: Certificate,
    val groupTag: GroupTag
)
