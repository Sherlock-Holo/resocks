package resocks.websocket.connection

import kotlinx.coroutines.experimental.nio.aConnect
import kotlinx.coroutines.experimental.nio.aWrite
import kotlinx.coroutines.experimental.withTimeout
import resocks.readsBuffer.ReadsBuffer
import resocks.websocket.frame.Frame
import resocks.websocket.frame.FrameContentType
import resocks.websocket.frame.FrameType
import resocks.websocket.frame.WebsocketFrame
import resocks.websocket.http.HttpHeader
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class ClientWebsocketConnection(val host: String, val port: Int) : WebsocketConnection {
    private val socketChannel = AsynchronousSocketChannel.open()
    private lateinit var readsBuffer: ReadsBuffer

    override var connStatus = ConnectionStatus.RUNNING

    suspend fun connect() {
        socketChannel.aConnect(InetSocketAddress(host, port))
        readsBuffer = ReadsBuffer(socketChannel)

        val clientHttpHeader = HttpHeader.offerHttpHeader()
        socketChannel.aWrite(ByteBuffer.wrap(clientHttpHeader.getHeaderByteArray()))

        val serverHttpHeader = HttpHeader.getHttpHeader(readsBuffer)

        if (!serverHttpHeader.checkHttpHeader(clientHttpHeader.secWebSocketKey!!)) TODO("secWebSocketKey check failed")
    }

    private fun closeConnection() {
        connStatus = ConnectionStatus.CLOSED

        socketChannel.close()
    }

    override suspend fun getFrame(): Frame {
        return withTimeout(1000 * 60 * 5) { WebsocketFrame.receiveFrame(readsBuffer, FrameType.SERVER) }
    }

    suspend fun putFrame(data: ByteArray) {
        putFrame(data, FrameContentType.BINARY)
    }

    override suspend fun putFrame(data: ByteArray, contentType: FrameContentType) {
        val frame = WebsocketFrame(FrameType.CLIENT, contentType, data)
        socketChannel.aWrite(ByteBuffer.wrap(frame.frameByteArray))
    }
}