package tice.ui.fragments

import android.view.View
import com.google.android.gms.maps.model.LatLng
import tice.models.Coordinates
import tice.models.Location
import tice.models.UserId
import tice.models.UserLocation
import tice.ui.viewModels.MapContainerViewModel

interface MapContainerFragmentInterface {
    val viewModel: MapContainerViewModel
    val currentMarkedPosition: Coordinates?

    suspend fun handleMemberLocationUpdate(update: UserLocation)
    fun handleNewMeetingPoint(meetingPoint: Location?)
    fun handleSearchButtonTap(view: View)
    fun removeMarkedLocation()
    fun handleRemovedMember(userId: UserId)
    fun enableUserLocationIndicator()

    fun rectFitMap()
    fun moveCamera(includingPoints: Set<Coordinates>)

    fun markCustomPosition(coordinates: Coordinates)
    fun locationString(coordinates: Coordinates): String
}