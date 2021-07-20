package tice.ui.fragments

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.snackbar.Snackbar
import com.ticeapp.TICE.R
import com.ticeapp.TICE.databinding.MeetupinfoFragmentBinding
import dagger.Module
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import tice.models.ParticipationStatus
import tice.ui.adapter.MemberListAdapter
import tice.ui.viewModels.MeetupInfoViewModel
import tice.ui.viewModels.MeetupInfoViewModel.*
import tice.ui.viewModels.MeetupInfoViewModel.MeetupInfoEvent.*
import tice.utility.getLogger
import tice.utility.ui.createDividerWithMargin
import tice.utility.ui.getViewModel
import tice.utility.ui.isLocationPermissionGranted

@Module
class MeetupInfoFragment : Fragment() {
    val logger by getLogger()

    private val viewModel: MeetupInfoViewModel by getViewModel()
    private val args: MeetupInfoFragmentArgs by navArgs()

    private val participatingMemberAdapter = MemberListAdapter(emptyList())
    private val notParticipatingMemberAdapter = MemberListAdapter(emptyList())

    private var _binding: MeetupinfoFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        _binding = MeetupinfoFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        val setting = menu.findItem(R.id.defaultMenu_SettingsMenu)
        setting.isVisible = false
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.setupData(args.teamId)

        binding.participantsRecyclerview.adapter = participatingMemberAdapter
        binding.notParticipantsRecyclerview.adapter = notParticipatingMemberAdapter

        binding.participantsRecyclerview.createDividerWithMargin()
        binding.notParticipantsRecyclerview.createDividerWithMargin()

        binding.stopJoinMeetupButton.setOnClickListener { onStopJoinDeleteMeetup() }

        if (isLocationPermissionGranted()) binding.userlocationText.text = getString(R.string.meetupSettings_locationSharing_on)
        else binding.userlocationText.text = getString(R.string.meetupSettings_stopSharing_notAuthorized_message)

        viewModel.state.observe(viewLifecycleOwner, Observer { handleStateChange(it) })
        viewModel.data.observe(viewLifecycleOwner, Observer { handleDataChange(it) })

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.event.collect { handleEvent(it) }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun handleStateChange(state: MeetupInfoState) {
        logger.debug("Meetup state changed: $state")
        when (state) {
            is MeetupInfoState.Idle -> {
                binding.meetupProgress.visibility = View.INVISIBLE
                handleMemberStatusChange(state.status)
            }
            MeetupInfoState.Loading -> {
                binding.meetupProgress.visibility = View.VISIBLE
                binding.stopJoinMeetupButton.text = ""
            }
        }
    }

    private fun handleEvent(event: MeetupInfoEvent) {
        when (event) {
            is NoMeetup -> findNavController().navigate(MeetupInfoFragmentDirections.actionGlobalMapFragment(event.parentId))
            is MeetupDeleted -> findNavController().navigate(MeetupInfoFragmentDirections.actionGlobalMapFragment(event.parentId))
            is MeetupJoined -> findNavController().navigate(MeetupInfoFragmentDirections.actionGlobalMapFragment(event.parentId))
            is MeetupLeft -> findNavController().navigate(MeetupInfoFragmentDirections.actionGlobalMapFragment(event.parentId))
            is ErrorEvent.MeetupError -> {
                Snackbar.make(requireView(), getString(R.string.error_meetup_interaction), Snackbar.LENGTH_SHORT).show()
            }
            is ErrorEvent.Error -> {
                Snackbar.make(requireView(), getString(R.string.error_message), Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleDataChange(data: MeetupInfoData) {
        logger.debug("Meetup data changed: $data")

        if (data.groupMemberNotParticipating.isEmpty()) {
            binding.notParticipatingText.visibility = View.GONE
            binding.notParticipantsRecyclerview.visibility = View.GONE
        }

        participatingMemberAdapter.addNewItems(data.groupMemberParticipating)
        notParticipatingMemberAdapter.addNewItems(data.groupMemberNotParticipating)
    }

    private fun handleMemberStatusChange(status: ParticipationStatus?) {
        logger.debug("Meetup member state changed: $status")
        when (status) {
            ParticipationStatus.ADMIN -> {
                binding.stopJoinMeetupButton.text = getString(R.string.meetupSettings_participation_delete_delete)
                binding.participationStatusText.text = getString(R.string.meetupSettings_participation_delete_title)
            }
            ParticipationStatus.MEMBER -> {
                binding.stopJoinMeetupButton.text = getString(R.string.meetupSettings_participation_leave_leave)
                binding.participationStatusText.text = getString(R.string.meetupSettings_participation_leave_title)
            }
            ParticipationStatus.NOT_PARTICIPATING -> {
                binding.stopJoinMeetupButton.text = getString(R.string.meetupSettings_participation_join_join)
                binding.participationStatusText.text = getString(R.string.meetupSettings_participation_join_title)
            }
            else -> {
                binding.meetupProgress.visibility = View.VISIBLE
                binding.participationStatusText.text = ""
            }
        }
    }

    private fun onStopJoinDeleteMeetup() {
        viewModel.handleMeetupInteraction()
    }
}
