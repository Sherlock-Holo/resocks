package resocks.client

import kotlinx.coroutines.experimental.runBlocking

fun main(args: Array<String>) = runBlocking {
    val client = Client("127.0.0.2", 4567, "127.0.0.2", 5678, "test")
    client.startServe()
}