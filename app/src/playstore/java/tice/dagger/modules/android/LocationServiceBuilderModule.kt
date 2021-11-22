package tice.dagger.modules.android

import android.content.Context
import dagger.Module
import dagger.Provides
import tice.dagger.scopes.AppScope
import tice.managers.LocationServiceController
import tice.managers.LocationServiceControllerType
import tice.managers.services.GMSLocationService
import tice.managers.services.LocationService
import tice.managers.services.NonGMSLocationService
import tice.utility.GMSAvailability

@Module
class LocationServiceBuilderModule {
    @AppScope
    @Provides
    fun provideLocationService(context: Context): LocationService = if (GMSAvailability.gmsAvailable(context)) GMSLocationService() else NonGMSLocationService()

    @AppScope
    @Provides
    fun provideLocationServiceController(context: Context): LocationServiceControllerType = if (GMSAvailability.gmsAvailable(context)) LocationServiceController(context, GMSLocationService::class.java) else LocationServiceController(context, NonGMSLocationService::class.java)
}
