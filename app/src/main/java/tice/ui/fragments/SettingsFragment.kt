package tice.ui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.ticeapp.TICE.BuildConfig
import com.ticeapp.TICE.R
import dagger.Module
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import tice.ui.models.GroupNameData
import tice.ui.viewModels.SettingsViewModel
import tice.utility.BuildFlavorStore
import tice.utility.getLogger
import tice.utility.ui.getViewModel
import java.io.File

@Module
class SettingsFragment : PreferenceFragmentCompat(), HasAndroidInjector {
    val logger by getLogger()

    private val viewModel: SettingsViewModel by getViewModel()

    private var userNamePrefs: EditTextPreference? = null

    override fun androidInjector(): AndroidInjector<Any> {
        return androidInjector()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_preferences, rootKey)

        val mapboxAccessTokenPreference = findPreference<EditTextPreference>(getString(R.string.settingsMapboxAccessToken))
        mapboxAccessTokenPreference?.isVisible = BuildFlavorStore.fromFlavorString(BuildConfig.FLAVOR_store) == BuildFlavorStore.FDROID
        mapboxAccessTokenPreference?.text = viewModel.currentMapboxAccessToken
        mapboxAccessTokenPreference?.setOnPreferenceChangeListener { _, newValue ->
            viewModel.setMapboxAccessToken(newValue as String)
            true
        }

        val versionPref = findPreference<Preference>(getString(R.string.versionKey))
        val versionTitle = getString(R.string.settings_version_title)

        val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
        val versionNum = packageInfo.versionName
        val flavor = BuildConfig.FLAVOR
        val store = BuildConfig.FLAVOR_store

        @Suppress("DEPRECATION")
        val versionCode: String =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode.toString() else packageInfo.versionCode.toString()

        var versionString = "$versionTitle $versionNum ($versionCode) $store"
        if (BuildConfig.FLAVOR_stage != "production") {
            versionString += " $flavor"
        }

        versionPref?.summary = versionString

        userNamePrefs = findPreference(getString(R.string.usernameKey))
        userNamePrefs?.setOnPreferenceChangeListener { _, newValue ->
            viewModel.changeUserName(newValue as String)
            true
        }

        findPreference<Preference>(getString(R.string.deleteAllData))?.setOnPreferenceClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_account_confirmDeletion_title)
                .setMessage(R.string.settings_account_confirmDeletion_message)
                .setNegativeButton(R.string.settings_account_confirmDeletion_cancel) { _, _ -> }
                .setPositiveButton(R.string.settings_account_confirmDeletion_delete) { _, _ -> viewModel.deleteAllData() }
                .show()
            true
        }

        findPreference<Preference>(getString(R.string.privacyPolicy))?.setOnPreferenceClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.settings_privacy_url))))
            true
        }

        findPreference<Preference>(getString(R.string.giveFeedback))?.setOnPreferenceClickListener {
            sendFeedback()
            true
        }

        findPreference<Preference>(getString(R.string.thirdPartyLicenses))?.setOnPreferenceClickListener {
            handleShowLicensesTap()
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.name.observe(viewLifecycleOwner, { displayUserName(it) })
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.event.collect { event ->
                when (event) {
                    is SettingsViewModel.SettingsEvent.UserDeleted -> findNavController().navigate(R.id.action_global_RegisterFragment)
                    is SettingsViewModel.SettingsEvent.ErrorEvent.InGroupError -> {
                        Snackbar.make(requireView(), getString(R.string.error_user_in_group), Snackbar.LENGTH_SHORT).show()
                    }
                    is SettingsViewModel.SettingsEvent.ErrorEvent.ChangeNameError -> {
                        Snackbar.make(requireView(), getString(R.string.error_change_name), Snackbar.LENGTH_SHORT).show()
                    }
                    is SettingsViewModel.SettingsEvent.ErrorEvent.DeleteError -> {
                        Snackbar.make(requireView(), getString(R.string.error_delete_user), Snackbar.LENGTH_SHORT).show()
                    }
                    is SettingsViewModel.SettingsEvent.ErrorEvent.Error -> {
                        Snackbar.make(requireView(), getString(R.string.error_message), Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun displayUserName(userNameType: GroupNameData) {
        when (userNameType) {
            is GroupNameData.TeamName -> {
                userNamePrefs?.text = userNameType.name
                userNamePrefs?.summary = userNameType.name
            }
            is GroupNameData.PseudoName -> userNamePrefs?.summary = userNameType.name
        }
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
