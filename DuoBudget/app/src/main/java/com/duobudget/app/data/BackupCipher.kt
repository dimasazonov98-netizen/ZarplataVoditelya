package com.duobudget.app.data
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCipher {
    private val MAGIC = "DUOBACK1".toByteArray(Charsets.US_ASCII)
    private const val ITERATIONS=600_000
    const val MAX_BYTES=12*1024*1024
    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        require(password.size in 10..128) { "Пароль должен содержать от 10 до 128 символов" }
        val spec=PBEKeySpec(password,salt,ITERATIONS,256)
        return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,"AES") } finally { spec.clearPassword() }
    }
    fun encrypt(plain: ByteArray,password: CharArray): ByteArray {
        require(plain.size <= MAX_BYTES-100) { "Слишком большой резервный файл" }
        val salt=ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(password,salt));cipher.updateAAD(MAGIC)
        return MAGIC+salt+cipher.iv+cipher.doFinal(plain)
    }
    fun decrypt(bytes: ByteArray,password: CharArray): ByteArray {
        require(bytes.size in (MAGIC.size+16+12+16)..MAX_BYTES) { "Недопустимый размер резервной копии" }
        require(bytes.copyOfRange(0,MAGIC.size).contentEquals(MAGIC)) { "Это не резервная копия DuoBudget" }
        val start=MAGIC.size;val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(password,bytes.copyOfRange(start,start+16)),GCMParameterSpec(128,bytes.copyOfRange(start+16,start+28)))
        cipher.updateAAD(MAGIC)
        return try { cipher.doFinal(bytes.copyOfRange(start+28,bytes.size)) } catch(e:Exception) { throw IllegalArgumentException("Неверный пароль или повреждённая резервная копия",e) }
    }
}

