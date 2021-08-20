package tice.ui.fragments

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.ticeapp.TICE.R
import com.ticeapp.TICE.databinding.GroupMapFragmentBinding
import dagger.Module
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import tice.ui.delegates.ActionBarAccess
import tice.ui.models.ShortChatMessages
import tice.ui.viewModels.GroupMapViewModel
import tice.ui.viewModels.GroupMapViewModel.MapEvent
import tice.ui.viewModels.GroupMapViewModel.MeetUpButtonState
import tice.ui.viewModels.MapContainerViewModel
import tice.utility.ui.getViewModel
import javax.inject.Inject
import javax.inject.Named

@Module
class GroupMapFragment : Fragment() {
    private val viewModel: GroupMapViewModel by getViewModel()
    private lateinit var mapContainerFragment: MapContainerFragment
    private lateinit var mapViewModel: Lazy<MapContainerViewModel>
    private val args: GroupMapFragmentArgs by navArgs()

    @Inject
    @Named("PLACES_SEARCH_REQUEST_CODE")
    lateinit var placesSearchRequestCode: String

    private lateinit var chatButtonText: TextView

    private var receivedMessageTimerJob: Job? = null

    private var _binding: GroupMapFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.initMapbox(requireContext())

        mapContainerFragment = if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS) GoogleMapsContainerFragment() else MapboxMapContainerFragment()
        mapViewModel = mapContainerFragment.getViewModel()

        if (savedInstanceState == null) {
            childFragmentManager.commit {
                setReorderingAllowed(true)
                add(R.id.map_fragment_container_view, mapContainerFragment)
                mapContainerFragment.teamId = args.teamId
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        _binding = GroupMapFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.setupData(args.teamId)

        chatButtonText = binding.chatView.buttonText

        binding.chatView.chatButton.setOnClickListener {
            findNavController().navigate(ChatFragmentDirections.actionGlobalChatFragment(args.teamId))
        }

        binding.meetupButton.view.setOnClickListener { meetupAction() }

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
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        val groupInfo = menu.findItem(R.id.defaultMenu_GroupInfoMenu)
        menu.findItem(R.id.defaultMenu_SettingsMenu).isVisible = false
        groupInfo.isVisible = true

        groupInfo.setOnMenuItemClickListener {
            findNavController().navigate(GroupMapFragmentDirections.actionGlobalTeamInfo(args.teamId))
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

    private fun meetupAction() {
        when (viewModel.meetUpButtonState.value) {
            is MeetUpButtonState.None -> showDialog()
            is MeetUpButtonState.WeShareLocation -> showDisableDialog()
            else -> viewModel.triggerMeetupAction()
        }
    }

    private fun handleStateChange(state: MeetUpButtonState) {
        when (state) {
            is MeetUpButtonState.TheyShareLocation -> {
                binding.meetupButton.title.visibility = View.VISIBLE
                binding.meetupButton.title.text = getString(R.string.team_locationSharing_start_title)
                binding.meetupButton.progressBar.visibility = View.INVISIBLE
                binding.meetupButton.summary.visibility = View.VISIBLE
                binding.meetupButton.summary.text = getString(R.string.team_locationSharing_othersActive_subtitle)
            }
            is MeetUpButtonState.OneSharesLocation -> {
                binding.meetupButton.title.visibility = View.VISIBLE
                binding.meetupButton.title.text = getString(R.string.team_locationSharing_start_title)
                binding.meetupButton.progressBar.visibility = View.INVISIBLE
                binding.meetupButton.summary.visibility = View.VISIBLE
                binding.meetupButton.summary.text = String.format(getString(R.string.team_locationSharing_otherActive_subtitle), state.userName)
            }
            is MeetUpButtonState.None -> {
                binding.meetupButton.title.text = getString(R.string.team_locationSharing_start_title)
                binding.meetupButton.progressBar.visibility = View.INVISIBLE
                binding.meetupButton.summary.visibility = View.GONE
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
                binding.meetupButton.summary.text = getString(R.string.team_locationSharing_active_subtitle)
            }
        }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
}
