package resocks.websocket.connection

import kotlinx.coroutines.experimental.TimeoutCancellationException
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.LinkedListChannel
import kotlinx.coroutines.experimental.nio.aConnect
import kotlinx.coroutines.experimental.nio.aWrite
import kotlinx.coroutines.experimental.withTimeout
import resocks.websocket.WebsocketException
import resocks.websocket.frame.ClientFrame
import resocks.websocket.frame.Frame
import resocks.websocket.frame.ServerFrame
import resocks.websocket.http.HttpHeader
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class ClientConnection(val host: String, val port: Int) {
    private val receiveMessageQueue = LinkedListChannel<Frame>()
    private val sendMessageQueue = LinkedListChannel<Frame>()
    private lateinit var closeStage: ConnectionStage
    private lateinit var connection: AsynchronousSocketChannel

    private var clientNotSendOverTime = false

    private var pingTimes = 0

    suspend fun connect(host: String): ClientConnection {
        connection = AsynchronousSocketChannel.open()
        connection.aConnect(InetSocketAddress(this.host, port))
        closeStage = ConnectionStage.RUNNING

        val clientHttpHeader = HttpHeader.offerHttpHeader()
        connection.aWrite(ByteBuffer.wrap(clientHttpHeader.getHeaderByteArray()))
        val serverHttpHeader = HttpHeader.getHttpHeader(connection)
        if (!serverHttpHeader.checkHttpHeader(clientHttpHeader.secWebSocketKey!!)) {
            throw WebsocketException("secWebSocketAccept check failed")
        }

        async { read() }

        async { write() }
        return this
    }

    suspend private fun read() {
        var lastData: ByteArray? = null
        loop@ while (true) {
            try {
                val serverFrame = withTimeout(1000 * 300) {
                    ServerFrame.receiveFrame(connection, lastData)
                }
                lastData = serverFrame.lastOneData
                when (serverFrame.opcode) {
                    0x1 -> TODO("opcode is 0x01, data is UTF-8 text")

                    0x2 -> receiveMessageQueue.send(serverFrame)

                    0x3, 0x7, 0xB, 0xF -> TODO("MDN says it has no meaning")

                    0x9 -> {
                        val pongFrame = ClientFrame(0xA, serverFrame.content)
                        sendMessageQueue.send(pongFrame)
                    }

                    0xA -> {
                        pingTimes = 0
                    }

                // close frame
                    0x8 -> {
                        if (closeStage == ConnectionStage.CLOSING) {
                            connection.shutdownInput()
                            connection.close()
                            closeStage = ConnectionStage.CLOSED
                            break@loop
                        } else {
                            val closeFrame = ClientFrame(0x8, serverFrame.content)
                            closeStage = ConnectionStage.BE_CLOSED
                            sendMessageQueue.send(closeFrame)
                            connection.shutdownInput()
                            break@loop
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                if (pingTimes >= 3) {
                    close()
                    break@loop
                }

                if (clientNotSendOverTime) {
                    val pingFrame = ClientFrame(9, null)
                    sendMessageQueue.send(pingFrame)
                    pingTimes++
                }
            }
        }
    }

    suspend private fun write() {
        loop@ while (true) {
            try {
                val clientFrame = withTimeout(1000 * 300) {
                    sendMessageQueue.receive()
                }
                when (clientFrame.opcode) {
                    0x2 -> {
                        connection.aWrite(ByteBuffer.wrap(clientFrame.content))
                        clientNotSendOverTime = false
                    }

                    0x8 -> {
                        if (closeStage == ConnectionStage.CLOSED) {
                            connection.aWrite(ByteBuffer.wrap(clientFrame.content))
                            connection.shutdownOutput()
                            break@loop
                        } else if (closeStage == ConnectionStage.BE_CLOSED) {
                            connection.aWrite(ByteBuffer.wrap(clientFrame.content))
                            connection.shutdownOutput()
                            connection.close()
                            closeStage = ConnectionStage.CLOSED
                            break@loop
                        }
                    }

                    0x9 -> {
                        connection.aWrite(ByteBuffer.wrap(clientFrame.content))
                        clientNotSendOverTime = true
                    }
                }
            } catch (e: TimeoutCancellationException) {
                clientNotSendOverTime = true
            }
        }
    }

    suspend fun close() {
        closeStage = ConnectionStage.CLOSING
        val closeFrame = ClientFrame(0x8, null)
        sendMessageQueue.send(closeFrame)
    }
}