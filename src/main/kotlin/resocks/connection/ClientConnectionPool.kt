package resocks.connection

class ClientConnectionPool(private val key: ByteArray, val port: Int, val host: String) : ConnectionPool {
    private val pool = ArrayList<LowLevelConnection>()

    suspend fun getConn(): LowLevelConnection {
        synchronized(pool) {
            return if (pool.isEmpty()) {
                val lowLevelConnection = LowLevelConnection.initClient(key, host, port)
                lowLevelConnection.pool = this
                lowLevelConnection

            } else pool.removeAt(0)
        }
    }

    override fun releaseConn(lowLevelConnection: LowLevelConnection): Boolean {
        synchronized(pool) {
            return pool.add(lowLevelConnection)
        }
    }
}