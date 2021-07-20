package tice.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import tice.TICEApplication
import javax.inject.Inject

class MembershipRenewalWorker(val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @Inject
    lateinit var membershipRenewalTask: MembershipRenewalTask

    override suspend fun doWork(): Result {
        (context as TICEApplication).appComponent.bind(this)

        return membershipRenewalTask.doWork()
    }
}
