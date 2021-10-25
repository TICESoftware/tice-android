package tice.utility

import tice.models.TrackerEvent

interface TrackerType {
    val isActive: Boolean

    fun start()
    fun stop()

    fun track(name: String, group: String, detail: String? = null, value: Double? = null, custom: List<String?> = emptyList())
    fun setProperty(index: Int, value: String?)
    fun setInstallDay(day: String)
    fun reset()

    suspend fun dispatch()
    fun track(event: TrackerEvent, custom: List<String?> = emptyList())
}
