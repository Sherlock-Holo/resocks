package resocks.server

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.nio.aConnect
import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import resocks.connection.LowLevelConnection
import resocks.connection.ServerConnectionPool
import resocks.encrypt.Cipher
import resocks.socks.Socks
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class Server(password: String,
             private val listenPort: Int,
             private val listenAddr: String? = null
) {

    private val key = Cipher.password2key(password)

    private lateinit var pool: ServerConnectionPool

    suspend fun startServe() {
        initPool()
        while (true) {
            val lowLevelConnection = accept()
//            println("accept new lowLevelConnection")
            async { handle(lowLevelConnection) }
        }
    }

    private suspend fun initPool() {
        pool = ServerConnectionPool.buildPoll(key, listenPort, listenAddr)
    }

    private suspend fun accept() = pool.getConn()

    private suspend fun handle(lowLevelConnection: LowLevelConnection) {
        val targetAddress = try {
            lowLevelConnection.read()!!
        } catch (e: IOException) {
            TODO()
        }


        val socksInfo = Socks.buildSocksInfo(targetAddress)

        val socketChannel = AsynchronousSocketChannel.open()

        try {
            socketChannel.aConnect(InetSocketAddress(InetAddress.getByAddress(socksInfo.addr), socksInfo.port))
        } catch (e: IOException) {
            TODO()
        }

        println("start relay")

        var llcCanUse = true

        //proxy server -> server
        async {
            try {
                while (true) {
                    if (!llcCanUse) {
                        return@async
                    }

                    val data = lowLevelConnection.read()
                    if (data == null) {
                        llcCanUse = false
                        lowLevelConnection.readFin(socketChannel)
                        return@async
                    }

                    socketChannel.aWrite(ByteBuffer.wrap(data))
                }
            } catch (e: IOException) {
                llcCanUse = false
                lowLevelConnection.writeFin(socketChannel)
            }
        }

        // server -> proxy server
        async {
            val buffer = ByteBuffer.allocate(1024 * 16)
            try {
                while (true) {
                    if (!llcCanUse) {
                        return@async
                    }

                    val length = socketChannel.aRead(buffer)
                    if (length <= 0) {
                        llcCanUse = false
                        lowLevelConnection.writeFin(socketChannel)
                        return@async
                    }

                    buffer.flip()
                    val data = ByteArray(length)
                    buffer.get(data)
                    buffer.clear()

                    lowLevelConnection.write(data)
                }
            } catch (e: IOException) {
                llcCanUse = false
                lowLevelConnection.writeFin(socketChannel)
            }
        }
    }
}