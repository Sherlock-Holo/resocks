package resocks.websocket.frame

interface Frame {
    var opcode: Int?
    var content: ByteArray
    val frameType: FrameType
}