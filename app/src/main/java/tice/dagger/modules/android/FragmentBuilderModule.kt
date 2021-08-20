package tice.dagger.modules.android

import dagger.Module
import dagger.android.ContributesAndroidInjector
import tice.ui.fragments.*

@Module
abstract class FragmentBuilderModule {

    @ContributesAndroidInjector
    abstract fun contributeRegisterFragment(): RegisterFragment

    @ContributesAndroidInjector
    abstract fun contributeCreateTeamFragment(): CreateTeamFragment

    @ContributesAndroidInjector
    abstract fun contributeTeamListFragment(): TeamListFragment

    @ContributesAndroidInjector
    abstract fun contributeCreateTeamInviteFragment(): CreateTeamInviteFragment

    @ContributesAndroidInjector
    abstract fun contributeMapFragment(): GroupMapFragment

    @ContributesAndroidInjector
    abstract fun contributeGoogleMapsContainerFragment(): GoogleMapsContainerFragment

    @ContributesAndroidInjector
    abstract fun contributeMapboxMapContainerFragment(): MapboxMapContainerFragment

    @ContributesAndroidInjector
    abstract fun contributeSettingsFragment(): SettingsFragment

    @ContributesAndroidInjector
    abstract fun contributeTeamInfoFragment(): TeamInfoFragment

    @ContributesAndroidInjector
    abstract fun contributeJoinTeamFragment(): JoinTeamFragment

    @ContributesAndroidInjector
    abstract fun contributeMeetupInfoFragment(): MeetupInfoFragment

    @ContributesAndroidInjector
    abstract fun contributeChatFragment(): ChatFragment
}
