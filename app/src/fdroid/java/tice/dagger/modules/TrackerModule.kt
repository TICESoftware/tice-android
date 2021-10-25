package tice.dagger.modules.android

import dagger.Binds
import dagger.Module
import tice.managers.services.TrackerSink
import tice.utility.TrackerType

@Module
abstract class TrackerModule {
    @Binds
    abstract fun bindTracker(trackerSink: TrackerSink): TrackerType
}
