package tice.ui.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.gson.Gson
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.Style.Companion.MAPBOX_STREETS
import com.mapbox.maps.extension.observable.eventdata.MapLoadingErrorEventData
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.delegates.listeners.OnMapLoadErrorListener
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.gestures.addOnMapLongClickListener
import com.mapbox.maps.plugin.gestures.addOnMoveListener
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.search.*
import com.mapbox.search.result.SearchAddress
import com.mapbox.search.result.SearchResult
import com.mapbox.search.ui.view.SearchBottomSheetView
import com.ticeapp.TICE.R
import com.ticeapp.TICE.databinding.MapboxContainerFragmentBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tice.models.*
import tice.ui.viewModels.MapboxContainerViewModel
import tice.utility.point
import tice.utility.pointCoordinates
import tice.utility.ui.getViewModel
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.absoluteValue

class MapboxMapContainerFragment : MapContainerFragment() {
    override val viewModel: MapboxContainerViewModel by getViewModel()

    private val binding get() = _binding!! as MapboxContainerFragmentBinding

    private lateinit var map: MapView

    private lateinit var userAnnotationManager: PointAnnotationManager
    private var userAnnotations = hashMapOf<UserId, PointAnnotation>()
    private lateinit var meetingPointAnnotationManager: PointAnnotationManager
    private lateinit var markedLocationAnnotationManager: PointAnnotationManager

    override val currentMarkedPosition: Coordinates?
        get() = markedLocationAnnotationManager.annotations.firstOrNull()?.point?.pointCoordinates()

