package resocks.connection

import java.util.concurrent.ConcurrentLinkedDeque

class ClientConnectionPool(private val key: ByteArray, val port: Int, val host: String) : ConnectionPool {
    private val pool = ConcurrentLinkedDeque<LowLevelConnection>()

    suspend fun getConn(): LowLevelConnection {
        return if (pool.isEmpty()) {
            val lowLevelConnection = LowLevelConnection.initClient(key, host, port)
//            println("create new lowLevelConnection")
            lowLevelConnection.pool = this
            lowLevelConnection

        } else pool.poll()
    }

    override fun releaseConn(lowLevelConnection: LowLevelConnection): Boolean {
        return pool.add(lowLevelConnection)
    }
}