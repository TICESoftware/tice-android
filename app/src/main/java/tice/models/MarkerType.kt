package tice.models

import java.util.*

sealed class MarkerType {
    data class UserMarker(val userId: UserId, val timestamp: Date, val name: String) : MarkerType()
    object MeetingPointMarker : MarkerType()
    object CustomPositionMarker : MarkerType()
}
