package com.football.livescores.matchlive.data.crypto

import okio.ByteString.Companion.decodeBase64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {

    /**
     * Giải mã chuỗi Base64 (OpenSSL compatible) bằng Passphrase
     */
    fun decryptAES256(ciphertextBase64: String, passphrase: String): String {
        try {
            val decodedBytes = ciphertextBase64.decodeBase64()?.toByteArray()
                ?: throw IllegalArgumentException("Invalid Base64 input")
            
            // Kiểm tra header "Salted__" (8 bytes đầu tiên)
            val header = "Salted__".toByteArray(Charsets.UTF_8)
            for (i in header.indices) {
                if (decodedBytes[i] != header[i]) {
                    throw IllegalArgumentException("Invalid ciphertext: missing Salted__ header")
                }
            }
            
            // Lấy Salt (8 bytes tiếp theo từ byte số 8 đến 15)
            val salt = decodedBytes.copyOfRange(8, 16)
            // Lấy Ciphertext thực tế (từ byte thứ 16 trở đi)
            val encryptedData = decodedBytes.copyOfRange(16, decodedBytes.size)
            
            // Khởi tạo Key (32 bytes) và IV (16 bytes) từ passphrase và salt
            val (key, iv) = evpBytesToKey(passphrase.toByteArray(Charsets.UTF_8), salt, 32, 16)
            
            val keySpec = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)
            
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            
            val decryptedBytes = cipher.doFinal(encryptedData)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("Giải mã thất bại: ${e.message}")
        }
    }

    /**
     * Thuật toán EVP_BytesToKey phỏng sinh khóa của OpenSSL dựa trên MD5
     */
    private fun evpBytesToKey(
        password: ByteArray, 
        salt: ByteArray, 
        keyLen: Int, 
        ivLen: Int
    ): Pair<ByteArray, ByteArray> {
        val md = MessageDigest.getInstance("MD5")
        val key = ByteArray(keyLen)
        val iv = ByteArray(ivLen)
        
        var d = ByteArray(0)
        var keyIndex = 0
        var ivIndex = 0
        
        val needed = keyLen + ivLen
        val combined = ByteArray(needed)
        var combinedIndex = 0
        
        while (combinedIndex < needed) {
            md.reset()
            md.update(d)
            md.update(password)
            md.update(salt)
            d = md.digest()
            
            val chunk = minOf(d.size, needed - combinedIndex)
            System.arraycopy(d, 0, combined, combinedIndex, chunk)
            combinedIndex += chunk
        }
        
        System.arraycopy(combined, 0, key, 0, keyLen)
        System.arraycopy(combined, keyLen, iv, 0, ivLen)
        
        return Pair(key, iv)
    }
}
