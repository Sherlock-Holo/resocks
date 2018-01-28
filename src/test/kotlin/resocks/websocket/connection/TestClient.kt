package resocks.websocket.connection

import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import java.nio.ByteBuffer

fun main(args: Array<String>) = runBlocking {
    val client = ClientConnection("127.0.0.2", 5678)
    client.connect()
    val port = ByteArray(4)
    ByteBuffer.allocate(4).putInt(8080).flip().get(port)

    client.putFrame(port)
    delay(10)
}