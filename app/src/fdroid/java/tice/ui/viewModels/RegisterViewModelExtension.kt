package tice.ui.viewModels

import tice.exceptions.GMSUnavailableException

fun RegisterViewModel.registerViaPush(userName: String?) {
    logger.error("Push registering requested but GMS unavailable.")
    throw GMSUnavailableException
}