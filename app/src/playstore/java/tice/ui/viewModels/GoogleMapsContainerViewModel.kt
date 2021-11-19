package tice.ui.viewModels

import com.google.android.libraries.places.api.Places
import tice.managers.LocationServiceController
import tice.managers.LocationServiceControllerType
import tice.managers.LocationSharingManagerType
import tice.managers.UserManagerType
import tice.managers.group.TeamManagerType
import tice.managers.storageManagers.GroupStorageManagerType
import tice.utility.provider.CoroutineContextProviderType
import tice.utility.provider.NameProviderType
import tice.utility.provider.UserDataGeneratorType
import javax.inject.Inject

class GoogleMapsContainerViewModel @Inject constructor(
    groupStorageManager: GroupStorageManagerType,
    teamManager: TeamManagerType,
    locationSharingManager: LocationSharingManagerType,
    userManager: UserManagerType,
    nameProvider: NameProviderType,
    userDataGenerator: UserDataGeneratorType,
    locationServiceController: LocationServiceControllerType,
    coroutineContextProvider: CoroutineContextProviderType
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

    val shouldShowSearchAutocompleteFragment: Boolean
        get() = Places.isInitialized()
}
