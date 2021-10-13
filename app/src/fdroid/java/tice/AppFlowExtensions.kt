package tice

import android.content.Context
import tice.exceptions.GMSUnavailableException

fun AppFlow.initFirebase(context: Context) {
    logger.error("Push initialization requested but GMS unavailable.")
    throw GMSUnavailableException
}
suspend fun AppFlow.updatePushDeviceId() {
    logger.error("Push device id update requestded but GMS unavailable.")
    throw GMSUnavailableException
}
