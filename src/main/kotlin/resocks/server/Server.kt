package resocks.server

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.nio.aConnect
import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import resocks.encrypt.Cipher
import resocks.encrypt.CipherModes
import resocks.proxy.PackageControl
import resocks.proxy.ResocksConnectionPool
import resocks.proxy.ResocksPackage
import resocks.websocket.connection.ServerConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class Server(private val port: Int, password: String, private val host: String? = null) {
    private val key = Cipher.password2key(password)

    suspend fun start() {
        if (host == null) {
            ServerConnection.start(port) { start1(it) }
        } else ServerConnection.start(host, port) { start1(it) }
    }

    private suspend fun start1(connection: ServerConnection) {
        val pool = ResocksConnectionPool()

        val decryptCipher = Cipher(CipherModes.AES_256_CTR, key, connection.getFrame().content)
        val encryptCipher = Cipher(CipherModes.AES_256_CTR, key)

        connection.putFrame(encryptCipher.IVorNonce!!)

        while (true) {
            val frame = connection.getFrame()
            val resocksPackage = ResocksPackage.makePackage(decryptCipher.decrypt(frame.content))
            val id = resocksPackage.id
            when (resocksPackage.control) {
                PackageControl.CONNECT -> {
                    val atyp = resocksPackage.packageByteArray[0].toInt() and 0xff

                    val addr: ByteArray
                    val port: Int

                    when (atyp) {
                        1 -> {
                            addr = resocksPackage.packageByteArray.copyOfRange(1, 4)
                            port = ByteBuffer.wrap(resocksPackage.packageByteArray.copyOfRange(5, 6)).short.toInt()
                        }

                        3 -> {
                            val addrLength = resocksPackage.packageByteArray[1].toInt() and 0xff
                            addr = resocksPackage.packageByteArray.copyOfRange(2, addrLength - 1)
                            port = ByteBuffer.wrap(resocksPackage.packageByteArray.copyOfRange(addrLength, addrLength + 1)).short.toInt()

                        }

                        4 -> {
                            addr = resocksPackage.packageByteArray.copyOfRange(1, 16)
                            port = ByteBuffer.wrap(resocksPackage.packageByteArray.copyOfRange(17, 18)).short.toInt()
                        }

                        else -> TODO("other atyp")
                    }

                    val targetServer = AsynchronousSocketChannel.open()
                    val address = InetSocketAddress(InetAddress.getByAddress(addr), port)
                    targetServer.aConnect(address)
                    pool.addCoon(id, targetServer)

                    async {
                        val buffer = ByteBuffer.allocate(8192)
                        while (true) {
                            if (targetServer.aRead(buffer) <= 0) {
                                if (pool.hasConn(id)) {
                                    targetServer.shutdownInput()
                                    val close1Package = ResocksPackage(id, PackageControl.CLOSE1)
                                    connection.putFrame(encryptCipher.encrypt(close1Package.packageByteArray))
                                    break

                                } else {
                                    targetServer.shutdownInput()
                                    val close2Package = ResocksPackage(id, PackageControl.CLOSE2)
                                    connection.putFrame(encryptCipher.encrypt(close2Package.packageByteArray))
                                    targetServer.close()
                                    break
                                }
                            }

                            buffer.flip()
                            val data = ByteArray(buffer.limit())
                            buffer.get(data)
                            buffer.compact()
                            val targetServerPackage = ResocksPackage(id, PackageControl.RUNNING, data)
                            connection.putFrame(encryptCipher.encrypt(targetServerPackage.packageByteArray))
                        }
                    }
                }

                PackageControl.RUNNING -> {
                    val targetServer = pool.getConn(id)
                    targetServer.aWrite(ByteBuffer.wrap(resocksPackage.packageByteArray))
                }

                PackageControl.CLOSE1 -> {
                    val targetServer = pool.getConn(id)
                    targetServer.shutdownOutput()
                    pool.removeConn(id)
                }

                PackageControl.CLOSE2 -> {
                    val targetServer = pool.getConn(id)
                    targetServer.shutdownOutput()
                    pool.removeConn(id)
                    targetServer.close()
                }
            }
        }
    }
}