package tice.dagger.modules.android

import dagger.Module
import dagger.android.ContributesAndroidInjector
import tice.ui.fragments.GoogleMapsContainerFragment
import tice.ui.fragments.MapboxMapContainerFragment

@Module
abstract class PlaystoreMapsContainerFragmentBuilderModule {
    @ContributesAndroidInjector
    abstract fun contributeGoogleMapsContainerFragment(): GoogleMapsContainerFragment

    @ContributesAndroidInjector
    abstract fun contributeMapboxMapContainerFragment(): MapboxMapContainerFragment
}
