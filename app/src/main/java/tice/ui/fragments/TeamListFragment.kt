package tice.ui.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.*
import android.widget.EditText
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.ticeapp.TICE.BuildConfig
import com.ticeapp.TICE.R
import com.ticeapp.TICE.databinding.TeamListFragmentBinding
import dagger.Module
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import tice.models.GroupId
import tice.ui.adapter.TeamListAdapter
import tice.ui.delegates.ActionBarAccess
import tice.ui.models.TeamData
import tice.ui.viewModels.TeamListViewModel
import tice.ui.viewModels.TeamListViewModel.ScreenState
import tice.ui.viewModels.TeamListViewModel.TeamListEvent
import tice.utility.ui.createDividerWithMargin
import tice.utility.ui.getViewModel

@Module
class TeamListFragment : Fragment() {

    private val viewModel: TeamListViewModel by getViewModel()

    private lateinit var adapter: TeamListAdapter

    private var _binding: TeamListFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = TeamListFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as ActionBarAccess).actionBar.setDisplayHomeAsUpEnabled(false)

        adapter = TeamListAdapter().apply {
            setViewHolderOnClickListener { item, _ -> onChosenTeam(item.groupId) }
            setViewHolderOnLongClickListener { item, _, itemView -> onLongClicked(item, itemView) }
        }

        binding.teamList.adapter = adapter
        binding.teamList.createDividerWithMargin()

        viewModel.setupData()

        binding.createButton.setOnClickListener { onButtonCreateTeamClicked() }

        viewModel.state.observe(viewLifecycleOwner, Observer { handleState(it) })

        viewModel.teamData.observe(viewLifecycleOwner, Observer { updateTeamDatas(it) })

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.event.collect { handleEvent(it) }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (BuildConfig.APPLICATION_ID == "app.tice.TICE.development") {
            val joinGroup = menu.findItem(R.id.defaultMenu_joinGroup)
            joinGroup.isVisible = true
            joinGroup.setOnMenuItemClickListener {
                val textField = EditText(requireContext())
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Group link (dev)")
                    .setView(textField)
                    .setPositiveButton("Join") { _, _ -> viewModel.joinTeam(textField.text.toString()) }
                    .setOnCancelListener(null)
                    .show()

                true
            }
        }
    }

    private fun handleState(screenState: ScreenState?) {
        binding.emptyVisuals.isVisible = screenState is ScreenState.EmptyVisuals
    }

    private fun handleEvent(event: TeamListEvent) {
        when (event) {
            is TeamListEvent.ErrorEvent.NotFoundError -> {
                Snackbar.make(requireView(), getString(R.string.error_not_found), Snackbar.LENGTH_LONG).show()
            }
            is TeamListEvent.ErrorEvent.DeleteError -> {
                Snackbar.make(requireView(), getString(R.string.error_delete_group), Snackbar.LENGTH_LONG).show()
            }
            is TeamListEvent.ErrorEvent.LeaveError -> {
                Snackbar.make(requireView(), getString(R.string.error_leave_group), Snackbar.LENGTH_LONG).show()
            }
            is TeamListEvent.ErrorEvent.Error -> {
                Snackbar.make(requireView(), getString(R.string.error_message), Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun updateTeamDatas(teamData: List<TeamData>) {
        adapter.setTeamData(teamData)
    }

    private fun onButtonCreateTeamClicked() {
        findNavController().navigate(R.id.action_global_createTeamFragment)
    }

    private fun onChosenTeam(groupId: GroupId) {
        findNavController().navigate(TeamListFragmentDirections.actionGlobalMapFragment(groupId))
    }

    @SuppressLint("RestrictedApi")
    private fun onLongClicked(teamData: TeamData, itemView: View) {
        val popup = PopupMenu(requireContext(), itemView)
        popup.menuInflater.inflate(R.menu.teamlist_menu, popup.menu)

        if (teamData.isAdmin) {
            popup.menu.findItem(R.id.leave_group).isVisible = false
            popup.setOnMenuItemClickListener { viewModel.deleteGroup(teamData.groupId); true }
        } else {
            popup.menu.findItem(R.id.delete_group).isVisible = false
            popup.setOnMenuItemClickListener { viewModel.leaveGroup(teamData.groupId); true }
        }

        MenuPopupHelper(requireContext(), popup.menu as MenuBuilder, itemView).apply {
            setForceShowIcon(true)
            show()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
