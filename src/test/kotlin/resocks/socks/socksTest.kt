package resocks.socks

import kotlinx.coroutines.experimental.nio.aAccept
import kotlinx.coroutines.experimental.runBlocking
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousServerSocketChannel

fun main(args: Array<String>) = runBlocking {
    val server = AsynchronousServerSocketChannel.open()
    server.bind(InetSocketAddress("127.0.0.2", 1089))
    Socks(server.aAccept()).init()
}