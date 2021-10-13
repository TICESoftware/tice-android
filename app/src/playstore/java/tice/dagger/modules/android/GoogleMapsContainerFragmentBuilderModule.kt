package tice.dagger.modules.android

import dagger.Module
import dagger.android.ContributesAndroidInjector
import tice.ui.fragments.GoogleMapsContainerFragment

@Module
abstract class GoogleMapsContainerFragmentBuilderModule {
    @ContributesAndroidInjector
    abstract fun contributeGoogleMapsContainerFragment(): GoogleMapsContainerFragment
}
