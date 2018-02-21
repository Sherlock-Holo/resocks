package resocks.connection

import resocks.encrypt.Cipher
import resocks.encrypt.CipherModes
import resocks.websocket.connection.ClientWebsocketConnection
import resocks.websocket.connection.ServerWebsocketConnection
import resocks.websocket.connection.WebsocketConnection

class LowLevelConnection private constructor() {
    private lateinit var websocketConnection: WebsocketConnection
    private lateinit var encryptCipher: Cipher
    private lateinit var decryptCipher: Cipher
    lateinit var pool: ConnectionPool

    var closeStatus = 0

    fun write(data: ByteArray) {
        websocketConnection.putFrame(encryptCipher.encrypt(data))
    }

    suspend fun read(): ByteArray? {
        val frame = websocketConnection.getFrame()
        val data = decryptCipher.decrypt(frame.content)

        when {
            data.contentEquals("close".toByteArray()) -> {
                println("receive close")
                closeStatus++
                return null
            }

            data.contentEquals("error".toByteArray()) -> {
                println("error stop")
                closeStatus = 2
                return null
            }

            else -> {
                return data
            }
        }
    }

    fun release() {
        println("release")
        pool.releaseConn(this)
    }

    fun stopWrite() {
        write("close".toByteArray())
        closeStatus++
    }

    fun errorStop() {
        println("error stop")
        write("error".toByteArray())
        release()
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