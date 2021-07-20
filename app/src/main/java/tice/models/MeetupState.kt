package tice.models

sealed class MeetupState {
    object None : MeetupState()
    data class Invited(val meetup: Meetup) : MeetupState()
    data class Participating(val meetup: Meetup) : MeetupState()
}
