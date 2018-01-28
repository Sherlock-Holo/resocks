package resocks.websocket.connection

import kotlinx.coroutines.experimental.TimeoutCancellationException
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.LinkedListChannel
import kotlinx.coroutines.experimental.nio.aAccept
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
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel

class ServerConnection private constructor(private val socketChannel: AsynchronousSocketChannel) {

    private val receiveQueue = LinkedListChannel<Frame>()
    private val sendQueue = LinkedListChannel<Frame>()

    private val readsBuffer = ReadsBuffer(socketChannel)

    var connStatus = ConnectionStatus.RUNNING
        private set

    private suspend fun accept() {
        val clientHttpHeader = HttpHeader.getHttpHeader(readsBuffer)
        val serverHttpHeader = HttpHeader.offerHttpHeader(clientHttpHeader.secWebSocketKey!!)
        socketChannel.aWrite(ByteBuffer.wrap(serverHttpHeader.getHeaderByteArray()))

        async { receive() }
        async { send() }
    }

    private suspend fun receive() {
        while (true) {
            try {
                val clientFrame = withTimeout(1000 * 60 * 5) { WebsocketFrame.receiveFrame(readsBuffer, FrameType.CLIENT) }

                when (clientFrame.contentType) {
                    FrameContentType.TEXT, FrameContentType.BINARY -> receiveQueue.offer(clientFrame)

                    FrameContentType.PING -> {
                        val pongFrame = WebsocketFrame(FrameType.SERVER, FrameContentType.PONG, clientFrame.content)
                        sendQueue.offer(pongFrame)
                    }

                    FrameContentType.PONG -> connStatus = ConnectionStatus.RUNNING

                    else -> TODO("other frame")
                }
            } catch (e: TimeoutCancellationException) {
                // start ping-pong handle
                if (connStatus == ConnectionStatus.RUNNING) {
                    val pingFrame = WebsocketFrame(FrameType.SERVER, FrameContentType.PING, "pong".toByteArray())
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

            val serverFrame = sendQueue.receiveOrNull()
            if (serverFrame != null) socketChannel.aWrite(ByteBuffer.wrap(serverFrame.frameByteArray))
            else break
        }
    }

    suspend fun getFrame(): Frame {
        if (connStatus == ConnectionStatus.RUNNING) return receiveQueue.receive()
        else throw WebsocketException("connection is closed")
    }

    fun putFrame(data: ByteArray): Boolean {
        if (connStatus == ConnectionStatus.RUNNING) return sendQueue.offer(WebsocketFrame(FrameType.SERVER, FrameContentType.BINARY, data))
        else throw WebsocketException("connection is closed")
    }

    fun putFrame(data: ByteArray, contentType: FrameContentType): Boolean {
        when (contentType) {
            FrameContentType.PING, FrameContentType.PONG, FrameContentType.CLOSE -> throw WebsocketException("not allow content type")
            else -> {
            }
        }

        if (connStatus == ConnectionStatus.RUNNING) return sendQueue.offer(WebsocketFrame(FrameType.SERVER, contentType, data))
        else throw WebsocketException("connection is closed")
    }


    private fun closeConnection() {
        connStatus = ConnectionStatus.CLOSED

        receiveQueue.cancel()
        sendQueue.cancel()

        socketChannel.shutdownInput()
        socketChannel.shutdownOutput()
        socketChannel.close()
    }

    companion object {
        suspend fun <T> start(port: Int, handle: suspend (connection: ServerConnection) -> T) {
            val server = AsynchronousServerSocketChannel.open().bind(InetSocketAddress(port))
            while (true) {
                val client = server.aAccept()
                val connection = ServerConnection(client)

                async {
                    connection.accept()
                    handle(connection)
                }
            }
        }

        suspend fun <T> start(host: String, port: Int, handle: suspend (connection: ServerConnection) -> T) {
            val server = AsynchronousServerSocketChannel.open().bind(InetSocketAddress(host, port))
            while (true) {
                val client = server.aAccept()
                val connection = ServerConnection(client)

                async {
                    connection.accept()
                    handle(connection)
                }
            }
        }
    }
}