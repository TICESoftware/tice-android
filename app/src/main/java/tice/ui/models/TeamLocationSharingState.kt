package tice.ui.models

sealed class TeamLocationSharingState {
    object None : TeamLocationSharingState()
    object WeShareLocation : TeamLocationSharingState()
    object TheyShareLocation : TeamLocationSharingState()
    data class OneSharesLocation(val userName: String) : TeamLocationSharingState()
}
