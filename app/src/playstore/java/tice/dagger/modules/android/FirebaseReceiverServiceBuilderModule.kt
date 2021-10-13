package tice.dagger.modules.android

import dagger.Module
import dagger.android.ContributesAndroidInjector
import tice.managers.messaging.FirebaseReceiverService

@Module
abstract class FirebaseReceiverServiceBuilderModule {
    @ContributesAndroidInjector
    abstract fun contributeFirebaseMessagingService(): FirebaseReceiverService
}
