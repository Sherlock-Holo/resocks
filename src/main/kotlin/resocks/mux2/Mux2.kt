package resocks.mux2

import kotlinx.coroutines.experimental.channels.LinkedListChannel
import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class Mux2(val id: Int, private val lowLevelWrite: suspend (byteArray: ByteArray) -> Unit) {
    val readQueue = LinkedListChannel<MuxPackage>()
    lateinit var targetSocketChannel: AsynchronousSocketChannel

    suspend fun write(data: ByteArray?, control: MuxPackageControl = MuxPackageControl.RUNNING) {
        val muxPackage = MuxPackage(id, control, data)
        lowLevelWrite(muxPackage.packageByteArray)
    }

    suspend fun write(muxPackage: MuxPackage) {
        targetSocketChannel.aWrite(ByteBuffer.wrap(muxPackage.data))
    }

    suspend fun read() = readQueue.receive()

    suspend fun read1() {
        val buffer = ByteBuffer.allocate(8192)
        while (true) {
            if (targetSocketChannel.aRead(buffer) <= 0) TODO()

            buffer.flip()
            val data = ByteArray(buffer.limit())
            buffer.get(data)
            buffer.compact()
            lowLevelWrite(MuxPackage(id, MuxPackageControl.RUNNING, data).packageByteArray)
        }
    }
}