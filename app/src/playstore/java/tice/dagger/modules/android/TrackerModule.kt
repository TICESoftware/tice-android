package tice.dagger.modules.android

import dagger.Binds
import dagger.Module
import tice.utility.TrackerType
import tice.utility.beekeeper.Beekeeper

@Module
abstract class TrackerModule {
    @Binds
    abstract fun bindTracker(beekeeper: Beekeeper): TrackerType
}
