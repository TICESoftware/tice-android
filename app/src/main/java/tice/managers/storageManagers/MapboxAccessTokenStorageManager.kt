package tice.managers.storageManagers

import tice.dagger.scopes.AppScope
import tice.exceptions.MapboxAccessTokenMissingException
import javax.inject.Inject
import javax.inject.Named

@AppScope
class MapboxAccessTokenStorageManager @Inject constructor(
    private val storageLocker: StorageLockerType,
    @Named("MAPBOX_ACCESS_TOKEN") private val mapboxAccessToken: String
) : MapboxAccessTokenStorageManagerType {

    override val customToken: String?
        get() = storageLocker.load(StorageLockerType.StorageKey.MAPBOX_ACCESS_TOKEN)

    override fun requireToken(): String {
        return customToken ?: run {
            if (mapboxAccessToken != "") {
                return mapboxAccessToken
            } else {
                throw MapboxAccessTokenMissingException.TokenMissing
            }
        }
    }

    override fun setCustomToken(token: String) {
        if (token != "") {
            storageLocker.store(StorageLockerType.StorageKey.MAPBOX_ACCESS_TOKEN, token)
        } else {
            storageLocker.remove(StorageLockerType.StorageKey.MAPBOX_ACCESS_TOKEN)
        }
    }
}
