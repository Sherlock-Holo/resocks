package resocks.websocket.connection

import resocks.websocket.frame.Frame
import resocks.websocket.frame.FrameContentType

interface WebsocketConnection {
    var connStatus: ConnectionStatus

    suspend fun getFrame(): Frame

    fun putFrame(data: ByteArray): Boolean

    fun putFrame(data: ByteArray, contentType: FrameContentType): Boolean
}