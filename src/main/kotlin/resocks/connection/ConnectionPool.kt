package resocks.connection

import resocks.websocket.connection.ServerWebsocketConnection

class ConnectionPool(private val key: ByteArray, val port: Int, val host: String? = null) {
    private val pool = ArrayList<LowLevelConnection>()

    private val forClient: Boolean

    init {
        if (host != null) {
            forClient = true
        } else {
            forClient = false
            ServerWebsocketConnection.startServer(port)
        }
    }


    suspend fun getConn(): LowLevelConnection {
        synchronized(pool) {
            return if (forClient) {
                if (pool.isEmpty()) {
                    val lowLevelConnection = LowLevelConnection.initClient(key, host!!, port)
                    lowLevelConnection.pool = this
                    return lowLevelConnection

                } else pool.removeAt(0)

            } else {
                if (pool.isEmpty()) {
                    val lowLevelConnection = LowLevelConnection.initServer(ServerWebsocketConnection.getClient(), key)
                    lowLevelConnection.pool = this
                    return lowLevelConnection
                    
                } else pool.removeAt(0)
            }
        }
    }

    fun releaseConn(lowLevelConnection: LowLevelConnection): Boolean {
        synchronized(pool) {
            return pool.add(lowLevelConnection)
        }
    }
}