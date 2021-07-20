package tice.dagger.modules.android

import dagger.Module
import dagger.android.ContributesAndroidInjector
import tice.ui.fragments.MigrationFragment

@Module
abstract class MigrationFragmentBuilderModule {

    @ContributesAndroidInjector(modules = [MigrationFragment::class])
    abstract fun contributeMigrationFragment(): MigrationFragment
}
