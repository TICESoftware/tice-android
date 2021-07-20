package tice.ui.viewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.iid.FirebaseInstanceId
import com.ticeapp.TICE.BuildConfig
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import tice.backend.BackendType
import tice.crypto.ConversationCryptoMiddlewareType
import tice.crypto.CryptoManagerType
import tice.managers.SignedInUserManagerType
import tice.managers.messaging.WebSocketReceiverType
import tice.managers.messaging.notificationHandler.VerifyDeviceHandlerType
import tice.managers.storageManagers.DeviceIdStorageManagerType
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
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class RegisterViewModel @Inject constructor(
    private val coroutineContextProvider: CoroutineContextProviderType,
    private val backend: BackendType,
    private val deviceIdStorageManager: DeviceIdStorageManagerType,
    private val signedInUserManager: SignedInUserManagerType,
    private val cryptoManager: CryptoManagerType,
    private val conversationCryptoMiddleware: ConversationCryptoMiddlewareType,
    private val webSocketReceiver: WebSocketReceiverType,
    private val verifyDeviceHandler: VerifyDeviceHandlerType,
    private val beekeeper: BeekeeperType,
    @Named("REQUEST_DEVICE_ID_TIMEOUT") private val requestDeviceIdTimeout: Long,
    @Named("DEVELOPMENT_VERIFICATION_CODE") private val developmentVerificationCode: String
) : ViewModel() {
    private val logger by getLogger()

    private val _state = MutableLiveData<RegisterUserState>(RegisterUserState.Idle)
    val state: LiveData<RegisterUserState>
        get() = _state

    private val _event = MutableSharedFlow<RegisterEvent>()
    val event: SharedFlow<RegisterEvent>
        get() = _event.onSubscription { if (signedInUserManager.signedIn()) emit(RegisterEvent.Registered) }

    fun createUserProcess(name: String) {
        if (_state.value != RegisterUserState.Idle) {
            return
        } else {
            _state.postValue(RegisterUserState.Loading)
        }

        val userName = verifyNameString(name)
        val startTime = Date()

        beekeeper.track(BeekeeperEvent.register(userName != null))

        viewModelScope.launch(coroutineContextProvider.IO) {
            val deviceId = run {
                withTimeoutOrNull(requestDeviceIdTimeout) {
                    suspendCoroutine<String> { continuation ->
                        FirebaseInstanceId.getInstance().instanceId.addOnSuccessListener { result -> continuation.resume(result.token) }
                    }
                } ?: run {
                    _state.postValue(RegisterUserState.Idle)
                    _event.emit(RegisterEvent.ErrorEvent.DeviceIDError)
                    beekeeper.track(BeekeeperEvent.registerFailed(null, Date().time - startTime.time))
                    FirebaseInstanceId.getInstance().deleteInstanceId()
                    return@launch
                }
            }

            val verificationCode = if (BuildConfig.APPLICATION_ID == "app.tice.TICE.development" && developmentVerificationCode != "") developmentVerificationCode else try {
                verifyDeviceHandler.verifyDeviceId(deviceId)
            } catch (e: Exception) {
                logger.error("Verification failed: $e")
                _event.emit(RegisterEvent.ErrorEvent.VerificationError)
                _state.postValue(RegisterUserState.Idle)
                beekeeper.track(BeekeeperEvent.registerFailed(e, Date().time - startTime.time))
                return@launch
            }

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
                backend.createUser(
                    userPublicKeys,
                    Platform.Android,
                    deviceId,
                    verificationCode,
                    userName
                )
            } catch (e: Exception) {
                logger.error("Creating user failed.", e)
                beekeeper.track(BeekeeperEvent.registerFailed(e, Date().time - startTime.time))
                _event.emit(RegisterEvent.ErrorEvent.CreateUserError)
                _state.postValue(RegisterUserState.Idle)
                return@launch
            }

            deviceIdStorageManager.storeDeviceId(deviceId)
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
