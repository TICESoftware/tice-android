package tice.managers

interface LocationServiceControllerType {
    var locationServiceRunning: Boolean

    fun requestStartingLocationService()
    fun stopLocationService()
}
