package tice.ui.viewModels

import androidx.lifecycle.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import tice.managers.SignedInUserManagerType
import tice.managers.group.TeamManagerType
import tice.managers.storageManagers.GroupStorageManagerType
import tice.managers.storageManagers.LocationSharingStorageManagerType
import tice.models.GroupId
import tice.models.Team
import tice.ui.models.GroupNameData
import tice.ui.models.MemberData
import tice.utility.getLogger
import tice.utility.provider.CoroutineContextProviderType
import tice.utility.provider.NameProviderType
import tice.utility.ui.verifyNameString
import javax.inject.Inject

class TeamInfoViewModel @Inject constructor(
    private val coroutineContextProvider: CoroutineContextProviderType,
    private val signedInUserManager: SignedInUserManagerType,
    private val locationSharingStorageManager: LocationSharingStorageManagerType,
    private val teamManager: TeamManagerType,
    private val groupStorageManager: GroupStorageManagerType,
    private val nameProvider: NameProviderType
) : ViewModel() {
    private val logger by getLogger()

    private val _state = MutableLiveData<TeamInfoState>(TeamInfoState.Idle)
    val state: LiveData<TeamInfoState>
        get() = _state

    private var _data = MediatorLiveData<TeamInfoData>()
    val data: LiveData<TeamInfoData>
        get() = _data

    private val _event = MutableSharedFlow<TeamInfoEvent>()
    val event: SharedFlow<TeamInfoEvent>
        get() = _event

    private lateinit var teamId: GroupId
    private var isSharingLocation: Boolean = false

    fun setupData(teamId: GroupId) {
        this.teamId = teamId
        _data.addSource(teamManager.getTeamLiveData(teamId)) { team ->
            viewModelScope.launch(coroutineContextProvider.IO) {
                team ?: return@launch _event.emit(TeamInfoEvent.NoTeam)

                _data.postValue(getTeamInfoData(team))
            }
        }

        viewModelScope.launch(coroutineContextProvider.Default) {
            locationSharingStorageManager.getStateFlowOfUserInGroup(
                signedInUserManager.signedInUser.userId,
                teamId
            ).collect { locationSharingState ->
                isSharingLocation = locationSharingState?.sharingEnabled == true
                groupStorageManager.loadTeam(teamId)?.let {
                    _data.postValue(getTeamInfoData(it))
                }
            }
        }
    }

    private suspend fun getTeamInfoData(team: Team): TeamInfoData {
        val userId = signedInUserManager.signedInUser.userId
        return TeamInfoData(
            nameProvider.getTeamName(team),
            groupStorageManager.loadMembership(userId, team.groupId).admin,
            isSharingLocation,
            groupStorageManager.members(team.groupId)
                .map { MemberData(nameProvider.getUserName(it.user), it.membership.admin) },
            team.shareURL.toString()
        )
    }

    fun updateGroup(newName: String) {
        viewModelScope.launch(coroutineContextProvider.IO) {
            if (_state.value != TeamInfoState.Idle) return@launch
            else _state.postValue(TeamInfoState.Loading)

            try {
                val teamName = verifyNameString(newName)
                val team = groupStorageManager.loadTeam(teamId) ?: return@launch _event.emit(
                    TeamInfoEvent.NoTeam
                )

                teamManager.setTeamName(team, teamName)
            } catch (e: Exception) {
                logger.error("Updating settings failed", e)
                _event.emit(TeamInfoEvent.ErrorEvent.SettingsError)
            }

            _state.postValue(TeamInfoState.Idle)
        }
    }

    fun leaveGroup() {
        viewModelScope.launch(coroutineContextProvider.IO) {
            if (_state.value != TeamInfoState.Idle) return@launch
            else _state.postValue(TeamInfoState.Loading)

            try {
                val team = groupStorageManager.loadTeam(teamId) ?: return@launch _event.emit(
                    TeamInfoEvent.NoTeam
                )

                teamManager.leave(team)
            } catch (e: Exception) {
                logger.error("Leaving group failed", e)
                _event.emit(TeamInfoEvent.ErrorEvent.LeaveGroupError)
            }

            _state.postValue(TeamInfoState.Idle)
        }
    }

    fun deleteGroup() {
        viewModelScope.launch(coroutineContextProvider.IO) {
            if (_state.value != TeamInfoState.Idle) return@launch
            else _state.postValue(TeamInfoState.Loading)

            try {
                val team = groupStorageManager.loadTeam(teamId) ?: return@launch _event.emit(
                    TeamInfoEvent.NoTeam
                )

                teamManager.delete(team)
            } catch (e: Exception) {
                logger.error("Deleting group failed", e)
                _event.emit(TeamInfoEvent.ErrorEvent.DeleteGroupError)
            }

            _state.postValue(TeamInfoState.Idle)
        }
    }

    fun triggerLocationSharingAction(enable: Boolean) {
        viewModelScope.launch(coroutineContextProvider.IO) {
            if (_state.value != TeamInfoState.Idle) return@launch
            else _state.postValue(TeamInfoState.Loading)

            try {
                val team = groupStorageManager.loadTeam(teamId) ?: return@launch _event.emit(
                    TeamInfoEvent.NoTeam
                )

                teamManager.setLocationSharing(team, enable)
            } catch (e: Exception) {
                logger.debug("Share location action failed.")
                _event.emit(TeamInfoEvent.ErrorEvent.ShareLocationError)
            }

            _state.postValue(TeamInfoState.Idle)
        }
    }

    data class TeamInfoData(
        val name: GroupNameData,
        val userIsAdmin: Boolean = false,
        val isSharingLocation: Boolean = false,
        val groupMember: List<MemberData> = emptyList(),
        val groupUrl: String? = null
    )

    sealed class TeamInfoState {
        object Idle : TeamInfoState()
        object Loading : TeamInfoState()
    }

    sealed class TeamInfoEvent {
        object NoTeam : TeamInfoEvent()

        sealed class ErrorEvent() : TeamInfoEvent() {
            object ShareLocationError : ErrorEvent()
            object SettingsError : ErrorEvent()
            object DeleteGroupError : ErrorEvent()
            object LeaveGroupError : ErrorEvent()
            object Error : ErrorEvent()
        }
    }
}
