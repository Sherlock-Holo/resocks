package resocks.proxy

import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.ConcurrentHashMap

class ResocksConnectionPool {
    private val capacity = 6
    private val pool = ConcurrentHashMap<Int, AsynchronousSocketChannel>(capacity)

    fun addCoon(id: Int, socketChannel: AsynchronousSocketChannel) {
        pool[id] = socketChannel
    }

    fun getConn(id: Int): AsynchronousSocketChannel {
        return pool[id] ?: throw ProxyException("error id")
    }

    fun removeConn(id: Int): AsynchronousSocketChannel {
        return pool.remove(id) ?: throw ProxyException("error id")
    }

    fun hasConn(id: Int) = pool.containsKey(id)
}