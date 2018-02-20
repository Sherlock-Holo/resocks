package resocks.server

import kotlinx.coroutines.experimental.runBlocking

fun main(args: Array<String>) = runBlocking {
    val server = Server("test", 5678, "127.0.0.2")
    server.startServe()
}