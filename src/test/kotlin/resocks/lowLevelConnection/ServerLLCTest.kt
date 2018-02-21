package resocks.lowLevelConnection

import kotlinx.coroutines.experimental.runBlocking
import resocks.connection.LowLevelConnection
import resocks.encrypt.Cipher
import resocks.websocket.connection.ServerWebsocketConnection

fun main(args: Array<String>) = runBlocking {
    ServerWebsocketConnection.startServer(6666, "127.0.0.2")
    val key = Cipher.password2key("test")
    val lowLevelConnection = LowLevelConnection.initServer(ServerWebsocketConnection.getClient(), key)
    println(String(lowLevelConnection.read()!!))
    println(String(lowLevelConnection.read()!!))

    lowLevelConnection.write("big".toByteArray())
    lowLevelConnection.write("wolf".toByteArray())
}