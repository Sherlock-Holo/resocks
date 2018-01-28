package resocks.websocket.connection

import kotlinx.coroutines.experimental.runBlocking
import java.nio.ByteBuffer

fun main(args: Array<String>) = runBlocking {
    val server = ServerConnection.start("127.0.0.2", 5678) {
        val frame = it.getFrame()
        println(frame.contentType)
        val port = ByteBuffer.wrap(frame.content).int
        println(port)
    }
}