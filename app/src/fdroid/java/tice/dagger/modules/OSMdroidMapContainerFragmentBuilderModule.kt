package tice.dagger.modules.android

import dagger.Module
import dagger.android.ContributesAndroidInjector
import tice.ui.fragments.OSMdroidMapContainerFragment

@Module
abstract class OSMdroidMapContainerFragmentBuilderModule {
    @ContributesAndroidInjector
    abstract fun contributeOSMdroidMapContainerFragment(): OSMdroidMapContainerFragment
}
