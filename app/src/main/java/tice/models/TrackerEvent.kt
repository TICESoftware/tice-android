package tice.models

class TrackerEvent private constructor(
    val name: String,
    val group: String,
    val detail: String? = null,
    val value: Double? = null
) {
    companion object {
        fun sessionStart(language: String) = TrackerEvent("SessionStart", "App", language)
        fun sessionEnd(duration: Long) = TrackerEvent("SessionEnd", "App", null, duration / 1000.0)
        fun register(named: Boolean) = TrackerEvent("Register", "Register", if (named) "NAMED" else "UNNAMED")
        fun didRegister(duration: Long) = TrackerEvent("DidRegister", "Register", null, duration / 1000.0)
        fun registerFailed(e: Exception?, duration: Long) = TrackerEvent("Error", "Register", e?.toString(), duration / 1000.0)
        fun membershipRenewalWorkerStarted() = TrackerEvent("MembershipRenewalStarted", "App")
        fun membershipRenewalWorkerCompleted() = TrackerEvent("MembershipRenewalCompleted", "App")
        fun backendWorkStarted() = TrackerEvent("BackgroundFetch", "App")
        fun backendWorkCompleted() = TrackerEvent("BackgroundFetchCompleted", "App")
        fun messageKeyWorkStarted() = TrackerEvent("DeleteMessageKeyCache", "App")
        fun messageKeyWorkCompleted() = TrackerEvent("DeleteMessageKeyCacheCompleted", "App")
    }
}
