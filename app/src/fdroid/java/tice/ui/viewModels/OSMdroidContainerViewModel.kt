package tice.ui.viewModels

import android.content.Context
import org.osmdroid.tileprovider.tilesource.MapBoxTileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex
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
    @Named("MAPBOX_PUBLIC_TOKEN") private val mapboxPublicToken: String
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
        map.setTileSource(CustomMapboxTileSource(mapboxPublicToken))
    }

    suspend fun locationString(coordinates: Coordinates): String = "${coordinates.latitude}, ${coordinates.longitude}"
}