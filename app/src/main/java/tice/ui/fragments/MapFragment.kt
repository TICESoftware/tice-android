package tice.ui.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.location.Geocoder
import android.os.Bundle
import android.text.format.DateFormat.getBestDateTimePattern
import android.view.*
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat.getColor
import androidx.core.content.ContextCompat.getDrawable
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.ticeapp.TICE.R
import com.ticeapp.TICE.databinding.MapFragmentBinding
import dagger.Module
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tice.models.CameraSettings
import tice.models.Location
import tice.ui.adapter.MapMarkerAdapter
import tice.ui.delegates.ActionBarAccess
import tice.ui.models.ShortChatMessages
import tice.ui.viewModels.MapViewModel
import tice.ui.viewModels.MapViewModel.MapEvent
import tice.ui.viewModels.MapViewModel.MeetUpButtonState
import tice.utility.getLogger
import tice.utility.ui.getViewModel
import tice.utility.ui.isLocationPermissionGranted
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

@Module
class MapFragment : Fragment(), OnMapReadyCallback {
    private val logger by getLogger()

    private val viewModel: MapViewModel by getViewModel()
    private val args: MapFragmentArgs by navArgs()

    private lateinit var mapBottomSheetBehavior: BottomSheetBehavior<ConstraintLayout>
    private lateinit var chatButtonText: TextView

    private var map: GoogleMap? = null
    private var mapMarkerAdapter: MapMarkerAdapter? = null
    private var userLocation: Location? = null

    private var receivedMessageTimerJob: Job? = null

