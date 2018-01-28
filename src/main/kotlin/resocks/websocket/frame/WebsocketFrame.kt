package resocks.websocket.frame

import resocks.readsBuffer.ReadsBuffer
import java.nio.ByteBuffer
import java.util.*
import kotlin.experimental.xor

class WebsocketFrame(
        override val frameType: FrameType, override val contentType: FrameContentType, override val content: ByteArray,
        private var maskKey: ByteArray? = null) : Frame {
    private var opcode = 1 shl 7
    override val frameByteArray: ByteArray

    init {
        opcode = when (contentType) {
            FrameContentType.BINARY -> opcode or 0x2

            FrameContentType.TEXT -> opcode or 0x1

            FrameContentType.PING -> opcode or 0x9

            FrameContentType.PONG -> opcode or 0xA

            FrameContentType.CLOSE -> opcode or 0x8
        }

        val initPayloadLength: Int
        val payloadLength: Int

        when (frameType) {
            FrameType.CLIENT -> {
                if (maskKey == null) {
                    maskKey = ByteArray(4)
                    Random().nextBytes(maskKey)
                }

                val maskData = mask(maskKey!!, content)

                when {
                    content.size <= 125 -> {
                        initPayloadLength = 1 shl 7 or content.size
                        payloadLength = content.size

                        frameByteArray = ByteArray(2 + 4 + payloadLength)

                        val frameBuffer = ByteBuffer.wrap(frameByteArray)
                        frameBuffer.put(opcode.toByte())
                        frameBuffer.put(initPayloadLength.toByte())
                        frameBuffer.put(maskKey)
                        frameBuffer.put(maskData)
                    }
                    content.size <= 65535 -> {
                        val tmp = ByteArray(2)
                        initPayloadLength = 1 shl 7 or 126
                        payloadLength = ByteBuffer.wrap(tmp).putShort(content.size.toShort()).flip().short.toInt()

                        frameByteArray = ByteArray(2 + 2 + 4 + payloadLength)

                        val frameBuffer = ByteBuffer.wrap(frameByteArray)
                        frameBuffer.put(opcode.toByte())
                        frameBuffer.put(initPayloadLength.toByte())
                        frameBuffer.putShort(payloadLength.toShort())
                        frameBuffer.put(maskKey)
                        frameBuffer.put(content)
                    }
                    else -> {
                        val tmp = ByteArray(8)
                        initPayloadLength = 1 shl 7 or 127
                        payloadLength = ByteBuffer.wrap(tmp).putLong(content.size.toLong()).flip().long.toInt()

                        frameByteArray = ByteArray(2 + 8 + 4 + payloadLength)

                        val frameBuffer = ByteBuffer.wrap(frameByteArray)
                        frameBuffer.put(opcode.toByte())
                        frameBuffer.put(initPayloadLength.toByte())
                        frameBuffer.putLong(payloadLength.toLong())
                        frameBuffer.put(maskKey)
                        frameBuffer.put(content)
                    }
                }
            }

            FrameType.SERVER -> {
                maskKey = null

                when {
                    content.size <= 125 -> {
                        initPayloadLength = content.size
                        payloadLength = initPayloadLength

                        frameByteArray = ByteArray(2 + payloadLength)

                        val frameBuffer = ByteBuffer.wrap(frameByteArray)
                        frameBuffer.put(opcode.toByte())
                        frameBuffer.put(initPayloadLength.toByte())
                        frameBuffer.put(content)
                    }
                    content.size <= 65535 -> {
                        val tmp = ByteArray(2)
                        initPayloadLength = 126
                        payloadLength = ByteBuffer.wrap(tmp).putShort(content.size.toShort()).flip().short.toInt()

                        frameByteArray = ByteArray(2 + 2 + payloadLength)

                        val frameBuffer = ByteBuffer.wrap(frameByteArray)
                        frameBuffer.put(opcode.toByte())
                        frameBuffer.put(initPayloadLength.toByte())
                        frameBuffer.putShort(payloadLength.toShort())
                        frameBuffer.put(content)
                    }
                    else -> {
                        val tmp = ByteArray(8)
                        initPayloadLength = 127
                        payloadLength = ByteBuffer.wrap(tmp).putLong(content.size.toLong()).flip().long.toInt()

                        frameByteArray = ByteArray(2 + 8 + payloadLength)

                        val frameBuffer = ByteBuffer.wrap(frameByteArray)
                        frameBuffer.put(opcode.toByte())
                        frameBuffer.put(initPayloadLength.toByte())
                        frameBuffer.putLong(payloadLength.toLong())
                        frameBuffer.put(content)
                    }
                }
            }
        }
    }

    companion object {
        suspend fun receiveFrame(readsBuffer: ReadsBuffer, frameType: FrameType): WebsocketFrame {
            val frameHeader = readsBuffer.readExactly(2)
            val contentType: FrameContentType

            when (frameHeader[0].toInt() and 0xff) {
            // binary frame
                1 shl 7 or 0x2 -> {
                    contentType = FrameContentType.BINARY
                }
            // ping frame
                1 shl 7 or 0x9 -> {
                    contentType = FrameContentType.PING
                }
            // pong frame
                1 shl 7 or 0xA -> {
                    contentType = FrameContentType.PONG
                }
            // close frame
                1 shl 7 or 0x8 -> {
                    contentType = FrameContentType.CLOSE
                }
                else -> {
                    TODO("other opcode?")
                }
            }

            val initPayloadLength = when (frameType) {
                FrameType.CLIENT -> frameHeader[1].toInt() and 0xff and 0x7F
                FrameType.SERVER -> frameHeader[1].toInt() and 0xff
            }

            val payloadLength = when {
                initPayloadLength <= 125 -> {
                    initPayloadLength
                }

                initPayloadLength == 126 -> {
//                    ByteUtils.byteArrayToShort(readsBuffer.readExactly(2)).toInt()
                    ByteBuffer.wrap(readsBuffer.readExactly(2)).flip().short.toInt()
                }

                initPayloadLength == 127 -> {
//                    ByteUtils.byteArrayToLong(readsBuffer.readExactly(8)).toInt()
                    ByteBuffer.wrap(readsBuffer.readExactly(8)).flip().long.toInt()
                }

                else -> TODO("other initPayloadLength?")
            }

            when (frameType) {
                FrameType.CLIENT -> {
                    val maskKey = readsBuffer.readExactly(4)
                    val data = readsBuffer.readExactly(payloadLength)
                    return WebsocketFrame(frameType, contentType, mask(maskKey, data), maskKey)
                }

                FrameType.SERVER -> {
                    val data = readsBuffer.readExactly(payloadLength)
                    return WebsocketFrame(frameType, contentType, data)
                }
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