package resocks.lowLevelConnection

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.LinkedListChannel
import resocks.encrypt.Cipher
import resocks.encrypt.CipherModes
import resocks.mux2.MuxException
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

    private lateinit var websocketConnection: WebsocketConnection

    private var size = 0
    private val capacity = 6

    private val idPool = BooleanArray(capacity) { false }
    private val readQueueMap = ConcurrentHashMap<Int, LinkedListChannel<MuxPackage>>()

    private val writeQueue = LinkedListChannel<MuxPackage>()


    fun isFull() = size >= capacity

    fun setReadQueue(id: Int, readDataQueue: LinkedListChannel<MuxPackage>) {
        synchronized(readQueueMap) {
            if (!readQueueMap.containsKey(id)) readQueueMap[id] = readDataQueue
        }
        size++
    }

    fun getID(): Int {
        for (id in 0 until capacity) {
            if (idPool[id]) return id
        }
        throw MuxException("no usable id")
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

    private suspend fun write1() {
        while (true) {
            val muxPackage = writeQueue.receive()
            websocketConnection.putFrame(encryptCipher.encrypt(muxPackage.packageByteArray))
        }
    }

    suspend fun read(): MuxPackage {
        val frame = websocketConnection.getFrame()
        return MuxPackage.makePackage(decryptCipher.decrypt(frame.content))
    }

    fun write(data: ByteArray) {
        websocketConnection.putFrame(encryptCipher.encrypt(data))
    }

    fun putPackage(muxPackageByteArray: ByteArray) {
        val muxPackage = MuxPackage.makePackage(muxPackageByteArray)
        writeQueue.offer(muxPackage)
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
            lowLevelConnection.websocketConnection = clientConnection

            lowLevelConnection.websocketConnection.putFrame(lowLevelConnection.encryptCipher.IVorNonce!!)
            val frame = lowLevelConnection.websocketConnection.getFrame()
            lowLevelConnection.decryptCipher = Cipher(CipherModes.AES_256_CTR, key, frame.content)



            async { lowLevelConnection.read1() }

            llConnPool.add(lowLevelConnection)
            return lowLevelConnection
        }


        fun bind(port: Int, addr: String? = null) {
            val serverSocketChannel = AsynchronousServerSocketChannel.open()

            if (addr != null) serverSocketChannel.bind(InetSocketAddress(addr, port))
            else serverSocketChannel.bind(InetSocketAddress(port))

            ServerConnection.startServer(serverSocketChannel)
        }

        suspend fun accept(): LowLevelConnection {
            val serverConnection = ServerConnection.getClient()
            val lowLevelConnection = LowLevelConnection()
            lowLevelConnection.websocketConnection = serverConnection

            lowLevelConnection.websocketConnection.putFrame(lowLevelConnection.encryptCipher.IVorNonce!!)
            val frame = lowLevelConnection.websocketConnection.getFrame()
            lowLevelConnection.decryptCipher = Cipher(CipherModes.AES_256_CTR, key, frame.content)

            async { lowLevelConnection.write1() }
            return lowLevelConnection
        }
    }
}