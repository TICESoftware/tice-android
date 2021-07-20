package tice.utility.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.app.ShareCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ticeapp.TICE.R
import tice.TICEApplication

fun Fragment.hideKeyboard() {
    val inputMethodManager =
        requireContext().getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow(requireView().windowToken, 0)
}

fun Fragment.hideKeyboard(view: View?) {
    val inputMethodManager =
        requireContext().getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow(view?.windowToken, 0)
}

fun Fragment.showKeyboard() {
    val inputMethodManager =
        requireContext().getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.toggleSoftInput(
        InputMethodManager.SHOW_FORCED,
        InputMethodManager.HIDE_IMPLICIT_ONLY
    )
}

fun Fragment.showShareSheet(url: String?) {
    ShareCompat.IntentBuilder.from(requireActivity())
        .setType("text/html")
        .setChooserTitle(getString(R.string.createTeamInvite_chooserTitle))
        .setSubject(getString(R.string.createTeamInvite_emailSubject))
        .setText(url)
        .startChooser()
}

fun Fragment.isLocationPermissionGranted(): Boolean {
    return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

inline fun <reified F : Fragment, reified V : ViewModel> F.getViewModel(): Lazy<V> {
    return object : Lazy<V> {
        private var cached: ViewModel? = null

        override val value: V
            get() {
                val cachedViewModel = if (cached != null) {
                    cached
                } else {
                    val factory = (activity?.application as TICEApplication).appComponent.getViewModelFactory()
                    cached = ViewModelProvider(this@getViewModel, factory).get(V::class.java)
                    cached
                }
                return cachedViewModel as V
            }

        override fun isInitialized(): Boolean = cached != null
    }
}
