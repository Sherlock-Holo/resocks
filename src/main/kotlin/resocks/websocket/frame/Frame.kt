package resocks.websocket.frame

interface Frame {
    val opcode: Int
    var content: ByteArray
}