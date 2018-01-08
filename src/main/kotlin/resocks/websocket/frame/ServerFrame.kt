package resocks.websocket.frame

import kotlinx.coroutines.experimental.nio.aRead
import resocks.websocket.WebsocketException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class ServerFrame(override val opcode: Int, data: ByteArray?, val lastOneData: ByteArray? = null) : Frame {
    private val frameHeader = ByteBuffer.allocate(10)
    override lateinit var content: ByteArray

    init {
        when (opcode) {
            0x1 -> TODO("opcode is 0x01, data is UTF-8 text")

            0x2 -> genBinaryFrame(data!!)

            0x3, 0x7, 0xB, 0xF -> TODO("MDN says it has no meaning")

            0x8 -> genControlFrame("close", data)

            0x9 -> genControlFrame("ping", data)

            0xA -> genControlFrame("pong", data)

            else -> TODO("other opcode?")
        }
    }

    private fun genBinaryFrame(data: ByteArray) {
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

            frameHeader.put(controlMessage.size.toByte())
            frameHeader.flip()
            content = ByteArray(frameHeader.limit() + controlMessage.size)
            System.arraycopy(frameHeader.array(), 0, content, 0, frameHeader.limit())
            System.arraycopy(controlMessage, 0, content, frameHeader.limit(), controlMessage.size)
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
            content = ByteArray(frameHeader.limit() + message.size)
            System.arraycopy(frameHeader.array(), 0, content, 0, frameHeader.limit())
            System.arraycopy(message, 0, content, frameHeader.limit(), message.size)
        }
    }

    companion object {
        suspend fun receiveFrame(socket: AsynchronousSocketChannel, lastOneData: ByteArray?): ServerFrame {
            val frameHeader = ByteBuffer.allocate(10)
            val opcode: Int
            var haveRead: Int

            if (lastOneData != null) {
                frameHeader.put(lastOneData)
                haveRead = lastOneData.size
            } else {
                haveRead = 0
            }

            while (haveRead < 2) {
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

            val initPayloadLength = first_2_bytes[1].toInt()
            val payloadLength: Int

            when {
                initPayloadLength <= 125 -> {
                    payloadLength = initPayloadLength
                    haveRead -= 2
                    frameHeader.flip()

                    if (haveRead == payloadLength) {
                        val data = ByteArray(payloadLength)
                        frameHeader.get(data)
                        return ServerFrame(opcode, data)

                    } else if (haveRead > payloadLength) {
                        val data = ByteArray(payloadLength)
                        frameHeader.get(data)
                        val lastData = ByteArray(haveRead - payloadLength)
                        frameHeader.get(lastData)
                        return ServerFrame(opcode, data, lastData)

                    } else if (haveRead == 0) {
                        val contentBuffer = ByteBuffer.allocate(payloadLength)
                        while (haveRead < payloadLength) haveRead += socket.aRead(contentBuffer)

                        return ServerFrame(opcode, contentBuffer.array())

                    } else {
                        val firstPart = ByteArray(haveRead)
                        frameHeader.get(firstPart)
                        val haveNotRead = payloadLength - haveRead
                        val secondPart = ByteBuffer.allocate(haveNotRead)
                        haveRead = 0
                        while (haveRead < haveNotRead) haveRead += socket.aRead(secondPart)

                        return ServerFrame(opcode, firstPart + secondPart.array())
                    }
                }

                initPayloadLength == 126 -> {
                    while (haveRead < 4) {
                        haveRead += socket.aRead(frameHeader)
                    }
                    payloadLength = frameHeader.int
                    frameHeader.compact()
                    haveRead -= 4
                    frameHeader.flip()
                }

                initPayloadLength == 127 -> {
                    while (haveRead < 10) {
                        haveRead += socket.aRead(frameHeader)
                    }
                    payloadLength = frameHeader.long.toInt()
                    frameHeader.compact()
                    haveRead -= 10
                    frameHeader.flip()
                }

                else -> TODO("other initPayloadLength?")
            }

            if (haveRead == 0) {
                val contentBuffer = ByteBuffer.allocate(payloadLength)
                while (haveRead < payloadLength) haveRead += socket.aRead(contentBuffer)

                return ServerFrame(opcode, contentBuffer.array())

            } else {
                val firstPart = ByteArray(haveRead)
                frameHeader.get(firstPart)

                val haveNotRead = payloadLength - haveRead
                val secondPart = ByteBuffer.allocate(haveNotRead)
                haveRead = 0
                while (haveRead < haveNotRead) haveRead += socket.aRead(secondPart)

                return ServerFrame(opcode, firstPart + secondPart.array())
            }
        }
    }
}