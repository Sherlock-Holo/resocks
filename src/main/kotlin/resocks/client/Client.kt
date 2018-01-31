package resocks.client

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.nio.aAccept
import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import resocks.encrypt.Cipher
import resocks.proxy.PackageControl
import resocks.proxy.ProxyException
import resocks.proxy.ResocksPackage
import resocks.proxy.WebscoketConnectionPool
import resocks.readsBuffer.ReadsBuffer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel

class Client(listenHost: String, listenPort: Int, password: String) {
    private val frontServer =
            AsynchronousServerSocketChannel.open().bind(InetSocketAddress(listenHost, listenPort))

    private lateinit var webscoketConnectionPool: WebscoketConnectionPool

    private val key = Cipher.password2key(password)

    suspend fun start(host: String, port: Int) {
        frontServer.setOption(StandardSocketOptions.TCP_NODELAY, true)
//        frontServer.setOption(StandardSocketOptions.SO_KEEPALIVE, true)

        webscoketConnectionPool = WebscoketConnectionPool(host, port, key)
        while (true) {
            val client = frontServer.aAccept()
            client.setOption(StandardSocketOptions.TCP_NODELAY, true)
            async { handle(client) }
        }
    }

    suspend fun start(websocketAddress: String) {
        frontServer.setOption(StandardSocketOptions.TCP_NODELAY, true)
//        frontServer.setOption(StandardSocketOptions.SO_KEEPALIVE, true)

        webscoketConnectionPool = WebscoketConnectionPool(websocketAddress, key)
        while (true) {
            val client = frontServer.aAccept()
            client.setOption(StandardSocketOptions.TCP_NODELAY, true)
            client.setOption(StandardSocketOptions.SO_KEEPALIVE, true)
            async { handle(client) }
        }
    }

    private suspend fun handle(client: AsynchronousSocketChannel) {
        val readsBuffer = ReadsBuffer(client)
        val version = readsBuffer.readExactly(1)[0].toInt() and 0xff
        if (version != 5) TODO("socks version is not 5")
        val nmethods = readsBuffer.readExactly(1)[0].toInt() and 0xff
        val methods = readsBuffer.readExactly(nmethods)
        val noAuth = methods.any { it.toInt() == 0 }
        if (!noAuth) TODO("auth modes has no-auth mode")

        val method = ByteBuffer.wrap(byteArrayOf(5, 0))
        client.aWrite(method)

        val requestHeader = readsBuffer.readExactly(4)
        val reqeustVersion = requestHeader[0].toInt() and 0xff
        val cmd = requestHeader[1].toInt() and 0xff
        val atyp = requestHeader[3].toInt() and 0xff

        var addrLength: Int? = null
        val addr: InetAddress

        if (reqeustVersion != 5 || cmd != 0) TODO("request error")

        when (atyp) {
            1 -> {
                addr = InetAddress.getByAddress(readsBuffer.readExactly(4))
            }

            3 -> {
                addrLength = readsBuffer.readExactly(1)[0].toInt() and 0xff
                addr = InetAddress.getByAddress(readsBuffer.readExactly(addrLength))
            }

            4 -> {
                addr = InetAddress.getByAddress(readsBuffer.readExactly(16))
            }

            else -> TODO("error atyp")
        }

        val port = readsBuffer.readExactly(2)
        var id: Int
        var websocketConnection: WebscoketConnectionPool.WebsocketConnection

        while (true) {
            try {
                websocketConnection = webscoketConnectionPool.getCoon()
                id = websocketConnection.getID()
                break
            } catch (e: ProxyException) {
            }
        }

        val connection = websocketConnection.clientConnection
        connection.connect()

        val addrByteArray =
                if (addrLength == null) byteArrayOf(atyp.toByte()) + addr.address + port
                else byteArrayOf(atyp.toByte(), addrLength.toByte()) + addr.address + port

        val requestPackage = ResocksPackage(id, PackageControl.CONNECT, addrByteArray)
        connection.putFrame(websocketConnection.encrypt(requestPackage.packageByteArray))

        val replyAddress = InetSocketAddress("::1", 0)
        val reply = byteArrayOf(5, 0, 0, 4) + replyAddress.address.address + replyAddress.port.toByte()
        client.aWrite(ByteBuffer.wrap(reply))

        websocketConnection.setConn(id, client)

        async {
            val buffer = readsBuffer.buffer
            while (true) {
                if (client.aRead(buffer) <= 0) {
                    client.shutdownInput()

                    if (websocketConnection.hasConn(id)) {
                        val close1Package = ResocksPackage(id, PackageControl.CLOSE1)

                        synchronized(connection) {
                            connection.putFrame(websocketConnection.encrypt(close1Package.packageByteArray))
                        }
                        break

                    } else {
                        val close2Package = ResocksPackage(id, PackageControl.CLOSE2)

                        synchronized(connection) {
                            connection.putFrame(websocketConnection.encrypt(close2Package.packageByteArray))
                        }
                        client.close()
                        break
                    }
                }

                buffer.flip()
                val data = ByteArray(buffer.limit())
                buffer.get(data)
                buffer.compact()

                val resocksPackage = ResocksPackage(id, PackageControl.RUNNING, data)

                synchronized(connection) {
                    connection.putFrame(websocketConnection.encrypt(resocksPackage.packageByteArray))
                }
            }
        }
    }
}