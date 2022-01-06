package tice.dagger.modules

import dagger.Binds
import dagger.Module
import tice.managers.SettingsManager
import tice.managers.SettingsManagerType
import tice.managers.storageManagers.*

@Module
abstract class StorageManagerModule {

    @Binds
    abstract fun bindStorageLocker(storageLocker: StorageLocker): StorageLockerType

    @Binds
    abstract fun bindSignedInUserStorageManager(signedInUserStorageManager: SignedInUserStorageManager): SignedInUserStorageManagerType

    @Binds
    abstract fun bindGroupStorageManager(groupStorageManager: GroupStorageManager): GroupStorageManagerType

    @Binds
    abstract fun bindUserStorageManager(userManager: UserStorageManager): UserStorageManagerType

    @Binds
    abstract fun bindDeviceIdStoragemanager(deviceIdStorageManager: DeviceIdStorageManager): DeviceIdStorageManagerType

    @Binds
    abstract fun bindCryptoStorageManager(cryptoStorageManager: CryptoStorageManager): CryptoStorageManagerType

    @Binds
    abstract fun bindSettingsStorageManager(settingsStorageManager: SettingsManager): SettingsManagerType

    @Binds
    abstract fun bindConversationStorageManager(conversationStorageManager: ConversationStorageManager): ConversationStorageManagerType

    @Binds
    abstract fun bindChatStorageManager(chatStorageManager: ChatStorageManager): ChatStorageManagerType

    @Binds
    abstract fun bindMapboxAccessTokenStorageManager(mapboxAccessTokenStorageManager: MapboxAccessTokenStorageManager): MapboxAccessTokenStorageManagerType
}
