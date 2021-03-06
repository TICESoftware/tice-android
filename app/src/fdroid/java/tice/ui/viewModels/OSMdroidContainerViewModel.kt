package tice.ui.viewModels

import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import tice.managers.LocationServiceControllerType
import tice.managers.LocationSharingManagerType
import tice.managers.MapboxGeocodingManagerType
import tice.managers.UserManagerType
import tice.managers.group.TeamManagerType
import tice.managers.storageManagers.GroupStorageManagerType
import tice.managers.storageManagers.MapboxAccessTokenStorageManager
import tice.managers.storageManagers.MapboxAccessTokenStorageManagerType
import tice.models.Coordinates
import tice.utility.CustomMapboxTileSource
import tice.utility.provider.CoroutineContextProviderType
import tice.utility.provider.NameProviderType
import tice.utility.provider.UserDataGeneratorType
import javax.inject.Inject
import javax.inject.Named

class OSMdroidContainerViewModel @Inject constructor(
    groupStorageManager: GroupStorageManagerType,
    teamManager: TeamManagerType,
    locationSharingManager: LocationSharingManagerType,
    userManager: UserManagerType,
    nameProvider: NameProviderType,
    userDataGenerator: UserDataGeneratorType,
    locationServiceController: LocationServiceControllerType,
    private val coroutineContextProvider: CoroutineContextProviderType,
    private val mapboxGeocodingManager: MapboxGeocodingManagerType,
    private val mapboxAccessTokenStorageManager: MapboxAccessTokenStorageManagerType
) : MapContainerViewModel(
    groupStorageManager,
    teamManager,
    locationSharingManager,
    userManager,
    nameProvider,
    userDataGenerator,
    locationServiceController,
    coroutineContextProvider
) {
    fun setupTileSource(map: MapView) {
        map.setTileSource(CustomMapboxTileSource(mapboxAccessTokenStorageManager.requireToken()))
    }

    suspend fun locationString(coordinates: Coordinates): String? = withContext(coroutineContextProvider.IO) { mapboxGeocodingManager.reverseGeocoding(coordinates) }
}