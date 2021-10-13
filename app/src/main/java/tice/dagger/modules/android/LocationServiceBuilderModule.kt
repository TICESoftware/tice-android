package tice.dagger.modules.android

import dagger.Module
import dagger.android.ContributesAndroidInjector
import tice.managers.services.LocationService

@Module
abstract class LocationServiceBuilderModule {
    @ContributesAndroidInjector
    abstract fun contributeLocationService(): LocationService
}
