package resocks.mux2

import kotlinx.coroutines.experimental.channels.LinkedListChannel

class Mux2(val id: Int, private val lowLevelWrite: suspend (byteArray: ByteArray) -> Unit) {
    val readQueue = LinkedListChannel<MuxPackage>()

    suspend fun write(data: ByteArray?, control: MuxPackageControl = MuxPackageControl.RUNNING) {
        val muxPackage = MuxPackage(id, control, data)
        lowLevelWrite(muxPackage.packageByteArray)
    }

    suspend fun read() = readQueue.receive()
}