package resocks.lowLevelConnection

import kotlinx.coroutines.experimental.runBlocking
import resocks.connection.LowLevelConnection
import resocks.encrypt.Cipher

fun main(args: Array<String>) = runBlocking {
    val key = Cipher.password2key("test")
    val lowLevelConnection = LowLevelConnection.initClient(key, "127.0.0.2", 6666)
    lowLevelConnection.write("sherlock".toByteArray())
    lowLevelConnection.write("holo".toByteArray())

    println(String(lowLevelConnection.read()!!))
    println(String(lowLevelConnection.read()!!))
}