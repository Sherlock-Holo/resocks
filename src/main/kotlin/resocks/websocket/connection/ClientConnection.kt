package resocks.websocket.connection

import kotlinx.coroutines.experimental.TimeoutCancellationException
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.LinkedListChannel
import kotlinx.coroutines.experimental.nio.aConnect
import kotlinx.coroutines.experimental.nio.aWrite
import kotlinx.coroutines.experimental.withTimeout
import resocks.readsBuffer.ReadsBuffer
import resocks.websocket.WebsocketException
import resocks.websocket.frame.ClientFrame
import resocks.websocket.frame.FrameType
import resocks.websocket.frame.ServerFrame
import resocks.websocket.http.HttpHeader
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class ClientConnection(val host: String, val port: Int) {
    private val receiveQueue = LinkedListChannel<ServerFrame>()
    private val sendQueue = LinkedListChannel<ClientFrame>()

    private lateinit var socketChannel: AsynchronousSocketChannel
    private lateinit var readsBuffer: ReadsBuffer

    private var connStatus = ConnectionStatus.RUNNING

    fun getConnStatus() = connStatus

    suspend fun connect() {
        socketChannel.aConnect(InetSocketAddress(host, port))
        readsBuffer = ReadsBuffer(socketChannel)

        val clientHttpHeader = HttpHeader.offerHttpHeader()
        socketChannel.aWrite(ByteBuffer.wrap(clientHttpHeader.getHeaderByteArray()))

        val serverHttpHeader = HttpHeader.getHttpHeader(readsBuffer)

        if (!clientHttpHeader.checkHttpHeader(serverHttpHeader.secWebSocketKey!!)) TODO("secWebSocketKey check failed")

        async { receive() }
        async { send() }
    }

    private suspend fun receive() {
        while (true) {
            try {
                val serverFrame = withTimeout(1000 * 60 * 5) { ServerFrame.receiveFrame(readsBuffer) }

                when (serverFrame.frameType) {
                    FrameType.BINARY -> receiveQueue.offer(serverFrame)

                    FrameType.PING -> {
                        val pongFrame = ClientFrame(FrameType.PONG, "pong".toByteArray())
                        sendQueue.offer(pongFrame)
                    }

                    FrameType.PONG -> connStatus = ConnectionStatus.RUNNING

                    else -> TODO("receive other frame")
                }
            } catch (e: TimeoutCancellationException) {
                // start ping-pong handle
                if (connStatus == ConnectionStatus.RUNNING) {
                    val pingFrame = ClientFrame(FrameType.PING, "ping".toByteArray())
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
            if (clientFrame != null) socketChannel.aWrite(ByteBuffer.wrap(clientFrame.content))
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

    suspend fun getFrame(): ServerFrame {
        if (connStatus == ConnectionStatus.RUNNING) return receiveQueue.receive()
        else throw WebsocketException("connection is closed")
    }

    fun putFrame(data: ByteArray): Boolean {
        if (connStatus == ConnectionStatus.RUNNING) return sendQueue.offer(ClientFrame(FrameType.BINARY, data))
        else throw WebsocketException("connection is closed")
    }
}