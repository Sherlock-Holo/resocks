package resocks.websocket.frame

import resocks.readsBuffer.ReadsBuffer
import resocks.websocket.WebsocketException
import java.nio.ByteBuffer
import java.util.*
import kotlin.experimental.xor

class ClientFrame(type: FrameType, data: ByteArray?, maskKey: ByteArray? = null) : Frame {
    private val frameHeader = ByteBuffer.allocate(14)
    override lateinit var content: ByteArray
    override val opcode: Int
    override val frameType: FrameType = type

    init {
        when (type) {
            FrameType.TEXT -> {
                opcode = 0x1
                TODO("opcode is 0x01, data is UTF-8 text")
            }

            FrameType.BINARY -> {
                opcode = 0x2
                genBinaryFrame(data!!, maskKey)
            }

            FrameType.CLOSE -> {
                opcode = 0x8
                genControlFrame("close", data)
            }

            FrameType.PING -> {
                opcode = 0x9
                genControlFrame("ping", data)
            }

            FrameType.PONG -> {
                opcode = 0xA
                genControlFrame("pong", data)
            }
        }
    }

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

    private fun genControlFrame(type: String, controlMessage: ByteArray?) {
        if (controlMessage != null) {
            if (controlMessage.size > 125) throw WebsocketException("$type controlMessage's length ${controlMessage.size} is larger than 125")

            when (type) {
                "ping" -> {
                    frameHeader.put((1 shl 7 or 0x9).toByte())
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
                    frameHeader.put((1 shl 7 or 0x9).toByte())
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
        suspend fun receiveFrame(readsBuffer: ReadsBuffer): ClientFrame {
            val frameHeader = readsBuffer.reads(2)
            val type: FrameType

            when (frameHeader[0].toInt()) {
            // binary frame
                1 shl 7 or 0x2 -> {
                    type = FrameType.BINARY
                }
            // ping frame
                1 shl 7 or 0x9 -> {
                    type = FrameType.PING
                }
            // pong frame
                1 shl 7 or 0xA -> {
                    type = FrameType.PONG
                }
            // close frame
                1 shl 7 or 0x8 -> {
                    type = FrameType.CLOSE
                }
                else -> {
                    TODO("other opcode?")
                }
            }

            val initPayloadLength = frameHeader[1].toInt() and 0x7F
            val payloadLength: Int

            payloadLength = when {
                initPayloadLength <= 125 -> {
                    initPayloadLength
                }

                initPayloadLength == 126 -> {
                    ByteUtils.getShortFromByteArray(readsBuffer.reads(2)).toInt()
                }

                initPayloadLength == 127 -> {
                    ByteUtils.getLongFromByteArray(readsBuffer.reads(8)).toInt()
                }

                else -> TODO("other initPayloadLength?")
            }

            val maskKey = readsBuffer.reads(4)
            val data = readsBuffer.reads(payloadLength)
            return ClientFrame(type, data, maskKey)
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