package tice.dagger.modules.android

import dagger.Module
import dagger.android.ContributesAndroidInjector
import tice.ui.fragments.ForceUpdateFragment

@Module
abstract class ForceUpdateFragmentBuilderModule {

    @ContributesAndroidInjector(modules = [ForceUpdateFragment::class])
    abstract fun contributeForceUpdateFragment(): ForceUpdateFragment
}
