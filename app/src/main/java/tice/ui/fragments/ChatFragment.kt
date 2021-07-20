package tice.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.ticeapp.TICE.R
import com.ticeapp.TICE.databinding.ChatFragmentBinding
import dagger.Module
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import tice.ui.adapter.ChatPagingAdapter
import tice.ui.delegates.ActionBarAccess
import tice.ui.viewModels.ChatViewModel
import tice.utility.ui.getViewModel
import tice.utility.ui.hideKeyboard

@Module
class ChatFragment : Fragment() {

    private val viewModel: ChatViewModel by getViewModel()
    private val args: ChatFragmentArgs by navArgs()

    private var _binding: ChatFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        setHasOptionsMenu(true)
        _binding = ChatFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.setUpData(args.teamId)
        Log.d("TAG", "onViewCreated: ${args.teamId}")

        viewModel.teamName.observe(
            viewLifecycleOwner,
            Observer { (activity as ActionBarAccess).actionBar.title = getString(R.string.chat_title) + it.name }
        )

        val recycler = binding.chatRecycler
        val adapter = ChatPagingAdapter()
        recycler.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch { viewModel.event.collect { handleEvent(it) } }

        adapter.registerAdapterDataObserver(
            object : RecyclerView.AdapterDataObserver() {
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    if (positionStart == 0) recycler.smoothScrollToPosition(0)
                }
            }
        )

        viewModel.paging.observe(viewLifecycleOwner, Observer { viewLifecycleOwner.lifecycleScope.launch { adapter.submitData(it) } })

        binding.chatTextLayout.setEndIconOnClickListener {
            val text = binding.chatTextField.text.toString()
            binding.chatTextField.text?.clear()

            if (text.trim().isNotEmpty()) {
                viewModel.addMessage(text)
                binding.chatTextField.text?.clear()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.findItem(R.id.defaultMenu_SettingsMenu).isVisible = false
    }

    private fun handleEvent(event: ChatViewModel.ChatEvent) {
        when (event) {
            ChatViewModel.ChatEvent.NoTeam -> findNavController().navigate(R.id.action_global_team_list)
            is ChatViewModel.ChatEvent.Error -> {
                Snackbar.make(requireView(), getString(R.string.error_message), Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        hideKeyboard(activity?.currentFocus)
        _binding = null
        super.onDestroyView()
    }
}
