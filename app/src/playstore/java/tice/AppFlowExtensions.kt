package tice

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.withTimeoutOrNull
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
