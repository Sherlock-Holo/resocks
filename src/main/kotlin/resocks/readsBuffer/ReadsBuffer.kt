package resocks.readsBuffer

import kotlinx.coroutines.experimental.nio.aRead
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class ReadsBuffer(private val socketChannel: AsynchronousSocketChannel) {
    private val buffer = ByteBuffer.allocate(8192)
    private var bufferContentLength = 0

    suspend fun reads(length: Int): ByteArray {
        while (bufferContentLength < length) {
            bufferContentLength += socketChannel.aRead(buffer)
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
            val char = reads(1)[0].toChar()
            sb.append(char)
            if (char == '\n') return sb.toString()
        }
    }
}