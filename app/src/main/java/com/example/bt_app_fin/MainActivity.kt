package com.example.bt_app_fin

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID


// Библиотеки для просмотра приколов
import android.widget.Toast


class MainActivity : ComponentActivity() {
    //Унификация тэга для приложения
    private val TAG = "BT"

    private val myMacAddress = "A8:10:87:6E:5C:30"
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")


    // Хуета блюпупа
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    // Создаем ебланские кнопки чисто чтобы они были тут, как переменные, чтобы в коде не срать
    private lateinit var macAddress: TextView
    private lateinit var connection: TextView
    private lateinit var pw: EditText
    private lateinit var sndBtn: Button

    // Переменные для кнопок изменения цвета светодиода
    private lateinit var redBtn: Button
    private lateinit var greenBtn: Button
    private lateinit var blueBtn: Button
    private lateinit var yellowBtn: Button
    private lateinit var turnOffAll: Button
    private lateinit var turnOnAll: Button



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initUI()

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // Проверка разрешений для Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
            ActivityCompat.requestPermissions(this, permissions, 1)
        }
        connectToHC08()

    }

    private fun connectToHC08() {
        Thread {
            try {
                val device: BluetoothDevice = bluetoothAdapter!!.getRemoteDevice(myMacAddress)

                // Важно: создаём новый сокет каждый раз
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()  // попытка соединения
                bluetoothSocket = socket
                inputStream = socket.inputStream
                outputStream = socket.outputStream

                runOnUiThread { connection.text = "Connected" }
                Log.d(TAG, "Connected to HC-08")

                // слушаем входящие данные
                listenForEcho()

            } catch (e: IOException) {
                Log.e(TAG, "Connection failed, retrying...", e)
                runOnUiThread { connection.text = "Disconnected" }

                // Задержка перед повтором, чтобы HC-08 успел освободить порт
                Thread.sleep(3000)
                connectToHC08()  // рекурсивная попытка подключения
            }
        }.start()
    }


    private fun listenForEcho() {
        Thread {
            val buffer = ByteArray(1024)
            while (true) {
                try {
                    val bytesRead = inputStream?.read(buffer) ?: 0
                    if (bytesRead > 0) {
                        val echo = String(buffer, 0, bytesRead)
                        Log.d(TAG, "MK echo: $echo")
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "InputStream error, reconnecting...", e)
                    runOnUiThread { connection.text = "Disconnected" }
                    connectToHC08()
                    break
                }
            }
        }.start()
    }


    private fun sendCommand(cmd: String) {
        Thread {
            try {
                if (outputStream == null) {
                    Log.e(TAG, "Not connected")
                    return@Thread
                }
                outputStream!!.write(cmd.toByteArray())
                Log.d(TAG, "Command sent: $cmd")
            } catch (e: IOException) {
                Log.e(TAG, "Error sending command", e)
                runOnUiThread { connection.text = "Disconnected" }
                connectToHC08()
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            inputStream?.close()
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing socket", e)
        }
    }


    private fun initUI() {

        macAddress = findViewById(R.id.MAC_ADRESS)
        connection = findViewById(R.id.CONNECTION)
        pw = findViewById(R.id.PW_FIELD)
        sndBtn = findViewById(R.id.SEND_BTN)

        redBtn = findViewById(R.id.redBtn)
        greenBtn = findViewById(R.id.greenBtn)
        blueBtn = findViewById(R.id.blueBtn)
        yellowBtn = findViewById(R.id.yellowBtn)
        turnOffAll = findViewById(R.id.turnOffAll)
        turnOnAll = findViewById(R.id.turnOnAll)


        macAddress.text = myMacAddress
        connection.text = "Disconnected"


        // Кнопка отправки команды
        sndBtn.setOnClickListener {
            val cmd = pw.text.toString()
            if (cmd.isNotEmpty()) {
                sendCommand(cmd)
            }
            else {
                sendCommand("Pass123!")
            }
        }


        // Кнопки управления светодиодами
        redBtn.setOnClickListener { sendCommand("RED\n") }
        greenBtn.setOnClickListener { sendCommand("GREEN\n") }
        blueBtn.setOnClickListener { sendCommand("BLUE\n") }
        yellowBtn.setOnClickListener { sendCommand("YELLOW\n") }
        turnOffAll.setOnClickListener { sendCommand("OFF_ALL\n") }
        turnOnAll.setOnClickListener { sendCommand("ON_ALL\n") }
    }

}


