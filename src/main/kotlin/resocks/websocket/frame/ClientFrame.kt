package resocks.websocket.frame

import kotlinx.coroutines.experimental.nio.aRead
import resocks.websocket.WebsocketException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.*
import kotlin.experimental.xor

class ClientFrame(override val opcode: Int, data: ByteArray?, maskKey: ByteArray? = null, val lastOneData: ByteArray? = null) : Frame {
    private val frameHeader = ByteBuffer.allocate(14)
    override lateinit var content: ByteArray

    init {
        when (opcode) {
            0x1 -> TODO("opcode is 0x01, data is UTF-8 text")

            0x2 -> genBinaryFrame(data!!, maskKey)

            0x3, 0x7, 0xB, 0xF -> TODO("MDN says it has no meaning")

            0x8 -> genControlFrame("close", data)

            0x9 -> genControlFrame("ping", data)

            0xA -> genControlFrame("pong", data)

            else -> TODO("other opcode?")
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
        suspend fun receiveFrame(socket: AsynchronousSocketChannel, lastOneData: ByteArray?): ClientFrame {
            val frameHeader = ByteBuffer.allocate(14)
            val opcode: Int
            var haveRead: Int

            if (lastOneData != null) {
                frameHeader.put(lastOneData)
                haveRead = lastOneData.size
            } else {
                haveRead = 0
            }

            while (haveRead <= 2) {
                haveRead += socket.aRead(frameHeader)
            }
            frameHeader.flip()
            val first_2_bytes = ByteArray(2)
            frameHeader.get(first_2_bytes)
            frameHeader.compact()
            when (first_2_bytes[0].toInt()) {
            // binary frame
                1 shl 7 or 0x2 -> {
                    opcode = 0x2
                }
            // ping frame
                1 shl 7 or 0x9 -> {
                    opcode = 0x9
                }
            // pong frame
                1 shl 7 or 0xA -> {
                    opcode = 0xA
                }
            // close frame
                1 shl 7 or 0x8 -> {
                    opcode = 0x8
                }
                else -> {
                    TODO("other opcode?")
                }
            }

            val initPayloadLength = first_2_bytes[1].toInt() and 0x7F
            val payloadLength: Int

            when {
                initPayloadLength <= 125 -> {
                    payloadLength = initPayloadLength
                    haveRead -= 2

                    while (haveRead < 4) {
                        haveRead += socket.aRead(frameHeader)
                    }
                    frameHeader.flip()

                    if (haveRead >= 4 + payloadLength) {
                        val maskKey = ByteArray(4)
                        var data = ByteArray(payloadLength)
                        frameHeader.get(maskKey)
                        frameHeader.get(data)

                        data = mask(maskKey, data)
                        if (haveRead != 4 + payloadLength) {
                            val lastData = ByteArray(haveRead - 4 - payloadLength)
                            frameHeader.get(lastData)
                            return ClientFrame(opcode, data, maskKey, lastData)
                        } else return ClientFrame(opcode, data, maskKey)

                    } else {
                        val maskKey = ByteArray(4)
                        frameHeader.get(maskKey)
                        val firstPart = ByteArray(haveRead - 4)
                        frameHeader.get(firstPart)

                        val haveNotRead = payloadLength - (haveRead - 4)
                        val secondPart = ByteBuffer.allocate(haveNotRead)
                        haveRead = 0
                        while (haveRead < haveNotRead) haveRead += socket.aRead(secondPart)

                        return ClientFrame(opcode, mask(maskKey, firstPart + secondPart.array()), maskKey)
                    }
                }

                initPayloadLength == 126 -> {
                    while (haveRead < 4) {
                        haveRead += socket.aRead(frameHeader)
                    }
                    frameHeader.flip()
                    payloadLength = frameHeader.int
                    frameHeader.compact()
                    haveRead -= 4
                }

                initPayloadLength == 127 -> {
                    while (haveRead < 10) {
                        haveRead += socket.aRead(frameHeader)
                    }
                    frameHeader.flip()
                    payloadLength = frameHeader.long.toInt()
                    frameHeader.compact()
                    haveRead -= 10
                }

                else -> TODO("other initPayloadLength?")
            }

            while (haveRead < 4) {
                haveRead += socket.aRead(frameHeader)
            }
            frameHeader.flip()

            if (haveRead == 4) {
                val maskKey = ByteArray(4)
                frameHeader.get(maskKey)
                val contentBuffer = ByteBuffer.allocate(payloadLength)
                haveRead = 0
                while (haveRead < payloadLength) haveRead += socket.aRead(contentBuffer)
                return ClientFrame(opcode, mask(maskKey, contentBuffer.array()), maskKey)
            } else {
                val maskKey = ByteArray(4)
                frameHeader.get(maskKey)

                val firstPart = ByteArray(haveRead - 4)
                frameHeader.get(firstPart)

                val haveNotRead = payloadLength - (haveRead - 4)
                val secondPart = ByteBuffer.allocate(haveNotRead)
                haveRead = 0
                while (haveRead < haveNotRead) haveRead += socket.aRead(secondPart)

                return ClientFrame(opcode, mask(maskKey, firstPart + secondPart.array()), maskKey)
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