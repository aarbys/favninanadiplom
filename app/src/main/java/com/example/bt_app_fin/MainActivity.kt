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

                    // включаем уведомления
                    gatt.setCharacteristicNotification(uartChar, true)
                    val desc = uartChar!!.getDescriptor(CCCD_UUID)
                    desc?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    desc?.let { gatt.writeDescriptor(it) }

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


        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            var func_name = "onDescriptorWrite"
            super.onDescriptorWrite(gatt, descriptor, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Notifications enabled for UART characteristic, +$func_name")
            } else {
                Log.e(TAG, "Failed to enable notifications, status=$status, +$func_name")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            var func_name = "onCharacteristicChanged"
            super.onCharacteristicChanged(gatt, characteristic)
            val raw = characteristic?.value
            if (raw != null) {
                val value = String(raw, Charsets.UTF_8)
                Log.d(TAG, "Received from MCU: $value, +$func_name")
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

    }


    // Отправка команд на МК
    private fun sndCmd(command: String) {
        var func_name = "sndCmd"
        uartChar?.let { char ->
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            char.value = (command + "!").toByteArray(Charsets.UTF_8) // ! как конец команды
            val result = bluetoothGatt?.writeCharacteristic(char) ?: false
            Log.d(TAG, "Sent: $command!, writeCharacteristic result=$result, +$func_name")
        } ?: Log.e(TAG, "UART characteristic not ready, +$func_name")
    }


}
