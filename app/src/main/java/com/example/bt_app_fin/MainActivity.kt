
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

    fun generateAesKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256) // AESka 256aya
        val key = keyGen.generateKey()
        this.sessionAesKey = key
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
    fun encryptWithAES(data: String): String {
        val key = sessionAesKey ?: throw IllegalStateException("AES key not generated!")
        val cipher = Cipher.getInstance(aesTransformation)

        val ivBytes = ByteArray(16)
        SecureRandom().nextBytes(ivBytes)
        val iv = IvParameterSpec(ivBytes)

        cipher.init(Cipher.ENCRYPT_MODE, key, iv)
        val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        val ret_data = ivBytes+encryptedBytes

        return Base64.encodeToString(ret_data, Base64.NO_WRAP)
    }

    fun getAesKeyBase64(): String {
        return Base64.encodeToString(sessionAesKey?.encoded, Base64.NO_WRAP)
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

}