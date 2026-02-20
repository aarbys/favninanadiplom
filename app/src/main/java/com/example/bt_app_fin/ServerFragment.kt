package com.example.bt_app_fin

import android.util.Log
import android.util.Log.VERBOSE
import android.util.Log.DEBUG
import android.util.Log.INFO
import android.util.Log.WARN
import android.util.Log.ERROR

import android.os.Bundle

import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException


class ServerFragment : Fragment(R.layout.activity_server) {
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


    private val sharedViewModel: SharedViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvDisplay = view.findViewById<TextView>(R.id.tvDisplay)
        val etInput = view.findViewById<EditText>(R.id.etInput)
        val btnGet = view.findViewById<Button>(R.id.btnGet)
        val btnSend = view.findViewById<Button>(R.id.btnSend)

        // Подписываемся на статус авторизации для блокировки кнопок
        sharedViewModel.isServerAuthorized.observe(viewLifecycleOwner) { isReady ->
            btnGet.isEnabled = isReady
            btnSend.isEnabled = isReady
            etInput.isEnabled = isReady
            if (!isReady) tvDisplay.text = "Требуется авторизация на первой странице"
            else {

                if (sharedViewModel.serverResponseText.value.isNullOrEmpty()){
                    tvDisplay.text = "Ожидаем братишка"
                }
            }
        }

        sharedViewModel.serverResponseText.observe(viewLifecycleOwner) { newResponse ->
            tvDisplay.text = newResponse
        }

        btnSend.setOnClickListener {
            val rawMessage = etInput.text.toString()
            if (rawMessage.isEmpty()) return@setOnClickListener

            sendEncryptedData(rawMessage)
        }


        btnGet.setOnClickListener {
            fetchDataFromServer()
        }
    }

    private fun sendEncryptedData(message: String) {
        val TAG = "sendEncryptedData"
        val client = sharedViewModel.client
        val url = "${sharedViewModel.serverUrl}/upload_data"
        val crypto = sharedViewModel.cryptoManagerPC

        try {

            val encryptedB64 = crypto.encryptWithAES(message)

            // Сборка JSOn
            val json = JSONObject().apply {
                put("user_id", sharedViewModel.currentUserId)
                put("payload", encryptedB64)
            }

            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    activity?.runOnUiThread { Toast.makeText(context, "Ошибка отправки", Toast.LENGTH_SHORT).show() }
                    logCreator("Ошибка отправки сообщения на сервер",tag=TAG, level = ERROR)
                }

                override fun onResponse(call: Call, response: Response) {
                    activity?.runOnUiThread {
                        if (response.isSuccessful)
                        {
                            Toast.makeText(context, "Данные в БД!", Toast.LENGTH_SHORT).show()
                            logCreator("Данные успешно переданы на сервер",tag=TAG, level = INFO)
                        }

                        else {
                            Toast.makeText(context, "Ошибка сервера: ${response.code}", Toast.LENGTH_SHORT).show()
                            logCreator("Ошибка сервера: ${response.code}",tag=TAG, level = ERROR)
                        }
                    }
                }
            })
        } catch (e: Exception) {
            logCreator(message=e.message?:"Unexcepted ERROR",tag=TAG,level=ERROR)
        }
    }

    private fun fetchDataFromServer() {
        val tag = "FETCH_DATA"
        val client = sharedViewModel.client
        val serverUrl = sharedViewModel.serverUrl
        val userId = sharedViewModel.currentUserId
        val crypto = sharedViewModel.cryptoManagerPC

        val url = "$serverUrl/get_data?user_id=$userId"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${sharedViewModel.uuidPc}")
            .get()
            .build()
        logCreator("Запрос данных для пользователя $userId", tag, INFO)



        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                logCreator("Ошибка запроса: ${e.message}", tag, ERROR)
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Сервер недоступен", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val encryptedPackage = response.body?.string() ?: return

                if (response.isSuccessful){
                    try {
                        val decryptedJson = crypto.decryptWithAES(encryptedPackage)

                        val jsonDict: JSONObject = JSONObject(decryptedJson)
                        val keys = jsonDict.keys()

                        val resultText = StringBuilder()
                        val sortedKeys = mutableListOf<String>()

                        while (keys.hasNext()) { sortedKeys.add(keys.next()) }
                        sortedKeys.sort()

                        for (timeKey in sortedKeys) {
                            val value = jsonDict.get(timeKey)
                            resultText.append("[$timeKey] -> $value\n")
                        }
                        // tvDisplay
                        activity?.runOnUiThread {
                            sharedViewModel.setServerResponse(resultText.toString())
                        }

                    }catch (e: Exception) {
                        logCreator("Ошибка дешифровки пакета: ${e.message}", tag, ERROR)
                        activity?.runOnUiThread {
                            Toast.makeText(context, "Ошибка безопасности данных", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })



    }
}