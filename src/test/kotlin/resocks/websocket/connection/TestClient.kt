package resocks.websocket.connection

import kotlinx.coroutines.experimental.runBlocking
import resocks.websocket.frame.FrameContentType

fun main(args: Array<String>) = runBlocking {
    val client = ClientWebsocketConnection("127.0.0.2", 5678)
    client.connect()
    client.putFrame("sherlock".toByteArray(), FrameContentType.TEXT)
    client.putFrame("holo".toByteArray())
    println(String(client.getFrame().content))
    println(String(client.getFrame().content))
}