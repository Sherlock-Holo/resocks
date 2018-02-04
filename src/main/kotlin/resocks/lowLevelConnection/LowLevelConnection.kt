package resocks.lowLevelConnection

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.LinkedListChannel
import resocks.encrypt.Cipher
import resocks.encrypt.CipherModes
import resocks.mux2.MuxPackage
import resocks.websocket.connection.ClientConnection
import resocks.websocket.connection.ConnectionStatus
import resocks.websocket.connection.ServerConnection
import resocks.websocket.connection.WebsocketConnection
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousServerSocketChannel
import java.util.concurrent.ConcurrentHashMap

class LowLevelConnection {
    val encryptCipher = Cipher(CipherModes.AES_256_CTR, key)
    lateinit var decryptCipher: Cipher
        private set

    /*private lateinit var clientConnection: ClientConnection
    private lateinit var serverConnection: ServerConnection*/

    private lateinit var websocketConnection: WebsocketConnection

    private val readQueueMap = ConcurrentHashMap<Int, LinkedListChannel<MuxPackage>>()
    private val capacity = 6
    private var size = 0

    fun isFull() = size >= capacity

    fun setReadQueue(id: Int, readDataQueue: LinkedListChannel<MuxPackage>) {
        synchronized(readQueueMap) {
            if (!readQueueMap.containsKey(id)) readQueueMap[id] = readDataQueue
        }
        size++
    }

    private suspend fun read1() {
        while (true) {
            val frame = websocketConnection.getFrame()
            val muxPackage = MuxPackage.makePackage(decryptCipher.decrypt(frame.content))
            if (readQueueMap.containsKey(muxPackage.id)) {
                val readQueue = readQueueMap[muxPackage.id]!!
                readQueue.offer(muxPackage)
            }
        }
    }

    fun write(data: ByteArray) {
        websocketConnection.putFrame(encryptCipher.encrypt(data))
    }


    companion object {
        private val llConnPool = ArrayList<LowLevelConnection>()
        lateinit var key: ByteArray

        suspend fun connect(host: String, port: Int): LowLevelConnection {
            synchronized(llConnPool) {
                val iter = llConnPool.iterator()
                while (iter.hasNext()) {
                    val clientConnection = iter.next().websocketConnection
                    if (clientConnection.connStatus == ConnectionStatus.CLOSED) iter.remove()
                }

                for (i in 0 until llConnPool.size) {
                    if (!llConnPool[i].isFull()) return llConnPool[i]
                }
            }

            val clientConnection = ClientConnection(host, port)
            clientConnection.connect()

            val lowLevelConnection = LowLevelConnection()
//            lowLevelConnection.clientConnection = clientConnection
            lowLevelConnection.websocketConnection = clientConnection

            lowLevelConnection.websocketConnection.putFrame(lowLevelConnection.encryptCipher.IVorNonce!!)
            val frame = lowLevelConnection.websocketConnection.getFrame()
            lowLevelConnection.decryptCipher = Cipher(CipherModes.AES_256_CTR, key, frame.content)



            async { lowLevelConnection.read1() }

            llConnPool.add(lowLevelConnection)
            return lowLevelConnection
        }


        fun bind(addr: String, port: Int) {
            val serverSocketChannel = AsynchronousServerSocketChannel.open()
            serverSocketChannel.bind(InetSocketAddress(addr, port))
            ServerConnection.startServer(serverSocketChannel)
        }

        suspend fun accept(): LowLevelConnection {
            val serverConnection = ServerConnection.getClient()
            val lowLevelConnection = LowLevelConnection()
//            lowLevelConnection.serverConnection = serverConnection
            lowLevelConnection.websocketConnection = serverConnection

            lowLevelConnection.websocketConnection.putFrame(lowLevelConnection.encryptCipher.IVorNonce!!)
            val frame = lowLevelConnection.websocketConnection.getFrame()
            lowLevelConnection.decryptCipher = Cipher(CipherModes.AES_256_CTR, key, frame.content)
            return lowLevelConnection
        }
    }
}