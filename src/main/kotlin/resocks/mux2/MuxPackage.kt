package resocks.mux2

class MuxPackage(val id: Int, val control: MuxPackageControl, val data: ByteArray? = null) {
    val packageByteArray =
            if (data != null) ByteArray(2 + data.size)
            else ByteArray(2)

    init {
        packageByteArray[0] = id.toByte()

        when (control) {
            MuxPackageControl.RUNNING -> {
                packageByteArray[1] = 0
                System.arraycopy(data, 0, packageByteArray, 2, data!!.size)
            }

            MuxPackageControl.CONNECT -> {
                packageByteArray[1] = 1
                System.arraycopy(data, 0, packageByteArray, 2, data!!.size)
            }

            MuxPackageControl.CLOSE -> {
                packageByteArray[1] = 2
            }
        }
    }

    companion object {
        fun makePackage(rawData: ByteArray): MuxPackage {
            val id = rawData[0].toInt() and 0xff
            val control = rawData[1].toInt() and 0xff

            val muxPackageType: MuxPackageControl
            var data: ByteArray? = null

            when (control) {
                0 -> {
                    muxPackageType = MuxPackageControl.RUNNING
                    data = ByteArray(rawData.size - 2)
                    System.arraycopy(rawData, 2, data, 0, data.size)
                }

                1 -> {
                    muxPackageType = MuxPackageControl.CONNECT
                    data = ByteArray(rawData.size - 2)
                    System.arraycopy(rawData, 2, data, 0, data.size)
                }

                2 -> {
                    muxPackageType = MuxPackageControl.CLOSE
                }

                else -> throw MuxException("error package control")
            }

            return MuxPackage(id, muxPackageType, data)
        }
    }
}