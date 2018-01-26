package resocks.websocket.http

import kotlinx.coroutines.experimental.nio.aRead
import resocks.websocket.WebsocketException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class ReadLienBuffer(private val socketChannel: AsynchronousSocketChannel, private val buffer: ByteBuffer) {
    private val builder = StringBuilder()
    private lateinit var otherBytes: ByteArray

    suspend fun readLine(): String {
        builder.delete(0, builder.length)
        while (true) {
            if (buffer.position() == 0) {
                if (socketChannel.aRead(buffer) <= 0) throw WebsocketException("read http header failed")
            }

            buffer.flip()

            loop@ while (buffer.hasRemaining()) {
                val byte = buffer.get()
                when (byte) {
                    '\r'.toByte() -> continue@loop

                    '\n'.toByte() -> {
                        buffer.compact()
                        buffer.flip()
                        otherBytes = ByteArray(buffer.limit())
                        buffer.get(otherBytes)
                        buffer.limit(buffer.capacity())
                        return builder.toString()
                    }

                    else -> builder.append(byte.toChar())
                }
            }
            buffer.clear()
        }
    }

    fun getOhterBytes() = otherBytes
}