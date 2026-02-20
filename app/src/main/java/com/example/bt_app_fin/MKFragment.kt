package com.example.bt_app_fin

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import android.graphics.Color
import android.os.Build

import android.util.Log
import android.util.Log.DEBUG
import android.util.Log.ERROR
import android.util.Log.INFO
import android.util.Log.VERBOSE
import android.util.Log.WARN
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat.getSystemService


class BluetoothFragment : Fragment(R.layout.activity_mk) {
    fun logCreator(
        message: String,
        tag: String = "BT",
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

    private val sharedViewModel: SharedViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Инициализация UI по твоим ID
        val tvStatus = view.findViewById<TextView>(R.id.CONNECTION)
        val tvMkData = view.findViewById<TextView>(R.id.tvMkData)
        val pwField = view.findViewById<EditText>(R.id.PW_FIELD)

        val btnRed = view.findViewById<Button>(R.id.redBtn)
        val btnYellow = view.findViewById<Button>(R.id.yellowBtn)
        val btnGreen = view.findViewById<Button>(R.id.greenBtn)
        val btnBlue = view.findViewById<Button>(R.id.blueBtn)
        val btnOnAll = view.findViewById<Button>(R.id.turnOnAll)
        val btnOffAll = view.findViewById<Button>(R.id.turnOffAll)
        val btnSend = view.findViewById<Button>(R.id.SEND_BTN)

        // Мониторим статус подключения
        sharedViewModel.isMkConnected.observe(viewLifecycleOwner) { isConnected ->
            if (isConnected) {
                tvStatus.text = "Status: Connected"
                tvStatus.setTextColor(Color.GREEN)
            } else {
                tvStatus.text = "Status: Disconnected"
                tvStatus.setTextColor(Color.RED)
            }
            // Блокируем кнопки, если нет связи
            setButtonsEnabled(isConnected, btnRed, btnYellow, btnGreen, btnBlue, btnOnAll, btnOffAll, btnSend)
        }

        // Отображение данных от МК
        sharedViewModel.mkData.observe(viewLifecycleOwner) { data ->
            tvMkData.text = data
        }

        // Кнопки отвечающие за цвета
        btnRed.setOnClickListener { sndCmd("RED") }
        btnYellow.setOnClickListener { sndCmd("YEL") }
        btnGreen.setOnClickListener { sndCmd("GREEN") }
        btnBlue.setOnClickListener { sndCmd("BBLUE") }
        btnOnAll.setOnClickListener { sndCmd("ON_ALL") }
        btnOffAll.setOnClickListener { sndCmd("OFF") }

        // Отправка текста из поля пароля
        btnSend.setOnClickListener {
            val text = pwField.text.toString()
            if (text.isNotEmpty()) {
                sndCmd(text)
                pwField.text.clear()
            }
        }


        createNotificationChannel()
        checkPermissionsAndConnect()


    }

    private fun setButtonsEnabled(enabled: Boolean, vararg buttons: Button) {
        buttons.forEach { it.isEnabled = enabled }
    }


