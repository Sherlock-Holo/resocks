package resocks.websocket.connection

import kotlinx.coroutines.experimental.runBlocking

fun main(args: Array<String>) = runBlocking {
    val client = ClientConnection("127.0.0.2", 5678)
    client.connect()
    client.putFrame("sherlock".toByteArray())
    client.putFrame("holo".toByteArray())
    println(String(client.getFrame().content))
    println(String(client.getFrame().content))
}