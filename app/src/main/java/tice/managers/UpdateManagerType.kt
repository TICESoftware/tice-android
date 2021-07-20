package tice.managers

interface UpdateManagerType {
    suspend fun check(currentVersion: Int)
}
