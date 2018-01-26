package resocks.readsBuffer

import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.withTimeout
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class ReadsBuffer(val socketChannel: AsynchronousSocketChannel) {
    private val buffer = ByteBuffer.allocate(8192)
    private var bufferContentLength = 0

    suspend fun reads(length: Int, timeout: Int = -1): ByteArray {
        when (timeout) {
            -1 -> {
                while (bufferContentLength < length) {
                    bufferContentLength += socketChannel.aRead(buffer)
                }
            }

            else -> {
                withTimeout(timeout) { bufferContentLength += socketChannel.aRead(buffer) }
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
            val char = reads(1)[0].toChar()
            sb.append(char)
            if (char == '\n') return sb.toString()
        }
    }
}