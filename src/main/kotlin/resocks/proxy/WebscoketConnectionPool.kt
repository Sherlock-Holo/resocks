package resocks.proxy

import resocks.websocket.connection.ClientConnection
import resocks.websocket.connection.ConnectionStatus
import java.util.concurrent.ConcurrentLinkedQueue

class WebscoketConnectionPool(private val host: String, private val port: Int) {
    private val pool = ConcurrentLinkedQueue<WebsocketConnection>()
    private val saveQueue = ArrayList<WebsocketConnection>()

    suspend fun getCoon(): WebsocketConnection {
        try {
            while (true) {
                val clientConnection = pool.poll()
                saveQueue.add(clientConnection)
                if (clientConnection.clientConnection.connStatus == ConnectionStatus.RUNNING && clientConnection.hasID()) {
                    return clientConnection
                }
            }

        } catch (e: IllegalStateException) {
            val clientConnection = WebsocketConnection()
            clientConnection.connect()
            return clientConnection
        } finally {
            pool.addAll(saveQueue)
            saveQueue.clear()
        }
    }

    fun putCoon(clientConnection: WebsocketConnection) {
        if (clientConnection.clientConnection.connStatus == ConnectionStatus.RUNNING) pool.add(clientConnection)
    }

    inner class WebsocketConnection {
        private val capacity = 6
        private var poolSize = 0

        val clientConnection = ClientConnection(host, port)

        private val idPool = BooleanArray(capacity) { true }

        internal suspend fun connect() = clientConnection.connect()

        fun getID(): Int {
            for (i in 0 until capacity) {
                if (idPool[i]) {
                    poolSize += 1
                    idPool[i] = false
                    return i
                }
            }
            throw ProxyException("no usable id")
        }

        fun putID(id: Int) {
            when {
                id !in 0 until capacity || idPool[id] -> throw ProxyException("illegal id")

                else -> {
                    idPool[id] = true
                    poolSize -= 1
                }
            }
        }

        internal fun hasID() = poolSize < capacity
    }
}