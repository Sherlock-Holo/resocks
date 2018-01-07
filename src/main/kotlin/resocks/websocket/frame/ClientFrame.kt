package resocks.websocket.frame

import kotlinx.coroutines.experimental.nio.aRead
import resocks.websocket.WebsocketException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.*
import kotlin.experimental.xor

class ClientFrame : Frame {
    private val frameHeader = ByteBuffer.allocate(14)
    override lateinit var content: ByteArray
    override val opcode: Int



    constructor(opcode: Int, data: ByteArray?, maskKey: ByteArray? = null) {
        this.opcode = opcode
        when (opcode) {
            0x1 -> TODO("opcode is 0x01, data is UTF-8 text")

            0x2 -> genBinaryFrame(data!!, maskKey)

            0x3, 0x7, 0xB, 0xF -> TODO("MDN says it has no meaning")

            0x8 -> genControlFrame("close", data)

            0x9 -> genControlFrame("ping", data)

            0xA -> genControlFrame("pong", data)
        }
    }


/*    private fun genPingFrame(pingData: ByteArray?) {
        if (pingData != null) {
            if (pingData.size > 125) throw WebsocketException("ping data's length ${pingData.size} is larger than 125")

            frameHeader.put((1 shl 7 or 9).toByte())
            frameHeader.put((1 shl 7 or pingData.size).toByte())

            frameHeader.flip()

            val maskKey = ByteArray(4)
            Random().nextBytes(maskKey)
            val maskedPingData = mask(maskKey, pingData)
            content = ByteArray(frameHeader.limit() + 4 + maskedPingData.size)
            System.arraycopy(frameHeader.array(), 0, content, 0, frameHeader.limit())
            System.arraycopy(maskKey, 0, content, frameHeader.limit(), 4)
            System.arraycopy(maskedPingData, 0, content, frameHeader.limit() + 4, maskedPingData.size)
        } else {
            val data = "ping".toByteArray()

            frameHeader.put((1 shl 7 or 9).toByte())
            frameHeader.put((1 shl 7 or data.size).toByte())

            frameHeader.flip()
            val maskKey = ByteArray(4)
            Random().nextBytes(maskKey)
            val maskedPingData = mask(maskKey, data)
            content = ByteArray(frameHeader.limit() + 4 + maskedPingData.size)
            System.arraycopy(frameHeader.array(), 0, content, 0, frameHeader.limit())
            System.arraycopy(maskKey, 0, content, frameHeader.limit(), 4)
            System.arraycopy(maskedPingData, 0, content, frameHeader.limit() + 4, maskedPingData.size)
        }
    }

    private fun genPongFrame(pongData: ByteArray?) {
        if (pongData != null) {
            if (pongData.size > 125) throw WebsocketException("pong data's length ${pongData.size} is larger than 125")

            frameHeader.put((1 shl 7 or 0xA).toByte())
            frameHeader.put((1 shl 7 or pongData.size).toByte())

            frameHeader.flip()
            val maskKey = ByteArray(4)
            Random().nextBytes(maskKey)
            val maskedPingData = mask(maskKey, pongData)
            content = ByteArray(frameHeader.limit() + 4 + maskedPingData.size)
            System.arraycopy(frameHeader.array(), 0, content, 0, frameHeader.limit())
            System.arraycopy(maskKey, 0, content, frameHeader.limit(), 4)
            System.arraycopy(maskedPingData, 0, content, frameHeader.limit() + 4, maskedPingData.size)
        } else {
            val data = "pong".toByteArray()

            frameHeader.put((1 shl 7 or 9).toByte())
            frameHeader.put((1 shl 7 or data.size).toByte())

            frameHeader.flip()
            val maskKey = ByteArray(4)
            Random().nextBytes(maskKey)
            val maskedPingData = mask(maskKey, data)
            content = ByteArray(frameHeader.limit() + 4 + maskedPingData.size)
            System.arraycopy(frameHeader.array(), 0, content, 0, frameHeader.limit())
            System.arraycopy(maskKey, 0, content, frameHeader.limit(), 4)
            System.arraycopy(maskedPingData, 0, content, frameHeader.limit() + 4, maskedPingData.size)
        }
    }*/

    private fun genBinaryFrame(data: ByteArray, maskKey: ByteArray?) {
        val dataLength = data.size
        frameHeader.put((1 shl 7 or 2).toByte())

        when {
            dataLength <= 125 -> {
                frameHeader.put((1 shl 7 or dataLength).toByte())
            }

            dataLength <= 65535 -> {
                frameHeader.put((1 shl 7 or 126).toByte())
                frameHeader.putInt(dataLength)
            }

            else -> {
                frameHeader.put((1 shl 7 or 127).toByte())
                frameHeader.putLong(dataLength.toLong())
            }
        }

        if (maskKey != null) {
            frameHeader.flip()
            content = ByteArray(frameHeader.limit() + 4 + dataLength)
            System.arraycopy(frameHeader.array(), 0, content, 0, frameHeader.limit())
            System.arraycopy(maskKey, 0, content, frameHeader.limit(), 4)
            System.arraycopy(data, 0, content, frameHeader.limit() + 4, dataLength)
        } else {
            frameHeader.flip()
            val newMaskKey = ByteArray(4)
            Random().nextBytes(newMaskKey)
            val maskedData = mask(newMaskKey, data)

            content = ByteArray(frameHeader.limit() + 4 + dataLength)
            System.arraycopy(frameHeader.array(), 0, content, 0, frameHeader.limit())
            System.arraycopy(newMaskKey, 0, content, frameHeader.limit(), 4)
            System.arraycopy(maskedData, 0, content, frameHeader.limit() + 4, dataLength)
        }
    }

