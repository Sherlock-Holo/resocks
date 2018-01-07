package resocks.websocket.frame

import java.nio.ByteBuffer

class ServerFrame : Frame {
    private val frameHeader = ByteBuffer.allocate(10)
    private val content: ByteArray
    private val dataLength: Int

    constructor(data: ByteArray) {
        dataLength = data.size
        frameHeader.put((1 shl 7 or 2).toByte())
        when {
            dataLength <= 125 -> {
                frameHeader.put(dataLength.toByte())
            }

            dataLength <= 65535 -> {
                frameHeader.put(126.toByte())
                frameHeader.putInt(dataLength)
            }

            else -> {
                frameHeader.put(127.toByte())
                frameHeader.putLong(dataLength.toLong())
            }
        }

        frameHeader.flip()
        content = ByteArray(frameHeader.limit() + dataLength)
        System.arraycopy(frameHeader.array(), 0, content, 0, frameHeader.limit())
        System.arraycopy(data, 0, content, frameHeader.limit(), dataLength)
    }

    override fun getContent(): ByteArray {
        return content
    }
}