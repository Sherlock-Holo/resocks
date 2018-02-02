package resocks.proxy

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.nio.aWrite
import resocks.ResocksException
import resocks.encrypt.Cipher
import resocks.encrypt.CipherModes
import resocks.websocket.connection.ClientConnection
import resocks.websocket.connection.ConnectionStatus
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.ConcurrentHashMap

class WebscoketConnectionPool {
    private val host: String
    private val port: Int
    private val pool = ArrayList<WebsocketConnection>()

    private val key: ByteArray

    constructor(host: String, port: Int, key: ByteArray) {
        this.host = host
        this.port = port
        this.key = key
    }

    constructor(websocketAddress: String, key: ByteArray) {
        if (!websocketAddress.startsWith("ws://")) throw ResocksException("websocketAddress should start with \"ws://\"")
        val address = websocketAddress.removePrefix("ws://")
        host = address.substring(0, address.lastIndexOf(':'))
        port = address.substring(address.lastIndexOf(':') + 1, address.length).toInt()
        this.key = key
    }

    suspend fun getCoon(): WebsocketConnection {
        synchronized(pool) {
            val iter = pool.iterator()
            while (iter.hasNext()) {
                val clientConnection = iter.next()
                when {
                    clientConnection.clientConnection.connStatus == ConnectionStatus.RUNNING && clientConnection.hasID() -> return clientConnection
                    clientConnection.clientConnection.connStatus == ConnectionStatus.CLOSED -> iter.remove()
                }
            }
        }

        val clientConnection = WebsocketConnection()
        clientConnection.connect()
        pool.add(clientConnection)
        return clientConnection
    }

    inner class WebsocketConnection {
        private val capacity = 6
        private var poolSize = 0

        private val socketChannelMap = ConcurrentHashMap<Int, AsynchronousSocketChannel>()

        private val encryptCipher = Cipher(CipherModes.AES_256_CTR, key)
        private lateinit var decryptCipher: Cipher

        val clientConnection = ClientConnection(host, port)

        private val idPool = BooleanArray(capacity) { true }

        fun encrypt(plainData: ByteArray) = encryptCipher.encrypt(plainData)

        fun decrypt(cipherData: ByteArray) = decryptCipher.decrypt(cipherData)

        internal suspend fun connect() {
            clientConnection.connect()
            clientConnection.putFrame(encryptCipher.IVorNonce!!)
            decryptCipher = Cipher(CipherModes.AES_256_CTR, key, clientConnection.getFrame().content)

            async {
                while (true) {
                    val frame = clientConnection.getFrame()
                    val resocksPackage = ResocksPackage.makePackage(decrypt(frame.content))
                    when (resocksPackage.control) {
                        PackageControl.RUNNING -> {
                            if (socketChannelMap.containsKey(resocksPackage.id)) {
                                val socketChannel = socketChannelMap[resocksPackage.id]!!
                                socketChannel.aWrite(ByteBuffer.wrap(resocksPackage.data))
                            }
                        }

                        PackageControl.CLOSE1 -> {
                            if (socketChannelMap.containsKey(resocksPackage.id)) {
                                val socketChannel = socketChannelMap[resocksPackage.id]!!
                                socketChannel.shutdownOutput()
                                removeID(resocksPackage.id)
                            }
                        }

                        PackageControl.CLOSE2 -> {
                            if (socketChannelMap.containsKey(resocksPackage.id)) {
                                val socketChannel = socketChannelMap[resocksPackage.id]!!
                                socketChannel.shutdownOutput()
                                removeID(resocksPackage.id)
                                socketChannel.close()
                            }
                        }
                    }
                }
            }
        }

        @Synchronized
        fun getID(): Int {
            for (i in 0 until capacity) {
                if (idPool[i]) {
                    poolSize += 1
                    idPool[i] = false
                    return i
                }
            }
            throw ProxyException("no usable id")
        }

        fun removeID(id: Int) {
            when {
                id !in 0 until capacity || idPool[id] -> throw ProxyException("illegal id")

                else -> {
                    idPool[id] = true
                    poolSize -= 1
                    socketChannelMap.remove(id)
                }
            }
        }

        internal fun hasID() = poolSize < capacity

        fun hasConn(id: Int) = socketChannelMap.containsKey(id)

        fun setConn(id: Int, socketChannel: AsynchronousSocketChannel) {
            if (!socketChannelMap.containsKey(id)) socketChannelMap[id] = socketChannel
            else TODO()
        }
    }
}