package tice.managers

interface LocationServiceControllerType {

    var isForegroundService: Boolean

    fun startLocationService()
    fun stopLocationService()
    fun restartService()
    fun promoteToForeground()
    fun demotetoBackground()
    var locationServiceRunning: Boolean
}
