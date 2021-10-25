package tice.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import tice.TICEApplication
import tice.managers.storageManagers.CryptoStorageManagerType
import tice.models.TrackerEvent
import tice.utility.TrackerType
import tice.utility.getLogger
import java.util.*
import javax.inject.Inject

class MessageKeyCacheWorker(val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    val logger by getLogger()

    @Inject
    lateinit var cryptoStorageManager: CryptoStorageManagerType

    @Inject
    lateinit var tracker: TrackerType

    override suspend fun doWork(): Result {
        (context as TICEApplication).appComponent.bind(this)

        logger.debug("Started cleaning message key work.")
        tracker.track(TrackerEvent.messageKeyWorkStarted())

        cryptoStorageManager.cleanMessageKeyCacheOlderThanAnHour(Date(Date().time - 60 * 60 * 1000))

        logger.debug("Stopped cleaning message key work.")
        tracker.track(TrackerEvent.messageKeyWorkCompleted())

        return Result.success()
    }
}
