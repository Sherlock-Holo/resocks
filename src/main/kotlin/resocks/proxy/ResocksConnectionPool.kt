package resocks.proxy

class ResocksConnectionPool {
    private val capacity = 6
    private val pool = BooleanArray(capacity) { true }

    @Synchronized
    fun addCoon(id: Int) {
        if (id !in 0 until capacity || !pool[id]) throw ProxyException("illegal id")
        pool[id] = false
    }

    fun removeConn(id: Int) {
        if (id !in 0 until capacity || pool[id]) throw ProxyException("illegal id")
        pool[id] = true
    }
}