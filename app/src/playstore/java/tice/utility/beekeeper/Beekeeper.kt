@file:UseSerializers(
    DateSerializer::class,
)

package tice.utility.beekeeper

import com.ticeapp.TICE.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.UseSerializers
import tice.dagger.scopes.AppScope
import tice.models.TrackerEvent
import tice.utility.SynchronizedList
import tice.utility.TrackerType
import tice.utility.provider.CoroutineContextProviderType
import tice.utility.serializer.DateSerializer
import java.util.*
import javax.inject.Inject
import kotlin.concurrent.fixedRateTimer
import kotlin.math.max

@AppScope
class Beekeeper @Inject constructor(
    private val dispatcher: Dispatcher,
    private val memory: Memory,
    private val coroutineContextProvider: CoroutineContextProviderType
) : TrackerType {

    private val product: String = "TICE-" + BuildConfig.FLAVOR_stage

    override var isActive: Boolean = false
        private set

    private var queue = SynchronizedList<Event>()

    private var dispatchTimer: Timer? = null

    override fun start() {
        isActive = true
        setInstallDay(memory.installDay ?: Date().toDay())
    }

    override fun stop() {
        isActive = false
    }

    override fun track(
        name: String,
        group: String,
        detail: String?,
        value: Double?,
        custom: List<String?>
    ) {
        val id = UUID.randomUUID().toString().replace("-", "")
        val install: Day = memory.installDay ?: Date().toDay()

        val mergedCustomCount = max(memory.custom.count(), custom.count())
        val mergedCustom: List<String?> = List(mergedCustomCount) { index ->
            custom.getOrElse(index) { memory.custom.getOrNull(index) }
        }

        val event = Event(
            id,
            product,
            Date(),
            name,
            group,
            detail,
            value,
            memory.previousEvent[group],
            memory.lastDay[group]?.get(name),
            install,
            mergedCustom
        )

        track(event)
    }

    override fun track(event: TrackerEvent, custom: List<String?>) {
        track(event.name, event.group, event.detail, event.value, custom)
    }

    fun track(event: Event) {
        if (memory.optedOut) return

        memory.remember(event)
        queue.add(event)

        if (dispatchTimer == null) {
            dispatchTimer = fixedRateTimer(
                "BeekeeperDispatchTimer",
                false,
                dispatcher.dispatchInterval,
                dispatcher.dispatchInterval
            ) {
                CoroutineScope(coroutineContextProvider.IO).launch {
                    dispatch()
                }
            }
        }
    }

    override suspend fun dispatch() {
        if (!isActive || memory.optedOut) {
            return
        }

        val events = queue.dequeue(dispatcher.maxBatchSize)

        if (events.isEmpty()) {
            stopTimer()
            return
        }

        try {
            dispatcher.dispatch(events)
        } catch (e: Exception) {
            queue.addAll(events)
        }
    }

    override fun setProperty(index: Int, value: String?) {
        memory.setProperty(index, value)
    }

    override fun setInstallDay(day: Day) {
        memory.installDay = day
    }

    private fun stopTimer() {
        dispatchTimer?.cancel()
        dispatchTimer = null
    }

    override fun reset() {
        stopTimer()
        memory.clear()
        queue.clear()
    }
}
