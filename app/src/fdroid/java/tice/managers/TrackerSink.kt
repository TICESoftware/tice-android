package tice.managers.services

import tice.dagger.scopes.AppScope
import tice.models.TrackerEvent
import tice.utility.TrackerType
import tice.utility.getLogger
import javax.inject.Inject

@AppScope
class TrackerSink @Inject constructor(): TrackerType {
    override val isActive = false

    val logger by getLogger()

    override fun start() {
        logger.debug("Tracking disabled.")
    }

    override fun stop() {
        logger.debug("Tracking disabled.")
    }

    override fun track(name: String, group: String, detail: String?, value: Double?, custom: List<String?>) {
        logger.debug("Tracking disabled.")
    }

    override fun track(event: TrackerEvent, custom: List<String?>) {
        logger.debug("Tracking disabled.")
    }

    override fun setProperty(index: Int, value: String?) {
        logger.debug("Tracking disabled.")
    }

    override fun setInstallDay(day: String) {
        logger.debug("Tracking disabled.")
    }

    override fun reset() {
        logger.debug("Tracking disabled.")
    }

    override suspend fun dispatch() {
        logger.debug("Tracking disabled.")
    }
}
