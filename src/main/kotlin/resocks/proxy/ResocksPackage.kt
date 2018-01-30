package resocks.proxy

class ResocksPackage(val id: Int, val control: PackageControl, val data: ByteArray? = null) {
    val packageByteArray =
            if (data != null) ByteArray(1 + data.size)
            else ByteArray(1)

    init {
        var header = id shl 4
        when (control) {
            PackageControl.RUNNING -> {
                header = header or 1
                System.arraycopy(data, 0, packageByteArray, 1, data!!.size)
            }

            PackageControl.CONNECT -> {
                header = header or 2
                System.arraycopy(data, 0, packageByteArray, 1, data!!.size)
            }

            PackageControl.CLOSE1 -> {
                header = header or 3
            }

            PackageControl.CLOSE2 -> {
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

        fun makePackage(rawData: ByteArray): ResocksPackage {
            val header = rawData[0].toInt() and 0xff
            val id = header and 0xf0

            val packageType: PackageControl
            var data: ByteArray? = null

            when (header) {
                in runningArray -> {
                    packageType = PackageControl.RUNNING
                    data = ByteArray(rawData.size - 1)
                    System.arraycopy(rawData, 1, data, 0, data.size)
                }

                in connectArray -> {
                    packageType = PackageControl.CONNECT
                    data = ByteArray(rawData.size - 1)
                    System.arraycopy(rawData, 1, data, 0, data.size)
                }

                in close1Array -> {
                    packageType = PackageControl.CLOSE1
                }

                in close2Array -> {
                    packageType = PackageControl.CLOSE2
                }
                
                else -> throw ProxyException("error package header")
            }

            return ResocksPackage(id, packageType, data)
        }
    }
}