package tice.ui.fragments

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.ticeapp.TICE.R
import com.ticeapp.TICE.databinding.RegisterFragmentBinding
import dagger.Module
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import tice.ui.delegates.ActionBarAccess
import tice.ui.viewModels.RegisterViewModel
import tice.ui.viewModels.RegisterViewModel.RegisterEvent
import tice.ui.viewModels.RegisterViewModel.RegisterUserState
import tice.utility.ui.getViewModel
import tice.utility.ui.hideKeyboard

@Module
class RegisterFragment : Fragment() {
    private val viewModel: RegisterViewModel by getViewModel()

    private var _binding: RegisterFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        _binding = RegisterFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as ActionBarAccess).actionBar.setDisplayHomeAsUpEnabled(false)
        (activity as ActionBarAccess).actionBar.hide()

        viewLifecycleOwner.lifecycleScope.launch { viewModel.event.collect { handleEvent(it) } }

        binding.registerButton.setOnClickListener { onButtonClicked() }

        viewModel.state.observe(viewLifecycleOwner, Observer { registerUserStateChange(it) })
    }

    private fun onButtonClicked() {
        viewModel.createUserProcess(binding.name.text.toString())
        hideKeyboard()
    }

    private fun registerUserStateChange(state: RegisterUserState) {
        when (state) {
            is RegisterUserState.Idle -> {
                binding.progressBar.visibility = View.INVISIBLE
                binding.nameLayout.visibility = View.VISIBLE
                binding.textInfo.visibility = View.VISIBLE
                binding.registerButton.visibility = View.VISIBLE
            }
            is RegisterUserState.Loading -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.nameLayout.visibility = View.INVISIBLE
                binding.textInfo.visibility = View.INVISIBLE
                binding.registerButton.visibility = View.INVISIBLE
            }
        }
    }

    private fun handleEvent(event: RegisterEvent) {
        when (event) {
            is RegisterEvent.Registered -> {
                findNavController().navigate(R.id.action_global_team_list)
                return
            }
            is RegisterEvent.ErrorEvent.CreateUserError -> {
                Snackbar.make(requireView(), getString(R.string.error_create_user), Snackbar.LENGTH_LONG).show()
            }
            is RegisterEvent.ErrorEvent.VerificationError -> {
                Snackbar.make(requireView(), getString(R.string.error_verification), Snackbar.LENGTH_LONG).show()
            }
            is RegisterEvent.ErrorEvent.DeviceIDError -> {
                Snackbar.make(requireView(), getString(R.string.error_no_deviceid), Snackbar.LENGTH_LONG).show()
            }
            is RegisterEvent.ErrorEvent.Error -> {
                Snackbar.make(requireView(), getString(R.string.error_message), Snackbar.LENGTH_LONG).show()
            }
        }

        binding.nameLayout.visibility = View.VISIBLE
        binding.textInfo.visibility = View.VISIBLE
        binding.registerButton.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        (activity as ActionBarAccess).actionBar.show()
        _binding = null
        super.onDestroyView()
    }
}
