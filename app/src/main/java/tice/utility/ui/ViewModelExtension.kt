package tice.utility.ui

import androidx.lifecycle.ViewModel

fun ViewModel.verifyNameString(name: String): String? {
    return if (name.trim().isEmpty()) null else name
}
