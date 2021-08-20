package tice.ui.viewModels

import android.text.format.DateFormat
import androidx.lifecycle.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import tice.managers.LocationManagerType
import tice.managers.LocationSharingManagerType
import tice.managers.SettingsManagerType
import tice.managers.UserManagerType
import tice.managers.group.TeamManagerType
import tice.managers.storageManagers.GroupStorageManagerType
import tice.models.*
import tice.utility.getLogger
import tice.utility.provider.CoroutineContextProviderType
import tice.utility.provider.NameProviderType
import tice.utility.provider.UserDataGeneratorType
import tice.utility.uuidString
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Named

class GoogleMapsContainerViewModel @Inject constructor(
    groupStorageManager: GroupStorageManagerType,
    teamManager: TeamManagerType,
    locationSharingManager: LocationSharingManagerType,
    userManager: UserManagerType,
    nameProvider: NameProviderType,
    userDataGenerator: UserDataGeneratorType,
    coroutineContextProvider: CoroutineContextProviderType
): MapContainerViewModel(
    groupStorageManager,
    teamManager,
    locationSharingManager,
    userManager,
    nameProvider,
    userDataGenerator,
    coroutineContextProvider) {

    val shouldShowSearchAutocompleteFragment: Boolean
        get() = Places.isInitialized()
}