package resocks.websocket

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.LinkedListChannel
import kotlinx.coroutines.experimental.nio.aConnect
import kotlinx.coroutines.experimental.nio.aWrite
import resocks.websocket.frame.Frame
import resocks.websocket.frame.ServerFrame
import resocks.websocket.http.HttpHeader
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class ClientConnection(val host: String, val port: Int) {
    private val receiveMessageQueue = LinkedListChannel<Frame>()
    private val sendMessageQueue = LinkedListChannel<Frame>()
    private var closing = false
    private lateinit var connection: AsynchronousSocketChannel

    suspend fun connect(host: String): ClientConnection {
        connection = AsynchronousSocketChannel.open()
        connection.aConnect(InetSocketAddress(this.host, port))

        val clientHttpHeader = HttpHeader.offerHttpHeader()
        connection.aWrite(ByteBuffer.wrap(clientHttpHeader.getHeaderByteArray()))
        val serverHttpHeader = HttpHeader.getHttpHeader(connection)
        if (!serverHttpHeader.checkHttpHeader(clientHttpHeader.secWebSocketKey!!)) {
            throw WebsocketException("secWebSocketAccept check failed")
        }

        async {
            read()
        }

        async {
            write()
        }
        return this
    }

    suspend private fun read() {
        var lastData: ByteArray? = null
        while (true) {
            val serverFrame = ServerFrame.receiveFrame(connection, lastData)
            lastData = serverFrame.lastOneData
            when (serverFrame.opcode) {
                0x1 -> TODO("opcode is 0x01, data is UTF-8 text")

                0x2 -> receiveMessageQueue.send(serverFrame)

                0x3, 0x7, 0xB, 0xF -> TODO("MDN says it has no meaning")
            }
        }
    }

    suspend private fun write() {
        while (true) {
            val clientFrame = sendMessageQueue.receive()
            connection.aWrite(ByteBuffer.wrap(clientFrame.content))
        }
    }
}