package resocks.websocket.frame

class ByteUtils {
    companion object {
        fun getShortFromByteArray(byteArray: ByteArray): Short {
            if (byteArray.size != 2) throw Throwable("byteArray size is ${byteArray.size} not 2")

            val short = byteArray[0].toInt() and 0xff shl 8 or byteArray[1].toInt()
            return short.toShort()
        }

        fun getIntFromByteArray(byteArray: ByteArray): Int {
            if (byteArray.size != 4) throw Throwable("byteArray size is ${byteArray.size} not 4")

            return (byteArray[0].toInt() and 0xff shl 24) or
                    (byteArray[1].toInt() and 0xff shl 16) or
                    (byteArray[2].toInt() and 0xff shl 8) or
                    (byteArray[3].toInt() and 0xff)
        }

        fun getLongFromByteArray(byteArray: ByteArray): Long {
            if (byteArray.size != 8) throw Throwable("byteArray size is ${byteArray.size} not 8")

            return (byteArray[0].toLong() and 0xff shl 56) or
                    (byteArray[1].toLong() and 0xff shl 48) or
                    (byteArray[2].toLong() and 0xff shl 40) or
                    (byteArray[3].toLong() and 0xff shl 32) or
                    (byteArray[4].toLong() and 0xff shl 24) or
                    (byteArray[5].toLong() and 0xff shl 16) or
                    (byteArray[6].toLong() and 0xff shl 8) or
                    (byteArray[7].toLong() and 0xff)
        }
    }
}