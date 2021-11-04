package tice

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.mapbox.search.MapboxSearchSdk
import com.mapbox.search.location.DefaultLocationProvider
import kotlinx.coroutines.withTimeoutOrNull
import tice.utility.BuildFlavorStore
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

fun AppFlow.initFirebase(context: Context) = FirebaseApp.initializeApp(context)
suspend fun AppFlow.updatePushDeviceId() {
    withTimeoutOrNull(2000) {
        suspendCoroutine<String> { continuation ->
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                continuation.resume(token)
            }
        }
    }?.let { verifyDeviceHandler.get().startUpdatingDeviceId(it) }
}
fun AppFlow.storeSpecificSetup(context: Context) {
    if (BuildFlavorStore.PLAY_STORE.gmsAvailable(application.applicationContext)) {
        initFirebase(application.applicationContext)
    } else {
        MapboxSearchSdk.initialize(application, mapboxAccessToken.get(), DefaultLocationProvider(application))
    }
}