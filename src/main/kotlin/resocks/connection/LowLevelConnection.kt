package resocks.connection

import resocks.encrypt.Cipher
import resocks.encrypt.CipherModes
import resocks.websocket.connection.ClientWebsocketConnection
import resocks.websocket.connection.ServerWebsocketConnection
import resocks.websocket.connection.WebsocketConnection
import java.io.IOException

class LowLevelConnection private constructor() {
    private lateinit var websocketConnection: WebsocketConnection
    private lateinit var encryptCipher: Cipher
    private lateinit var decryptCipher: Cipher
    lateinit var pool: ConnectionPool

    var socketIsClosed = false
        private set

    var isRelease = false

    fun write(data: ByteArray) {
        if (!socketIsClosed) websocketConnection.putFrame(encryptCipher.encrypt(data))

        else throw IOException("lowLevelConnection is closed")
    }

    suspend fun read(): ByteArray? {
        if (socketIsClosed) return null

        val frame = websocketConnection.getFrame()
        val data = decryptCipher.decrypt(frame.content)

        return if (data.contentEquals("close".toByteArray())) {
            println("receive close")
            socketIsClosed = true
            null
        } else {
            data
        }
    }

    fun release() {
        /*if (!isRelease) pool.releaseConn(this)
        else return*/
        if (!isRelease) {
            isRelease = true
        } else {
            println("release")
            pool.releaseConn(this)
        }
    }

    fun close() {
        write("close".toByteArray())

        socketIsClosed = true

    }


    companion object {
        suspend fun initClient(key: ByteArray, host: String, port: Int): LowLevelConnection {
            val clientWebsocketConnection = ClientWebsocketConnection(host, port)
            clientWebsocketConnection.connect()
//            println("create new client websocket connection")

            val lowLevelConnection = LowLevelConnection()
            lowLevelConnection.websocketConnection = clientWebsocketConnection

            lowLevelConnection.encryptCipher = Cipher(CipherModes.AES_256_CTR, key)
            clientWebsocketConnection.putFrame(lowLevelConnection.encryptCipher.IVorNonce!!)

            val decryptCipherIV = clientWebsocketConnection.getFrame().content
            lowLevelConnection.decryptCipher = Cipher(CipherModes.AES_256_CTR, key, decryptCipherIV)

            return lowLevelConnection
        }

        suspend fun initServer(serverWebsocketConnection: ServerWebsocketConnection, key: ByteArray): LowLevelConnection {
            val lowLevelConnection = LowLevelConnection()
            lowLevelConnection.websocketConnection = serverWebsocketConnection

            val decryptCipherIV = serverWebsocketConnection.getFrame().content
            lowLevelConnection.decryptCipher = Cipher(CipherModes.AES_256_CTR, key, decryptCipherIV)

            lowLevelConnection.encryptCipher = Cipher(CipherModes.AES_256_CTR, key)
            serverWebsocketConnection.putFrame(lowLevelConnection.encryptCipher.IVorNonce!!)

//            println("lowLevelConnection handshake finished")

            return lowLevelConnection
        }
    }
}