package tice.managers.messaging

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.*
import okio.ByteString
import tice.crypto.AuthManagerType
import tice.dagger.scopes.AppScope
import tice.managers.SignedInUserManagerType
import tice.managers.messaging.WebSocketReceiverType.ConnectionState
import tice.models.messaging.Envelope
import tice.utility.getLogger
import tice.utility.provider.CoroutineContextProviderType
import tice.utility.safeParse
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Named

@AppScope
class WebSocketReceiver @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val signedInUserManager: SignedInUserManagerType,
    private val authManager: AuthManagerType,
    private val postOffice: PostOfficeType,
    private val coroutineContextProvider: CoroutineContextProviderType,
    @Named("WEB_SOCKET_URL") private val webSocketURL: String,
    @Named("WEB_SOCKET_RETRY_DELAY") private val retryDelay: Long
) : WebSocketReceiverType {
    private val logger by getLogger()

    private val socketCloseCode = 1000

    private var socket: WebSocket? = null

    override var connectionState: ConnectionState = ConnectionState.DISCONNECTED

    override fun connect() {
        logger.debug("Connect web socket.")

        if (connectionState == ConnectionState.CONNECTED) {
            logger.debug("Socket already connected.")
            return
        }

        val signedInUser = signedInUserManager.signedInUser
        val authHeader = authManager.generateAuthHeader(signedInUser.privateSigningKey, signedInUser.userId)
        val request = Request.Builder().url(webSocketURL).addHeader("X-Authorization", authHeader).build()

        socket = okHttpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    super.onOpen(webSocket, response)
                    connectionState = ConnectionState.CONNECTED

                    logger.debug("Web socket opened successfully.")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    super.onFailure(webSocket, t, response)
                    connectionState = ConnectionState.CONNECTION_FAILURE
                    reconnect()

                    logger.error("Web socket failure: $t")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    super.onMessage(webSocket, text)
                    logger.trace("Web socket received message: $text")

                    val envelope: Envelope = Json.safeParse(Envelope.serializer(), text)
                    logger.debug("Received envelope ${envelope.id} from ${envelope.senderId} over websocket.")
                    postOffice.receiveEnvelope(envelope)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    super.onMessage(webSocket, bytes)
                    logger.trace("Web socket received message: ${bytes.string(Charset.defaultCharset())}")

                    val envelope: Envelope = Json.safeParse(Envelope.serializer(), bytes.string(Charset.defaultCharset()))
                    logger.debug("Received envelope ${envelope.id} from ${envelope.senderId} over websocket.")
                    postOffice.receiveEnvelope(envelope)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    super.onClosing(webSocket, code, reason)
                    logger.debug("Closing web socket ($code). Reason: $reason")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    super.onClosed(webSocket, code, reason)
                    logger.debug("Web socket closed ($code). Reason: $reason")
                    connectionState = ConnectionState.DISCONNECTED
                }
            }
        )
    }

    override fun disconnect() {
        logger.debug("Disconnect. Closing web socket.")
        if (connectionState == ConnectionState.DISCONNECTED) {
            logger.debug("Socket already disconnected.")
            return
        }

        connectionState = ConnectionState.DISCONNECTED
        socket?.close(socketCloseCode, null)
    }

    private fun reconnect() {
        CoroutineScope(coroutineContextProvider.IO).launch {
            delay(retryDelay)
            if (connectionState != ConnectionState.DISCONNECTED) {
                logger.debug("Reconnect WebSocket")
                connect()
            }
        }
    }
}
