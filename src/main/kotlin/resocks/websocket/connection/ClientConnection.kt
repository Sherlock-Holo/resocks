package resocks.websocket.connection

import kotlinx.coroutines.experimental.TimeoutCancellationException
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.LinkedListChannel
import kotlinx.coroutines.experimental.nio.aConnect
import kotlinx.coroutines.experimental.nio.aWrite
import kotlinx.coroutines.experimental.withTimeout
import resocks.readsBuffer.ReadsBuffer
import resocks.websocket.WebsocketException
import resocks.websocket.frame.Frame
import resocks.websocket.frame.FrameContentType
import resocks.websocket.frame.FrameType
import resocks.websocket.frame.WebsocketFrame
import resocks.websocket.http.HttpHeader
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class ClientConnection(val host: String, val port: Int) {
    private val receiveQueue = LinkedListChannel<Frame>()
    private val sendQueue = LinkedListChannel<Frame>()

    private val socketChannel = AsynchronousSocketChannel.open()
    private lateinit var readsBuffer: ReadsBuffer

    var connStatus = ConnectionStatus.RUNNING
        private set

    suspend fun connect() {
        socketChannel.aConnect(InetSocketAddress(host, port))
        readsBuffer = ReadsBuffer(socketChannel)

        val clientHttpHeader = HttpHeader.offerHttpHeader()
        socketChannel.aWrite(ByteBuffer.wrap(clientHttpHeader.getHeaderByteArray()))

        val serverHttpHeader = HttpHeader.getHttpHeader(readsBuffer)

        if (!serverHttpHeader.checkHttpHeader(clientHttpHeader.secWebSocketKey!!)) TODO("secWebSocketKey check failed")

        async { receive() }
        async { send() }
    }

    private suspend fun receive() {
        while (true) {
            try {
                val serverFrame = withTimeout(1000 * 60 * 5) { WebsocketFrame.receiveFrame(readsBuffer, FrameType.SERVER) }

                when (serverFrame.contentType) {
                    FrameContentType.BINARY -> receiveQueue.offer(serverFrame)

                    FrameContentType.PING -> {
                        val pongFrame = WebsocketFrame(FrameType.CLIENT, FrameContentType.PONG, serverFrame.content)
                        sendQueue.offer(pongFrame)
                    }

                    FrameContentType.PONG -> connStatus = ConnectionStatus.RUNNING

                    else -> TODO("receive other frame")
                }
            } catch (e: TimeoutCancellationException) {
                // start ping-pong handle
                if (connStatus == ConnectionStatus.RUNNING) {
                    val pingFrame = WebsocketFrame(FrameType.CLIENT, FrameContentType.PING, "ping".toByteArray())
                    sendQueue.offer(pingFrame)
                    connStatus = ConnectionStatus.PING
                } else {
                    closeConnection()
                    break
                }
            }

        }
    }

    private suspend fun send() {
        while (true) {
            if (connStatus == ConnectionStatus.CLOSED) break

            val clientFrame = sendQueue.receiveOrNull()
            if (clientFrame != null) socketChannel.aWrite(ByteBuffer.wrap(clientFrame.frameByteArray))
            else break
        }
    }

    private fun closeConnection() {
        connStatus = ConnectionStatus.CLOSED

        receiveQueue.cancel()
        sendQueue.cancel()

        socketChannel.shutdownInput()
        socketChannel.shutdownOutput()
        socketChannel.close()
    }

    suspend fun getFrame(): Frame {
        if (connStatus == ConnectionStatus.RUNNING) return receiveQueue.receive()
        else throw WebsocketException("connection is closed")
    }

    fun putFrame(data: ByteArray): Boolean {
        if (connStatus == ConnectionStatus.RUNNING) return sendQueue.offer(WebsocketFrame(FrameType.CLIENT, FrameContentType.BINARY, data))
        else throw WebsocketException("connection is closed")
    }

    fun putFrame(data: ByteArray, contentType: FrameContentType): Boolean {
        when (contentType) {
            FrameContentType.PING, FrameContentType.PONG, FrameContentType.CLOSE -> throw WebsocketException("not allow content type")
            else -> {
            }
        }

        if (connStatus == ConnectionStatus.RUNNING) return sendQueue.offer(WebsocketFrame(FrameType.CLIENT, contentType, data))
        else throw WebsocketException("connection is closed")
    }
}