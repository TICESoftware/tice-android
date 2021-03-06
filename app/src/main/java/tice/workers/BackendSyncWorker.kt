package tice.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import tice.TICEApplication
import tice.managers.messaging.PostOfficeType
import tice.models.TrackerEvent
import tice.utility.TrackerType
import tice.utility.getLogger
import javax.inject.Inject

class BackendSyncWorker(val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    val logger by getLogger()

    @Inject
    lateinit var postOffice: PostOfficeType

    @Inject
    lateinit var tracker: TrackerType

    override suspend fun doWork(): Result {
        (context as TICEApplication).appComponent.bind(this)

        logger.debug("Started backend synchronization work.")
        tracker.track(TrackerEvent.backendWorkStarted())

        postOffice.fetchMessages()

        logger.debug("Stopped backend synchronization work.")
        tracker.track(TrackerEvent.backendWorkCompleted())

        return Result.success()
    }
}
