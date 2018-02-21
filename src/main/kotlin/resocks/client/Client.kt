package resocks.client

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.nio.aAccept
import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import resocks.connection.ClientConnectionPool
import resocks.encrypt.Cipher
import resocks.socks.Socks
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel

class Client(
        listenAddr: String,
        listenPort: Int,
        serverAddr: String,
        serverPort: Int,
        password: String
) {
    private val key = Cipher.password2key(password)
    private val listenAsynchronousServerSocketChannel = AsynchronousServerSocketChannel.open()
    private val lowLevelConnectionPool = ClientConnectionPool(key, serverPort, serverAddr)

    init {
        listenAsynchronousServerSocketChannel.bind(InetSocketAddress(listenAddr, listenPort))
    }

    suspend fun startServe() {
        while (true) {
            val client = accept()
            async { handle(client) }
        }
    }

    private suspend fun accept() = listenAsynchronousServerSocketChannel.aAccept()

    private suspend fun handle(socketChannel: AsynchronousSocketChannel) {
        val socks = Socks(socketChannel)
        try {
            socks.init()
        } catch (e: IOException) {
            socketChannel.close()
            return
        }

        if (!socks.isSuccessful) {
            socketChannel.close()
            return
        }

        val lowLevelConnection = lowLevelConnectionPool.getConn()

        lowLevelConnection.write(socks.targetAddress)

        println("send target address")

        // client -> proxy server
        async {
            val buffer = ByteBuffer.allocate(1024 * 16)
            val lastData = socks.readsBuffer.finishAndGetLastData()
            try {
                if (lastData != null) {
                    lowLevelConnection.write(lastData)
                }

                while (true) {
                    val length = socketChannel.aRead(buffer)
                    if (length <= 0) {
                        lowLevelConnection.close()
                        return@async
                    }

                    buffer.flip()
                    val data = ByteArray(length)
                    buffer.get(data)
                    buffer.clear()

                    lowLevelConnection.write(data)
//                    println("send data")
                }
            } catch (e: IOException) {
//                socketChannel.close()
            } finally {
                socketChannel.close()
                lowLevelConnection.release()
            }
        }

        // proxy server -> client
        async {
            try {
                while (true) {
                    val data = lowLevelConnection.read() ?: return@async

                    socketChannel.aWrite(ByteBuffer.wrap(data))
                }
            } catch (e: IOException) {
//                e.printStackTrace()
            } finally {
                socketChannel.close()
                lowLevelConnection.release()
            }
        }
    }
}