package tice.ui.viewModels

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.mapbox.maps.ResourceOptionsManager
import com.mapbox.search.MapboxSearchSdk
import com.mapbox.search.location.DefaultLocationProvider
import tice.managers.LocationSharingManagerType
import tice.managers.UserManagerType
import tice.managers.group.TeamManagerType
import tice.managers.storageManagers.GroupStorageManagerType
import tice.utility.provider.CoroutineContextProviderType
import tice.utility.provider.NameProviderType
import tice.utility.provider.UserDataGeneratorType
import javax.inject.Inject
import javax.inject.Named

class MapboxMapContainerViewModel @Inject constructor(
    groupStorageManager: GroupStorageManagerType,
    teamManager: TeamManagerType,
    locationSharingManager: LocationSharingManagerType,
    userManager: UserManagerType,
    nameProvider: NameProviderType,
    userDataGenerator: UserDataGeneratorType,
    coroutineContextProvider: CoroutineContextProviderType,
    @Named("MAPBOX_SECRET_TOKEN") val mapboxSecretToken: String
) : MapContainerViewModel(
    groupStorageManager,
    teamManager,
    locationSharingManager,
    userManager,
    nameProvider,
    userDataGenerator,
    coroutineContextProvider) {

    fun initMapboxSdk(activity: Activity) {
        ResourceOptionsManager.getDefault(activity, mapboxSecretToken)
    }
}
