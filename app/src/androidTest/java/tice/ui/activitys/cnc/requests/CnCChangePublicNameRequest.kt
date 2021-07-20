package tice.ui.activitys.cnc.requests

import kotlinx.serialization.Serializable

@Serializable
data class CnCChangePublicNameRequest(
    private val publicName: String
)
