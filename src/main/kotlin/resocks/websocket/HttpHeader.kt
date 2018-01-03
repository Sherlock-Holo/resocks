package resocks.websocket

import java.security.MessageDigest
import java.util.*

class HttpHeader private constructor(isClient: Boolean, value: String) {
    private val headerBuilder = StringBuilder()
    private val header: String

    init {
        header = if (isClient) {
            headerBuilder.append("GET /chat HTTP/1.1\r\n")
            headerBuilder.append("Host: github.com\r\n")
            headerBuilder.append("Upgrade: websocket\r\n")
            headerBuilder.append("Connection: Upgrade\r\n")
            headerBuilder.append("Sec-WebSocket-Key: $value\r\n")
            headerBuilder.append("Sec-WebSocket-Version: 13\r\n")
            headerBuilder.toString()
        } else {
            headerBuilder.append("HTTP/1.1 101 Switching Protocols\r\n")
            headerBuilder.append("Upgrade: websocket\r\n")
            headerBuilder.append("Connection: Upgrade\r\n")
            headerBuilder.append("Sec-WebSocket-Accept: $value\r\n")
            headerBuilder.toString()
        }
    }

    fun getHeaderText() = header

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
    }
}