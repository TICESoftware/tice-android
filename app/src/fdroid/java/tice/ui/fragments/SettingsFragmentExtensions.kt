package tice.ui.fragments

import android.app.AlertDialog
import android.webkit.WebView

fun SettingsFragment.handleShowLicensesTap() {
    val webView = WebView(requireContext())
    webView.settings.javaScriptEnabled = false
    webView.loadUrl("file:///android_asset/licenses.html")

    AlertDialog.Builder(requireContext())
        .setTitle("Licenses")
        .setView(webView)
        .show()
}
