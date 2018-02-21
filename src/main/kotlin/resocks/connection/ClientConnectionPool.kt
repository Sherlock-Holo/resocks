package resocks.connection

import java.util.concurrent.ConcurrentLinkedDeque

class ClientConnectionPool(private val key: ByteArray, val port: Int, val host: String) : ConnectionPool {
    private val pool = ConcurrentLinkedDeque<LowLevelConnection>()

    suspend fun getConn(): LowLevelConnection {
        /*synchronized(pool) {
            return if (pool.isEmpty()) {
                val lowLevelConnection = LowLevelConnection.initClient(key, host, port)
                println("create new lowLevelConnection")
                lowLevelConnection.pool = this
                lowLevelConnection

            } else pool.removeAt(0)
        }*/

        return if (pool.isEmpty()) {
            val lowLevelConnection = LowLevelConnection.initClient(key, host, port)
//            println("create new lowLevelConnection")
            lowLevelConnection.pool = this
            lowLevelConnection

        } else pool.poll()
    }

    override fun releaseConn(lowLevelConnection: LowLevelConnection): Boolean {
        /*synchronized(pool) {
            return pool.add(lowLevelConnection)
        }*/
        return pool.add(lowLevelConnection)
    }
}