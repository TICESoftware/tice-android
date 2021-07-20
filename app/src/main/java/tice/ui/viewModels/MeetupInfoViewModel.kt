package tice.ui.viewModels

import androidx.lifecycle.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import tice.managers.group.MeetupManagerType
import tice.managers.storageManagers.GroupStorageManagerType
import tice.models.GroupId
import tice.models.Meetup
import tice.models.ParticipationStatus
import tice.models.User
import tice.ui.models.MemberData
import tice.utility.getLogger
import tice.utility.provider.CoroutineContextProviderType
import tice.utility.provider.NameProviderType
import javax.inject.Inject

class MeetupInfoViewModel @Inject constructor(
    private val meetupManager: MeetupManagerType,
    private val groupStorageManager: GroupStorageManagerType,
    private val coroutineContextProvider: CoroutineContextProviderType,
    private val nameProvider: NameProviderType
) : ViewModel() {
    private val logger by getLogger()

    private val _state = MutableLiveData<MeetupInfoState>(MeetupInfoState.Idle(null))
    val state: LiveData<MeetupInfoState>
        get() = _state

    private var _data: MediatorLiveData<MeetupInfoData> = MediatorLiveData()
    val data: LiveData<MeetupInfoData>
        get() = _data

    private var _event = MutableSharedFlow<MeetupInfoEvent>()
    val event: SharedFlow<MeetupInfoEvent>
        get() = _event

    private lateinit var teamId: GroupId
    private lateinit var meetup: Meetup
    private var participationStatus: ParticipationStatus? = null

    fun setupData(teamId: GroupId) {
        this.teamId = teamId

        viewModelScope.launch(coroutineContextProvider.IO) {
            groupStorageManager.meetupInTeam(teamId)?.let { meetupManager.reload(it) }
        }

        _data.addSource(groupStorageManager.getMeetupObservableForTeam(teamId)) {
            viewModelScope.launch(coroutineContextProvider.IO) {
                it ?: let { return@launch _event.emit(MeetupInfoEvent.NoMeetup(teamId)) }
                meetup = it

                _data.postValue(getMeetupData())
            }
        }

        _data.addSource(groupStorageManager.getTeamObservable(teamId)) {
            viewModelScope.launch(coroutineContextProvider.IO) {
                this@MeetupInfoViewModel.meetup = groupStorageManager.meetupInTeam(teamId) ?: run {
                    _event.emit(MeetupInfoEvent.NoMeetup(teamId))
                    return@launch
                }

                _data.postValue(getMeetupData())
            }
        }
    }

    private suspend fun getMeetupData(): MeetupInfoData? {
        val meetupMembers = groupStorageManager.members(meetup.groupId)
        val teamMembers = groupStorageManager.members(meetup.teamId)
        val teamMembersNotParticipating =
            teamMembers.filter { teamMember -> !meetupMembers.any { it.user.userId == teamMember.user.userId } }

        participationStatus = meetupManager.participationStatus(meetup)
        if (_state.value is MeetupInfoState.Idle) _state.postValue(MeetupInfoState.Idle(participationStatus))

        return MeetupInfoData(
            meetupMembers.map { MemberData(getUserName(it.user), it.membership.admin) },
            teamMembersNotParticipating.map {
                MemberData(getUserName(it.user), it.membership.admin)
            }
        )
    }

    fun handleMeetupInteraction() {
        if (_state.value !is MeetupInfoState.Idle) return
        else _state.postValue(MeetupInfoState.Loading)

        viewModelScope.launch(coroutineContextProvider.IO) {
            try {
                logger.debug("Handle meetup interaction: ${meetupManager.participationStatus(meetup)}")
                when (meetupManager.participationStatus(meetup)) {
                    ParticipationStatus.ADMIN -> {
                        meetupManager.delete(meetup)
                        _event.emit(MeetupInfoEvent.MeetupDeleted(meetup.teamId))
                    }
                    ParticipationStatus.MEMBER -> {
                        meetupManager.leave(meetup)
                        _event.emit(MeetupInfoEvent.MeetupLeft(meetup.teamId))
                    }
                    ParticipationStatus.NOT_PARTICIPATING -> {
                        meetupManager.join(meetup)
                        _event.emit(MeetupInfoEvent.MeetupJoined(meetup.teamId))
                    }
                }
                _state.postValue(MeetupInfoState.Idle(participationStatus))
            } catch (e: Exception) {
                logger.error("Meetup interaction failed.", e)
                _event.emit(MeetupInfoEvent.ErrorEvent.MeetupError)
                _state.postValue(MeetupInfoState.Idle(participationStatus))
                return@launch
            }
        }
    }

    private fun getUserName(user: User): String {
        return nameProvider.getUserName(user)
    }

    data class MeetupInfoData(
        val groupMemberParticipating: List<MemberData>,
        val groupMemberNotParticipating: List<MemberData>
    )

    sealed class MeetupInfoState {
        data class Idle(val status: ParticipationStatus?) : MeetupInfoState()
        object Loading : MeetupInfoState()
    }

    sealed class MeetupInfoEvent {
        data class NoMeetup(val parentId: GroupId) : MeetupInfoEvent()
        data class MeetupDeleted(val parentId: GroupId) : MeetupInfoEvent()
        data class MeetupJoined(val parentId: GroupId) : MeetupInfoEvent()
        data class MeetupLeft(val parentId: GroupId) : MeetupInfoEvent()

        sealed class ErrorEvent() : MeetupInfoEvent() {
            object MeetupError : ErrorEvent()
            object Error : ErrorEvent()
        }
    }
}
