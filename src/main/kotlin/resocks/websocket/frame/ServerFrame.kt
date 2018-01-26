package resocks.websocket.frame

import resocks.readsBuffer.ReadsBuffer
import resocks.websocket.WebsocketException
import java.nio.ByteBuffer

class ServerFrame(override val frameType: FrameType, data: ByteArray?) : Frame {
    override lateinit var content: ByteArray
    override var opcode: Int? = null

    init {
        when (frameType) {
            FrameType.TEXT -> TODO("opcode is 0x01, data is UTF-8 text")

            FrameType.BINARY -> {
                opcode = 0x2
                genBinaryFrame(data!!)
            }

            else -> genControlFrame(frameType, data)
        }
    }

    private fun genBinaryFrame(data: ByteArray) {
        val frameHeader = ByteBuffer.allocate(10)
        val dataLength = data.size
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

    private fun genControlFrame(type: FrameType, controlMessage: ByteArray?) {
        val frameHeader = ByteBuffer.allocate(10)
        if (controlMessage != null) {
            if (controlMessage.size > 125) throw WebsocketException("$type controlMessage's length ${controlMessage.size} is larger than 125")

            when (type) {
                FrameType.PING -> {
                    opcode = 0x9
                    frameHeader.put((1 shl 7 or 0x9).toByte())
                }

                FrameType.PONG -> {
                    opcode = 0xA
                    frameHeader.put((1 shl 7 or 0xA).toByte())
                }

                FrameType.CLOSE -> {
                    opcode = 0x8
                    frameHeader.put((1 shl 7 or 0x8).toByte())
                }

                else -> TODO("other type?")
            }

            frameHeader.put(controlMessage.size.toByte())
            frameHeader.flip()
            content = ByteArray(frameHeader.limit() + controlMessage.size)
            System.arraycopy(frameHeader.array(), 0, content, 0, frameHeader.limit())
            System.arraycopy(controlMessage, 0, content, frameHeader.limit(), controlMessage.size)
        } else {
            val message: ByteArray

            when (type) {
                FrameType.PING -> {
                    opcode = 0x9
                    frameHeader.put((1 shl 7 or 0x9).toByte())
                    message = "ping".toByteArray()
                }

                FrameType.PONG -> {
                    opcode
                    frameHeader.put((1 shl 7 or 0xA).toByte())
                    message = "pong".toByteArray()
                }

                FrameType.CLOSE -> {
                    opcode = 0x8
                    frameHeader.put((1 shl 7 or 0x8).toByte())
                    message = "close".toByteArray()
                }

                else -> TODO("other type?")
            }
            frameHeader.put((1 shl 7 or message.size).toByte())

            frameHeader.flip()
            content = ByteArray(frameHeader.limit() + message.size)
            System.arraycopy(frameHeader.array(), 0, content, 0, frameHeader.limit())
            System.arraycopy(message, 0, content, frameHeader.limit(), message.size)
        }
    }

    companion object {
        suspend fun receiveFrame(readsBuffer: ReadsBuffer): ServerFrame {
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

            val initPayloadLength = frameHeader[1].toInt()

            val payloadLength = when {
                initPayloadLength <= 125 -> initPayloadLength

                initPayloadLength == 126 -> {
                    ByteUtils.getShortFromByteArray(readsBuffer.reads(2)).toInt()
                }

                initPayloadLength == 127 -> {
                    ByteUtils.getLongFromByteArray(readsBuffer.reads(8)).toInt()
                }

                else -> TODO("other initPayloadLength?")
            }

            val data = readsBuffer.reads(payloadLength)

            return ServerFrame(type, data)
        }
    }
}