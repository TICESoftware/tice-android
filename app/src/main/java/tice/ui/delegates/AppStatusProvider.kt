package tice.ui.delegates

interface AppStatusProvider {
    val status: Status

    enum class Status { FOREGROUND, BACKGROUND }
}
