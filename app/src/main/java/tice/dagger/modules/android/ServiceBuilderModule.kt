package tice.dagger.modules.android

import dagger.Module
import dagger.android.ContributesAndroidInjector
import tice.managers.messaging.FirebaseReceiverService
import tice.managers.services.LocationService

@Module
abstract class ServiceBuilderModule {

    @ContributesAndroidInjector
    abstract fun contributeFirebaseMessagingService(): FirebaseReceiverService

    @ContributesAndroidInjector
    abstract fun contributeLocationService(): LocationService
}
