package tice.dagger.modules

import dagger.Binds
import dagger.Module
import tice.managers.MapboxGeocodingManager
import tice.managers.MapboxGeocodingManagerType

@Module
abstract class FdroidManagerModule {

    @Binds
    abstract fun bindMapboxGeocodingManager(mapboxGeocodingManager: MapboxGeocodingManager): MapboxGeocodingManagerType
}