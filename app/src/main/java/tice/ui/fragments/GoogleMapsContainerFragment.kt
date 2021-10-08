package tice.ui.fragments

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.android.material.snackbar.Snackbar
import com.ticeapp.TICE.R
import com.ticeapp.TICE.databinding.GoogleMapsContainerFragmentBinding
import kotlinx.coroutines.*
import tice.models.Coordinates
import tice.models.UserId
import tice.models.UserLocation
import tice.models.coordinates
import tice.ui.viewModels.GoogleMapsContainerViewModel
import tice.utility.ui.getViewModel
import java.io.IOException
import java.lang.Math.cos
import java.util.*
import javax.inject.Inject
import javax.inject.Named

sealed class MarkerType {
    data class UserMarker(val userId: UserId, val timestamp: Date, val name: String) : MarkerType()
    object MeetingPointMarker : MarkerType()
    object CustomPositionMarker : MarkerType()
}

class GoogleMapsContainerFragment : MapContainerFragment(), OnMapReadyCallback {
    override val viewModel: GoogleMapsContainerViewModel by getViewModel()

    private val binding get() = _binding!! as GoogleMapsContainerFragmentBinding

    @Inject
    @Named("PLACES_SEARCH_REQUEST_CODE")
    lateinit var placesSearchRequestCode: String

    private lateinit var searchAutocompleteResultLauncher: ActivityResultLauncher<Intent>

    private lateinit var map: GoogleMap

