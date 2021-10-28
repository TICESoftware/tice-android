package tice.ui.viewModels

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcaptcha.sdk.HCaptcha
import com.ticeapp.TICE.BuildConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import tice.backend.BackendType
import tice.crypto.ConversationCryptoMiddlewareType
import tice.crypto.CryptoManagerType
import tice.managers.SignedInUserManagerType
import tice.managers.messaging.WebSocketReceiverType
import tice.managers.messaging.notificationHandler.VerifyDeviceHandlerType
import tice.models.Platform
import tice.models.SignedInUser
import tice.models.TrackerEvent
import tice.models.UserPublicKeys
import tice.models.responses.CreateUserResponse
import tice.utility.BuildFlavorStore
import tice.utility.TrackerType
import tice.utility.getLogger
import tice.utility.provider.CoroutineContextProviderType
import tice.utility.ui.verifyNameString
import java.util.*
import javax.inject.Inject
import javax.inject.Named

class RegisterViewModel @Inject constructor(
    val coroutineContextProvider: CoroutineContextProviderType,
    private val backend: BackendType,
    private val signedInUserManager: SignedInUserManagerType,
    private val cryptoManager: CryptoManagerType,
    private val conversationCryptoMiddleware: ConversationCryptoMiddlewareType,
    private val webSocketReceiver: WebSocketReceiverType,
    val verifyDeviceHandler: VerifyDeviceHandlerType,
    val tracker: TrackerType,
    @Named("HCAPTCHA_SITE_KEY") private val hcaptchaSiteKey: String
) : ViewModel() {
    val logger by getLogger()

    val _state = MutableLiveData<RegisterUserState>(RegisterUserState.Idle)
    val state: LiveData<RegisterUserState>
        get() = _state

    val _event = MutableSharedFlow<RegisterEvent>()
    val event: SharedFlow<RegisterEvent>
        get() = _event.onSubscription { if (signedInUserManager.signedIn()) emit(RegisterEvent.Registered) }

    lateinit var startTime: Date

    fun registerUser(name: String, context: Context) {
        if (_state.value == RegisterUserState.Idle) {
            _state.postValue(RegisterUserState.Loading)
        } else {
            return
        }

        val userName = verifyNameString(name)
        startTime = Date()

        tracker.track(TrackerEvent.register(userName != null))

        if (BuildFlavorStore.fromFlavorString(BuildConfig.FLAVOR_store).gmsAvailable(context)) {
            logger.debug("Google Play Services available. Register via push.")
            registerViaPush(userName, context)
        } else {
            logger.debug("Google Play Services unavailable. Register via captcha.")
            registerViaCaptcha(userName, context)
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

    suspend fun createUser(userName: String?, verificationCode: String, deviceId: String? = null) {
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
            tracker.track(TrackerEvent.registerFailed(e, Date().time - startTime.time))
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

        tracker.track(TrackerEvent.didRegister(Date().time - startTime.time))
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
