package tice.ui.viewModels

import android.text.format.DateFormat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import tice.managers.LocationServiceController
import tice.managers.LocationServiceControllerType
import tice.managers.LocationSharingManagerType
import tice.managers.UserManagerType
import tice.managers.group.TeamManagerType
import tice.managers.storageManagers.GroupStorageManagerType
import tice.models.*
import tice.utility.getLogger
import tice.utility.provider.CoroutineContextProviderType
import tice.utility.provider.NameProviderType
import tice.utility.provider.UserDataGeneratorType
import java.text.SimpleDateFormat
import java.util.*

open class MapContainerViewModel(
    private val groupStorageManager: GroupStorageManagerType,
    private val teamManager: TeamManagerType,
    locationSharingManager: LocationSharingManagerType,
    private val userManager: UserManagerType,
    private val nameProvider: NameProviderType,
    private val userDataGenerator: UserDataGeneratorType,
    private val locationServiceController: LocationServiceControllerType,
    private val coroutineContextProvider: CoroutineContextProviderType,
) : ViewModel() {
    private val logger by getLogger()

    lateinit var teamId: GroupId

    private var memberIds: Set<UserId> = setOf()

    val meetingPointFlow: SharedFlow<Location?>
        get() = groupStorageManager.getMeetingPointFlow(teamId).shareIn(viewModelScope, SharingStarted.WhileSubscribed())

    val memberLocationUpdateFlow: SharedFlow<UserLocation> =
        locationSharingManager.memberLocationFlow
            .filter { userLocation ->
                groupStorageManager.members(teamId).any { it.user.userId == userLocation.userId }
            }
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed())

    val removedMembersFlow: SharedFlow<UserId>
        get() = groupStorageManager.getMembershipUserIdFlowOfGroup(teamId)
            .mapNotNull { userIds ->
                val actualMemberIds = userIds.toSet()
                memberIds.minus(actualMemberIds).firstOrNull()
            }
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed())

    fun handleGrantedLocationPermission() {
        locationServiceController.requestStartingLocationService()
    }

    suspend fun getMemberNames(userId: UserId): Pair<String, String>? =
        userManager.getUser(userId)?.let {
            val displayName = nameProvider.getUserName(it)
            val shortName = nameProvider.getShortName(displayName)

            return Pair(displayName, shortName)
        }

    fun colorForMember(userId: UserId): Int = userDataGenerator.generateColor(userId)

    fun dateString(timestamp: Date): String {
        val format = DateFormat.getBestDateTimePattern(Locale.getDefault(), "jj:mm:ss")
        return SimpleDateFormat(format, Locale.getDefault()).apply {
            TimeZone.getDefault()
            applyLocalizedPattern(format)
        }.format(timestamp)
    }

    fun setMeetingPoint(position: Coordinates) {
        viewModelScope.launch(coroutineContextProvider.IO) {
            try {
                val team = groupStorageManager.loadTeam(teamId) ?: throw Exception("No running Meetup")
                teamManager.setMeetingPoint(position, team)
            } catch (e: Exception) {
                logger.error(e.message, e)
            }
        }
    }
}
