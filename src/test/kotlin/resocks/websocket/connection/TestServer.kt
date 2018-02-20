package resocks.websocket.connection

import kotlinx.coroutines.experimental.runBlocking
import resocks.websocket.frame.FrameContentType

fun main(args: Array<String>) = runBlocking<Unit> {
    ServerWebsocketConnection.startServer(5678, "127.0.0.2")
    val server = ServerWebsocketConnection.getClient()
    println(String(server.getFrame().content))
    println(String(server.getFrame().content))
    server.putFrame("big".toByteArray(), FrameContentType.TEXT)
    server.putFrame("wolf".toByteArray(), FrameContentType.TEXT)
}