package resocks.connection

import resocks.encrypt.Cipher
import resocks.encrypt.CipherModes
import resocks.websocket.connection.ClientWebsocketConnection
import resocks.websocket.connection.ServerWebsocketConnection
import resocks.websocket.connection.WebsocketConnection
import java.nio.channels.AsynchronousSocketChannel

class LowLevelConnection private constructor() {
    private lateinit var websocketConnection: WebsocketConnection
    private lateinit var encryptCipher: Cipher
    private lateinit var decryptCipher: Cipher
    lateinit var pool: ConnectionPool

    fun write(data: ByteArray) {
        websocketConnection.putFrame(encryptCipher.encrypt(data))
    }

    suspend fun read(): ByteArray? {
//        if (errorStatus) throw LowLevelConnectionException("socketChannel is error")
//        if (finStatus) throw LowLevelConnectionException("socketChannel is close")

        val frame = websocketConnection.getFrame()
        val data = decryptCipher.decrypt(frame.content)

        return when {
            data.contentEquals("writeFin".toByteArray()) -> {
                println("receive fin")
                null
            }

            data.contentEquals("error".toByteArray()) -> {
                throw LowLevelConnectionException("receive error")
            }

            else -> data
        }
    }

    private fun release() {
        pool.releaseConn(this)
    }

    fun writeFin(socketChannel: AsynchronousSocketChannel) {
        write("writeFin".toByteArray())

        socketChannel.close()
        release()
    }

    fun readFin(socketChannel: AsynchronousSocketChannel) {
        socketChannel.close()
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