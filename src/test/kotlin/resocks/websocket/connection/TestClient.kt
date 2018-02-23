package resocks.websocket.connection

import kotlinx.coroutines.experimental.runBlocking
import resocks.websocket.frame.FrameContentType
import java.io.FileInputStream

fun main(args: Array<String>) = runBlocking {
    val client = ClientWebsocketConnection("127.0.0.2", 5678)

    val byteArray = FileInputStream("/tmp/randomfile").readAllBytes()
    println(byteArray.size)
    client.connect()
    client.putFrame("sherlock".toByteArray())
    client.putFrame(byteArray)
    println(String(client.getFrame().content))
    println(String(client.getFrame().content))
}