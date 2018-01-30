package resocks.proxy

import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.ConcurrentHashMap

class ResocksConnectionPool {
    /*private val capacity = 6
    private val pool = BooleanArray(capacity) { true }

    @Synchronized
    fun addCoon(id: Int) {
        if (id !in 0 until capacity || !pool[id]) throw ProxyException("illegal id")
        pool[id] = false
    }

    fun removeConn(id: Int) {
        if (id !in 0 until capacity || pool[id]) throw ProxyException("illegal id")
        pool[id] = true
    }*/

    private val capacity = 6
    private val pool = ConcurrentHashMap<Int, AsynchronousSocketChannel>(capacity)

    fun addCoon(id: Int, socketChannel: AsynchronousSocketChannel) {
        pool[id] = socketChannel
    }

    fun getConn(id: Int): AsynchronousSocketChannel {
        return pool[id] ?: throw ProxyException("error id")
    }

    fun remove(id: Int) = pool.remove(id)
}