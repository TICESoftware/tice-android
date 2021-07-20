package tice.exceptions

sealed class MinVersionException : Exception() {
    data class Outdated(val minVersion: Int) : MinVersionException()
}
