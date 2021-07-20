package tice.ui.adapter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.View
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import com.ticeapp.TICE.R
import com.ticeapp.TICE.databinding.CustomMapMarkerBinding
import tice.models.Location
import tice.models.UserId
import tice.ui.viewModels.MapViewModel
import tice.utility.getLogger

class MapMarkerAdapter constructor(
    private val context: Context,
    private val map: GoogleMap?
) {
    private val logger by getLogger()

    private var newTempMeetingPoint: Marker? = null
    private var currentMeetingPoint: Marker? = null
    private val userMarkers: MutableMap<UserId, Marker> = mutableMapOf()
    var userLocation: Location? = null
        set(value) {
            field = value
            if (rectFittingEnabled) {
                doRectFitting()
            }
        }
    var rectFittingEnabled = false
    var binding = CustomMapMarkerBinding.inflate(LayoutInflater.from(context))

    fun handleLocationUpdate(userLocationUpdate: MapViewModel.UserLocationUpdate) {
        val location = LatLng(userLocationUpdate.latitude, userLocationUpdate.longitude)
        userMarkers[userLocationUpdate.userId]?.apply {
            position = location
            title = userLocationUpdate.displayName
        } ?: map?.let {
            val marker = it.addMarker(
                MarkerOptions()
                    .position(location)
                    .title(userLocationUpdate.displayName)
                    .icon(
                        BitmapDescriptorFactory.fromBitmap(
                            getMarkerBitmapFromView(
                                userLocationUpdate.shortName,
                                userLocationUpdate.color
                            )
                        )
                    )
            )
            marker.tag = userLocationUpdate.userId
            userMarkers[userLocationUpdate.userId] = marker
        }
        if (rectFittingEnabled) {
            doRectFitting()
        }
    }

    fun handleUserIdChange(userIds: Set<UserId>?) {
        userIds?.let {
            val toBeRemoved = userMarkers.filterKeys {
                !userIds.contains(it)
            }
            toBeRemoved.values.forEach {
                logger.debug("Remove marker for user.")
                it.remove()
                userMarkers.values.remove(it)
            }
        } ?: removeAllMarkers()
        if (rectFittingEnabled) {
            doRectFitting()
        }
    }

    fun handleMeetingPointUpdate(location: Location?) {
        location?.let {
            currentMeetingPoint ?: run {
                currentMeetingPoint = map?.addMarker(MarkerOptions().position(LatLng(location.latitude, location.longitude)))
                currentMeetingPoint?.showInfoWindow()
            }

            currentMeetingPoint?.apply {
                position = LatLng(location.latitude, location.longitude)
                title = context.getString(R.string.map_location_meetingPoint)
                showInfoWindow()
            }
        } ?: currentMeetingPoint?.remove()
    }

    fun markNewMeetingPointPosition(position: LatLng, name: String) {
        newTempMeetingPoint?.remove()
        newTempMeetingPoint = map?.addMarker(MarkerOptions().position(position).title(name))
        newTempMeetingPoint?.showInfoWindow()
    }

    fun removeNewMeetingPointPosition() {
        newTempMeetingPoint?.remove()
    }

    fun getNewMeetingPointPosition(): LatLng? = newTempMeetingPoint?.position

    private fun removeAllMarkers() {
        logger.debug("Remove all markers from map.")
        if (!userMarkers.values.isNullOrEmpty()) {
            userMarkers.values.forEach {
                it.remove()
            }
        }
        userMarkers.clear()
    }

    private fun getMarkerBitmapFromView(text: String, color: Int): Bitmap {
        val customMarkerView: View = binding.root

        binding.customMarkerText.text = text
        ((binding.customMakerLayout.background as LayerDrawable).getDrawable(0) as GradientDrawable).setColor(color)

        customMarkerView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        customMarkerView.layout(0, 0, customMarkerView.measuredWidth, customMarkerView.measuredHeight)

        val usedHeight = customMarkerView.measuredHeight / 3 + customMarkerView.measuredHeight
        val returnedBitmap = Bitmap.createBitmap(customMarkerView.measuredWidth, usedHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        customMarkerView.draw(canvas)
        return returnedBitmap
    }

    fun doRectFitting() {
        val bounds = LatLngBounds.builder()

        if (!userMarkers.isNullOrEmpty()) {
            userMarkers.values.forEach { bounds.include(it.position) }
        }
        userLocation?.let { bounds.include(LatLng(it.latitude, it.longitude)) }
        currentMeetingPoint?.let { bounds.include(it.position) }

        try {
            map?.setMaxZoomPreference(17.0f)
            map?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 200))
        } catch (e: Exception) {
            logger.debug("Couldn't apply rectangular bounds.")
        }
    }
}
