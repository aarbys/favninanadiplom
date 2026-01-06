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

class MainActivity : ComponentActivity() {

    private val TAG = "BT"

    // Тут бля куча переменных, все их заполняем.  И кнопочки инициализируем
    private val SERVICE_UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    private val CHAR_UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")

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

        redBtn.setOnClickListener { sndCmd("Red") }
        greenBtn.setOnClickListener { sndCmd("GREEN") }
        blueBtn.setOnClickListener { sndCmd("BLUE") }
        yellowBtn.setOnClickListener { sndCmd("YELLOW") }
        turnOffAll.setOnClickListener { sndCmd("OFF_ALL") }
        turnOnAll.setOnClickListener { sndCmd("ON_ALL") }
    }

    private fun startBleConnection() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val device = bluetoothAdapter.getRemoteDevice(deviceMac)

        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected, discovering services...")
                connectionText.post { connectionText.text = "Connected" }
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.e(TAG, "Disconnected, retrying...")
                connectionText.post { connectionText.text = "Disconnected" }
                gatt.close()
                startBleConnection()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    uartChar = service.getCharacteristic(CHAR_UUID)
                    Log.d(TAG, "UART characteristic found")

                    // включаем уведомления (notify)
                    gatt.setCharacteristicNotification(uartChar, true)
                    val desc = uartChar?.getDescriptor(CCCD_UUID)
                    desc?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    if (desc != null) gatt.writeDescriptor(desc)

                } else {
                    Log.e(TAG, "Service 0xFFF0 not found")
                }
            }
        }

        private var rxBuffer = StringBuilder()

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = characteristic.value
            for (b in data) {
                val c = b.toChar()
                rxBuffer.append(c)
                if (c == '\r' || c == '\n' || c == '!') {
                    val line = rxBuffer.toString().trim()
                    rxBuffer.clear()
                    Log.d(TAG, "Full echo from MC: $line")
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Command sent successfully")
            } else {
                Log.e(TAG, "Failed to send command, status=$status")
            }
        }


    }


    // Отправка команд на МК
    private val handler = Handler(Looper.getMainLooper())
    private fun sndCmd(msg: String) {
        uartChar?.let { char ->
            val fullMsg = "$msg\n" // символ конца команды
            char.setValue(fullMsg.toByteArray())
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            val result = bluetoothGatt?.writeCharacteristic(char)
            Log.d(TAG, "Sent: $msg, writeCharacteristic result=$result")

            // Небольшая пауза 100ms перед отправкой следующей команды (если нужно)
            handler.postDelayed({
                Log.d(TAG, "Ready for next command")
            }, 1000)
        } ?: Log.e(TAG, "UART characteristic not ready")
    }
    // а тут реальная отправка, эмуляция NRF connection


}
