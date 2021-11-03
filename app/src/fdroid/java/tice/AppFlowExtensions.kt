package tice

import android.content.Context
import tice.exceptions.GMSUnavailableException

suspend fun AppFlow.updatePushDeviceId() {
    logger.error("Push device id update requestded but GMS unavailable.")
    throw GMSUnavailableException
}
fun AppFlow.storeSpecificSetup(context: Context) {

}