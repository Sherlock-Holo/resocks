package resocks.encrypt

import java.security.MessageDigest

fun password2key(passwd: String): ByteArray {
    var keyGen = MessageDigest.getInstance("MD5")
    keyGen.update(passwd.toByteArray())
    var encodeKey = keyGen.digest()
    keyGen = MessageDigest.getInstance("MD5")
    keyGen.update(encodeKey + passwd.toByteArray())
    encodeKey += keyGen.digest()
    return encodeKey
}