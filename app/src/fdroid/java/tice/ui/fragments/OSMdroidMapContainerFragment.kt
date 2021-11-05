package tice.ui.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import com.ticeapp.TICE.NavigationControllerDirections
import com.ticeapp.TICE.R
import com.ticeapp.TICE.databinding.OsmdroidContainerFragmentBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration.getInstance
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.ItemizedIconOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.OverlayItem
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import tice.models.*
import tice.ui.viewModels.OSMdroidContainerViewModel
import tice.utility.coordinates
import tice.utility.geoPoint
import tice.utility.ui.getViewModel
import tice.utility.uuidString

class OSMdroidMapContainerFragment : MapContainerFragment() {
    private val binding get() = _binding!! as OsmdroidContainerFragmentBinding

    override val viewModel: OSMdroidContainerViewModel by getViewModel()

    private lateinit var map: MapView
    private lateinit var userLocationProvider: GpsMyLocationProvider
    private lateinit var userLocationOverlay: MyLocationNewOverlay

    private lateinit var mapEventsOverlay: MapEventsOverlay

    private var markedLocation: Marker? = null
    private var meetingPointMarker: Marker? = null
    private val userMarkers: HashMap<UserId, Marker> = hashMapOf()

    override val currentMarkedPosition: Coordinates?
        get() = markedLocation?.position?.coordinates()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        getInstance().load(requireContext(), PreferenceManager.getDefaultSharedPreferences(requireContext()))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)
        _binding = OsmdroidContainerFragmentBinding.inflate(inflater, container, false)
        _bottomSheet = binding.bottomSheet
        _mapButtons = binding.mapButtons
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        map = binding.mapView
        viewModel.setupTileSource(map)

        userLocationProvider = GpsMyLocationProvider(requireContext())
        userLocationProvider.startLocationProvider { location, _ ->
            if (currentLocation != location.coordinates()) {
                logger.debug("User location changed.")
                handleNewDeviceLocation(location.latitude, location.longitude)
            }
        }
        userLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), map)

        mapEventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean = handleClickOnMap(p)
            override fun longPressHelper(p: GeoPoint): Boolean = handleLongClickOnMap(p)
        })
        map.overlays.add(mapEventsOverlay)

        // Disable search for now as Osmdroid search is not implemented yet
        _mapButtons!!.searchButton.visibility = View.GONE

        mapSetupFinished()
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        // TODO: pause location updates in user location overlay
    }

    private fun handleClickOnMap(p: GeoPoint): Boolean {
        super.handleClickOnMap()
        return true
    }

    private fun handleLongClickOnMap(p: GeoPoint): Boolean {
        super.handleLongClickOnMap(p.coordinates())
        return true
    }

    override suspend fun handleMemberLocationUpdate(update: UserLocation) {
        val userNames = viewModel.getMemberNames(update.userId) ?: run {
            logger.error("Could not get user name for member.")
            return
        }

        val position = GeoPoint(update.location.latitude, update.location.longitude)

        val tag = MarkerType.UserMarker(
            update.userId,
            update.location.timestamp,
            userNames.first
        )

        userMarkers[update.userId]?.let { marker ->
            marker.position = position
            marker.relatedObject = tag

            if (bottomSheetUserId == update.userId) {
                showMemberLocationInBottomSheet(
                    update.userId,
                    userNames.first,
                    Coordinates(update.location.latitude, update.location.longitude),
                    update.location.timestamp
                )
            }
        } ?: run {
            val color = viewModel.colorForMember(update.userId)
            val icon = BitmapDrawable(requireContext().resources, getMarkerBitmapFromView(userNames.second, color))

            val marker = Marker(map)
            marker.id = update.userId.uuidString()
            marker.position = position
            marker.relatedObject = tag
            marker.icon = icon
            marker.setOnMarkerClickListener(this::handleClickOnUserMarker)

            map.overlays.add(marker)
            userMarkers[update.userId] = marker
        }

        if (rectFittingEnabled) {
            rectFitMap()
        }
    }

    override fun handleNewMeetingPoint(meetingPoint: Location?) {
        if (meetingPoint != null) {
            val position = GeoPoint(meetingPoint.latitude, meetingPoint.longitude)
            meetingPointMarker?.let {
                it.position = position
            } ?: run {
                val marker = Marker(map)
                marker.position = position
                marker.title = getString(R.string.map_location_meetingPoint)
                map.overlays.add(marker)
                meetingPointMarker = marker
            }
        } else {
            meetingPointMarker?.let {
                map.overlays.remove(it)
            }
            meetingPointMarker = null
        }

        if (rectFittingEnabled) {
            rectFitMap()
        }
    }

    private fun handleClickOnUserMarker(marker: Marker, mapView: MapView): Boolean {
        val tag = marker.relatedObject as MarkerType.UserMarker
        CoroutineScope(Dispatchers.Main).launch { showMemberLocationInBottomSheet(tag.userId, tag.name, marker.position.coordinates(), tag.timestamp) }
        return true
    }

    override fun handleSearchButtonTap(view: View) {
        logger.error("Not yet implemented")
    }

    override fun markCustomPosition(coordinates: Coordinates) {
        val marker = Marker(map)
        marker.position = coordinates.geoPoint()
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

        map.overlays.add(marker)
        markedLocation = marker
    }

    override fun removeMarkedLocation() {
        markedLocation?.let {
            map.overlays.remove(it)
        }
        markedLocation = null
    }

    override fun handleRemovedMember(userId: UserId) {
        userMarkers[userId]?.let {
            map.overlays.remove(it)
        }
        userMarkers.remove(userId)
    }

    override fun enableUserLocationIndicator() {
        userLocationOverlay.enableMyLocation()
        map.overlays.add(userLocationOverlay)

        val lastLocationAvailable = userLocationOverlay.runOnFirstFix(Runnable {
            CoroutineScope(Dispatchers.Main).launch {
                moveCamera(setOf(userLocationOverlay.myLocation.coordinates()))
            }
        })

        if (lastLocationAvailable) {
            logger.debug("Jump to last known user location.")
        } else {
            logger.debug("Wait for next user location update and jump to that location.")
        }
    }

    override fun rectFitMap() {
        val points = mutableSetOf<Coordinates>()

        userMarkers.forEach { points.add(it.value.position.coordinates()) }
        currentLocation?.let { points.add(it) }
        meetingPointMarker?.let { points.add(it.position.coordinates()) }

        if (points.isNotEmpty()) {
            moveCamera(points)
        }
    }

    override fun moveCamera(includingPoints: Set<Coordinates>) {
        val boundingBox = BoundingBox.fromGeoPointsSafe(includingPoints.map(Coordinates::geoPoint).toList())
        map.zoomToBoundingBox(boundingBox, true, 70, 18.0, 1000L)
    }

    override suspend fun locationString(coordinates: Coordinates): String {
        val fallbackString = getString(R.string.map_location_string, coordinates.latitude.toString(), coordinates.longitude.toString())
        return viewModel.locationString(coordinates) ?: fallbackString
    }
}