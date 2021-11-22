package tice.dagger.modules.android

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import tice.managers.LocationManagerType
import tice.managers.LocationServiceController
import tice.managers.LocationServiceControllerType
import tice.managers.services.LocationService
import tice.managers.services.NonGMSLocationService
import tice.utility.GMSAvailability
import tice.utility.provider.CoroutineContextProviderType

@Module
class LocationServiceBuilderModule {
    @Provides
    fun provideLocationService(context: Context): LocationService = NonGMSLocationService()

    @Provides
    fun provideLocationServiceController(context: Context): LocationServiceControllerType = LocationServiceController(context, NonGMSLocationService::class.java)
}
