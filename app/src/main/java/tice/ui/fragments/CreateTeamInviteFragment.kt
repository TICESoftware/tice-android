package tice.ui.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.*
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.snackbar.Snackbar
import com.ticeapp.TICE.R
import com.ticeapp.TICE.databinding.CreateTeamInviteFragmentBinding
import dagger.Module
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import tice.ui.delegates.ActionBarAccess
import tice.ui.viewModels.CreateTeamInviteViewModel
import tice.utility.ui.getViewModel
import tice.utility.ui.showShareSheet

@Module
class CreateTeamInviteFragment : Fragment() {

    private val viewModel: CreateTeamInviteViewModel by getViewModel()

    private val args: CreateTeamInviteFragmentArgs by navArgs()

    private var _binding: CreateTeamInviteFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        _binding = CreateTeamInviteFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.setup(args.teamId)

        (activity as ActionBarAccess).actionBar.setDisplayHomeAsUpEnabled(false)

        viewModel.teamData.observe(viewLifecycleOwner, { updateTeamData(it) })
        viewLifecycleOwner.lifecycleScope.launch { viewModel.event.collect { handleEvent(it) } }

        binding.teamInviteContinueButton.setOnClickListener { onContinueButton() }
        binding.teamInviteShareButton.setOnClickListener { onShareButton() }
        binding.teamInviteBorderLayout.setOnClickListener { onCopyToClipboardButton() }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.findItem(R.id.defaultMenu_SettingsMenu).isVisible = false
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun handleEvent(event: CreateTeamInviteViewModel.CreateTeamInviteEvent) {
        when (event) {
            CreateTeamInviteViewModel.CreateTeamInviteEvent.NoTeam -> findNavController().navigate(R.id.action_global_team_list)
            is CreateTeamInviteViewModel.CreateTeamInviteEvent.Error -> {
                Snackbar.make(requireView(), getString(R.string.error_message), Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateTeamData(teamData: CreateTeamInviteViewModel.TeamData) {
        binding.teamInviteJoinText.text = String.format(getString(R.string.createTeamInvite_invitation_text), teamData.teamName)
        binding.teamInviteUrl.text = teamData.teamUrl
    }

    private fun onContinueButton() {
        if (args.startImmediately) {
            findNavController().navigate(CreateTeamInviteFragmentDirections.actionGlobalMapFragment(args.teamId))
        } else {
            findNavController().navigate(R.id.returnToSource_createTeamInvite)
        }
    }

    private fun onShareButton() {
        showShareSheet(binding.teamInviteJoinText.text.toString() + viewModel.teamData.value?.teamUrl)
    }

    private fun onCopyToClipboardButton() {
        val clipText = binding.teamInviteJoinText.text.toString() + viewModel.teamData.value?.teamUrl
        val clipData = ClipData.newPlainText("label", clipText)

        getSystemService(requireContext(), ClipboardManager::class.java)?.setPrimaryClip(clipData)
        Snackbar.make(requireView(), R.string.createTeamInvite_copied, Snackbar.LENGTH_LONG).show()
    }
}
