package resocks.websocket.http

import kotlinx.coroutines.experimental.channels.LinkedListChannel
import kotlinx.coroutines.experimental.nio.aRead
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class ReadLienBuffer(private val socketChannel: AsynchronousSocketChannel, private val buffer: ByteBuffer) {
    private val readChannel = LinkedListChannel<Byte>()
    private val builder = StringBuilder()

    suspend fun readLine(): String {
        builder.delete(0, builder.length)
        while (true) {
            if (socketChannel.aRead(buffer) <= 0) TODO("maybe throw exception?")

            buffer.flip()

            while (buffer.hasRemaining()) readChannel.offer(buffer.get())
            buffer.clear()

            loop@ while (true) {
                val byte = readChannel.poll()
                if (byte != null) {
                    when (byte) {
                        "\r".toByte() -> continue@loop

                        "\n".toByte() -> {
                            while (true) {
                                val writeBackByte = readChannel.poll()

                                if (writeBackByte != null) buffer.put(writeBackByte)
                                else return builder.toString()
                            }
                        }

                        else -> builder.append(byte)
                    }
                } else continue
            }
        }
    }
}