package tice.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.ticeapp.TICE.R
import com.ticeapp.TICE.databinding.ForceUpdateFragmentBinding
import dagger.Module
import dagger.android.support.DaggerFragment
import tice.dagger.setup.ViewModelFactory
import tice.ui.delegates.ActionBarAccess
import tice.ui.viewModels.ForceUpdateViewModel
import tice.ui.viewModels.ForceUpdateViewModel.UpdateState
import javax.inject.Inject

@Module
class ForceUpdateFragment : DaggerFragment() {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory
    private lateinit var viewModel: ForceUpdateViewModel

    private var _binding: ForceUpdateFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ForceUpdateFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this, viewModelFactory).get(ForceUpdateViewModel::class.java)
        viewModel.checkForUpdate()

        (activity as ActionBarAccess).actionBar.hide()
        viewModel.state.observe(viewLifecycleOwner, { handleState(it) })

        binding.forceUpdateButton.setOnClickListener { viewModel.checkAgain() }
    }

    private fun handleState(updateState: UpdateState) {
        when (updateState) {
            is UpdateState.InitialState -> {
                binding.forceUpdateLayout.visibility = View.INVISIBLE
                binding.forceUpdateProgress.visibility = View.INVISIBLE
                binding.forceUpdateTiceLogo.visibility = View.VISIBLE
            }
            is UpdateState.UpdateAvailable -> {
                binding.forceUpdateLayout.visibility = View.VISIBLE
                binding.forceUpdateProgress.visibility = View.INVISIBLE
                binding.forceUpdateTitle.text = getString(R.string.force_update_title)
                binding.forceUpdateText.text = String.format(getString(R.string.force_update_text), updateState.minVersion)
                binding.forceUpdateTiceLogo.visibility = View.INVISIBLE
            }
            is UpdateState.UpdateNotNecessary -> {
                findNavController().navigate(R.id.action_global_RegisterFragment)
            }
            is UpdateState.Loading -> {
                binding.forceUpdateLayout.visibility = View.INVISIBLE
                binding.forceUpdateProgress.visibility = View.VISIBLE
                binding.forceUpdateTiceLogo.visibility = View.INVISIBLE
            }
            is UpdateState.Error -> {
                binding.forceUpdateLayout.visibility = View.VISIBLE
                binding.forceUpdateProgress.visibility = View.INVISIBLE
                binding.forceUpdateTiceLogo.visibility = View.INVISIBLE
                binding.forceUpdateTitle.text = getString(R.string.force_update_title_error)
                binding.forceUpdateText.text = String.format(getString(R.string.force_update_text_error), updateState.exception)
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
