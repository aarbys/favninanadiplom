package com.example.bt_app_fin

import android.Manifest
import android.bluetooth.*
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import java.util.*
import android.os.Handler
import android.os.Looper
import kotlin.math.log

class MainActivity : ComponentActivity() {
    private var notificationsEnabled = false
    private val TAG = "BT"

    // Тут бля куча переменных, все их заполняем.  И кнопочки инициализируем
    private val SERVICE_UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    //UART_SERVICE_UUID
    private val CHAR_UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
    //UART_TX_CHARACTERISTIC_UUID
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")



    private lateinit var macAddressText: TextView
    private lateinit var connectionText: TextView
    private lateinit var pwField: EditText
    private lateinit var sndBtn: Button

    private lateinit var redBtn: Button
    private lateinit var greenBtn: Button
    private lateinit var blueBtn: Button
    private lateinit var yellowBtn: Button
    private lateinit var turnOffAll: Button
    private lateinit var turnOnAll: Button

    // Тут инициализируем всю хуйню для блюпупа
    private var bluetoothGatt: BluetoothGatt? = null
    private var uartChar: BluetoothGattCharacteristic? = null

    private val deviceMac = "A8:10:87:6E:5C:30"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initUI()

        // Тут траблы с android выше 12, делаем проверки и требуем права на блютуз
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
            ActivityCompat.requestPermissions(this, permissions, 100)
        }

        startBleConnection()

    }

    private fun initUI() {
        macAddressText = findViewById(R.id.MAC_ADRESS)
        connectionText = findViewById(R.id.CONNECTION)
        pwField = findViewById(R.id.PW_FIELD)
        sndBtn = findViewById(R.id.SEND_BTN)

        redBtn = findViewById(R.id.redBtn)
        greenBtn = findViewById(R.id.greenBtn)
        blueBtn = findViewById(R.id.blueBtn)
        yellowBtn = findViewById(R.id.yellowBtn)
        turnOffAll = findViewById(R.id.turnOffAll)
        turnOnAll = findViewById(R.id.turnOnAll)

        macAddressText.text = deviceMac
        connectionText.text = "Disconnected"

        sndBtn.setOnClickListener {
            val text = pwField.text.toString()
            if (text.isNotEmpty()) sndCmd(text)
        }

        redBtn.setOnClickListener { sndCmd("Red\n") }
        greenBtn.setOnClickListener { sndCmd("GREEN\n") }
        blueBtn.setOnClickListener { sndCmd("BLUE\n") }
        yellowBtn.setOnClickListener { sndCmd("YEL\n") }
        turnOffAll.setOnClickListener { sndCmd("OFF_ALL\n") }
        turnOnAll.setOnClickListener { sndCmd("ON_ALL\n") }
    }

    private fun startBleConnection() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val device = bluetoothAdapter.getRemoteDevice(deviceMac)

        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            var func_name = "onConnectionStateChange"
            super.onConnectionStateChange(gatt, status, newState)
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected, discovering services..., +$func_name")
                connectionText.post { connectionText.text = "Connected" }
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.e(TAG, "Disconnected, retrying..., +$func_name")
                connectionText.post { connectionText.text = "Disconnected" }
                gatt.close()
                startBleConnection()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            var func_name = "onServicesDiscovered"
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    uartChar = service.getCharacteristic(CHAR_UUID)
                    Log.d(TAG, "UART characteristic found, +$func_name")

                } else {
                    Log.e(TAG, "Service $SERVICE_UUID not found, retrying discovery...+, +$func_name")
                    // попробуем ещё раз через 200 мс
                    Handler(Looper.getMainLooper()).postDelayed({
                        gatt.discoverServices()
                    }, 200)
                }
            } else {
                Log.e(TAG, "onServicesDiscovered failed with status $status, +$func_name")
            }
        }


        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            var func_name = "onCharacteristicWrite"
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Command sent successfully, status=$status, +$func_name")
            } else {
                Log.e(TAG, "Failed to send command, status=$status, +$func_name")
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            var func_name = "onCharacteristicWrite"
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val value = String(characteristic.value, Charsets.UTF_8)
                val data = characteristic.value ?: return
                val hex = data.joinToString(" ") { String.format("%02X", it) }
                Log.d(TAG, "Echo from MCU (hex): $hex, func=$func_name")
                Log.d(TAG, "Echo from MCU: $value, func=$func_name")
            } else {
                Log.e(TAG, "Read failed, status=$status, func=$func_name")
            }
        }



    }


    // Отправка команд на МК
    fun sndCmd(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, cmd: String) {

        val command = if (cmd.endsWith("!") || cmd.endsWith("\n")) cmd else cmd + "\n"

        val bytes = command.toByteArray(Charsets.US_ASCII)

        characteristic.value = bytes
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT // write-with-response
        val result = gatt.writeCharacteristic(characteristic)

        Log.d(TAG, "Sent command: $command, result=$result")
    }

}
