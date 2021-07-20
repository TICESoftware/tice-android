package tice.managers.messaging

interface WebSocketReceiverType {
    enum class ConnectionState {
        CONNECTED,
        DISCONNECTED,
        CONNECTION_FAILURE
    }

    val connectionState: ConnectionState

    fun connect()
    fun disconnect()
}
