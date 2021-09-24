package tice.ui.viewModels

import android.util.Base64
import androidx.lifecycle.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import tice.managers.LocationSharingManagerType
import tice.managers.SignedInUserManagerType
import tice.managers.group.TeamManagerType
import tice.managers.storageManagers.ChatStorageManagerType
import tice.managers.storageManagers.GroupStorageManagerType
import tice.models.GroupId
import tice.models.LocationSharingState
import tice.models.Team
import tice.models.chat.Message
import tice.ui.models.TeamData
import tice.ui.models.TeamLocationSharingState
import tice.utility.getLogger
import tice.utility.provider.CoroutineContextProviderType
import tice.utility.provider.NameProviderType
import java.text.DateFormat
import java.util.*
import javax.inject.Inject

class TeamListViewModel @Inject constructor(
    private val teamManager: TeamManagerType,
    private val locationSharingManager: LocationSharingManagerType,
    private val groupStorageManager: GroupStorageManagerType,
    private val signedInUserManager: SignedInUserManagerType,
    private val coroutineContextProvider: CoroutineContextProviderType,
    private val nameProvider: NameProviderType,
    private val chatStorageManager: ChatStorageManagerType
) : ViewModel() {
    private val logger by getLogger()

    private val _teamData = MediatorLiveData<List<TeamData>>()
    val teamData: LiveData<List<TeamData>>
        get() = _teamData

    private val _state = MutableLiveData<ScreenState>()
    val state: LiveData<ScreenState>
        get() = _state

    private val _event = MutableSharedFlow<TeamListEvent>()
    val event: SharedFlow<TeamListEvent>
        get() = _event

    private var unreadMessages = mapOf<GroupId, MessageIndicator>()
    private var teamList = listOf<Team>()

    private var setupCompleted = false

    fun setupData() {
        _teamData.addSource(groupStorageManager.teams) {
            teamList = it
            viewModelScope.launch(coroutineContextProvider.IO) {
                _teamData.postValue(teamDataFromTeams(it))
            }

            if (teamList.isEmpty()) _state.postValue(ScreenState.EmptyVisuals)
            else {
                _state.postValue(ScreenState.TeamList)

                viewModelScope.launch(coroutineContextProvider.IO) {
                    teamList.forEach {
                        locationSharingManager.getFlowOfAllLocationSharingStatesOfGroup(it.groupId).collect {
                            _teamData.postValue(teamDataFromTeams(teamList))
                        }
                    }
                }
            }
        }

        _teamData.addSource(groupStorageManager.getMeetupIdParticipating(signedInUserManager.signedInUser.userId)) {
            viewModelScope.launch(coroutineContextProvider.IO) {
                _teamData.postValue(teamDataFromTeams(teamList))
            }
        }

        _teamData.addSource(chatStorageManager.lastMessagePerGroup()) {
            viewModelScope.launch(coroutineContextProvider.IO) {
                unreadMessages = processUnreadMessages(it)
                _teamData.postValue(teamDataFromTeams(teamList))
            }
        }

        setupCompleted = true
    }

    private suspend fun teamDataFromTeams(teams: List<Team>): List<TeamData> {
        return teams.mapNotNull { team ->
            val membership = groupStorageManager.loadNullableMembership(signedInUserManager.signedInUser.userId, team.groupId) ?: return@mapNotNull null

            val members = groupStorageManager.members(team.groupId).toList()
            val isAdmin = membership.admin
            val memberNames = members
                .filter { it.user.userId != signedInUserManager.signedInUser.userId }
                .map { nameProvider.getUserName(it.user) }
            val locationSharingState = locationSharingManager.getAllLocationSharingStatesOfGroup(team.groupId).let {
                teamLocationSharingState(it)
            }
            val messageIndicator = unreadMessages.get(team.groupId)

            TeamData(
                nameProvider.getTeamName(team),
                team.groupId,
                memberNames,
                isAdmin,
                locationSharingState,
                messageIndicator
            )
        }
    }

    fun deleteGroup(teamId: GroupId) {
        viewModelScope.launch(coroutineContextProvider.IO) {
            try {
                val team = groupStorageManager.loadTeam(teamId) ?: return@launch _event.emit(TeamListEvent.ErrorEvent.NotFoundError)
                teamManager.delete(team)
            } catch (e: Exception) {
                logger.error("Deleting group failed", e)
                _event.emit(TeamListEvent.ErrorEvent.DeleteError)
            }
        }
    }

    fun leaveGroup(teamId: GroupId) {
        viewModelScope.launch(coroutineContextProvider.IO) {
            try {
                val team = groupStorageManager.loadTeam(teamId) ?: return@launch _event.emit(TeamListEvent.ErrorEvent.NotFoundError)
                teamManager.leave(team)
            } catch (e: Exception) {
                logger.error("Leaving group failed", e)
                _event.emit(TeamListEvent.ErrorEvent.LeaveError)
            }
        }
    }

    // For development purposes only
    fun joinTeam(joinString: String) {
        viewModelScope.launch(coroutineContextProvider.IO) {
            val url = joinString.split("/").last().split("#")
            val team = teamManager.getOrFetchTeam(GroupId.fromString(url[0]), Base64.decode(url[1], Base64.URL_SAFE))
            teamManager.join(team)
        }
    }

    private fun teamLocationSharingState(locationSharingStateList: List<LocationSharingState>): TeamLocationSharingState {
        val sharingEnabled = locationSharingStateList.filter { it.sharingEnabled }.map {
            it.userId to it
        }.toMap()

        val signedInUserId = signedInUserManager.signedInUser.userId

        if (sharingEnabled.isEmpty()) {
            return TeamLocationSharingState.None
        }
        return if (sharingEnabled.containsKey(signedInUserId)) {
            TeamLocationSharingState.WeShareLocation
        } else {
            TeamLocationSharingState.TheyShareLocation
        }
    }

    private fun processUnreadMessages(data: Map<GroupId, Message>): Map<GroupId, MessageIndicator> {
        return data.mapValues {
            val date = createDateFormat(it.value.date)
            val unReadMessages = !it.value.read
            logger.debug("Last message in group is read: ${it.value.read} - $it")

            MessageIndicator(date, unReadMessages)
        }
    }

    private fun createDateFormat(date: Date): String {
        val format = DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault())
        return format.format(date)
    }

    sealed class ScreenState {
        object EmptyVisuals : ScreenState()
        object TeamList : ScreenState()
    }

    sealed class TeamListEvent {
        sealed class ErrorEvent() : TeamListEvent() {
            object NotFoundError : ErrorEvent()
            object DeleteError : ErrorEvent()
            object LeaveError : ErrorEvent()
            object Error : ErrorEvent()
        }
    }

    data class MessageIndicator(
        val date: String,
        val unread: Boolean
    )
}
