package resocks.readsBuffer

import kotlinx.coroutines.experimental.nio.aRead
import resocks.websocket.WebsocketException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class ReadsBuffer(val socketChannel: AsynchronousSocketChannel) {
    val buffer = ByteBuffer.allocate(8192)
    private var bufferContentLength = 0

    suspend fun readExactly(length: Int): ByteArray {
        while (bufferContentLength < length) {
            val readLength = socketChannel.aRead(buffer)
            if (readLength > 0) bufferContentLength += readLength
            else {
                throw WebsocketException("unexpected end of stream")
            }
        }

        val byteArray = ByteArray(length)
        buffer.flip()
        buffer.get(byteArray)
        buffer.compact()
        bufferContentLength -= length
        return byteArray
    }

    suspend fun readLine(): String {
        val sb = StringBuilder()
        while (true) {
            val char = readExactly(1)[0].toChar()
            sb.append(char)
            if (char == '\n') return sb.toString()
        }
    }
}