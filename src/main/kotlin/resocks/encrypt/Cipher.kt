package resocks.encrypt

import java.nio.ByteBuffer

class Cipher(key: ByteArray, iv: ByteArray? = null, cipherMode: String) {
    private val inner: GeneralCipher
    init {
        when (cipherMode) {
            "aes-256-ctr" -> {
                inner = AES_256_CTR(key, iv)
            }
            else -> TODO("support other cipher mode")
        }
    }

    fun encrypt(plainText: ByteArray) = inner.encrypt(plainText)

    fun encrypt(plainBuffer: ByteBuffer, cipherBuffer: ByteBuffer) = inner.encrypt(plainBuffer, cipherBuffer)

    fun decrypt(cipherText: ByteArray) = inner.decrypt(cipherText)

    fun decrypt(cipherBuffer: ByteBuffer, plainBuffer: ByteBuffer) = inner.decrypt(cipherBuffer, plainBuffer)

    fun finish() = inner.finish()

    fun getIVorNonce() = inner.getIVorNonce()
}