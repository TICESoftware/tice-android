package tice.ui.viewModels

import android.util.Base64
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import tice.exceptions.TeamManagerException
import tice.managers.SignedInUserManagerType
import tice.managers.group.TeamManagerType
import tice.managers.storageManagers.GroupStorageManagerType
import tice.models.GroupId
import tice.models.Team
import tice.ui.models.GroupNameData
import tice.utility.getLogger
import tice.utility.provider.CoroutineContextProviderType
import tice.utility.provider.NameProviderType
import java.util.*
import javax.inject.Inject

class JoinTeamViewModel @Inject constructor(
    private val coroutineContextProvider: CoroutineContextProviderType,
    private val teamManager: TeamManagerType,
    private val groupStorageManager: GroupStorageManagerType,
    private val signedInUserManager: SignedInUserManagerType,
    private val nameProvider: NameProviderType
) : ViewModel() {
    private val logger by getLogger()

    private val _state = MutableLiveData<JoinTeamState>(JoinTeamState.Loading)
    val state: LiveData<JoinTeamState>
        get() = _state

    private val _event = MutableSharedFlow<JoinTeamEvent>()
    val event: SharedFlow<JoinTeamEvent>
        get() = _event.onSubscription { if (!signedInUserManager.signedIn()) emit(JoinTeamEvent.NotSignedIn) }

    private lateinit var team: Team
    private var latestTeamName: GroupNameData = GroupNameData.TeamName("")

    fun fetchGroup(groupIdString: String, groupKeyString: String) {
        viewModelScope.launch(coroutineContextProvider.IO) {
            try {
                val groupId = UUID.fromString(groupIdString)
                val groupKey = Base64.decode(groupKeyString, Base64.URL_SAFE)

                team = teamManager.getOrFetchTeam(groupId, groupKey)

                if (groupStorageManager.isMember(signedInUserManager.signedInUser.userId, team.groupId)) {
                    _event.emit(JoinTeamEvent.AlreadyMember(team.groupId))
                    _state.postValue(JoinTeamState.Idle)
                    return@launch
                }

                latestTeamName = nameProvider.getTeamName(team)
                _state.postValue(JoinTeamState.TeamAvailable(latestTeamName))
            } catch (e: Exception) {
                logger.error("URL processing failed.", e)
                _event.emit(JoinTeamEvent.ErrorEvent.TeamURLError)
                _state.postValue(JoinTeamState.TeamNotFound)
            }
        }
    }

    fun joinTeam() {
        viewModelScope.launch(coroutineContextProvider.IO) {
            try {
                teamManager.join(team)
                _event.emit(JoinTeamEvent.JoinedTeam)
            } catch (e: TeamManagerException.MemberLimitExceeded) {
                logger.info("Cannot join team because the member limit has been reached.")
                _event.emit(JoinTeamEvent.ErrorEvent.MemberLimitExceeded)
            } catch (e: Exception) {
                logger.error("Joining group failed.", e)
                _event.emit(JoinTeamEvent.ErrorEvent.Error)
            }
        }
    }

    sealed class JoinTeamState {
        object TeamNotFound : JoinTeamState()
        object Loading : JoinTeamState()
        object Idle : JoinTeamState()
        data class TeamAvailable(val groupName: GroupNameData) : JoinTeamState()
    }

    sealed class JoinTeamEvent {
        object NotSignedIn : JoinTeamEvent()
        object JoinedTeam : JoinTeamEvent()
        data class AlreadyMember(val groupId: GroupId) : JoinTeamEvent()
        sealed class ErrorEvent() : JoinTeamEvent() {
            object JoinTeamError : ErrorEvent()
            object TeamURLError : ErrorEvent()
            object MemberLimitExceeded : ErrorEvent()
            object Error : ErrorEvent()
        }
    }
}
