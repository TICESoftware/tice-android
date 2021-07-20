package tice.ui.viewModels

import androidx.lifecycle.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import tice.managers.*
import tice.managers.group.TeamManagerType
import tice.managers.storageManagers.ChatStorageManagerType
import tice.managers.storageManagers.GroupStorageManagerType
import tice.models.*
import tice.models.chat.Message
import tice.models.chat.Message.TextMessage
import tice.ui.models.GroupNameData
import tice.ui.models.ShortChatMessages
import tice.ui.models.TeamLocationSharingState
import tice.utility.getLogger
import tice.utility.provider.CoroutineContextProviderType
import tice.utility.provider.NameProviderType
import tice.utility.provider.UserDataGeneratorType
import java.util.*
import javax.inject.Inject
import kotlin.math.cos

class MapViewModel @Inject constructor(
    private val locationManager: LocationManagerType,
    private val groupStorageManager: GroupStorageManagerType,
    private val teamManager: TeamManagerType,
    private val locationSharingManager: LocationSharingManagerType,
    private val userManager: UserManagerType,
    private val coroutineContextProvider: CoroutineContextProviderType,
    private val nameProvider: NameProviderType,
    private val userDataGenerator: UserDataGeneratorType,
    private val settingsManager: SettingsManagerType,
    private val chatStorageManager: ChatStorageManagerType,
    private val signedInUserManager: SignedInUserManagerType
) : ViewModel() {
    private val logger by getLogger()

    private lateinit var teamId: GroupId

    private val _teamName = MediatorLiveData<GroupNameData>()
    val teamName: LiveData<GroupNameData>
        get() = _teamName

    private val _userLocationUpdate = MutableLiveData<UserLocationUpdate>()
    val userLocationUpdate: LiveData<UserLocationUpdate>
        get() = _userLocationUpdate

    private val _meetingPoint = MediatorLiveData<Location?>()
    val meetingPoint: LiveData<Location?>
        get() = _meetingPoint

    private val _meetUpButtonState = MediatorLiveData<MeetUpButtonState>()
    val meetUpButtonState: LiveData<MeetUpButtonState>
        get() = _meetUpButtonState

    private val _event = MutableSharedFlow<MapEvent>()
    val event: SharedFlow<MapEvent>
        get() = _event

    val ownLocationUpdateFlow
        get() = locationManager.getOwnLocationUpdateFlow()

    var cameraLocation: CameraSettings
        get() = settingsManager.cameraSettings
        set(value) {
            settingsManager.cameraSettings = value
        }

    var currentUserId: UserId? = null

    private val _userInfo = MutableLiveData<UserInfoUpdate>()
    val userInfo: LiveData<UserInfoUpdate>
        get() = _userInfo

    private val _usersInMeetup = MutableLiveData<Set<UserId>>()
    val usersInMeetup: LiveData<Set<UserId>>
        get() = _usersInMeetup

    val unreadCount: LiveData<Int>
        get() = chatStorageManager.unreadMessageCountLiveData(teamId)

    private val _rectFittingEnabled = MutableLiveData(false)
    val rectFittingEnabled: LiveData<Boolean>
        get() = _rectFittingEnabled

    var setup = false

    fun setupData(teamId: GroupId) {
        if (setup) return

        this.teamId = teamId

        viewModelScope.launch(coroutineContextProvider.IO) {
            try {
                groupStorageManager.loadTeam(teamId)?.let { teamManager.reload(it) }
            } catch (e: Exception) {
                logger.error("Reloading team failed", e)
            }
        }

        _meetingPoint.addSource(groupStorageManager.getTeamObservable(teamId)) { team ->
            viewModelScope.launch(coroutineContextProvider.IO) {
                team?.let {
                    _meetingPoint.postValue(team.meetingPoint)
                }
            }
        }

        _meetUpButtonState.addSource(groupStorageManager.getTeamObservable(teamId)) { team ->
            viewModelScope.launch(coroutineContextProvider.IO) {
                locationSharingManager.getFlowOfAllLocationSharingStatesOfGroup(teamId)
                    .collect { list ->
                        when (val locationSharingState = teamLocationSharingState(list)) {
                            is TeamLocationSharingState.None -> {
                                _meetUpButtonState.postValue(MeetUpButtonState.None)
                            }
                            is TeamLocationSharingState.WeShareLocation -> {
                                _meetUpButtonState.postValue(MeetUpButtonState.WeShareLocation)
                            }
                            is TeamLocationSharingState.TheyShareLocation -> {
                                _meetUpButtonState.postValue(MeetUpButtonState.TheyShareLocation)
                            }
                            is TeamLocationSharingState.OneSharesLocation -> {
                                _meetUpButtonState.postValue(MeetUpButtonState.OneSharesLocation(locationSharingState.userName))
                            }
                        }
                    }
            }
        }

        _teamName.addSource(groupStorageManager.getTeamObservable(teamId)) { team ->
            logger.debug("Updating team name.")

            viewModelScope.launch(coroutineContextProvider.IO) {
                team ?: return@launch _event.emit(MapEvent.NoTeam)
                _teamName.postValue(nameProvider.getTeamName(team))
            }
        }

        viewModelScope.launch(coroutineContextProvider.Default) {
            val userIds = groupStorageManager.loadMembershipsOfGroup(teamId).map { it.userId }.toSet()

            locationSharingManager.getLocationUpdateFlow(userIds, teamId).collect { userLocation ->
                logger.debug("Process userLocation update for user ${userLocation.userId}")
                if (!userIds.contains(userLocation.userId)) return@collect
                logger.debug("Updating userLocation for user ${userLocation.userId}")

                userManager.getUser(userLocation.userId)?.let {
                    val displayName = nameProvider.getUserName(it)
                    val shortName = nameProvider.getShortName(displayName)
                    val color = userDataGenerator.generateColor(it.userId)
                    _userLocationUpdate.postValue(
                        UserLocationUpdate(
                            it.userId,
                            displayName,
                            shortName,
                            color,
                            userLocation.location.latitude,
                            userLocation.location.longitude
                        )
                    )

                    if (currentUserId == userLocation.userId) {
                        _userInfo.postValue(
                            UserInfoUpdate(
                                it.userId,
                                displayName,
                                userLocation.location.latitude,
                                userLocation.location.longitude,
                                userLocation.location.timestamp
                            )
                        )
                    }
                }
            }
        }

        viewModelScope.launch(coroutineContextProvider.IO) {
            chatStorageManager.getLastUnreadMessagesFlow(teamId).drop(1).collect { message ->
                message ?: return@collect

                val sender = userManager.getUser(message.senderId) ?: throw Exception()
                val senderColor = userDataGenerator.generateColor(message.senderId)
                val senderNameShort = nameProvider.getShortName(nameProvider.getUserName(sender))

                val text = when (message) {
                    is TextMessage -> message.text
                    is Message.MetaMessage -> message.text
                    else -> throw Exception("Unhandled MessageType")
                }
                _event.emit(
                    MapEvent.NewMessage(
                        ShortChatMessages.TextMessage(
                            senderColor,
                            senderNameShort,
                            text
                        )
                    )
                )
            }
        }
        setup = true
    }

    fun setMeetingPoint(position: LatLng) {
        viewModelScope.launch(coroutineContextProvider.IO) {
            try {
                val team = groupStorageManager.loadTeam(teamId) ?: throw Exception("No running Meetup")
                teamManager.setMeetingPoint(position, team)
                _meetingPoint.postValue(_meetingPoint.value)
            } catch (e: Exception) {
                logger.error(e.message, e)
                _event.emit(MapEvent.ErrorEvent.MarkerError)
            }
        }
    }

    fun triggerMeetupAction() {
        if (_meetUpButtonState.value == MeetUpButtonState.Loading) {
            return
        }

        viewModelScope.launch(coroutineContextProvider.IO) {
            val team = groupStorageManager.loadTeam(teamId) ?: run {
                logger.error("Could not load team.")
                return@launch
            }

            val locationSharingStates = locationSharingManager.getAllLocationSharingStatesOfGroup(teamId)

            when (teamLocationSharingState(locationSharingStates)) {
                is TeamLocationSharingState.OneSharesLocation -> {
                    team.let { teamManager.setLocationSharing(it, true) }
                }
                is TeamLocationSharingState.TheyShareLocation -> {
                    team.let { teamManager.setLocationSharing(it, true) }
                }
                is TeamLocationSharingState.WeShareLocation -> {
                    team.let { teamManager.setLocationSharing(it, false) }
                }
                is TeamLocationSharingState.None -> {
                    try {
                        _meetUpButtonState.postValue(MeetUpButtonState.Loading)
                        team.let { teamManager.setLocationSharing(it, true) }
                        _event.emit(MapEvent.MeetupCreated)
                    } catch (e: Exception) {
                        logger.error(e.message ?: "Creating meetup failed", e)
                        _event.emit(MapEvent.ErrorEvent.MeetupError)
                        _meetUpButtonState.postValue(MeetUpButtonState.None)
                    }
                }
            }
        }
    }

    fun initUserData(id: UserId) {
        currentUserId = id

        viewModelScope.launch(coroutineContextProvider.IO) {
            val lastLocation = locationSharingManager.lastLocation(UserGroupIds(id, teamId))

            lastLocation?.let {
                userManager.getUser(id)?.let {
                    val displayName = nameProvider.getUserName(it)
                    _userInfo.postValue(
                        UserInfoUpdate(
                            it.userId,
                            displayName,
                            lastLocation.latitude,
                            lastLocation.longitude,
                            lastLocation.timestamp
                        )
                    )
                }
            }
        }
    }

    fun onRectFittingClicked() {
        val rectFittingEnabled = rectFittingEnabled.value?.let { !it } ?: false
        _rectFittingEnabled.postValue(rectFittingEnabled)
    }

    fun disableRectFitting() {
        _rectFittingEnabled.postValue(false)
    }

    fun getCoordinate(location: Location): LatLngBounds {
        val latRadian = Math.toRadians(location.latitude)
        val degLatKm = 110.574235
        val degLongKm = 110.572833 * cos(latRadian)
        val deltaLat: Double = 5000 / 1000.0 / degLatKm
        val deltaLong: Double = 5000 / 1000.0 / degLongKm

        val minLat: Double = location.latitude - deltaLat
        val minLong: Double = location.longitude - deltaLong
        val maxLat: Double = location.latitude + deltaLat
        val maxLong: Double = location.longitude + deltaLong

        return LatLngBounds(LatLng(minLat, minLong), LatLng(maxLat, maxLong))
    }

    private suspend fun teamLocationSharingState(locationSharingStateList: List<LocationSharingState>): TeamLocationSharingState {
        val sharingEnabled = locationSharingStateList.filter { it.sharingEnabled }.map {
            it.userId to it
        }.toMap()

        _usersInMeetup.postValue(sharingEnabled.keys)
        val signedInUserId = signedInUserManager.signedInUser.userId

        if (sharingEnabled.isEmpty()) {
            return TeamLocationSharingState.None
        }
        if (sharingEnabled.containsKey(signedInUserId)) {
            return TeamLocationSharingState.WeShareLocation
        }
        if (sharingEnabled.size == 1) {
            userManager.getUser(sharingEnabled.keys.first())?.let { user ->
                user.publicName?.let {
                    return TeamLocationSharingState.OneSharesLocation(it)
                }
            }
        }
        return TeamLocationSharingState.TheyShareLocation
    }

    data class UserLocationUpdate(
        val userId: UserId,
        val displayName: String,
        val shortName: String,
        val color: Int,
        val latitude: Double,
        val longitude: Double
    )

    data class UserInfoUpdate(
        val userId: UserId,
        val displayName: String,
        val latitude: Double,
        val longitude: Double,
        val timestamp: Date
    )

    sealed class MeetUpButtonState {
        object None : MeetUpButtonState()
        object WeShareLocation : MeetUpButtonState()
        object Loading : MeetUpButtonState()
        object TheyShareLocation : MeetUpButtonState()
        data class OneSharesLocation(val userName: String) : MeetUpButtonState()
    }

    sealed class MapEvent {
        object NoTeam : MapEvent()

        data class NewMessage(val shortChatMessages: ShortChatMessages) : MapEvent()

        object MeetupCreated : MapEvent()
        sealed class ErrorEvent() : MapEvent() {
            object MarkerError : ErrorEvent()
            object MeetupError : ErrorEvent()
            object Error : ErrorEvent()
        }
    }
}
