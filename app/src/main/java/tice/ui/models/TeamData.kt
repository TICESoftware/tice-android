package tice.ui.models

import tice.models.GroupId
import tice.ui.viewModels.TeamListViewModel

data class TeamData(
    val teamName: GroupNameData,
    val groupId: GroupId,
    val memberNames: List<String>,
    val isAdmin: Boolean,
    val locationSharingState: TeamLocationSharingState,
    val unreadMessages: TeamListViewModel.MessageIndicator?
)
