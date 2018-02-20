package resocks.server

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.nio.aConnect
import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import resocks.connection.LowLevelConnection
import resocks.connection.ServerConnectionPoll
import resocks.socks.Socks
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class Server(private val key: ByteArray,
             private val listenPort: Int,
             private val listenAddr: String? = null
) {

    private lateinit var pool: ServerConnectionPoll

    suspend fun startServe() {
        initPool()
        while (true) {
            val lowLevelConnection = accept()
            async { handle(lowLevelConnection) }
        }
    }

    private suspend fun initPool() {
        pool = ServerConnectionPoll.buildPoll(key, listenPort, listenAddr)
    }

    private suspend fun accept() = pool.getConn()

    private suspend fun handle(lowLevelConnection: LowLevelConnection) {
        val targetAddress = lowLevelConnection.read()!!

        val socksInfo = Socks.buildSocksInfo(targetAddress)

        val socketChannel = AsynchronousSocketChannel.open()
        socketChannel.aConnect(InetSocketAddress(InetAddress.getByAddress(socksInfo.addr), socksInfo.port))

        //proxy server -> server
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
                    }

                    socketChannel.aWrite(ByteBuffer.wrap(data))
                }
            } catch (e: IOException) {
            }
        }

        // server -> proxy server
        async {
            val buffer = ByteBuffer.allocate(1024 * 16)
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
                                socketChannel.close()
                                lowLevelConnection.release()
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
            }
        }
    }
}