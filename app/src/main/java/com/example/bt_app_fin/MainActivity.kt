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

        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            Log.e("BLE", "onConnectionStateChange CALLED")
            Log.e("BLE", "status=$status newState=$newState")

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.e("BLE", "CONNECTED")
                bluetoothGatt = gatt
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.e("BLE", "DISCONNECTED")
            }
        }


        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            gatt.setCharacteristicNotification(uartChar, true)

            val service = gatt.getService(SERVICE_UUID)
            val uartChar = service?.getCharacteristic(CHAR_UUID)
            Log.e("BLE", "Characteristic found? ${uartChar != null}")


            gatt.setCharacteristicNotification(uartChar, true)

            val cccd = uartChar!!.getDescriptor(
                CCCD_UUID
            )

            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(cccd)

            Log.e("BLE", "UART READY")
        }




        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = characteristic.value.toString(Charsets.UTF_8)
            Log.e("BLE", "RX=$data")
        }


        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            Log.e("BLE", "CCCD write status=$status")
        }

    }






    // Функция отправки команд
    private fun sendCommand(cmd: String) {
        if (uartChar == null || bluetoothGatt == null) {
            Log.e("BLE", "UART not ready yet")
            return
        }

        uartChar!!.value = cmd.toByteArray(Charsets.UTF_8)
        bluetoothGatt!!.writeCharacteristic(uartChar!!)
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
            sendCommand("red\n")
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

