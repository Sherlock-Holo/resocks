package resocks.muxStream

class MuxPackage(val id: Int, val controlMux: MuxPackageControl, val data: ByteArray? = null) {
    val packageByteArray =
            if (data != null) ByteArray(1 + data.size)
            else ByteArray(1)

    init {
        var header = id shl 4
        when (controlMux) {
            MuxPackageControl.RUNNING -> {
                header = header or 1
                System.arraycopy(data, 0, packageByteArray, 1, data!!.size)
            }

            MuxPackageControl.CONNECT -> {
                header = header or 2
                System.arraycopy(data, 0, packageByteArray, 1, data!!.size)
            }

            MuxPackageControl.CLOSE1 -> {
                header = header or 3
            }

            MuxPackageControl.CLOSE2 -> {
                header = header or 4
            }
        }
        packageByteArray[0] = header.toByte()
    }

    companion object {
        private val runningArray = IntArray(6) { (it + 1) or 1 }
        private val connectArray = IntArray(6) { (it + 1) or 2 }
        private val close1Array = IntArray(6) { (it + 1) or 3 }
        private val close2Array = IntArray(6) { (it + 1) or 4 }

        fun makePackage(rawData: ByteArray): MuxPackage {
            val header = rawData[0].toInt() and 0xff
            val id = header and 0xf0

            val muxPackageType: MuxPackageControl
            var data: ByteArray? = null

            when (header) {
                in runningArray -> {
                    muxPackageType = MuxPackageControl.RUNNING
                    data = ByteArray(rawData.size - 1)
                    System.arraycopy(rawData, 1, data, 0, data.size)
                }

                in connectArray -> {
                    muxPackageType = MuxPackageControl.CONNECT
                    data = ByteArray(rawData.size - 1)
                    System.arraycopy(rawData, 1, data, 0, data.size)
                }

                in close1Array -> {
                    muxPackageType = MuxPackageControl.CLOSE1
                }

                in close2Array -> {
                    muxPackageType = MuxPackageControl.CLOSE2
                }

                else -> throw MuxException("error package header")
            }

            return MuxPackage(id, muxPackageType, data)
        }
    }
}