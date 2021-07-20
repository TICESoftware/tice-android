package tice.dagger.modules

import dagger.Binds
import dagger.Module
import tice.crypto.*
import tice.managers.*
import tice.managers.group.*
import tice.managers.messaging.notificationHandler.VerifyDeviceHandler
import tice.managers.messaging.notificationHandler.VerifyDeviceHandlerType
import tice.managers.storageManagers.LocationSharingStorageManager
import tice.managers.storageManagers.LocationSharingStorageManagerType
import tice.utility.provider.CoroutineContextProvider
import tice.utility.provider.CoroutineContextProviderType

@Module
abstract class ManagerModule {

    @Binds
    abstract fun bindSignedInUserManager(signedInUserManager: SignedInUserManager): SignedInUserManagerType

    @Binds
    abstract fun bindTeamManager(teamManager: TeamManager): TeamManagerType

    @Binds
    abstract fun bindGroupManager(groupManager: GroupManager): GroupManagerType

    @Binds
    abstract fun bindUserManager(userManager: UserManager): UserManagerType

    @Binds
    abstract fun bindCryptoManager(cryptoManager: CryptoManager): CryptoManagerType

    @Binds
    abstract fun bindConversationCryptoMiddleware(conversationCryptoMiddleware: ConversationCryptoMiddleware): ConversationCryptoMiddlewareType

    @Binds
    abstract fun bindDoubleRatchetProvider(doubleRatchetProvider: DoubleRatchetProvider): DoubleRatchetProviderType

    @Binds
    abstract fun bindAuthManager(authManager: AuthManager): AuthManagerType

    @Binds
    abstract fun bindMeetupManager(meetupManager: MeetupManager): MeetupManagerType

    @Binds
    abstract fun bindCoroutineContextProvider(coroutineContextProvider: CoroutineContextProvider): CoroutineContextProviderType

    @Binds
    abstract fun bindLocationManager(locationManager: LocationManager): LocationManagerType

    @Binds
    abstract fun bindConversationManager(locationManager: ConversationManager): ConversationManagerType

    @Binds
    abstract fun bindVerifyUserHandler(verifyDeviceHandler: VerifyDeviceHandler): VerifyDeviceHandlerType

    @Binds
    abstract fun bindChatManager(chatManager: ChatManager): ChatManagerType

    @Binds
    abstract fun bindPopupNotificationManager(popupNotificationManager: PopupNotificationManager): PopupNotificationManagerType

    @Binds
    abstract fun bindLocationServiceController(locationServiceController: LocationServiceController): LocationServiceControllerType

    @Binds
    abstract fun bindLocationSharingStorageManager(locationSharingStorageManager: LocationSharingStorageManager): LocationSharingStorageManagerType

    @Binds
    abstract fun bindLocationSharingManager(locationSharingManager: LocationSharingManager): LocationSharingManagerType
}
