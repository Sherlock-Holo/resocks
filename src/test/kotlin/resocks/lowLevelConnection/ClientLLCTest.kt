package resocks.lowLevelConnection

import kotlinx.coroutines.experimental.runBlocking
import resocks.connection.LowLevelConnection
import resocks.encrypt.Cipher
import java.io.FileInputStream

fun main(args: Array<String>) = runBlocking {
    val key = Cipher.password2key("test")
    val lowLevelConnection = LowLevelConnection.initClient(key, "127.0.0.2", 6666)
    val byteArray = FileInputStream("/tmp/randomfile").readAllBytes()
    lowLevelConnection.write("sherlock".toByteArray())
    lowLevelConnection.write(byteArray)

    println(String(lowLevelConnection.read()!!))
    println(String(lowLevelConnection.read()!!))
}