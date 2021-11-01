package tice.ui.viewModels

import android.content.Context
import tice.exceptions.GMSUnavailableException

fun RegisterViewModel.registerViaPush(userName: String?, context: Context) {
    logger.error("Push registering requested but GMS unavailable.")
    throw GMSUnavailableException
}