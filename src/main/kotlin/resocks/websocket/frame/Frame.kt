package resocks.websocket.frame

interface Frame {
    val content: ByteArray
    val contentType: FrameContentType
    val frameType: FrameType
    val frameByteArray: ByteArray
}