package resocks.connection

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.LinkedListChannel
import resocks.websocket.connection.ServerWebsocketConnection

class ServerConnectionPoll : ConnectionPool {
    private val pool = LinkedListChannel<LowLevelConnection>()

    suspend fun getConn() = pool.receive()

    private fun addConn(lowLevelConnection: LowLevelConnection) {
        pool.offer(lowLevelConnection)
    }

    override fun releaseConn(lowLevelConnection: LowLevelConnection): Boolean {
        return pool.offer(lowLevelConnection)
    }


    companion object {
        suspend fun buildPoll(key: ByteArray, port: Int, addr: String? = null): ServerConnectionPoll {
            val serverConnectionPoll = ServerConnectionPoll()
            ServerWebsocketConnection.startServer(port, addr)

            async {
                while (true) {
                    val serverWebsocketConnection = ServerWebsocketConnection.getClient()
                    println("accept new websocket connection")
                    async {
                        val lowLevelConnection = LowLevelConnection.initServer(serverWebsocketConnection, key)
                        lowLevelConnection.pool = serverConnectionPoll
                        serverConnectionPoll.addConn(lowLevelConnection)
                    }
                }
            }

            return serverConnectionPoll
        }
    }
}