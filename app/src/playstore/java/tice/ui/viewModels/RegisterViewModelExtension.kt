package tice.ui.viewModels

import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.ticeapp.TICE.BuildConfig
import kotlinx.coroutines.launch
import tice.utility.beekeeper.BeekeeperEvent
import tice.utility.beekeeper.track
import java.util.*

fun RegisterViewModel.registerViaPush(userName: String?) {
    FirebaseMessaging
        .getInstance()
        .token
        .addOnSuccessListener { deviceId ->
            logger.debug("Retrieved device id. Request verification code via push from backend.")

            viewModelScope.launch(coroutineContextProvider.IO) {
                val verificationCode =
                    if (BuildConfig.APPLICATION_ID == "app.tice.TICE.development" && developmentVerificationCode != "") developmentVerificationCode else try {
                        verifyDeviceHandler.verifyDeviceId(deviceId)
                    } catch (e: Exception) {
                        logger.error("Verification failed: $e")
                        _event.emit(RegisterViewModel.RegisterEvent.ErrorEvent.VerificationError)
                        _state.postValue(RegisterViewModel.RegisterUserState.Idle)
                        beekeeper.track(BeekeeperEvent.registerFailed(e, Date().time - startTime.time))
                        return@launch
                    }

                logger.debug("Received verification code. Creating user.")
                createUser(userName, verificationCode, deviceId)
            }
        }
        .addOnFailureListener {
            logger.error("Could not retrieve device id. Error: $it")
            viewModelScope.launch(coroutineContextProvider.Default) { _event.emit(RegisterViewModel.RegisterEvent.ErrorEvent.DeviceIDError) }
            _state.postValue(RegisterViewModel.RegisterUserState.Idle)
            beekeeper.track(BeekeeperEvent.registerFailed(it, Date().time - startTime.time))
        }
}
