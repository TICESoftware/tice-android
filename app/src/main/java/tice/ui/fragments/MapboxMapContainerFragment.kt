package tice.ui.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.gson.Gson
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Point
import com.mapbox.maps.*
import com.mapbox.maps.Style.Companion.MAPBOX_STREETS
import com.mapbox.maps.plugin.animation.Cancelable
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.*
import com.mapbox.maps.plugin.delegates.listeners.OnMapLoadErrorListener
import com.mapbox.maps.plugin.delegates.listeners.OnMapLoadedListener
import com.mapbox.maps.plugin.delegates.listeners.eventdata.MapLoadErrorType
import com.mapbox.maps.plugin.gestures.*
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.scalebar.scalebar
import com.ticeapp.TICE.databinding.MapboxContainerFragmentBinding
import tice.models.*
import tice.ui.viewModels.MapboxMapContainerViewModel
import tice.utility.ui.getViewModel
import kotlin.math.absoluteValue

class MapboxMapContainerFragment : MapContainerFragment() {
    override val viewModel: MapboxMapContainerViewModel by getViewModel()

    private val binding get() = _binding!! as MapboxContainerFragmentBinding

    private lateinit var map: MapView

    private lateinit var userAnnotationManager: PointAnnotationManager
    private var userAnnotations = hashMapOf<UserId, PointAnnotation>()
    private lateinit var meetingPointAnnotationManager: PointAnnotationManager
    private lateinit var markedLocationAnnotationManager: PointAnnotationManager

    override val currentMarkedPosition: Coordinates?
        get() = markedLocationAnnotationManager.annotations.firstOrNull()?.point?.pointCoordinates()

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

        map.location.addOnIndicatorPositionChangedListener {
            if (currentLocation != it.pointCoordinates()) {
                logger.debug("Mapbox indicator position changed.")
                handleNewDeviceLocation(it.latitude(), it.longitude())
            }
        }
        map.getMapboxMap().addOnMoveListener(object : OnMoveListener {
            override fun onMove(detector: MoveGestureDetector): Boolean = false
            override fun onMoveBegin(detector: MoveGestureDetector) = disableRectFitting()
            override fun onMoveEnd(detector: MoveGestureDetector) {}
        })
        map.getMapboxMap().addOnMapClickListener(this::handleClickOnMap)
        map.getMapboxMap().addOnMapLongClickListener(this::handleLongClickOnMap)

        mapSetupFinished()
    }

    private fun handleClickOnUserAnnotation(annotation: PointAnnotation): Boolean {
        val tag = Gson().fromJson(annotation.getData()!!, MarkerType.UserMarker::class.java)
        showMemberLocationInBottomSheet(tag.userId, tag.name, annotation.point.pointCoordinates(), tag.timestamp)
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
            .withPoint(coordinates.point)
            .withIconImage(getMarkerBitmapFromView("", Color.RED))
        markedLocationAnnotationManager.create(pointAnnotationOptions)
    }

    override fun removeMarkedLocation() {
        markedLocationAnnotationManager.deleteAll()
    }

    override fun handleSearchButtonTap(view: View) {
        TODO("Not yet implemented")
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
        map.getMapboxMap().loadStyleUri(MAPBOX_STREETS,
            {
                map.location.updateSettings {
                    enabled = true
                    pulsingEnabled = true
                }
                map.scalebar.enabled = false
            },
            object : OnMapLoadErrorListener {
                override fun onMapLoadError(mapLoadErrorType: MapLoadErrorType, message: String) {
                    logger.error("Error loading mapbox map.")
                }
            })
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

    override fun locationString(coordinates: Coordinates): String {
        return ""
    }
}