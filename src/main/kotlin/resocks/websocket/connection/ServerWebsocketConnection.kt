package resocks.websocket.connection

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.nio.aAccept
import kotlinx.coroutines.experimental.nio.aWrite
import kotlinx.coroutines.experimental.withTimeout
import resocks.readsBuffer.ReadsBuffer
import resocks.websocket.frame.Frame
import resocks.websocket.frame.FrameContentType
import resocks.websocket.frame.FrameType
import resocks.websocket.frame.WebsocketFrame
import resocks.websocket.http.HttpHeader
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel

class ServerWebsocketConnection private constructor(private val socketChannel: AsynchronousSocketChannel) : WebsocketConnection {

    private val readsBuffer = ReadsBuffer(socketChannel)

    override var connStatus = ConnectionStatus.RUNNING

    private suspend fun accept() {
        val clientHttpHeader = HttpHeader.getHttpHeader(readsBuffer)
        val serverHttpHeader = HttpHeader.offerHttpHeader(clientHttpHeader.secWebSocketKey!!)
        socketChannel.aWrite(ByteBuffer.wrap(serverHttpHeader.getHeaderByteArray()))

        /*if (!directly) {
            async { receive() }
            async { send() }
        }*/
    }

    override suspend fun getFrame(): Frame {
        val frame = withTimeout(1000 * 60 * 5) { WebsocketFrame.receiveFrame(readsBuffer, FrameType.CLIENT) }
        return frame
    }

    suspend fun putFrame(data: ByteArray) = putFrame(data, FrameContentType.BINARY)

    override suspend fun putFrame(data: ByteArray, contentType: FrameContentType) {
        val frame = WebsocketFrame(FrameType.SERVER, contentType, data)
        socketChannel.aWrite(ByteBuffer.wrap(frame.frameByteArray))
    }


    private fun closeConnection() {
        connStatus = ConnectionStatus.CLOSED

        socketChannel.close()
    }


    companion object {
        private lateinit var server: AsynchronousServerSocketChannel

        var started = false
            private set

        suspend fun <T> start(port: Int, handle: suspend (websocketConnection: ServerWebsocketConnection) -> T) {
            started = true

            server = AsynchronousServerSocketChannel.open()
            server.bind(InetSocketAddress(port))
            while (true) {
                val client = server.aAccept()
                client.setOption(StandardSocketOptions.TCP_NODELAY, true)
                val connection = ServerWebsocketConnection(client)

                async {
                    connection.accept()
                    handle(connection)
                }
            }
        }

        suspend fun <T> start(host: String, port: Int, handle: suspend (websocketConnection: ServerWebsocketConnection) -> T) {
            started = true

            server = AsynchronousServerSocketChannel.open()
            server.bind(InetSocketAddress(host, port))
            while (true) {
                val client = server.aAccept()
                client.setOption(StandardSocketOptions.TCP_NODELAY, true)
                val connection = ServerWebsocketConnection(client)

                async {
                    connection.accept()
                    handle(connection)
                }
            }
        }


        fun startServer(port: Int, host: String? = null) {
            started = true

            server = AsynchronousServerSocketChannel.open()
            if (host == null) server.bind(InetSocketAddress(port))
            else server.bind(InetSocketAddress(host, port))
        }

        fun startServer(serverSocketChannel: AsynchronousServerSocketChannel) {
            started = true

            server = serverSocketChannel
        }

        suspend fun getClient(): ServerWebsocketConnection {
            val socketChannel = server.aAccept()
            socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true)
            val serverConnection = ServerWebsocketConnection(socketChannel)
            serverConnection.accept()
            return serverConnection
        }
    }
}