    private fun startBleConnection() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val device = bluetoothAdapter.getRemoteDevice(sharedViewModel.deviceMac)
        // Разрешение на использование блюпупа на андроидах выше 12
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        ) {
            sharedViewModel.bluetoothGatt = device.connectGatt(requireContext(), false, gattCallback)
        }
    }



    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val func = "onConnectionStateChange"
            try {
                // Если устройство подключено, то ставим в приложении статус "Connected"
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    logCreator(message = "Connected to ${sharedViewModel.deviceMac}", tag = "Connect")
                    // Ставим статус
                    activity?.runOnUiThread {
                        sharedViewModel.setMkConnection(true)
                    }

                    gatt.discoverServices()
                }

                // Если у нас отключен МК
                else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    logCreator(message = "Disconnected. Retry in 2s", level = WARN, tag = "Connect")

                    // Ставим статус
                    activity?.runOnUiThread {
                        sharedViewModel.setMkConnection(false)
                    }
                    //Закрываем подключение + зануляем все, чтобы не было фантомных отправок команд
                    sharedViewModel.uartChar = null
                    gatt.close()
                    sharedViewModel.bluetoothGatt = null

                    // Начинаем новое подключение
                    logCreator(
                        message = "Scheduling reconnection in 2s",
                        level = DEBUG,
                        tag = "Connect"
                    )
                    sharedViewModel.reconnectHandler.postDelayed({
                        startBleConnection()
                    }, 2000)
                }
            } catch (e: Exception) {
                logCreator(
                    message = "[$func] Error: Connection callback error: ${e.message}",
                    level = ERROR,
                    tag = "Connect"
                )
            }

        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val func = "onServicesDiscovered"
            try {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    // Если нет сервиса на МК, то падает ошибка
                    val service = gatt.getService(sharedViewModel.SERVICE_UUID)
                        ?: throw Exception("Service not found on device")

                    // Если нет характеристики куда записывать на МК, то падает ошибка
                    sharedViewModel.uartChar = service.getCharacteristic(sharedViewModel.CHAR_UUID)
                        ?: throw Exception("Characteristic not found in service")

                    logCreator(message = "Ready for use", level = DEBUG, tag = "Discovery")
                    // После того как нашли сервис, включаем его прослушку на ответы
                    enableNotifications(gatt, sharedViewModel.uartChar!!)
                }
            } catch (e: Exception) {
                logCreator(
                    message = "[$func] Error: Discovery error: ${e.message}",
                    level = ERROR,
                    tag = "Discovery"
                )
            }


        }

        // Включение уведомления для эха
        private fun enableNotifications(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val func = "enableNotifications"
            try {
                gatt.setCharacteristicNotification(characteristic, true)

                // Просим от МК присылать данные нам, если не нулевый порт отправки
                val descriptor = characteristic.getDescriptor(sharedViewModel.CCCD_UUID)
                if (descriptor != null) {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                    logCreator(
                        message = "Notifications enabled for ${characteristic.uuid}",
                        level = DEBUG,
                        tag = "Notification"
                    )
                } else {
                    logCreator(
                        message = "Descriptor not found!",
                        level = ERROR,
                        tag = "Notification"
                    )
                }
            } catch (e: Exception) {
                logCreator(
                    message = "[$func] Error: Error enabling notifications: ${e.message}",
                    level = ERROR,
                    tag = "Notification"
                )
            }
        }

        // Смотрим что нам вернул наш МК на команду
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val func = "onCharacteristicChanged"
            try {
                // Получаем байты
                val data = characteristic.value ?: return

                // Конвертируем в строку
                val part = String(data, Charsets.UTF_8)

                sharedViewModel.bleBuffer += part
                if (sharedViewModel.bleBuffer.contains("\n")) {
                    val message = sharedViewModel.bleBuffer.trim()
                    if (message.isNotEmpty()) {
                        logCreator(message = "MCU says: $message", level = DEBUG, tag = "ECHO")
                        activity?.runOnUiThread {
                            showNotification(message)
                        }
                    }
                    sharedViewModel.bleBuffer = ""
                }

            } catch (e: Exception) {
                logCreator(
                    message = "[$func] Error: Error receiving data: ${e.message}",
                    level = ERROR,
                    tag = "ECHO"
                )
            }
        }


    }

    // IN PROGRESS
    fun sndCmd(cmd: String) {
//
    }


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "MCU Messages"
            val descriptionText = "Уведомления от вашего контроллера"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(sharedViewModel.CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val manager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(message: String, title: String = "Сообщение с МК") {
        val builder = NotificationCompat.Builder(requireContext(), sharedViewModel.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(requireContext())) {

            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            ) {
                notify(System.currentTimeMillis().toInt(), builder.build())
            }
        }
    }

    // Функция на проверку всех необходимых нам разрешений, ну если их нет, то идем просить
    private fun checkPermissionsAndConnect() {
        // Тут будем держать список всех необходимых нам разрешений
        val permissions = mutableListOf<String>()

        // С 12 серии андроида необходимо разрешение
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        // А тут с 13 версии уведомлялки надо просить
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        //Просим разрешения
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(requireActivity(), permissions.toTypedArray(), 100)
        } else {//Если старый андроид, то пофиг на разрешения
            startBleConnection()
        }
    }

}