    private fun genCloseFrame(reason: ByteArray?) {
        if (reason != null) {
            if (reason.size > 125) throw WebsocketException("close data's length ${reason.size} is larger than 125")

            frameHeader.put((1 shl 7 or 0x8).toByte())
            frameHeader.put((1 shl 7 or reason.size).toByte())

            frameHeader.flip()
            val maskKey = ByteArray(4)
            Random().nextBytes(maskKey)
            val maskedPingData = mask(maskKey, reason)
            content = ByteArray(frameHeader.limit() + 4 + maskedPingData.size)
            System.arraycopy(frameHeader.array(), 0, content, 0, frameHeader.limit())
            System.arraycopy(maskKey, 0, content, frameHeader.limit(), 4)
            System.arraycopy(maskedPingData, 0, content, frameHeader.limit() + 4, maskedPingData.size)
        } else {
            val data = "I want to close".toByteArray()

            frameHeader.put((1 shl 7 or 9).toByte())
            frameHeader.put((1 shl 7 or data.size).toByte())

            frameHeader.flip()
            val maskKey = ByteArray(4)
            Random().nextBytes(maskKey)
            val maskedPingData = mask(maskKey, data)
            content = ByteArray(frameHeader.limit() + 4 + maskedPingData.size)
            System.arraycopy(frameHeader.array(), 0, content, 0, frameHeader.limit())
            System.arraycopy(maskKey, 0, content, frameHeader.limit(), 4)
            System.arraycopy(maskedPingData, 0, content, frameHeader.limit() + 4, maskedPingData.size)
        }
    }

    private fun genControlFrame(type: String, controlMessage: ByteArray?) {
        if (controlMessage != null) {
            if (controlMessage.size > 125) throw WebsocketException("$type controlMessage's length ${controlMessage.size} is larger than 125")

            when (type) {
                "ping" -> {
                    frameHeader.put((1 shl 7 or 0xA).toByte())
                }

                "pong" -> {
                    frameHeader.put((1 shl 7 or 0xA).toByte())
                }

                "close" -> {
                    frameHeader.put((1 shl 7 or 0x8).toByte())
                }

                else -> TODO("other type?")
            }

            frameHeader.put((1 shl 7 or controlMessage.size).toByte())

            frameHeader.flip()
            val maskKey = ByteArray(4)
            Random().nextBytes(maskKey)
            val maskedPingData = mask(maskKey, controlMessage)
            content = ByteArray(frameHeader.limit() + 4 + maskedPingData.size)
            System.arraycopy(frameHeader.array(), 0, content, 0, frameHeader.limit())
            System.arraycopy(maskKey, 0, content, frameHeader.limit(), 4)
            System.arraycopy(maskedPingData, 0, content, frameHeader.limit() + 4, maskedPingData.size)
        } else {
            val message: ByteArray
            when (type) {
                "ping" -> {
                    frameHeader.put((1 shl 7 or 0xA).toByte())
                    message = "ping".toByteArray()
                }

                "pong" -> {
                    frameHeader.put((1 shl 7 or 0xA).toByte())
                    message = "pong".toByteArray()
                }

                "close" -> {
                    frameHeader.put((1 shl 7 or 0x8).toByte())
                    message = "close".toByteArray()
                }

                else -> TODO("other type?")
            }

            frameHeader.put((1 shl 7 or message.size).toByte())

            frameHeader.flip()
            val maskKey = ByteArray(4)
            Random().nextBytes(maskKey)
            val maskedPingData = mask(maskKey, message)
            content = ByteArray(frameHeader.limit() + 4 + maskedPingData.size)
            System.arraycopy(frameHeader.array(), 0, content, 0, frameHeader.limit())
            System.arraycopy(maskKey, 0, content, frameHeader.limit(), 4)
            System.arraycopy(maskedPingData, 0, content, frameHeader.limit() + 4, maskedPingData.size)
        }
    }






    companion object {
        suspend fun receiveFrame(socket: AsynchronousSocketChannel): ClientFrame {
            val frameHeader = ByteBuffer.allocate(14)
            var haveRead = 0
            while (haveRead <= 2) {
                haveRead += socket.aRead(frameHeader)
            }
        }

        private fun mask(maskKey: ByteArray, data: ByteArray): ByteArray {
            val maskedData = ByteArray(data.size)
            for (i in 0 until data.size) {
                maskedData[i] = data[i] xor maskKey[i % 4]
            }
            return maskedData
        }
    }
}