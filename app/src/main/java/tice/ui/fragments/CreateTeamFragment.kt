package tice.ui.fragments

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.ticeapp.TICE.R
import com.ticeapp.TICE.databinding.CreateTeamFragmentBinding
import dagger.Module
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import tice.ui.viewModels.CreateTeamViewModel
import tice.ui.viewModels.CreateTeamViewModel.CreateTeamEvent
import tice.ui.viewModels.CreateTeamViewModel.CreateTeamState
import tice.utility.getLogger
import tice.utility.ui.getViewModel
import tice.utility.ui.hideKeyboard
import tice.utility.ui.showKeyboard

@Module
class CreateTeamFragment : Fragment() {
    val logger by getLogger()

    private val viewModel: CreateTeamViewModel by getViewModel()

    private var _binding: CreateTeamFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.findItem(R.id.defaultMenu_SettingsMenu).isVisible = false
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        setHasOptionsMenu(true)
        _binding = CreateTeamFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.createTeamTeamNameText.requestFocus()
        showKeyboard()

        viewModel.state.observe(viewLifecycleOwner, { handleState(it) })
        viewLifecycleOwner.lifecycleScope.launch { viewModel.event.collect { handleEvent(it) } }

        binding.createTeamContinueButton.setOnClickListener { onConfirmCreation() }
    }

    override fun onDestroyView() {
        hideKeyboard(activity?.currentFocus)
        _binding = null
        super.onDestroyView()
    }

    private fun handleState(state: CreateTeamState) {
        when (state) {
            is CreateTeamState.Idle -> {
                binding.createTeamTeamNameTextLayout.placeholderText = state.pseudoName
                binding.createTeamProgressBar.visibility = View.INVISIBLE
                binding.createTeamContinueButton.visibility = View.VISIBLE
            }
            CreateTeamState.Loading -> {
                binding.createTeamProgressBar.visibility = View.VISIBLE
                binding.createTeamContinueButton.visibility = View.INVISIBLE
            }
        }
    }

    private fun handleEvent(event: CreateTeamEvent) {
        when (event) {
            is CreateTeamEvent.Done -> {
                val start = binding.createTeamStartSwitch.isChecked
                findNavController().navigate(CreateTeamFragmentDirections.actionTeamCreated(event.teamId, start))
            }
            is CreateTeamEvent.ErrorEvent.CreateGroupError -> {
                Snackbar.make(requireView(), getString(R.string.error_create_group), Snackbar.LENGTH_SHORT).show()
            }
            is CreateTeamEvent.ErrorEvent.Error -> {
                Snackbar.make(requireView(), getString(R.string.error_message), Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun onConfirmCreation() {
        viewModel.createGroup(binding.createTeamTeamNameText.text.toString())
    }
}
