package resocks.websocket.frame

interface Frame {
    val content: ByteArray
    var opcode: Int
    val contentType: FrameContentType
    val frameType: FrameType
    val frameByteArray: ByteArray
}