package tice.ui.fragments

import android.os.Bundle
import android.view.*
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.ticeapp.TICE.R
import com.ticeapp.TICE.databinding.TeamInfoFragmentBinding
import dagger.Module
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import tice.ui.adapter.MemberListAdapter
import tice.ui.models.GroupNameData
import tice.ui.viewModels.TeamInfoViewModel
import tice.ui.viewModels.TeamInfoViewModel.*
import tice.utility.getLogger
import tice.utility.ui.createDividerWithMargin
import tice.utility.ui.getViewModel
import tice.utility.ui.hideKeyboard
import tice.utility.ui.showShareSheet

@Module
class TeamInfoFragment : Fragment() {
    private val logger by getLogger()

    private val viewModel: TeamInfoViewModel by getViewModel()
    private val args: TeamInfoFragmentArgs by navArgs()

    private lateinit var currentData: TeamInfoData
    private val recyclerAdapter = MemberListAdapter(emptyList())

    private var _binding: TeamInfoFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        _binding = TeamInfoFragmentBinding.inflate(inflater, container, false)
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

        binding.teamNameText.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) onChangeTeamName() }

        val recyclerView = binding.teamRecyclerView
        recyclerView.adapter = recyclerAdapter
        recyclerView.createDividerWithMargin()

        binding.addMemberButton.setOnClickListener { onAddMember() }

        viewModel.state.observe(viewLifecycleOwner, { onHandleState(it) })
        viewModel.data.observe(viewLifecycleOwner, { handleDataChange(it) })

        viewLifecycleOwner.lifecycleScope.launch { viewModel.event.collect { handleEvent(it) } }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
    private fun handleEvent(event: TeamInfoEvent) {
        when (event) {
            is TeamInfoEvent.NoTeam -> findNavController().navigate(R.id.action_global_team_list)
            is TeamInfoEvent.ErrorEvent.ShareLocationError -> {
                Snackbar.make(requireView(), getString(R.string.error_meetup), Snackbar.LENGTH_LONG).show()
            }
            is TeamInfoEvent.ErrorEvent.LeaveGroupError -> {
                Snackbar.make(requireView(), getString(R.string.error_leave_group), Snackbar.LENGTH_LONG).show()
            }
            is TeamInfoEvent.ErrorEvent.DeleteGroupError -> {
                Snackbar.make(requireView(), getString(R.string.error_delete_group), Snackbar.LENGTH_LONG).show()
            }
            is TeamInfoEvent.ErrorEvent.SettingsError -> {
                Snackbar.make(requireView(), getString(R.string.error_update_settings), Snackbar.LENGTH_LONG).show()
            }
            is TeamInfoEvent.ErrorEvent.Error -> {
                Snackbar.make(requireView(), getString(R.string.error_message), Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun onHandleState(state: TeamInfoState) {
        when (state) {
            is TeamInfoState.Idle -> { binding.teamInfoProgress.isVisible = false }
            is TeamInfoState.Loading -> { binding.teamInfoProgress.isVisible = true }
        }
    }

    private fun handleDataChange(teamData: TeamInfoData) {
        this.currentData = teamData
        setTeamName(teamData.name)
        recyclerAdapter.addNewItems(teamData.groupMember.toList())

        if (teamData.userIsAdmin) {
            binding.manageGroupButton.text = getString(R.string.groupSettings_participation_delete)
            binding.manageGroupButton.setOnClickListener { showDeleteDialog() }
        } else {
            binding.manageGroupButton.text = getString(R.string.groupSettings_participation_leave)
            binding.manageGroupButton.setOnClickListener { showLeaveDialog() }
        }

        if (teamData.isSharingLocation) {
            binding.manageMeetupButton.text = getString(R.string.groupInfo_stop_location_sharing)
            binding.manageMeetupButton.setOnClickListener { showDisableDialog() }
        } else {
            binding.manageMeetupButton.text = getString(R.string.groupInfo_start_location_sharing)
            binding.manageMeetupButton.setOnClickListener { showDialog() }
        }
    }

    private fun setTeamName(teamName: GroupNameData) {
        when (teamName) {
            is GroupNameData.TeamName -> binding.teamNameText.setText(teamName.name)
            is GroupNameData.PseudoName -> {
                binding.teamNameTextLayout.hint = teamName.name
                binding.teamNameTextLayout.placeholderText = teamName.name
            }
        }
    }

    private fun onAddMember() {
        showShareSheet(currentData.groupUrl)
    }

    private fun onChangeTeamName() {
        viewModel.updateGroup(binding.teamNameText.text.toString())
        hideKeyboard()
    }

    private fun showDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.team_startLocationSharing_title)
            .setMessage(R.string.team_startLocationSharing_message)
            .setNegativeButton(R.string.alert_cancel) { _, _ -> }
            .setPositiveButton(R.string.team_locationSharing_confirm) { _, _ -> viewModel.triggerLocationSharingAction(true) }
            .show()
    }

    private fun showDisableDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.team_stopLocationSharing_title)
            .setMessage(R.string.team_stopLocationSharing_message)
            .setNegativeButton(R.string.alert_cancel) { _, _ -> }
            .setPositiveButton(R.string.team_locationSharing_active_subtitle) { _, _ -> viewModel.triggerLocationSharingAction(false) }
            .show()
    }

    private fun showDeleteDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.groupInfo_delete_group_text)
            .setNegativeButton(R.string.alert_cancel) { _, _ -> }
            .setPositiveButton(R.string.groupInfo_delete_group_confirm) { _, _ -> viewModel.deleteGroup() }
            .show()
    }

    private fun showLeaveDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.groupInfo_leave_group_text)
            .setNegativeButton(R.string.alert_cancel) { _, _ -> }
            .setPositiveButton(R.string.groupInfo_leave_group_confirm) { _, _ -> viewModel.leaveGroup() }
            .show()
    }
}
