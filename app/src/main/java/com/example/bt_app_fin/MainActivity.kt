
package com.example.bt_app_fin

import android.util.Log
import android.util.Log.VERBOSE
import android.util.Log.DEBUG
import android.util.Log.INFO
import android.util.Log.WARN
import android.util.Log.ERROR
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

import android.content.pm.PackageManager

import android.util.Base64
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import java.security.SecureRandom
import javax.crypto.spec.SecretKeySpec
class MainActivity : AppCompatActivity() {
    fun logCreator(
        message: String,
        tag: String = "MainPage",
        level: Int = DEBUG,
    ) {
        when (level) {
            VERBOSE -> Log.v(tag, message)
            DEBUG -> Log.d(tag, message)
            INFO -> Log.i(tag, message)
            WARN -> Log.w(tag, message)
            ERROR -> Log.e(tag, message)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navView: BottomNavigationView = findViewById(R.id.bottom_navigation)


        if (savedInstanceState == null) {
            loadFragment(LoginFragment())
        }

        // Тут переключение вкладок
        navView.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_login -> LoginFragment()
                R.id.nav_server -> ServerFragment()
                R.id.nav_mk -> BluetoothFragment()
                else -> LoginFragment()
            }
            loadFragment(fragment)
            true
        }
    }

    // Тут меняем че показывать пользователю
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                logCreator("Permissions granted!", level = DEBUG, tag = "Permission")

            } else {
                logCreator("Permissions denied by user", level = ERROR, tag = "Permission")
            }
        }
    }
}



class CryptManager {
    fun logCreator(
        message: String,
        tag: String = "CryptManager",
        level: Int = DEBUG,
    ) {
        when (level) {
            VERBOSE -> Log.v(tag, message)
            DEBUG -> Log.d(tag, message)
            INFO -> Log.i(tag, message)
            WARN -> Log.w(tag, message)
            ERROR -> Log.e(tag, message)
        }
    }

    private val rsaTransformation = "RSA/ECB/PKCS1Padding"
    private val aesTransformation = "AES/CBC/PKCS5Padding"
    private var sessionAesKey: SecretKey? = null

    private var currentAesIv: ByteArray? = null
    private var mcuPublicKey: PublicKey? = null

    private val STATIC_KEY = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10,
        0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20)
    private val STATIC_IV = byteArrayOf(0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77,
        0x88.toByte(), 0x99.toByte(), 0xAA.toByte(), 0xBB.toByte(),
        0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte()
    )

    fun initSession(publicKey: PublicKey, aesKey: ByteArray, aesIv: ByteArray) {
        this.mcuPublicKey = publicKey
        this.sessionAesKey = SecretKeySpec(aesKey, "AES")
        this.currentAesIv = aesIv
    }

    fun generateAesKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256) // AESka 256aya
        val key = keyGen.generateKey()
        this.sessionAesKey = key
        this.currentAesIv = generateRandomBytes(16)
        return key
    }


    fun getPublicKeyFromPem(pem: String): PublicKey {
        // Чистим ключ от говна лишнего
        val cleanPem = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\n", "")
            .replace("\r", "")
            .trim()
        val decoded = Base64.decode(cleanPem, Base64.DEFAULT)
        val spec = X509EncodedKeySpec(decoded)
        return KeyFactory.getInstance("RSA").generatePublic(spec)
    }


    // Тут кароче шифр для рсашки, которой мы отправим данные на сервак
    fun encryptWithRSA(data: String, publicKey: PublicKey): String {
        val cipher = Cipher.getInstance(rsaTransformation)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
    }

    // шифр аески нашей, которую мы записали туда
    fun encryptWithAES(data: String): ByteArray {
        val key = sessionAesKey ?: throw IllegalStateException("AES key not generated!")
        val freshIv = generateRandomBytes(16)
        currentAesIv = freshIv
        val cipher = Cipher.getInstance(aesTransformation)
        val iv = IvParameterSpec(freshIv)

        cipher.init(Cipher.ENCRYPT_MODE, key, iv)
        val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))

        return freshIv + encryptedBytes
    }
    fun encryptWithAESMK(text: String): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(STATIC_KEY, "AES")
        val ivSpec = IvParameterSpec(STATIC_IV)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)

        // Внимание: для МК нужно дополнить строку до 16 байт,
        // если не используешь Padding на обеих сторонах
        return cipher.doFinal(text.toByteArray())
    }




    fun getAesKeyBase64(): String {
        return Base64.encodeToString(sessionAesKey?.encoded, Base64.NO_WRAP)
    }

    fun decryptWithAESMK(encryptedBytes: ByteArray): String {
        return try {
            if (encryptedBytes.size != 16) {
                logCreator(
                    "Ошибка дешифровки AES MK: ожидалось 16 байт, получено ${encryptedBytes.size}",
                    "CryptManager",
                    ERROR
                )
                return ""
            }

            val keyBytes = STATIC_KEY
            if (keyBytes == null) {
                logCreator("Ошибка дешифровки AES MK: sessionAesKey == null", "CryptManager", ERROR)
                return ""
            }

            val keySpec = SecretKeySpec(keyBytes, "AES")
            val ivSpec = IvParameterSpec(STATIC_IV)

            val cipher = Cipher.getInstance("AES/CBC/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)

            String(decryptedBytes, Charsets.UTF_8)
                .trimEnd('\u0000', ' ', '\r', '\n')
        } catch (e: Exception) {
            logCreator("Ошибка дешифровки AES MK: ${e.message}", "CryptManager", ERROR)
            ""
        }
    }

    fun decryptWithAES(encryptedPackage: String): String {
        return try {
            val fullPackage = Base64.decode(encryptedPackage, Base64.DEFAULT)
            val iv = fullPackage.copyOfRange(0, 16)
            val ciphertext = fullPackage.copyOfRange(16, fullPackage.size)

            val keySpec = SecretKeySpec(sessionAesKey?.encoded, "AES")
            val ivSpec = IvParameterSpec(iv)


            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)


            val decryptedBytes = cipher.doFinal(ciphertext)

            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            logCreator("Ошибка дешифровки AES: ${e.message}", "CryptManager", ERROR)
            ""

        }

    }

    // bytes -> PublicKey
    fun getPublicKeyFromBytes(keyBytes: ByteArray): PublicKey {
        val spec = X509EncodedKeySpec(keyBytes)
        return KeyFactory.getInstance("RSA").generatePublic(spec)
    }

    // random bytes for IV and key
    fun generateRandomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        SecureRandom().nextBytes(bytes)
        return bytes
    }

    // Encrypt with rsa (handshake only)
    fun encryptHandshakeRSA(password: String, aesKey: ByteArray, aesIv: ByteArray, publicKey: PublicKey): ByteArray {
        val cipher = Cipher.getInstance(rsaTransformation)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)

        val payload = password.toByteArray(Charsets.UTF_8) +
                " ".toByteArray(Charsets.UTF_8) +
                aesKey +
                aesIv


        return cipher.doFinal(payload)
    }

}