    private lateinit var reverseGeocoding: ReverseGeocodingSearchEngine
    private var reverseGeocodingSearchRequestTask: SearchRequestTask? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.initMapbox(requireActivity())
        reverseGeocoding = MapboxSearchSdk.createReverseGeocodingSearchEngine()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)
        _binding = MapboxContainerFragmentBinding.inflate(inflater, container, false)
        _bottomSheet = binding.bottomSheet
        _mapButtons = binding.mapButtons
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        map = binding.mapView
        meetingPointAnnotationManager = map.annotations.createPointAnnotationManager(map)
        userAnnotationManager = map.annotations.createPointAnnotationManager(map)
        userAnnotationManager.addClickListener(this::handleClickOnUserAnnotation)
        markedLocationAnnotationManager = map.annotations.createPointAnnotationManager(map)

        // Disable search for now as Mapbox search is not implemented yet
        _mapButtons!!.searchButton.visibility = View.GONE
        binding.mapboxSearchView.visibility = View.GONE

        map.location.addOnIndicatorPositionChangedListener {
            if (currentLocation != it.pointCoordinates()) {
                logger.debug("Mapbox indicator position changed.")
                handleNewDeviceLocation(it.latitude(), it.longitude())
            }
        }
        map.getMapboxMap().addOnMoveListener(
            object : OnMoveListener {
                override fun onMove(detector: MoveGestureDetector): Boolean = false
                override fun onMoveBegin(detector: MoveGestureDetector) = disableRectFitting()
                override fun onMoveEnd(detector: MoveGestureDetector) {}
            }
        )
        map.getMapboxMap().addOnMapClickListener(this::handleClickOnMap)
        map.getMapboxMap().addOnMapLongClickListener(this::handleLongClickOnMap)

        binding.mapboxSearchView.initializeSearch(savedInstanceState, SearchBottomSheetView.Configuration())

        mapSetupFinished()
    }

    override fun onDestroy() {
        reverseGeocodingSearchRequestTask?.cancel()
        super.onDestroy()
    }

    private fun handleClickOnUserAnnotation(annotation: PointAnnotation): Boolean {
        val tag = Gson().fromJson(annotation.getData()!!, MarkerType.UserMarker::class.java)
        CoroutineScope(Dispatchers.Main).launch { showMemberLocationInBottomSheet(tag.userId, tag.name, annotation.point.pointCoordinates(), tag.timestamp) }
        return true
    }

    private fun handleClickOnMap(point: Point): Boolean {
        super.handleClickOnMap()
        return true
    }

    private fun handleLongClickOnMap(point: Point): Boolean {
        super.handleLongClickOnMap(point.pointCoordinates())
        return true
    }

    override fun markCustomPosition(coordinates: Coordinates) {
        val pointAnnotationOptions = PointAnnotationOptions()
            .withPoint(coordinates.point())
            .withIconImage(getMarkerBitmapFromView("", Color.RED))
        markedLocationAnnotationManager.create(pointAnnotationOptions)
    }

    override fun removeMarkedLocation() {
        markedLocationAnnotationManager.deleteAll()
    }

    override fun handleSearchButtonTap(view: View) {
        binding.mapboxSearchView.visibility = View.VISIBLE
    }

    override suspend fun handleMemberLocationUpdate(update: UserLocation) {
        val userNames = viewModel.getMemberNames(update.userId) ?: run {
            logger.error("Could not get user name for member.")
            return
        }

        val point = Point.fromLngLat(update.location.longitude, update.location.latitude)

        // TODO: Move to ViewModel
        val tag = Gson().toJsonTree(
            MarkerType.UserMarker(
                update.userId,
                update.location.timestamp,
                userNames.first
            )
        )
        userAnnotations[update.userId]?.let { annotation ->
            annotation.point = point
            annotation.setData(tag)
            annotation.iconColorInt = viewModel.colorForMember(update.userId)
            userAnnotationManager.update(annotation)

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
            val pointAnnotationOptions = PointAnnotationOptions()
                .withPoint(point)
                .withIconImage(getMarkerBitmapFromView(userNames.second, color))
            val annotation = userAnnotationManager.create(pointAnnotationOptions)
            annotation.setData(tag)
            userAnnotations[update.userId] = annotation
        }

        if (rectFittingEnabled) {
            rectFitMap()
        }
    }

    override fun handleRemovedMember(userId: UserId) {
        userAnnotations[userId]?.let {
            userAnnotationManager.delete(it)
        }
    }

    override fun handleNewMeetingPoint(meetingPoint: Location?) {
        val currentAnnotation = meetingPointAnnotationManager.annotations.firstOrNull()
        if (meetingPoint != null) {
            val point = Point.fromLngLat(meetingPoint.longitude, meetingPoint.latitude)
            currentAnnotation?.let { annotation ->
                annotation.point = point
                meetingPointAnnotationManager.update(annotation)
            } ?: run {
                val pointAnnotationOptions = PointAnnotationOptions()
                    .withPoint(point)
                    .withTextField(getString(R.string.map_location_meetingPoint))
                    .withIconImage(getMarkerBitmapFromView("", Color.RED))
                meetingPointAnnotationManager.create(pointAnnotationOptions)
            }
        } else {
            currentAnnotation?.let {
                meetingPointAnnotationManager.delete(it)
            }
        }

        if (rectFittingEnabled) {
            rectFitMap()
        }
    }

    override fun enableUserLocationIndicator() {
        logger.debug("Mapbox: enableUserLocationIndicator called.")
        map.getMapboxMap().loadStyleUri(
            MAPBOX_STREETS,
            {
                map.location.updateSettings {
                    enabled = true
                    pulsingEnabled = true
                }
                map.scalebar.enabled = false
            },
            object : OnMapLoadErrorListener {
                override fun onMapLoadError(eventData: MapLoadingErrorEventData) {
                    logger.error("Error loading mapbox map: ${eventData.message}")
                }
            }
        )
    }

    override fun rectFitMap() {
        val points = mutableSetOf<Coordinates>()

        userAnnotationManager.annotations.forEach { points.add(it.point.pointCoordinates()) }
        currentLocation?.let { points.add(it) }
        meetingPointAnnotationManager.annotations.firstOrNull()
            ?.let { points.add(it.point.pointCoordinates()) }

        if (points.isNotEmpty()) {
            moveCamera(points)
        }
    }

    override fun moveCamera(includingPoints: Set<Coordinates>) {
//        The cameraForCoordinates functions produces incorrect results in certain conditions.
//        We should check for bugfixes regularly and calculate the rect manually as a workaround.
//        val cameraOptions = map.getMapboxMap().cameraForCoordinates(includingPoints.map(Coordinates::point))
//        val edgeInsets = EdgeInsets(200.0, 150.0, 500.0, 150.0)

        // Workaround ->
        val minLat = includingPoints.minOf(Coordinates::latitude)
        val maxLat = includingPoints.maxOf(Coordinates::latitude)
        val minLng = includingPoints.minOf(Coordinates::longitude)
        val maxLng = includingPoints.maxOf(Coordinates::longitude)

        val center = Point.fromLngLat((minLng + maxLng) / 2, (minLat + maxLat) / 2)

        val vInset = (center.latitude() - maxLat).absoluteValue
        val hInset = (center.longitude() - maxLng).absoluteValue
        val edgeInsets = EdgeInsets(vInset, hInset, vInset, hInset)

        val cameraOptions = CameraOptions.Builder().center(center).padding(edgeInsets).build()
        // <- Workaround

        map.camera.flyTo(cameraOptions)
    }

    override suspend fun locationString(coordinates: Coordinates): String {
        val fallbackString = getString(R.string.map_location_string, coordinates.latitude.toString(), coordinates.longitude.toString())

        val options = ReverseGeoOptions(coordinates.point(), limit = 1)

        return suspendCoroutine { continuation ->
            reverseGeocodingSearchRequestTask =
                reverseGeocoding.search(
                    options,
                    object : SearchCallback {
                        override fun onError(e: Exception) = continuation.resumeWith(Result.failure(e))
                        override fun onResults(results: List<SearchResult>, responseInfo: ResponseInfo) =
                            continuation.resume(results.firstOrNull()?.address?.formattedAddress(SearchAddress.FormatStyle.Medium) ?: fallbackString)
                    }
                )
        }
    }
}
