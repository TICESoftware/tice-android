package tice.ui.fragments

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ticeapp.TICE.BuildConfig
import com.ticeapp.TICE.NavigationControllerDirections
import com.ticeapp.TICE.R
import com.ticeapp.TICE.databinding.MigrationFragmentBinding
import dagger.Module
import dagger.android.support.DaggerFragment
import tice.dagger.setup.ViewModelFactory
import tice.ui.activitys.MainActivity
import tice.ui.delegates.ActionBarAccess
import tice.ui.viewModels.MigrationViewModel
import java.io.File
import javax.inject.Inject

@Module
class MigrationFragment : DaggerFragment() {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory
    private lateinit var viewModel: MigrationViewModel

    private var _binding: MigrationFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = MigrationFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(activity as MainActivity, viewModelFactory).get(MigrationViewModel::class.java)

        (activity as ActionBarAccess).actionBar.hide()
        viewModel.state.observe(viewLifecycleOwner, { handleState(it) })
    }

    private fun handleState(state: MigrationViewModel.MigrationState) {
        when (state) {
            MigrationViewModel.MigrationState.InitialState -> {
                binding.migrationTiceLogo.visibility = View.VISIBLE
                binding.migrationLayout.visibility = View.INVISIBLE
            }
            MigrationViewModel.MigrationState.Finished -> {
                findNavController().navigate(R.id.action_global_forceUpdateFragment)
                handleIntentData()
            }
            MigrationViewModel.MigrationState.Migrating -> {
                binding.migrationTiceLogo.visibility = View.INVISIBLE
                binding.migrationLayout.visibility = View.VISIBLE
            }
            MigrationViewModel.MigrationState.Error -> {
                binding.migrationTiceLogo.visibility = View.INVISIBLE
                binding.migrationLayout.visibility = View.INVISIBLE
                showMigrationErrorDialog()
            }
        }
    }

    private fun handleIntentData() {
        requireActivity().intent.data?.let { uri ->
            if (viewModel.state.value == MigrationViewModel.MigrationState.Migrating) {
                val groupIdString = uri.pathSegments[1]
                val groupKeyString = uri.fragment ?: ""
                findNavController().navigate(NavigationControllerDirections.actionGlobalJoinTeamFragment(groupIdString, groupKeyString))
            }
        }
        requireActivity().intent.data = null
    }

    private fun showMigrationErrorDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.migration_dialog_text)
            .setNegativeButton(R.string.migration_button_error) { _, _ ->
                sendFeedback()
                showMigrationErrorDialog()
            }
            .setPositiveButton(R.string.migration_button_retry) { _, _ ->
                viewModel.initializeMigration()
            }
            .setCancelable(false)
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun sendFeedback() {
        val context = requireContext()

        val packageName = context.packageName
        val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
        val versionName = packageInfo.versionName
        val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
        val sdkVersion = Build.VERSION.SDK_INT
        val versionRelease = Build.VERSION.RELEASE
        val device = Build.DEVICE
        val model = Build.MODEL
        val product = Build.PRODUCT

        val metaInfoString =
            """
            Env: $packageName
            Ver: $versionName ($versionCode)
            OS: $versionRelease (SDK $sdkVersion)
            Dev: $device, $model, $product
            """.trimIndent()

        val body = "\n\n\n$metaInfoString"

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.settings_feedback_subject))
            putExtra(Intent.EXTRA_EMAIL, arrayOf(getString(R.string.settings_feedback_email)))
            putExtra(Intent.EXTRA_TEXT, body)

            val filesDirectory = context.filesDir
            val logFile = File(filesDirectory.absolutePath + "/logs/log.txt")
            val logFileUri = FileProvider.getUriForFile(
                context.applicationContext,
                BuildConfig.APPLICATION_ID + ".fileprovider",
                logFile
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_STREAM, logFileUri)
        }
        startActivity(
            Intent.createChooser(
                intent,
                getString(R.string.settings_feedback_chooserText)
            )
        )
    }
}
