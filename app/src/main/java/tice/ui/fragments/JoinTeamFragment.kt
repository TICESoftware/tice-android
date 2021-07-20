package tice.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.ticeapp.TICE.R
import com.ticeapp.TICE.databinding.JoinTeamFragmentBinding
import dagger.Module
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import tice.ui.viewModels.JoinTeamViewModel
import tice.utility.getLogger
import tice.utility.ui.getViewModel
import java.util.*

@Module
class JoinTeamFragment : DialogFragment() {
    val logger by getLogger()

    private val viewModel: JoinTeamViewModel by getViewModel()

    private var _binding: JoinTeamFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = JoinTeamFragmentBinding.inflate(inflater, container, false)
        setHasOptionsMenu(true)
        setCancelable(false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.state.observe(viewLifecycleOwner, Observer { handleState(it) })

        binding.joinTeamContinueButton.setOnClickListener { joinTeam() }
        binding.joinTeamCancelButton.setOnClickListener { dialog?.dismiss() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.event.onSubscription { arguments?.let { viewModel.fetchGroup(it["groupId"].toString(), it["groupKey"].toString()) } }
                .collect { handleEvent(it) }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun joinTeam() {
        viewModel.joinTeam()
    }

    private fun handleState(state: JoinTeamViewModel.JoinTeamState) {
        when (state) {
            is JoinTeamViewModel.JoinTeamState.Idle -> {
                dialog?.dismiss()
            }
            is JoinTeamViewModel.JoinTeamState.Loading -> {
                binding.joinTeamProgressBar.visibility = View.VISIBLE
                binding.joinTeamContinueButton.visibility = View.INVISIBLE
                binding.joinTeamCancelButton.visibility = View.INVISIBLE
                binding.joinTeamContinueButton.isEnabled = false
            }
            is JoinTeamViewModel.JoinTeamState.TeamAvailable -> {
                binding.joinTeamProgressBar.visibility = View.GONE
                binding.joinTeamContinueButton.visibility = View.VISIBLE
                binding.joinTeamContinueButton.isEnabled = true
                binding.joinTeamCancelButton.visibility = View.VISIBLE

                binding.joinTeamTitle.text = String.format(getString(R.string.joinGroup_info_text), state.groupName.name)
            }
        }
    }

    private fun handleEvent(event: JoinTeamViewModel.JoinTeamEvent) {
        when (event) {
            is JoinTeamViewModel.JoinTeamEvent.NotSignedIn -> {
                findNavController().navigate(R.id.action_global_RegisterFragment)
                Snackbar.make(requireParentFragment().requireView(), getString(R.string.register_sign_in), Snackbar.LENGTH_LONG).show()
                dialog?.dismiss()
            }
            is JoinTeamViewModel.JoinTeamEvent.JoinedTeam -> {
                findNavController().navigate(
                    JoinTeamFragmentDirections.actionGlobalTeamInfo(
                        UUID.fromString(
                            arguments?.get("groupId").toString()
                        )
                    )
                )
                dialog?.dismiss()
            }
            is JoinTeamViewModel.JoinTeamEvent.AlreadyMember -> {
                findNavController().navigate(
                    JoinTeamFragmentDirections.actionGlobalTeamInfo(
                        UUID.fromString(
                            arguments?.get("groupId").toString()
                        )
                    )
                )
                Snackbar.make(requireParentFragment().requireView(), getString(R.string.joinGroup_already_member), Snackbar.LENGTH_LONG)
                    .show()
                dialog?.dismiss()
            }
            is JoinTeamViewModel.JoinTeamEvent.ErrorEvent.TeamURLError -> {
                Snackbar.make(requireParentFragment().requireView(), getString(R.string.error_process_group), Snackbar.LENGTH_LONG).show()
                dialog?.dismiss()
            }
            is JoinTeamViewModel.JoinTeamEvent.ErrorEvent.Error -> {
                Snackbar.make(requireParentFragment().requireView(), getString(R.string.error_message), Snackbar.LENGTH_LONG).show()
                dialog?.dismiss()
            }
        }
    }
}
