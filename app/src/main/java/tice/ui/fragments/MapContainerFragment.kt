package tice.ui.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.viewbinding.ViewBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ticeapp.TICE.R
import com.ticeapp.TICE.databinding.BottomsheetMapBinding
import com.ticeapp.TICE.databinding.CustomMapMarkerBinding
import com.ticeapp.TICE.databinding.MapButtonsBinding
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import tice.models.*
import tice.utility.getLogger
import java.util.*

abstract class MapContainerFragment : Fragment(), MapContainerFragmentInterface {
    val logger by getLogger()

    var _binding: ViewBinding? = null
    var _bottomSheet: BottomsheetMapBinding? = null
    private val bottomSheet
        get() = _bottomSheet!!
    var _mapButtons: MapButtonsBinding? = null
    private val mapButtons
        get() = _mapButtons!!
    private lateinit var markerBinding: CustomMapMarkerBinding

    lateinit var teamId: GroupId

    lateinit var mapBottomSheetBehavior: BottomSheetBehavior<ConstraintLayout>
    var bottomSheetUserId: UserId? = null

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    private val locationPermissionGranted: Boolean
        get() = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    var rectFittingEnabled = false

    var currentLocation: Coordinates? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.teamId = teamId
        markerBinding = CustomMapMarkerBinding.inflate(LayoutInflater.from(context))
        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission(), this::handleLocationPermissionChange)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapBottomSheetBehavior = BottomSheetBehavior.from(bottomSheet.bottomsheet)
        mapBottomSheetBehavior.state = STATE_HIDDEN

        mapButtons.rectButton.setOnClickListener(this::toggleRectFitting)
        mapButtons.searchButton.setOnClickListener(this::handleSearchButtonTap)
        mapButtons.userLocationButton.setOnClickListener(this::handleUserLocationButtonTap)
        bottomSheet.setMeetingPointButton.setOnClickListener(this::setMeetingPoint)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _mapButtons = null
        _bottomSheet = null
        _binding = null
    }

    private fun handleLocationPermissionChange(granted: Boolean) {
        if (granted) {
            enableUserLocationIndicator()
        } else {
            logger.warn("Location permission denied.")
        }
    }

    private fun requestLocationPermission() {
        val requestLocationPermission = { requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }

        when {
            locationPermissionGranted -> return
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ->
                MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.notification_locationService_body)
                    .setPositiveButton(R.string.map_location_info_okay) { _, _ -> requestLocationPermission() }
                    .setOnCancelListener(null)
                    .show()
            else -> requestLocationPermission()
        }
    }

    private fun requestLocationPermissionAndShowUserLocation() {
        if (locationPermissionGranted) {
            enableUserLocationIndicator()
        } else {
            requestLocationPermission()
        }
    }

    fun mapSetupFinished() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.memberLocationUpdateFlow.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collect(this@MapContainerFragment::handleMemberLocationUpdate)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.meetingPointFlow.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collect(this@MapContainerFragment::handleNewMeetingPoint)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.removedMembersFlow.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collect(this@MapContainerFragment::handleRemovedMember)
        }

        requestLocationPermissionAndShowUserLocation()
        enableRectFitting()
    }

    open fun handleClickOnMap() {
        mapBottomSheetBehavior.state = STATE_HIDDEN
        if (bottomSheetUserId != null) bottomSheetUserId = null
        removeMarkedLocation()
    }

    fun handleLongClickOnMap(location: Coordinates) {
        disableRectFitting()

        if (bottomSheetUserId != null) bottomSheetUserId = null
        removeMarkedLocation()

        markCustomPosition(location)

        showMarkedLocationInBottomSheet(location)
    }

    fun handleNewDeviceLocation(latitude: Double, longitude: Double) {
        currentLocation = Coordinates(latitude, longitude)

        if (rectFittingEnabled) {
            rectFitMap()
        }
    }

    private fun handleUserLocationButtonTap(view: View) {
        disableRectFitting()

        currentLocation?.let {
            moveCamera(setOf(it))
        } ?: requestLocationPermissionAndShowUserLocation()
    }

    private fun toggleRectFitting(view: View) {
        if (rectFittingEnabled) disableRectFitting() else enableRectFitting()
    }

    private fun enableRectFitting() {
        rectFittingEnabled = true
        mapButtons.rectButton.setImageDrawable(
            ContextCompat.getDrawable(
                requireContext(),
                R.drawable.ic_rectfitting_enabled
            )
        )
        rectFitMap()
    }

    fun disableRectFitting() {
        rectFittingEnabled = false
        mapButtons.rectButton.setImageDrawable(
            ContextCompat.getDrawable(
                requireContext(),
                R.drawable.ic_rectfitting_disabled
            )
        )
    }

    fun showMarkedLocationInBottomSheet(coordinates: Coordinates) {
        bottomSheet.sheetTitle.text = getString(R.string.map_location_pin)
        bottomSheet.address.text = locationString(coordinates)
        bottomSheet.setMeetingPointButton.visibility = View.VISIBLE
        bottomSheet.lastSeenText.visibility = View.INVISIBLE

        mapBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    fun showMemberLocationInBottomSheet(
        userId: UserId,
        displayName: String,
        location: Coordinates,
        timestamp: Date
    ) {
        removeMarkedLocation()

        bottomSheetUserId = userId

        bottomSheet.sheetTitle.text = displayName
        bottomSheet.address.text = locationString(location)
        bottomSheet.setMeetingPointButton.visibility = View.INVISIBLE
        bottomSheet.lastSeenText.text = String.format(
            getString(R.string.map_location_lastSeen),
            viewModel.dateString(timestamp)
        )
        bottomSheet.lastSeenText.visibility = View.VISIBLE

        mapBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    fun getMarkerBitmapFromView(text: String, color: Int): Bitmap {
        val customMarkerView: View = markerBinding.root

        markerBinding.customMarkerText.text = text
        ((markerBinding.customMakerLayout.background as LayerDrawable).getDrawable(0) as GradientDrawable).setColor(
            color
        )

        customMarkerView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        customMarkerView.layout(
            0,
            0,
            customMarkerView.measuredWidth,
            customMarkerView.measuredHeight
        )

        val usedHeight = customMarkerView.measuredHeight / 3 + customMarkerView.measuredHeight
        val returnedBitmap =
            Bitmap.createBitmap(customMarkerView.measuredWidth, usedHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        customMarkerView.draw(canvas)
        return returnedBitmap
    }

    private fun setMeetingPoint(view: View) {
        currentMarkedPosition?.let { position ->
            viewModel.setMeetingPoint(position)
            mapBottomSheetBehavior.state = STATE_HIDDEN
            removeMarkedLocation()
        } ?: logger.error("Tried to set new meeting point but there isn't any marked position.")
    }
}
