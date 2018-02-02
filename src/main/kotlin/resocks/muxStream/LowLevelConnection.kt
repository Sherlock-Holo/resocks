package resocks.muxStream

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.LinkedListChannel
import kotlinx.coroutines.experimental.nio.aWrite
import resocks.encrypt.Cipher
import resocks.encrypt.CipherModes
import resocks.websocket.connection.ClientConnection
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

class LowLevelConnection(host: String, port: Int, private val key: ByteArray) {
    private val websocketConnection = ClientConnection(host, port)

    private val muxPool = ConcurrentHashMap<Int, ClientMuxStream>()
    private val muxPoolCapacity = 6
    private var muxPoolSize = 0

    private val sendQueue = LinkedListChannel<MuxPackage>()

    private val encryptCipher = Cipher(CipherModes.AES_256_CTR, key)
    private lateinit var decryptCipher: Cipher

    suspend fun connect(): LowLevelConnection {
        websocketConnection.connect()

        websocketConnection.putFrame(encryptCipher.IVorNonce!!)
        val ivFrame = websocketConnection.getFrame()
        decryptCipher = Cipher(CipherModes.AES_256_CTR, key, ivFrame.content)

        async { write1() }
        async { read() }

        return this
    }

    fun isFull() = muxPoolSize < muxPoolCapacity

    @Synchronized
    fun addMuxStream(clientMuxStream: ClientMuxStream): Int {
        val id: Int = (0 until 6).first { !muxPool.containsKey(it) }

        muxPool[id] = clientMuxStream
        muxPoolSize += 1
        return id
    }

    fun removeMuxStream(id: Int) {
        if (!muxPool.containsKey(id)) throw MuxException("remove illegal id")

        muxPool.remove(id)
        muxPoolSize -= 1
    }

    private suspend fun write1() {
        while (true) {
            val muxPackage = sendQueue.receiveOrNull()
            if (muxPackage != null) {
                websocketConnection.putFrame(encryptCipher.encrypt(muxPackage.packageByteArray))
            }
        }
    }

    fun write(muxPackage: MuxPackage) {
        sendQueue.offer(muxPackage)
    }

    private suspend fun read() {
        while (true) {
            val frame = websocketConnection.getFrame()
            val muxPackage = MuxPackage.makePackage(decryptCipher.decrypt(frame.content))
            if (muxPool.containsKey(muxPackage.id)) {
                val clientSocketChannel = muxPool[muxPackage.id]!!
                clientSocketChannel.socksSocketChannel.aWrite(ByteBuffer.wrap(muxPackage.data))
            }
        }
    }
}