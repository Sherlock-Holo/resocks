package resocks.connection

interface ConnectionPool {
    fun releaseConn(lowLevelConnection: LowLevelConnection): Boolean
}