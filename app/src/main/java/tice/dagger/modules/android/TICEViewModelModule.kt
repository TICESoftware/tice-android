package tice.dagger.modules.android

import androidx.lifecycle.ViewModel
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import tice.dagger.setup.ViewModelKey
import tice.ui.viewModels.*

@Module
abstract class TICEViewModelModule {

    @Binds
    @IntoMap
    @ViewModelKey(RegisterViewModel::class)
    abstract fun bindRegisterViewModel(myViewModel: RegisterViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(CreateTeamViewModel::class)
    abstract fun bindCreateTeamViewModel(myViewModel: CreateTeamViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(TeamListViewModel::class)
    abstract fun bindTeamListViewModel(myViewModel: TeamListViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(CreateTeamInviteViewModel::class)
    abstract fun bindCreateTeamInviteViewModel(myViewModel: CreateTeamInviteViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(GroupMapViewModel::class)
    abstract fun bindMapViewModel(myViewModel: GroupMapViewModel): ViewModel

//    @Binds
//    @IntoMap
//    @ViewModelKey(MapboxContainerViewModel::class)
//    abstract fun bindMapboxContainerViewModel(myViewModel: MapboxContainerViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(SettingsViewModel::class)
    abstract fun bindSettingsViewModel(myViewModel: SettingsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(TeamInfoViewModel::class)
    abstract fun bindTeamInfoViewModel(myViewModel: TeamInfoViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(MeetupInfoViewModel::class)
    abstract fun bindMeetupInfoViewModel(myViewModel: MeetupInfoViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(JoinTeamViewModel::class)
    abstract fun bindJoinTeamViewModel(myViewModel: JoinTeamViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ChatViewModel::class)
    abstract fun bindChatViewModel(myViewModel: ChatViewModel): ViewModel
}
