package resocks.websocket.http

import kotlinx.coroutines.experimental.nio.aRead
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.security.MessageDigest
import java.util.*

class HttpHeader private constructor(isClient: Boolean, value: String) {
    private val headerBuilder = StringBuilder()
    private val header: String
//    private val headerBuffer: By

    init {
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

    fun getHeaderText() = header.toByteArray()

    companion object {
        fun getHttpHeader(secWebSocketKey: String) = HttpHeader(false, genSecWebSocketAccept(secWebSocketKey))

        fun getHttpHeader(): HttpHeader {
            val array = ByteArray(16)
            Random().nextBytes(array)
            return HttpHeader(true, String(array))
        }

        private fun genSecWebSocketAccept(secWebSocketKey: String): String {
            val sha1 = MessageDigest.getInstance("SHA1")
            sha1.update((secWebSocketKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray())
            return String(Base64.getEncoder().encode(sha1.digest()))
        }

        suspend fun receiveHttpHeader(socket: AsynchronousSocketChannel): ByteArray {
            val headCache = LinkedList<Byte>()
            val buffer = ByteBuffer.allocate(4)
            val oldArray = ByteArray(4)
            var nowArray: ByteArray
            var haveRead: Int

            socket.aRead(buffer)
            System.arraycopy(buffer.array(), 0, oldArray, 0, 4)
            buffer.clear()
            headCache.addAll(oldArray.asIterable())

            loop@ while (true) {
                haveRead = socket.aRead(buffer)
                buffer.flip()
                nowArray = buffer.array()
                headCache.addAll(nowArray.asIterable())
                when (haveRead) {
                    4 -> {
                        if (nowArray.contentEquals("\r\n\r\n".toByteArray())) break@loop
                        else System.arraycopy(nowArray, 0, oldArray, 0, 4)
                    }
                    else -> {
                        if ((oldArray.sliceArray(haveRead until 4) + nowArray).contentEquals("\r\n\r\n".toByteArray())) break@loop
                        else {
                            System.arraycopy(oldArray, haveRead, oldArray, 0, 4 - haveRead)
                            System.arraycopy(nowArray, 0, oldArray, 4 - haveRead, haveRead)
                        }
                    }
                }
                buffer.clear()
            }
            return headCache.toByteArray()
        }

        fun checkHttpHeader(headCache: ByteArray): Boolean {
            // check client handshake
            
            val headString = String(headCache)
            val headList = headString.substring(0 until headString.length - 2).split("\r\n")

            if (!headList[0].startsWith("GET")) return false
            if (headList[2] != "Upgrade: websocket") return false
            if (headList[3] != "Connection: Upgrade") return false
            if (headList[5] != "Sec-WebSocket-Version: 13") return false

            return true
        }

        fun checkHttpHeader(headCache: ByteArray, secWebSocketKey: String): Boolean {
            // check client handshake

            val headString = String(headCache)
            val headList = headString.substring(0 until headString.length - 2).split("\r\n")

            if (headList[0] != "HTTP/1.1 101 Switching Protocols") return false
            if (headList[1] != "Upgrade: websocket") return false
            if (headList[2] != "Connection: Upgrade") return false
            if (headList[3] != "Sec-WebSocket-Accept: " + genSecWebSocketAccept(secWebSocketKey)) return false
            return true
        }
    }
}