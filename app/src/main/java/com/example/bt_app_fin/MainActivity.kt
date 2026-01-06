package com.example.bt_app_fin
// Библиотеки, которые пришли при создании проекта
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity

// Мои импорты для блюпупа
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import java.util.UUID

//Логи
import android.util.Log


// Библиотеки для просмотра приколов
import android.widget.Toast


class MainActivity : ComponentActivity() {
    // Хуетень для сохранения подключения на все время
    private var bluetoothGatt: BluetoothGatt? = null

    // Параша для общения по UUID, хуй пойми зачем сказали надо

    private var uartChar: BluetoothGattCharacteristic? = null


    private val SERVICE_UUID =
        UUID.fromString("0000FFF0-0000-1000-8000-00805F9B34FB")

    private val CHAR_UUID =
        UUID.fromString("0000FFF1-0000-1000-8000-00805F9B34FB")

    private val CCCD_UUID =
        UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    // Хуйня для подключения нужная
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    uartChar = service.getCharacteristic(CHAR_UUID)
                    Log.d("BLE", "UART characteristic found: ${uartChar?.uuid}")
                } else {
                    Log.e("BLE", "SERVICE NOT FOUND!")
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Тут ты получаешь ответ от МК
                val response = characteristic.value
                val text = response.toString(Charsets.UTF_8)
                Log.d("BLE", "MK response: $text")
            } else {
                Log.e("BLE", "Characteristic read failed, status=$status")
            }
        }
    }







    // Функция отправки команд
    private fun sendCommand(cmd: String) {
        val gatt = bluetoothGatt
        val char = uartChar

        if (gatt == null || char == null) {
            Log.e("BLE", "UART not ready yet")
            return
        }

        char.setValue(cmd.toByteArray())
        val writeResult = gatt.writeCharacteristic(char)
        Log.d("BLE", "writeCharacteristic result=$writeResult")

        // Сразу читаем, чтобы получить ответ
        gatt.readCharacteristic(char)
    }





    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
//        enableEdgeToEdge()

        // Базовые переменные необходимые для моего устройства
        val myMacAddress: String = "A8:10:87:6E:5C:30"
        val macAddress = findViewById<TextView>(R.id.MAC_ADRESS)
        val connection = findViewById<TextView>(R.id.CONNECTION)
        val pw: EditText = findViewById(R.id.PW_FIELD)
        val sndBtn: Button = findViewById(R.id.SEND_BTN)

        // Переменные для кнопок изменения цвета светодиода
        val redBtn: Button = findViewById(R.id.redBtn)
        val greenBtn: Button = findViewById(R.id.greenBtn)
        val blueBtn: Button = findViewById(R.id.blueBtn)
        val yellowBtn: Button = findViewById(R.id.yellowBtn)
        val turnOffAll: Button = findViewById(R.id.turnOffAll)
        val turnOnAll: Button = findViewById(R.id.turnOnAll)


        // Добавление мак адреса к нашей строке, ну так хоть чутка менее зависима система к
        // смене устройств
        macAddress.text = buildString {
            append(macAddress.text)
            append(" ")
            append(myMacAddress)
        }

        // получаем блюпуп
        //Конкретно эта штука для Android>12
        if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT),
                1
            )
            return
        }

        val bluetoothManager =
            getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        //Подключение по блюпупу
        if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val stmka = bluetoothAdapter.getRemoteDevice(myMacAddress)
            bluetoothGatt = stmka.connectGatt(this, true, gattCallback)
        }


        redBtn.setOnClickListener {
            sendCommand("Pass123!\n")
        }
        greenBtn.setOnClickListener {
            sendCommand("green\n")
        }
        yellowBtn.setOnClickListener {
            sendCommand("yellow\n")
        }
        blueBtn.setOnClickListener {
            sendCommand("blue\n")
        }


    }
}

