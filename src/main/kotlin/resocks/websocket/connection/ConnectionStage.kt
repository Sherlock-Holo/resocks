package resocks.websocket.connection

enum class ConnectionStage {
    RUNNING, CLOSING, BE_CLOSED, CLOSED;
}