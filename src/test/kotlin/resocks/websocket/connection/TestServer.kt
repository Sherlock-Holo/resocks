package resocks.websocket.connection

import kotlinx.coroutines.experimental.runBlocking

fun main(args: Array<String>) = runBlocking {
    val server = ServerConnection.start("127.0.0.2", 5678) {
        println(String(it.getFrame().content))
        println(String(it.getFrame().content))
        it.putFrame("嗷呜~".toByteArray())
        it.putFrame("嗷呜~".toByteArray())
    }
}