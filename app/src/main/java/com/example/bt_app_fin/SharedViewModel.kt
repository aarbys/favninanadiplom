package com.example.bt_app_fin

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.util.Log.DEBUG
import android.util.Log.ERROR
import android.util.Log.INFO
import android.util.Log.VERBOSE
import android.util.Log.WARN
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.UUID


class SharedViewModel : ViewModel() {
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

    // BT vars
    //MAC-address устройства
    val deviceMac = "A8:10:87:6E:5C:30"
    // Создание подключения по блюпупу
    var bluetoothGatt: BluetoothGatt? = null
    var uartChar: BluetoothGattCharacteristic? = null
    //Хуйня для реконекта
    val reconnectHandler = Handler(Looper.getMainLooper())
    // UUID конкретно спизженные для моего HC-08 из NRF connection
    val SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    val CHAR_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    // Конкретно этот для получения эха
    val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    // Уведомлялка на телефон приходит
    val CHANNEL_ID = "mcu_status_channel"

    // Хуйня для уведомлялки чтобы в 1 строку было
    var bleBuffer = ByteArray(0)


    //. PC vars
    var currentUserId: Int? = null
    var serverUrl : String = "http://192.168.7.233:5000"
    val client: OkHttpClient = OkHttpClient()
    val cryptoManagerPC: CryptManager = CryptManager()
    var uuidPc: String ?= null

    // Это к серверу мы смотрим подключение
    private val _isServerAuthorized = MutableLiveData<Boolean>(false)
    val isServerAuthorized: LiveData<Boolean> = _isServerAuthorized

    // Текст с сервака, чтобы не потерять данные
    private val _serverResponseText = MutableLiveData<String>("Ожидание данных")
    val serverResponseText: LiveData<String> = _serverResponseText

    // Состояние МК
    private val _isMkConnected = MutableLiveData<Boolean>(false)
    val isMkConnected: LiveData<Boolean> = _isMkConnected

    // Данные с МК
    private val _mkData = MutableLiveData<String>("МК не подключен")
    val mkData: LiveData<String> = _mkData

    // управление
    fun setServerAuth(status: Boolean) {
        _isServerAuthorized.postValue(status)
    }

    fun setServerResponse(text: String) {
        _serverResponseText.postValue(text)
    }

    fun setMkConnection(status: Boolean) {
        _isMkConnected.postValue(status)
    }

    fun setMkData(data: String) {
        _mkData.postValue(data)
    }

    fun sendEncryptedDataToServer(message: String, source: String = "MK") {
        val sessionId = uuidPc
        if (message.isBlank()) return

        if (sessionId.isNullOrBlank()) {
            logCreator("Skip upload from $source: no session id", tag = "SERVER_SYNC", level = WARN)
            return
        }

        try {
            val encryptedPayload = cryptoManagerPC.encryptWithAES(message)
            val encryptedB64 = Base64.encodeToString(encryptedPayload, Base64.NO_WRAP)
            val json = JSONObject().apply {
                put("payload", encryptedB64)
            }

            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$serverUrl/upload_data")
                .addHeader("Authorization", "Bearer $sessionId")
                .post(body)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    logCreator("Upload failed from $source: ${e.message}", tag = "SERVER_SYNC", level = ERROR)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (it.isSuccessful) {
                            logCreator("Data from $source uploaded", tag = "SERVER_SYNC", level = INFO)
                        } else {
                            logCreator("Upload error from $source: ${it.code}", tag = "SERVER_SYNC", level = ERROR)
                        }
                    }
                }
            })
        } catch (e: Exception) {
            logCreator("Encryption error from $source: ${e.message}", tag = "SERVER_SYNC", level = ERROR)
        }
    }




}
