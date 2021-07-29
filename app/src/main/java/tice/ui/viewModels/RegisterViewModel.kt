package tice.ui.viewModels

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.messaging.FirebaseMessaging
import com.hcaptcha.sdk.HCaptcha
import com.ticeapp.TICE.BuildConfig
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import tice.backend.BackendType
import tice.crypto.ConversationCryptoMiddlewareType
import tice.crypto.CryptoManagerType
import tice.managers.SignedInUserManagerType
import tice.managers.messaging.WebSocketReceiverType
import tice.managers.messaging.notificationHandler.VerifyDeviceHandlerType
import tice.models.*
import tice.models.responses.CreateUserResponse
import tice.utility.beekeeper.BeekeeperEvent
import tice.utility.beekeeper.BeekeeperType
import tice.utility.beekeeper.track
import tice.utility.getLogger
import tice.utility.provider.CoroutineContextProviderType
import tice.utility.ui.verifyNameString
import java.util.*
import javax.inject.Inject
import javax.inject.Named

class RegisterViewModel @Inject constructor(
    private val coroutineContextProvider: CoroutineContextProviderType,
    private val backend: BackendType,
    private val signedInUserManager: SignedInUserManagerType,
    private val cryptoManager: CryptoManagerType,
    private val conversationCryptoMiddleware: ConversationCryptoMiddlewareType,
    private val webSocketReceiver: WebSocketReceiverType,
    private val verifyDeviceHandler: VerifyDeviceHandlerType,
    private val beekeeper: BeekeeperType,
    @Named("REQUEST_DEVICE_ID_TIMEOUT") private val requestDeviceIdTimeout: Long,
    @Named("HCAPTCHA_SITE_KEY") private val hcaptchaSiteKey: String,
    @Named("DEVELOPMENT_VERIFICATION_CODE") private val developmentVerificationCode: String
) : ViewModel() {
    private val logger by getLogger()

    private val _state = MutableLiveData<RegisterUserState>(RegisterUserState.Idle)
    val state: LiveData<RegisterUserState>
        get() = _state

    private val _event = MutableSharedFlow<RegisterEvent>()
    val event: SharedFlow<RegisterEvent>
        get() = _event.onSubscription { if (signedInUserManager.signedIn()) emit(RegisterEvent.Registered) }

    private lateinit var startTime: Date

    fun registerUser(name: String, context: Context) {
        if (_state.value != RegisterUserState.Idle) {
            return
        } else {
            _state.postValue(RegisterUserState.Loading)
        }

        val userName = verifyNameString(name)
        startTime = Date()

        beekeeper.track(BeekeeperEvent.register(userName != null))

        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS) {
            logger.debug("Google Play Services available. Register via push.")
            registerViaPush(userName)
        } else {
            logger.debug("Google Play Services unavailable. Register via captcha.")
            registerViaCaptcha(userName, context)
        }
    }

    private fun registerViaPush(userName: String?) {
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
                            _event.emit(RegisterEvent.ErrorEvent.VerificationError)
                            _state.postValue(RegisterUserState.Idle)
                            beekeeper.track(BeekeeperEvent.registerFailed(e, Date().time - startTime.time))
                            return@launch
                        }

                    logger.debug("Received verification code. Creating user.")
                    createUser(userName, verificationCode, deviceId)
                }
            }
            .addOnFailureListener {
                logger.error("Could not retrieve device id. Error: $it")
                viewModelScope.launch(coroutineContextProvider.Default) { _event.emit(RegisterEvent.ErrorEvent.DeviceIDError) }
                _state.postValue(RegisterUserState.Idle)
                beekeeper.track(BeekeeperEvent.registerFailed(it, Date().time - startTime.time))
            }
    }

    private fun registerViaCaptcha(userName: String?, context: Context) {
        HCaptcha
            .getClient(context)
            .verifyWithHCaptcha(hcaptchaSiteKey)
            .addOnSuccessListener { response ->
                logger.debug("Captcha success. Creating user.")

                viewModelScope.launch(coroutineContextProvider.IO) {
                    createUser(userName, response.tokenResult)
                }
            }
            .addOnFailureListener { exception ->
                logger.error("Captcha error: (${exception.statusCode}) - ${exception.hCaptchaError.message}")
                viewModelScope.launch(coroutineContextProvider.Default) { _event.emit(RegisterEvent.ErrorEvent.VerificationError) }
                _state.postValue(RegisterUserState.Idle)
            }
    }

    private suspend fun createUser(userName: String?, verificationCode: String, deviceId: String? = null) {
        val signingKeyPair = cryptoManager.generateSigningKeyPair()
        val publicKeyMaterial = conversationCryptoMiddleware.renewHandshakeKeyMaterial(signingKeyPair.privateKey, signingKeyPair.publicKey)
        val userPublicKeys = UserPublicKeys(
            signingKeyPair.publicKey,
            publicKeyMaterial.identityKey,
            publicKeyMaterial.signedPrekey,
            publicKeyMaterial.prekeySignature,
            publicKeyMaterial.oneTimePrekeys
        )

        // Sends CreateUserRequest
        val createUserResponse: CreateUserResponse = try {
            if (deviceId != null) {
                backend.createUserUsingPush(userPublicKeys, Platform.Android, deviceId, verificationCode, userName)
            } else {
                backend.createUserUsingCaptcha(userPublicKeys, Platform.Android, verificationCode, userName)
            }
        } catch (e: Exception) {
            logger.error("Creating user failed: $e")
            beekeeper.track(BeekeeperEvent.registerFailed(e, Date().time - startTime.time))
            _event.emit(RegisterEvent.ErrorEvent.CreateUserError)
            _state.postValue(RegisterUserState.Idle)
            return
        }

        signedInUserManager.storeSignedInUser(
            SignedInUser(
                createUserResponse.userId,
                userName,
                signingKeyPair.publicKey,
                signingKeyPair.privateKey
            )
        )

        webSocketReceiver.connect()

        beekeeper.track(BeekeeperEvent.didRegister(Date().time - startTime.time))
        _event.emit(RegisterEvent.Registered)
    }

    sealed class RegisterUserState {
        object Idle : RegisterUserState()
        object Loading : RegisterUserState()
    }

    sealed class RegisterEvent {
        object Registered : RegisterEvent()
        sealed class ErrorEvent() : RegisterEvent() {
            object DeviceIDError : ErrorEvent()
            object VerificationError : ErrorEvent()
            object CreateUserError : ErrorEvent()
            object Error : ErrorEvent()
        }
    }
}
