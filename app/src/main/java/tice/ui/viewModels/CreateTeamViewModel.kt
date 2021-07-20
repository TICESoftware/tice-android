package tice.ui.viewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import tice.managers.group.TeamManagerType
import tice.models.GroupId
import tice.models.JoinMode
import tice.models.PermissionMode
import tice.utility.getLogger
import tice.utility.provider.CoroutineContextProviderType
import tice.utility.provider.NameProviderType
import tice.utility.ui.verifyNameString
import javax.inject.Inject

class CreateTeamViewModel @Inject constructor(
    private val coroutineContextProvider: CoroutineContextProviderType,
    private val teamManager: TeamManagerType,
    private val nameProvider: NameProviderType
) : ViewModel() {
    private val logger by getLogger()

    private val _state = MutableLiveData<CreateTeamState>(CreateTeamState.Idle(nameProvider.getSignedInUserTeamName()))
    val state: LiveData<CreateTeamState>
        get() = _state

    private val _event = MutableSharedFlow<CreateTeamEvent>()
    val event: SharedFlow<CreateTeamEvent>
        get() = _event

    fun createGroup(groupName: String) {
        viewModelScope.launch(coroutineContextProvider.IO) {
            if (_state.value !is CreateTeamState.Idle) return@launch
            else _state.postValue(CreateTeamState.Loading)

            val nameOfGroup = verifyNameString(groupName)

            try {
                val team = teamManager.createTeam(
                    JoinMode.Open,
                    PermissionMode.Everyone,
                    nameOfGroup,
                    true,
                    null
                )
                _event.emit(CreateTeamEvent.Done(team.groupId))
            } catch (e: Exception) {
                _event.emit(CreateTeamEvent.ErrorEvent.CreateGroupError)
                logger.error("CreateGroup failed", e)
            }

            _state.postValue(CreateTeamState.Idle(nameProvider.getSignedInUserTeamName()))
        }
    }

    sealed class CreateTeamState {
        data class Idle(val pseudoName: String) : CreateTeamState()
        object Loading : CreateTeamState()
    }

    sealed class CreateTeamEvent {
        data class Done(val teamId: GroupId) : CreateTeamEvent()
        sealed class ErrorEvent : CreateTeamEvent() {
            object CreateGroupError : ErrorEvent()
            object Error : ErrorEvent()
        }
    }
}
