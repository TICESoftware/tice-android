package tice.dagger.modules

import dagger.Binds
import dagger.Module
import tice.managers.MapboxGeocodingManager
import tice.managers.MapboxGeocodingManagerType
import tice.managers.SignedInUserManager
import tice.managers.SignedInUserManagerType

@Module
abstract class FdroidManagerModule {

    @Binds
    abstract fun bindMapboxGeocodingManager(mapboxGeocodingManager: MapboxGeocodingManager): MapboxGeocodingManagerType
}