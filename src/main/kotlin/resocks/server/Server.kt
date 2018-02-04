package resocks.server

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.nio.aConnect
import kotlinx.coroutines.experimental.nio.aWrite
import resocks.ResocksException
import resocks.lowLevelConnection.LowLevelConnection
import resocks.mux2.Mux2
import resocks.mux2.MuxException
import resocks.mux2.MuxPackageControl
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class Server(bindPort: Int, binAddr: String? = null) {
    init {
        LowLevelConnection.bind(bindPort, binAddr)
    }

    suspend fun start() {
        while (true) {
            val llConn = LowLevelConnection.accept()
            async { handle(llConn) }
        }
    }

    private suspend fun handle(llConn: LowLevelConnection) {
        val muxs = arrayOfNulls<Mux2>(6)

        while (true) {
            val muxPackage = llConn.read()

            when (muxPackage.control) {
                MuxPackageControl.CONNECT -> {
                    if (muxs[muxPackage.id] != null) throw MuxException("can't reconnect")

                    val addressByteArray = muxPackage.data!!
                    val parseResult = parseAddress(addressByteArray)
                    val addr = parseResult[0] as InetAddress
                    val port = parseResult[1] as Int

                    val target = AsynchronousSocketChannel.open()
                    target.aConnect(InetSocketAddress(addr, port))

                    val mux = Mux2(muxPackage.id) { llConn.putPackage(it) }
                    mux.targetSocketChannel = target
                    muxs[mux.id] = mux

                    async { mux.read1() }
                }

                MuxPackageControl.RUNNING -> {
                    if (muxs[muxPackage.id] != null) throw MuxException("no this mux: ${muxPackage.id}")

                    val mux = muxs[muxPackage.id]!!
                    mux.targetSocketChannel.aWrite(ByteBuffer.wrap(muxPackage.data))
                }
            }
        }
    }

    companion object {
        private fun parseAddress(addressByteArray: ByteArray): Array<Any> {
            val atyp = addressByteArray[0].toInt() and 0xff
            val addr = when (atyp) {
                1 -> {
                    InetAddress.getByAddress(addressByteArray.copyOfRange(1, 5))
                }

                3 -> {
                    val addrLength = addressByteArray[1].toInt() and 0xff
                    InetAddress.getByAddress(addressByteArray.copyOfRange(2, 2 + addrLength))
                }

                4 -> {
                    InetAddress.getByAddress(addressByteArray.copyOfRange(1, 17))
                }

                else -> throw ResocksException("error atyp")
            }

            val port = addressByteArray.copyOfRange(addressByteArray.size - 2, addressByteArray.size)
            return arrayOf(addr, ByteBuffer.wrap(port).short.toInt())
        }
    }
}