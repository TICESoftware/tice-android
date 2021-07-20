package tice.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class GroupType {
    @SerialName("team")
    Team,

    @SerialName("meetup")
    Meetup
}
