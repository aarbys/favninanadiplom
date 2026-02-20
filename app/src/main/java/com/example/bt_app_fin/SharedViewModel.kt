package com.example.bt_app_fin

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID


class SharedViewModel : ViewModel() {

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
    var bleBuffer = ""


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




}
