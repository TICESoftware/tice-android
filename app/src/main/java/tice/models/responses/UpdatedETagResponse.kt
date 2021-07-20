package tice.models.responses

import kotlinx.serialization.Serializable
import tice.models.GroupTag

@Serializable
class UpdatedETagResponse(val groupTag: GroupTag)
