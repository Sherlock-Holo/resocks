package resocks.socks

import kotlinx.coroutines.experimental.nio.aWrite
import resocks.readsBuffer.ReadsBuffer
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class Socks(val socketChannel: AsynchronousSocketChannel) {
    var version: Int? = null
        private set

    var atyp: Int? = null
        private set

    lateinit var addr: InetAddress
        private set

    var addrLength: Int? = null
        private set

    var port: Int? = null
        private set

    lateinit var portByteArray: ByteArray
        private set

    lateinit var targetAddress: ByteArray
        private set

    var isSuccessful = false
        private set

    suspend fun init() {
        val readsBuffer = ReadsBuffer(socketChannel)

        version = readsBuffer.readExactly(1)[0].toInt() and 0xff
        if (version != 5) throw SocksException("socks version is not 5")

        val nmethods = readsBuffer.readExactly(1)[0].toInt() and 0xff
        val methods = readsBuffer.readExactly(nmethods)
        val noAuth = methods.any { it.toInt() == 0 }

        if (!noAuth) throw SocksException("auth mode is not no-auth")

        val method = ByteBuffer.wrap(byteArrayOf(5, 0))
        socketChannel.aWrite(method)

        val requestHeader = readsBuffer.readExactly(4)
        val requestVersion = requestHeader[0].toInt() and 0xff
        val cmd = requestHeader[1].toInt() and 0xff
        atyp = requestHeader[3].toInt() and 0xff

        if (requestVersion != 5 || cmd != 1) throw SocksException("request error")

        when (atyp) {
            1 -> {
                addr = InetAddress.getByAddress(readsBuffer.readExactly(4))

                targetAddress = ByteArray(7)
                System.arraycopy(addr.address, 0, targetAddress, 1, 4)
            }

            3 -> {
                addrLength = readsBuffer.readExactly(1)[0].toInt() and 0xff
                addr = InetAddress.getByAddress(readsBuffer.readExactly(addrLength!!))

                targetAddress = ByteArray(1 + 1 + addrLength!! + 2)
                targetAddress[1] = addrLength!!.toByte()
                System.arraycopy(addr.address, 0, targetAddress, 2, addrLength!!)
            }

            4 -> {
                addr = InetAddress.getByAddress(readsBuffer.readExactly(16))

                targetAddress = ByteArray(19)
                System.arraycopy(addr.address, 0, targetAddress, 1, 16)
            }

            else -> throw SocksException("error atyp")
        }

        portByteArray = readsBuffer.readExactly(2)
        port = ByteBuffer.wrap(portByteArray).short.toInt()

        System.arraycopy(portByteArray, 0, targetAddress, targetAddress.size - 2, 2)

        val replyAddress = InetAddress.getByName("::1")
        val replyPort = ByteArray(2)
        ByteBuffer.wrap(replyPort).putShort(0)
        val reply = byteArrayOf(5, 0, 0, 4) + replyAddress.address + replyPort
        socketChannel.aWrite(ByteBuffer.wrap(reply))
        isSuccessful = true
    }
}