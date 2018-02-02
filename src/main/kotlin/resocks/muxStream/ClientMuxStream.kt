package resocks.muxStream

import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.*

class ClientMuxStream(
        val socksSocketChannel: AsynchronousSocketChannel, private val host: String, private val port: Int, private val key: ByteArray
) {
    
    private lateinit var llConn: LowLevelConnection
    var id: Int? = null

    suspend fun init() {
        llConn = getLLConn(host, port, key)
        id = llConn.addMuxStream(this)

        val hostByteArray = InetAddress.getByName(host).address

        val portByteArray = ByteArray(2)
        ByteBuffer.wrap(portByteArray).putShort(port.toShort()).flip()

        val muxPackage = MuxPackage(id!!, PackageControl.CONNECT, hostByteArray + portByteArray)
        llConn.write(muxPackage)
    }

    fun write(data: ByteArray) {
        val muxPackage = MuxPackage(id!!, PackageControl.RUNNING, data)
        llConn.write(muxPackage)
    }

    companion object {
        private val pool = ArrayList<LowLevelConnection>()

        suspend fun getLLConn(host: String, port: Int, key: ByteArray): LowLevelConnection {
//            return pool.firstOrNull { !it.isFull() } ?: LowLevelConnection(host, port, key).connect()
            var llConn = pool.firstOrNull { !it.isFull() }
            return if (llConn != null) llConn
            else {
                llConn = LowLevelConnection(host, port, key)
                llConn.connect()
                pool.add(llConn)
                llConn
            }
        }
    }
}