    private var userMarkers: HashMap<UserId, Marker> = hashMapOf()
    private var meetingPointMarker: Marker? = null
    private var markedLocationMarker: Marker? = null
    private lateinit var locationProvider: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    override val currentMarkedPosition: Coordinates?
        get() = markedLocationMarker?.position?.coordinates()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationProvider = FusedLocationProviderClient(requireContext())
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(location: LocationResult?) {
                location?.let {
                    handleNewDeviceLocation(it.lastLocation.latitude, it.lastLocation.longitude)
                }
            }
        }

        searchAutocompleteResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            this::handleSearchResult
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)
        _binding = GoogleMapsContainerFragmentBinding.inflate(inflater, container, false)
        _bottomSheet = binding.bottomSheet
        _mapButtons = binding.mapButtons
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireContext().packageManager.getApplicationInfo(
            requireContext().packageName,
            PackageManager.GET_META_DATA
        ).metaData.getString("com.google.android.geo.API_KEY")?.let { apiKey ->
            Places.initialize(requireContext(), apiKey)
            Places.createClient(requireContext())
        }

        val supportMapFragment: SupportMapFragment? =
            childFragmentManager.findFragmentById(R.id.google_maps_fragment) as SupportMapFragment?
        supportMapFragment?.getMapAsync(this)
    }

    override fun onPause() {
        super.onPause()
        locationProvider.removeLocationUpdates(locationCallback)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.isMyLocationButtonEnabled = false
        map.uiSettings.isMapToolbarEnabled = false

        map.setOnCameraMoveStartedListener { reason ->
            if (reason == REASON_GESTURE) {
                disableRectFitting()
            }
        }

        map.setOnMarkerClickListener(this::handleClickOnMarker)
        map.setOnMapClickListener(this::handleClickOnMap)
        map.setOnMapLongClickListener(this::handleLongClickOnMap)

        mapSetupFinished()
    }

    private fun handleClickOnMarker(marker: Marker): Boolean {
        marker.showInfoWindow()

        CoroutineScope(Dispatchers.Main).launch {
            when (val tag = marker.tag) {
                is MarkerType.UserMarker ->
                    showMemberLocationInBottomSheet(
                        tag.userId,
                        tag.name,
                        Coordinates(marker.position.latitude, marker.position.longitude),
                        tag.timestamp
                    )
                is MarkerType.CustomPositionMarker -> showMarkedLocationInBottomSheet(marker.position.coordinates())
            }
        }

        return true
    }

    private fun handleClickOnMap(location: LatLng) {
        super.handleClickOnMap()
    }

    private fun handleLongClickOnMap(location: LatLng) {
        super.handleLongClickOnMap(location.coordinates())
    }

    override fun markCustomPosition(coordinates: Coordinates) {
        val marker = map.addMarker(
            MarkerOptions()
                .position(coordinates.latLng)
        )
        marker.tag = MarkerType.CustomPositionMarker
        markedLocationMarker = marker
    }

    override fun removeMarkedLocation() {
        if (markedLocationMarker != null) {
            markedLocationMarker?.remove()
            markedLocationMarker = null
        }
    }

    override fun handleSearchButtonTap(view: View) {
        activity?.let {
            if (!viewModel.shouldShowSearchAutocompleteFragment) {
                logger.error("Not showing search autocomplete fragment.")
                return@let
            }

            val fields =
                listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS)
            val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)

            currentLocation?.let {
                intent.setLocationBias(rectBounds(it.latitude, it.longitude))
            }

            searchAutocompleteResultLauncher.launch(intent.build(requireContext()))
        } ?: logger.error("Activity not available for handling search button tap.")
    }

    private fun handleSearchResult(result: ActivityResult) {
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                result.data?.let { intent ->
                    Autocomplete.getPlaceFromIntent(intent).latLng?.let {
                        markedLocationMarker?.remove()
                        markCustomPosition(it.coordinates())
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 17.0f))
                    } ?: logger.info("Search result does not contain location.")
                }
            }
            AutocompleteActivity.RESULT_ERROR -> {
                result.data?.let {
                    val status = Autocomplete.getStatusFromIntent(it)
                    status.statusMessage?.let { message -> logger.debug(message) }
                } ?: Snackbar.make(
                    requireView(),
                    getString(R.string.error_message),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    override suspend fun handleMemberLocationUpdate(update: UserLocation) {
        val userNames = viewModel.getMemberNames(update.userId) ?: run {
            logger.error("Could not get user name for member.")
            return
        }

        val newTag =
            MarkerType.UserMarker(update.userId, update.location.timestamp, userNames.first)
        val location = LatLng(update.location.latitude, update.location.longitude)
        userMarkers[update.userId]?.let { marker ->
            marker.position = location
            marker.tag = newTag

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
            val marker = map.addMarker(
                MarkerOptions()
                    .position(location)
                    .title(userNames.first)
                    .icon(
                        BitmapDescriptorFactory.fromBitmap(
                            getMarkerBitmapFromView(
                                userNames.second,
                                color
                            )
                        )
                    )
            )
            marker.tag = newTag
            userMarkers[update.userId] = marker
        }

        if (rectFittingEnabled) {
            rectFitMap()
        }
    }

    override fun handleRemovedMember(userId: UserId) {
        userMarkers[userId]?.remove()
    }

    override fun handleNewMeetingPoint(meetingPoint: tice.models.Location?) {
        if (meetingPoint != null) {
            val latLng = LatLng(meetingPoint.latitude, meetingPoint.longitude)
            meetingPointMarker?.let { marker ->
                marker.position = latLng
            } ?: run {
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(latLng)
                        .title(getString(R.string.map_location_meetingPoint))
                )
                marker.tag = MarkerType.MeetingPointMarker
                meetingPointMarker = marker
            }
        } else {
            meetingPointMarker?.remove()
        }
    }

    // Permission check is handled by super class
    @SuppressLint("MissingPermission")
    override fun enableUserLocationIndicator() {
        map.isMyLocationEnabled = true
        val locationUpdateRequest = LocationRequest
            .create()
            .setInterval(10000)
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        locationProvider.requestLocationUpdates(
            locationUpdateRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    override fun rectFitMap() {
        val points = mutableSetOf<Coordinates>()

        userMarkers.values.forEach { points.add(it.position.coordinates()) }
        currentLocation?.let { points.add(Coordinates(it.latitude, it.longitude)) }
        meetingPointMarker?.let { points.add(it.position.coordinates()) }

        moveCamera(points)
    }

    override fun moveCamera(includingPoints: Set<Coordinates>) {
        val bounds = LatLngBounds.builder()

        for (point in includingPoints) {
            bounds.include(point.latLng)
        }

        try {
            map.setMaxZoomPreference(17.0f)
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 200))
        } catch (e: Exception) {
            logger.debug("Failed to move map camera to defined rectangle.")
        }
    }

    private fun rectBounds(latitude: Double, longitude: Double): RectangularBounds {
        val latRadian = Math.toRadians(latitude)
        val degLatKm = 110.574235
        val degLongKm = 110.572833 * cos(latRadian)
        val deltaLat: Double = 5000 / 1000.0 / degLatKm
        val deltaLong: Double = 5000 / 1000.0 / degLongKm

        val minLat: Double = latitude - deltaLat
        val minLong: Double = longitude - deltaLong
        val maxLat: Double = latitude + deltaLat
        val maxLong: Double = longitude + deltaLong

        val bounds = LatLngBounds(LatLng(minLat, minLong), LatLng(maxLat, maxLong))
        return RectangularBounds.newInstance(bounds)
    }

    override suspend fun locationString(coordinates: Coordinates): String {
        val fallbackString = getString(R.string.map_location_string, coordinates.latitude.toString(), coordinates.longitude.toString())

        return try {
            Geocoder(requireContext()).getFromLocation(coordinates.latitude, coordinates.longitude, 1)
                .first().getAddressLine(0)
        } catch (e: IOException) {
            fallbackString
        } catch (e: NoSuchElementException) {
            fallbackString
        }
    }
}
