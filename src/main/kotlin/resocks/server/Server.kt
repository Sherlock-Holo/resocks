package resocks.server

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.nio.aConnect
import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import resocks.connection.LowLevelConnection
import resocks.connection.ServerConnectionPoll
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

    private lateinit var pool: ServerConnectionPoll

    suspend fun startServe() {
        initPool()
        while (true) {
            val lowLevelConnection = accept()
//            println("accept new lowLevelConnection")
            async { handle(lowLevelConnection) }
        }
    }

    private suspend fun initPool() {
        pool = ServerConnectionPoll.buildPoll(key, listenPort, listenAddr)
    }

    private suspend fun accept() = pool.getConn()

    private suspend fun handle(lowLevelConnection: LowLevelConnection) {
        val targetAddress = try {
            lowLevelConnection.read()!!
        } catch (e: IOException) {
            lowLevelConnection.errorStop()
            return
        }

        println("receive targetAddress")

        val socksInfo = Socks.buildSocksInfo(targetAddress)
//        println("atyp ${socksInfo.atyp}")
//        println(InetAddress.getByAddress(socksInfo.addr).hostAddress + " ${socksInfo.port}")

        val socketChannel = AsynchronousSocketChannel.open()

        try {
            socketChannel.aConnect(InetSocketAddress(InetAddress.getByAddress(socksInfo.addr), socksInfo.port))
        } catch (e: IOException) {
            lowLevelConnection.errorStop()
            return
        }

        println("start relay")

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

//                    println("data is not null")
                    socketChannel.aWrite(ByteBuffer.wrap(data))
                }
            } catch (e: IOException) {
                lowLevelConnection.errorStop()
                socketChannel.close()
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
                lowLevelConnection.errorStop()
                socketChannel.close()
            }
        }
    }
}