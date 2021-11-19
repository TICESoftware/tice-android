package tice.ui.viewModels

import android.content.Context
import com.mapbox.maps.ResourceOptionsManager
import tice.managers.LocationServiceControllerType
import tice.managers.LocationSharingManagerType
import tice.managers.UserManagerType
import tice.managers.group.TeamManagerType
import tice.managers.storageManagers.GroupStorageManagerType
import tice.utility.provider.CoroutineContextProviderType
import tice.utility.provider.NameProviderType
import tice.utility.provider.UserDataGeneratorType
import javax.inject.Inject
import javax.inject.Named

class MapboxContainerViewModel @Inject constructor(
    groupStorageManager: GroupStorageManagerType,
    teamManager: TeamManagerType,
    locationSharingManager: LocationSharingManagerType,
    userManager: UserManagerType,
    nameProvider: NameProviderType,
    userDataGenerator: UserDataGeneratorType,
    coroutineContextProvider: CoroutineContextProviderType,
    locationServiceController: LocationServiceControllerType,
    @Named("MAPBOX_ACCESS_TOKEN") private val mapboxAccessToken: String
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
    fun initMapbox(context: Context) {
        ResourceOptionsManager.getDefault(context, mapboxAccessToken)
    }
}
