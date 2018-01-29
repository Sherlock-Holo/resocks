package resocks.proxy

import resocks.ResocksException
import resocks.websocket.connection.ClientConnection
import resocks.websocket.connection.ConnectionStatus

class WebscoketConnectionPool {
    private val host: String
    private val port: Int
    private val pool = ArrayList<WebsocketConnection>()

    constructor(host: String, port: Int) {
        this.host = host
        this.port = port
    }

    constructor(websocketAddress: String) {
        if (!websocketAddress.startsWith("ws://")) throw ResocksException("websocketAddress should start with \"ws://\"")
        val address = websocketAddress.removePrefix("ws://")
        host = address.substring(0, address.lastIndexOf(':'))
        port = address.substring(address.lastIndexOf(':') + 1, address.length).toInt()
    }

    suspend fun getCoon(): WebsocketConnection {
        synchronized(pool) {
            val iter = pool.iterator()
            while (iter.hasNext()) {
                val clientConnection = iter.next()
                when {
                    clientConnection.clientConnection.connStatus == ConnectionStatus.RUNNING && clientConnection.hasID() -> return clientConnection
                    clientConnection.clientConnection.connStatus == ConnectionStatus.CLOSED -> iter.remove()
                }
            }
        }

        val clientConnection = WebsocketConnection()
        clientConnection.connect()
        pool.add(clientConnection)
        return clientConnection
    }

    inner class WebsocketConnection {
        private val capacity = 6
        private var poolSize = 0

        val clientConnection = ClientConnection(host, port)

        private val idPool = BooleanArray(capacity) { true }

        internal suspend fun connect() = clientConnection.connect()

        @Synchronized
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