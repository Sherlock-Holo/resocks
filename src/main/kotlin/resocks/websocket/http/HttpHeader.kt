package resocks.websocket.http

import kotlinx.coroutines.experimental.nio.aRead
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.security.MessageDigest
import java.util.*

class HttpHeader {
    private val header: String

    private constructor(isClient: Boolean, value: String) {
        val headerBuilder = StringBuilder()
        header = if (isClient) {
            headerBuilder.append("GET /chat HTTP/1.1\r\n")
            headerBuilder.append("Host: github.com\r\n")
            headerBuilder.append("Upgrade: websocket\r\n")
            headerBuilder.append("Connection: Upgrade\r\n")
            headerBuilder.append("Sec-WebSocket-Key: $value\r\n")
            headerBuilder.append("Sec-WebSocket-Version: 13\r\n\r\n")
            headerBuilder.toString()
        } else {
            headerBuilder.append("HTTP/1.1 101 Switching Protocols\r\n")
            headerBuilder.append("Upgrade: websocket\r\n")
            headerBuilder.append("Connection: Upgrade\r\n")
            headerBuilder.append("Sec-WebSocket-Accept: $value\r\n\r\n")
            headerBuilder.toString()
        }
    }

    private constructor(headerCache: ByteArray) {
        header = String(headerCache)
    }

    fun getHeaderByteArray() = header.toByteArray()

    fun getHeaderString() = header

    fun checkHttpHeader(): Boolean {
        // check client handshake

        val headList = header.substring(0 until header.length - 2).split("\r\n")

        if (!headList[0].startsWith("GET")) return false
        if (headList[2] != "Upgrade: websocket") return false
        if (headList[3] != "Connection: Upgrade") return false
        if (headList[5] != "Sec-WebSocket-Version: 13") return false

        return true
    }

    fun checkHttpHeader(secWebSocketKey: String): Boolean {
        // check client handshake

        val headList = header.substring(0 until header.length - 2).split("\r\n")

        if (headList[0] != "HTTP/1.1 101 Switching Protocols") return false
        if (headList[1] != "Upgrade: websocket") return false
        if (headList[2] != "Connection: Upgrade") return false
        if (headList[3] != "Sec-WebSocket-Accept: " + genSecWebSocketAccept(secWebSocketKey)) return false

        return true
    }


    companion object {
        // offer server http header

        fun offerHttpHeader(secWebSocketKey: String) = HttpHeader(false, genSecWebSocketAccept(secWebSocketKey))

        // offer client http header

        fun offerHttpHeader(): HttpHeader {
            val array = ByteArray(16)
            Random().nextBytes(array)
            return HttpHeader(true, Base64.getEncoder().encodeToString(array))
        }

        fun genSecWebSocketAccept(secWebSocketKey: String): String {
            val sha1 = MessageDigest.getInstance("SHA1")
            sha1.update((secWebSocketKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray())
            return String(Base64.getEncoder().encode(sha1.digest()))
        }

        suspend fun getHttpHeader(socket: AsynchronousSocketChannel): HttpHeader {
            val headerCache = LinkedList<Byte>()
            val buffer = ByteBuffer.allocate(4)
            val oldArray = ByteArray(4)
            val nowArray = ByteArray(4)
            var haveRead = 0

            while (haveRead < 4) {
                haveRead += socket.aRead(buffer)
            }

            buffer.flip()
            buffer.get(nowArray)
            buffer.clear()

            System.arraycopy(nowArray, 0, oldArray, 0, 4)
            buffer.clear()
            headerCache.addAll(oldArray.asIterable())

            loop@ while (true) {
                haveRead = socket.aRead(buffer)
                buffer.flip()
                buffer.get(nowArray)
                buffer.clear()
                headerCache.addAll(nowArray.asIterable())

                when (haveRead) {
                    4 -> {
                        if (nowArray.contentEquals("\r\n\r\n".toByteArray())) break@loop
                        else System.arraycopy(nowArray, 0, oldArray, 0, 4)
                    }
                    else -> {
                        if (
                        (oldArray.sliceArray(haveRead until 4) +
                                nowArray.sliceArray(0 until haveRead)).contentEquals("\r\n\r\n".toByteArray())) break@loop
                        else {
                            System.arraycopy(oldArray, haveRead, oldArray, 0, 4 - haveRead)
                            System.arraycopy(nowArray, 0, oldArray, 4 - haveRead, haveRead)
                        }
                    }
                }
                buffer.clear()
            }
//            return headerCache.toByteArray()
            return HttpHeader(headerCache.toByteArray())
        }
    }
}