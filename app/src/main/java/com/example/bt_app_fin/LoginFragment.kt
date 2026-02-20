package com.example.bt_app_fin

import android.os.Bundle
import android.util.Log
import android.util.Log.VERBOSE
import android.util.Log.DEBUG
import android.util.Log.INFO
import android.util.Log.WARN
import android.util.Log.ERROR
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException




class LoginFragment : Fragment(R.layout.activity_login) {

    fun logCreator(
        message: String,
        tag: String = "BT",
        level: Int = DEBUG,
    ) {
        when (level) {
            Log.VERBOSE -> Log.v(tag, message)
            Log.DEBUG -> Log.d(tag, message)
            Log.INFO -> Log.i(tag, message)
            Log.WARN -> Log.w(tag, message)
            Log.ERROR -> Log.e(tag, message)
        }
    }


    // Тянем херню в которой все данные хранятся
    private val sharedViewModel: SharedViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val loginField = view.findViewById<EditText>(R.id.loginField)
        val passwordField = view.findViewById<EditText>(R.id.passwordField)
        val loginBtn = view.findViewById<Button>(R.id.btnLogin)

        loginBtn.setOnClickListener {
            val user = loginField.text.toString()
            val pass = passwordField.text.toString()

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(requireContext(), "Не все поля заполнены", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                loginIntoServer(user, pass)
            }
        }
    }




    fun loginIntoServer(login: String, password:String){
        val client = sharedViewModel.client
        val serverUrl = sharedViewModel.serverUrl
        val cryptoManagerPC = sharedViewModel.cryptoManagerPC
        val tag = "LoginFragment_AUTH"
        // Получаем ключ для шифрования данных
        val keyRequest = Request.Builder()
            .url("$serverUrl/get_public_key")
            .build()

        client.newCall(keyRequest).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                logCreator(
                    tag=tag,
                    message = "Не удалось получить ключ сервера: ${e.message}",
                    level=ERROR)

                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Ошибка сети: Сервер недоступен", Toast.LENGTH_SHORT).show()
                }

            }


            override fun onResponse(call: Call, response: Response) {
                val pubKeyPem = response.body?.string() ?: return

                try {
                    // Готовим данные
                    val publicKey = cryptoManagerPC.getPublicKeyFromPem(pubKeyPem)
                    cryptoManagerPC.generateAesKey() // Генерация ключа
                    val aesKeyB64 = cryptoManagerPC.getAesKeyBase64() // Ключ сессии

                    // json'ка для входа на сервак
                    val authJson = JSONObject().apply {
                        put("login", login)
                        put("password", password)
                        put("aes_key", aesKeyB64)
                    }.toString()

                    // Шифруем нашу json'ку
                    val encryptedPacket = cryptoManagerPC.encryptWithRSA(authJson, publicKey)

                    // Кидаем в тело запроса и отправляем на сервак
                    val body = encryptedPacket.toRequestBody("text/plain".toMediaType())
                    val authRequest = Request.Builder()
                        .url("$serverUrl/auth")
                        .post(body)
                        .build()


                    client.newCall(authRequest).enqueue(object : Callback {
                        override fun onResponse(call: Call, response: Response) {
                            if (response.isSuccessful) {
                                val respData = response.body?.string()
                                val json = JSONObject(respData ?: "")

                                // Тут айдишку сейвим
                                sharedViewModel.currentUserId = json.getInt("user_id")
                                sharedViewModel.uuidPc = json.optString("session_id", "")

                                logCreator(
                                    tag=tag,
                                    message = "Авторизация успешна! ID пользователя: ${sharedViewModel.currentUserId}",
                                    level=INFO
                                )
                                sharedViewModel.setServerAuth(true)

                            } else {
                                logCreator(
                                    tag=tag,
                                    message = "Сервер отклонил вход: ${response.code}",
                                    level=INFO
                                )
                            }
                        }

                        override fun onFailure(call: Call, e: IOException) {
                            logCreator(
                                tag=tag,
                                message = "Ошибка при отправке пакета авторизации",
                                level=ERROR)
                        }
                    })

                } catch (e: Exception) {
                    logCreator(
                        tag=tag,
                        message = "Ошибка шифрования: ${e.message}",
                        level=ERROR)
                }
            }
        })

    }




}