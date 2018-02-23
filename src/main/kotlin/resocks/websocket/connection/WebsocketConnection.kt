package resocks.websocket.connection

import resocks.websocket.frame.Frame
import resocks.websocket.frame.FrameContentType

interface WebsocketConnection {
    var connStatus: ConnectionStatus

    suspend fun getFrame(): Frame

    suspend fun putFrame(data: ByteArray)

    suspend fun putFrame(data: ByteArray, contentType: FrameContentType)
}