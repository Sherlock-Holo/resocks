package resocks.client

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.nio.aAccept
import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import resocks.lowLevelConnection.LowLevelConnection
import resocks.mux2.Mux2
import resocks.mux2.MuxPackageControl
import resocks.socks.Socks
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel

class Client(bindAddr: String, bindPort: Int, private val proxyAddr: String, private val proxyPort: Int) {
    private val bindSocketChannel = AsynchronousServerSocketChannel.open()

    init {
        bindSocketChannel.bind(InetSocketAddress(bindAddr, bindPort))
    }

    suspend fun start() {
        while (true) {
            val socketChannel = bindSocketChannel.aAccept()
            val socks = getSocks(socketChannel)

            async { handle(socks) }
        }
    }

    private suspend fun getSocks(socketChannel: AsynchronousSocketChannel): Socks {
        val socks = Socks(socketChannel)
        socks.init()
        return socks
    }

    private suspend fun handle(socks: Socks) {
        val llConn = LowLevelConnection.connect(proxyAddr, proxyPort)
        val mux = Mux2(llConn.getID()) { llConn.write(it) }
        val socksSocketChannel = socks.socketChannel
        mux.write(socks.targetAddress, MuxPackageControl.CONNECT)

        async {
            val sendBuffer = ByteBuffer.allocate(8192)
            while (true) {
                if (socksSocketChannel.aRead(sendBuffer) <= 0) TODO()

                sendBuffer.flip()
                val data = ByteArray(sendBuffer.limit())
                sendBuffer.get(data)
                sendBuffer.clear()
                mux.write(data)
            }
        }

        async {
            val receiveBuffer = ByteBuffer.allocate(8192)
            while (true) {
                val muxPackage = mux.read()
                receiveBuffer.put(muxPackage.data)
                receiveBuffer.flip()
                socksSocketChannel.aWrite(receiveBuffer)
                receiveBuffer.compact()
            }
        }
    }
}