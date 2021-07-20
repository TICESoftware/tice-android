package tice.ui.viewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import tice.managers.storageManagers.GroupStorageManagerType
import tice.models.GroupId
import tice.utility.provider.CoroutineContextProviderType
import tice.utility.provider.NameProviderType
import javax.inject.Inject

class CreateTeamInviteViewModel @Inject constructor(
    private val coroutineContextProvider: CoroutineContextProviderType,
    private val nameProvider: NameProviderType,
    private val groupStorageManager: GroupStorageManagerType
) : ViewModel() {

    private val _teamData = MediatorLiveData<TeamData>()
    val teamData: LiveData<TeamData>
        get() = _teamData

    private val _event = MutableSharedFlow<CreateTeamInviteEvent>()
    val event: SharedFlow<CreateTeamInviteEvent>
        get() = _event

    fun setup(teamId: GroupId) {
        _teamData.addSource(groupStorageManager.getTeamObservable(teamId)) {
            viewModelScope.launch(coroutineContextProvider.IO) {
                it ?: return@launch _event.emit(CreateTeamInviteEvent.NoTeam)

                val nameData = nameProvider.getTeamName(it)
                _teamData.postValue(TeamData(nameData.name, it.shareURL.toString()))
            }
        }
    }

    data class TeamData(val teamName: String, val teamUrl: String)

    sealed class CreateTeamInviteEvent {
        object NoTeam : CreateTeamInviteEvent()
        object Error : CreateTeamInviteEvent()
    }
}