    private val AUTOCOMPLETE_REQUEST_CODE = 1
    private val fields: List<Place.Field> = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS)

    private var _binding: MapFragmentBinding? = null
    private val binding get() = _binding!!

    private var softInputMode: Int = 0
    private var placesInitialized = false

    var locationListener: LocationSource.OnLocationChangedListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        _binding = MapFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.setupData(args.teamId)

        val googleMapsAPIKey = requireContext().packageManager.getApplicationInfo(requireContext().packageName, PackageManager.GET_META_DATA).metaData.getString("googleMapsAPIKey")
        if (googleMapsAPIKey != null) {
            Places.initialize(requireContext(), googleMapsAPIKey)
            Places.createClient(requireContext())
            placesInitialized = true
        }

        mapBottomSheetBehavior = BottomSheetBehavior.from(binding.bottom.bottomsheet)
        mapBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        chatButtonText = binding.chatView.buttonText

        binding.chatView.chatButton.setOnClickListener {
            findNavController().navigate(ChatFragmentDirections.actionGlobalChatFragment(args.teamId))
        }

        binding.meetupButton.view.setOnClickListener { meetupAction() }

        val supportMapFragment: SupportMapFragment? = childFragmentManager.findFragmentById(R.id.map_container) as SupportMapFragment?
        supportMapFragment?.getMapAsync(this)

        viewModel.meetUpButtonState.observe(viewLifecycleOwner, Observer { handleStateChange(it) })

        viewModel.teamName.observe(
            viewLifecycleOwner,
            { (activity as ActionBarAccess).actionBar.title = it.name }
        )

        viewModel.unreadCount.observe(
            viewLifecycleOwner,
            { handleUnreadCount(it) }
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.event.collect { handleEvent(it) }
        }

        binding.bottom.bsButton.setOnClickListener { setNewMarker() }

        binding.searchButton.setOnClickListener {
            if (!placesInitialized) {
                logger.error("Places search screen cannot be shown because Places API is not initialized.")
                return@setOnClickListener
            }

            val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
            viewModel.disableRectFitting()

            userLocation?.let { location ->
                intent.setLocationBias(RectangularBounds.newInstance(viewModel.getCoordinate(location)))
            }
            startActivityForResult(intent.build(requireContext()), AUTOCOMPLETE_REQUEST_CODE)
        }
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(readyMap: GoogleMap?) {
        map = readyMap!!

        handleLocationPermissionChange()

        mapMarkerAdapter = MapMarkerAdapter(requireContext(), map)

        binding.userLocationButton.setOnClickListener {
            if (isLocationPermissionGranted()) {
                userLocation?.let {
                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 17.0f))
                } ?: run {
                    handleLocation()
                    logger.debug("User location is null. $userLocation")
                }
                viewModel.disableRectFitting()
            } else {
                handleNullLocation()
                logger.debug("Permissions were not granted.")
            }
        }

        viewModel.userLocationUpdate.observe(
            viewLifecycleOwner,
            { mapMarkerAdapter?.handleLocationUpdate(it) }
        )
        viewModel.meetingPoint.observe(
            viewLifecycleOwner,
            { mapMarkerAdapter?.handleMeetingPointUpdate(it) }
        )

        map?.setOnMapLongClickListener {
            val address = getAddress(it.latitude, it.longitude)
            viewModel.disableRectFitting()

            binding.bottom.address.text = address ?: getString(
                R.string.map_location_string,
                it.latitude.toString(),
                it.longitude.toString()
            )
            binding.bottom.sheetTitle.text = getString(R.string.map_location_pin)
            binding.bottom.bsButton.visibility = View.VISIBLE
            binding.bottom.lastSeenText.visibility = View.INVISIBLE

            mapMarkerAdapter?.markNewMeetingPointPosition(
                it,
                address?.let { it1 -> it1.split(",")[0] } ?: getString(
                    R.string.map_location_string,
                    it.latitude.toString(),
                    it.longitude.toString()
                )
            )
            mapBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        map?.setOnMapClickListener {
            mapMarkerAdapter?.removeNewMeetingPointPosition()
            mapBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

            viewModel.currentUserId = null
            viewModel.disableRectFitting()
        }

        map?.setOnCameraMoveStartedListener {
            if (it == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                viewModel.disableRectFitting()
            }
        }

        viewModel.userInfo.observe(
            viewLifecycleOwner,
            {
                binding.bottom.sheetTitle.text = it.displayName.toUpperCase(Locale.getDefault())
                binding.bottom.lastSeenText.visibility = View.VISIBLE
                binding.bottom.lastSeenText.text = String.format(
                    getString(R.string.map_location_lastSeen),
                    getDate(it.timestamp)
                )
                binding.bottom.bsButton.visibility = View.INVISIBLE
                binding.bottom.address.text = getAddress(it.latitude, it.longitude)?.let { address ->
                    address.split(",")[0]
                } ?: getString(
                    R.string.map_location_string,
                    it.latitude.toString(),
                    it.longitude.toString()
                )
            }
        )

        viewModel.usersInMeetup.observe(
            viewLifecycleOwner,
            {
                mapMarkerAdapter?.handleUserIdChange(it)
            }
        )

        viewModel.rectFittingEnabled.observe(viewLifecycleOwner, Observer { handleRectFittingState(it) })

        map?.setOnMarkerClickListener {
            it.showInfoWindow()

            if (it.tag != null) {
                mapBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED

                viewModel.initUserData(it.tag as UUID)
            }
            true
        }

        binding.rectButton.setOnClickListener {
            viewModel.onRectFittingClicked()
            mapBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        val groupInfo = menu.findItem(R.id.defaultMenu_GroupInfoMenu)
        menu.findItem(R.id.defaultMenu_SettingsMenu).isVisible = false
        groupInfo.isVisible = true

        groupInfo.setOnMenuItemClickListener {
            findNavController().navigate(MapFragmentDirections.actionGlobalTeamInfo(args.teamId))
            true
        }
    }

    private fun handleEvent(event: MapEvent) {
        when (event) {
            is MapEvent.NewMessage -> handleLastMessage(event.shortChatMessages, binding.chatView.lastMessage)
            is MapEvent.NoTeam -> findNavController().navigate(R.id.action_global_team_list)
            is MapEvent.MeetupCreated -> {
                Snackbar.make(
                    requireView(),
                    getString(R.string.meetup_snackbar_started),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
            is MapEvent.ErrorEvent.MarkerError -> {
                Snackbar.make(
                    requireView(),
                    getString(R.string.error_marker_update),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
            is MapEvent.ErrorEvent.MeetupError -> {
                Snackbar.make(
                    requireView(),
                    getString(R.string.error_meetup),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
            is MapEvent.ErrorEvent.Error -> {
                Snackbar.make(
                    requireView(),
                    getString(R.string.error_message),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == AUTOCOMPLETE_REQUEST_CODE) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    data?.let {
                        val place = Autocomplete.getPlaceFromIntent(data)
                        showPlace(place)
                    }
                }
                AutocompleteActivity.RESULT_ERROR -> {
                    data?.let {
                        val status = Autocomplete.getStatusFromIntent(data)
                        status.statusMessage?.let { it1 -> logger.debug(it1) }
                    } ?: Snackbar.make(
                        requireView(),
                        getString(R.string.error_message),
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun meetupAction() {
        when (viewModel.meetUpButtonState.value) {
            is MeetUpButtonState.None -> showDialog()
            is MeetUpButtonState.WeShareLocation -> showDisableDialog()
            else -> viewModel.triggerMeetupAction()
        }

        viewModel.disableRectFitting()
    }

    private fun handleStateChange(state: MeetUpButtonState) {
        when (state) {
            is MeetUpButtonState.TheyShareLocation -> {
                binding.meetupButton.title.visibility = View.VISIBLE
                binding.meetupButton.title.text = getString(R.string.team_locationSharing_start_title)
                binding.meetupButton.progressBar.visibility = View.INVISIBLE
                if (isLocationPermissionGranted()) {
                    binding.meetupButton.summary.visibility = View.VISIBLE
                    binding.meetupButton.summary.text = getString(R.string.team_locationSharing_othersActive_subtitle)
                }
            }
            is MeetUpButtonState.OneSharesLocation -> {
                binding.meetupButton.title.visibility = View.VISIBLE
                binding.meetupButton.title.text = getString(R.string.team_locationSharing_start_title)
                binding.meetupButton.progressBar.visibility = View.INVISIBLE
                if (isLocationPermissionGranted()) {
                    binding.meetupButton.summary.visibility = View.VISIBLE
                    binding.meetupButton.summary.text = String.format(getString(R.string.team_locationSharing_otherActive_subtitle), state.userName)
                }
            }
            is MeetUpButtonState.None -> {
                binding.meetupButton.title.text = getString(R.string.team_locationSharing_start_title)
                binding.meetupButton.progressBar.visibility = View.INVISIBLE
                if (isLocationPermissionGranted()) {
                    binding.meetupButton.summary.visibility = View.GONE
                }
            }
            is MeetUpButtonState.Loading -> {
                binding.meetupButton.title.visibility = View.GONE
                binding.meetupButton.summary.visibility = View.GONE
                binding.meetupButton.progressBar.visibility = View.VISIBLE
            }
            is MeetUpButtonState.WeShareLocation -> {
                binding.meetupButton.title.visibility = View.VISIBLE
                binding.meetupButton.title.text = getString(R.string.team_locationSharing_active_title)
                binding.meetupButton.summary.visibility = View.VISIBLE
                binding.meetupButton.progressBar.visibility = View.INVISIBLE
                if (isLocationPermissionGranted()) {
                    binding.meetupButton.summary.text = getString(R.string.team_locationSharing_active_subtitle)
                }
            }
        }
        if (!isLocationPermissionGranted()) {
            binding.meetupButton.summary.visibility = View.VISIBLE
            binding.meetupButton.summary.text = getString(R.string.meetup_manageParticipation_locationSharingOff)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION) ||
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
        ) {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.map_location_permission_info)
                .setNegativeButton(R.string.map_location_permission_later) { _, _ -> }
                .setPositiveButton(R.string.map_location_permission_enable) { _, _ ->
                    val permission = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    requestPermissions(permission, 1)
                }
                .show()
        }
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                binding.meetupButton.view.setCardBackgroundColor(getColor(requireContext(), R.color.colorButtonDisabled))
            } else {
                binding.meetupButton.view.setCardBackgroundColor(getColor(requireContext(), R.color.primaryColor))
                binding.meetupButton.summary.visibility = View.GONE
                handleLocation()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleLocationPermissionChange() {
        if (isLocationPermissionGranted()) {
            handleLocation()
        } else {
            val permission = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )

            MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.notification_locationService_body)
                .setPositiveButton(R.string.map_location_info_okay) { _, _ -> requestPermissions(permission, 1) }
                .setOnCancelListener { requestPermissions(permission, 1) }
                .show()
        }

        map?.uiSettings?.isMyLocationButtonEnabled = false
        val setting = viewModel.cameraLocation

        map?.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition(setting.latLng, setting.zoom, setting.tilt, setting.bearing)))
    }

    private fun showDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.team_startLocationSharing_title)
            .setMessage(R.string.team_startLocationSharing_message)
            .setNegativeButton(R.string.alert_cancel) { _, _ -> }
            .setPositiveButton(R.string.team_locationSharing_confirm) { _, _ -> viewModel.triggerMeetupAction() }
            .show()
    }

    private fun showDisableDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.team_stopLocationSharing_title)
            .setMessage(R.string.team_stopLocationSharing_message)
            .setNegativeButton(R.string.alert_cancel) { _, _ -> }
            .setPositiveButton(R.string.team_locationSharing_active_subtitle) { _, _ -> viewModel.triggerMeetupAction() }
            .show()
    }

    private fun getDate(timestamp: Date): String {
        val format = getBestDateTimePattern(Locale.getDefault(), "jj:mm:ss")
        return SimpleDateFormat(format, Locale.getDefault()).apply {
            TimeZone.getDefault()
            applyLocalizedPattern(format)
        }.format(timestamp)
    }

    private fun getAddress(lat: Double, lng: Double): String? {
        return try {
            val addresses = Geocoder(requireContext()).getFromLocation(lat, lng, 1)
            addresses?.firstOrNull()?.getAddressLine(0)
        } catch (e: IOException) {
            logger.debug("Retrieving address from location failed. $e")
            null
        }
    }

    private fun showPlace(place: Place) {
        map?.let {
            val position = place.latLng
            it.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 17f))
            if (position != null) {
                mapMarkerAdapter?.markNewMeetingPointPosition(position, place.name.toString())
                mapBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                binding.bottom.address.text = place.address
            }
        }
    }

    override fun onDestroyView() {
        map?.cameraPosition?.let {
            viewModel.cameraLocation = CameraSettings(it.target, it.zoom, it.tilt, it.bearing)
        }

        activity?.window?.setSoftInputMode(softInputMode)
        viewModel.disableRectFitting()
        _binding = null

        super.onDestroyView()
    }

    private fun handleUnreadCount(unreadCount: Int?) {
        if (unreadCount == 0) {
            chatButtonText.visibility = View.INVISIBLE
        } else {
            chatButtonText.text = unreadCount.toString()
            chatButtonText.visibility = View.VISIBLE
        }
    }

    private fun handleLastMessage(
        shortChatMessages: ShortChatMessages?,
        lastMessage: ConstraintLayout
    ) {
        receivedMessageTimerJob?.cancel()
        receivedMessageTimerJob = lifecycleScope.launch {
            when (shortChatMessages) {
                is ShortChatMessages.TextMessage -> {
                    lastMessage.visibility = View.VISIBLE
                    ((binding.chatView.userAvatar.layout.background as LayerDrawable).getDrawable(0) as GradientDrawable).setColor(
                        shortChatMessages.senderColor
                    )
                    binding.chatView.messagePreview.text = shortChatMessages.messageText
                    binding.chatView.userAvatar.nameShort.text = shortChatMessages.senderNameShort

                    delay(5000L)
                    lastMessage.visibility = View.GONE
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleLocation() {
        map?.isMyLocationEnabled = true
        map?.setLocationSource(
            object : LocationSource {
                override fun activate(p0: LocationSource.OnLocationChangedListener?) {
                    locationListener = p0
                }

                override fun deactivate() {
                    locationListener = null
                }
            }
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.ownLocationUpdateFlow.first { location ->
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 17.0f))
                true
            }

            viewModel.ownLocationUpdateFlow.collect { location ->
                userLocation = location
                mapMarkerAdapter?.userLocation = location

                val androidLocation = android.location.Location("")
                androidLocation.latitude = location.latitude
                androidLocation.longitude = location.longitude
                androidLocation.altitude = location.altitude
                androidLocation.accuracy = location.horizontalAccuracy
                androidLocation.time = location.timestamp.time

                locationListener?.onLocationChanged(androidLocation)
            }
        }
    }

    private fun setNewMarker() {
        mapBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        viewModel.setMeetingPoint(mapMarkerAdapter?.getNewMeetingPointPosition()!!)
        mapMarkerAdapter?.removeNewMeetingPointPosition()
    }

    @SuppressLint("ShowToast")
    private fun handleNullLocation() {
        val snackBar = Snackbar.make(
            requireView(),
            getString(R.string.meetup_snackbar_locationDisabled),
            Snackbar.LENGTH_LONG
        ).setAnchorView(binding.chatView.chatButton)

        val snackBarText = snackBar.view.findViewById<TextView>(R.id.snackbar_text)
        snackBarText.maxLines = 3
        snackBar.show()
    }

    private fun handleRectFittingState(rectFittingEnabled: Boolean) {
        if (rectFittingEnabled) {
            mapMarkerAdapter?.rectFittingEnabled = true
            mapMarkerAdapter?.doRectFitting()
            binding.rectButton.setImageDrawable(getDrawable(requireContext(), R.drawable.ic_rectfitting_enabled))
        } else {
            mapMarkerAdapter?.rectFittingEnabled = false
            binding.rectButton.setImageDrawable(getDrawable(requireContext(), R.drawable.ic_rectfitting_disabled))
        }
    }
}
