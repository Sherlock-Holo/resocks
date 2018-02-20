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

    private suspend fun accept(): AsynchronousSocketChannel {
        return listenAsynchronousServerSocketChannel.aAccept()
    }

    private suspend fun handle(socketChannel: AsynchronousSocketChannel) {
        val socks = Socks(socketChannel)
        socks.init()

        if (!socks.isSuccessful) TODO()

        val lowLevelConnection = lowLevelConnectionPool.getConn()

        lowLevelConnection.write(socks.targetAddress)

        // client -> proxy server
        async {
            val buffer = ByteBuffer.allocate(8192)
            try {
                while (true) {
                    val length = socketChannel.aRead(buffer)
                    if (length <= 0) {
                        socketChannel.shutdownInput()
                        lowLevelConnection.stopWrite()

                        when (lowLevelConnection.closeStatus) {
                            1 -> {
                                return@async
                            }

                            2 -> {
                                lowLevelConnection.release()
                                socketChannel.close()
                                return@async
                            }
                        }
                    }

                    buffer.flip()
                    val data = ByteArray(length)
                    buffer.get(data)
                    buffer.clear()

                    lowLevelConnection.write(data)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        // proxy server -> client
        async {
            try {
                while (true) {
                    val data = lowLevelConnection.read()
                    if (data == null) {
                        socketChannel.shutdownOutput()

                        when (lowLevelConnection.closeStatus) {
                            1 -> {
                                return@async
                            }

                            2 -> {
                                socketChannel.close()
                                lowLevelConnection.release()
                                return@async
                            }
                        }
                    } else {
                        socketChannel.aWrite(ByteBuffer.wrap(data))
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}