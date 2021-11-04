package tice.ui.viewModels

import org.osmdroid.views.MapView
import tice.managers.LocationSharingManagerType
import tice.managers.UserManagerType
import tice.managers.group.TeamManagerType
import tice.managers.storageManagers.GroupStorageManagerType
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
    coroutineContextProvider: CoroutineContextProviderType,
    @Named("MAPBOX_ACCESS_TOKEN") private val mapboxAccessToken: String
) : MapContainerViewModel(
    groupStorageManager,
    teamManager,
    locationSharingManager,
    userManager,
    nameProvider,
    userDataGenerator,
    coroutineContextProvider
) {
    fun setupTileSource(map: MapView) {
        map.setTileSource(CustomMapboxTileSource(mapboxAccessToken))
    }

    suspend fun locationString(coordinates: Coordinates): String = "${coordinates.latitude}, ${coordinates.longitude}"
}