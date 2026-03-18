package com.example.bt_app_fin

import android.os.Handler
import android.os.Looper
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
import androidx.annotation.RequiresPermission
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
    private val mkCryptManager = CryptManager()
    private var isEncryptionReady = true


    private val rsaKeyBuffer = mutableListOf<Byte>()
    private var isCollectingKey = false
    private val EXPECTED_RSA_SIZE = 294 // Примерный размер для RSA-2048 в DER



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
            setButtonsEnabled(
                isConnected,
                btnRed,
                btnYellow,
                btnGreen,
                btnBlue,
                btnOnAll,
                btnOffAll,
                btnSend
            )
        }

        // Отображение данных от МК
        sharedViewModel.mkData.observe(viewLifecycleOwner) { data ->
            tvMkData.text = data
        }


        createNotificationChannel()
        checkPermissionsAndConnect()
        // Кнопки отвечающие за цвета
        btnRed.setOnClickListener { sndCmd("RED") }
        btnYellow.setOnClickListener { sndCmd("YEL") }
        btnGreen.setOnClickListener { sndCmd("GREEN") }
        btnBlue.setOnClickListener { sndCmd("BLUE") }
        btnOnAll.setOnClickListener { sndCmd("ON_ALL") }
        btnOffAll.setOnClickListener { sndCmd("OFF") }

        // Отправка текста из поля пароля
        btnSend.setOnClickListener@androidx.annotation.RequiresPermission(
            android.Manifest.permission.BLUETOOTH_CONNECT
        ) {
            val pubKey = mkCryptManager.getPublicKeyFromBytes(rsaKeyBuffer.toByteArray())
            val aesKey = mkCryptManager.generateRandomBytes(32)
            val aesIv = mkCryptManager.generateRandomBytes(16)
            mkCryptManager.initSession(pubKey, aesKey, aesIv)
            val finalKey = rsaKeyBuffer.toByteArray()
            val hexString = finalKey.joinToString(" ") { String.format("%02x", it) }
            logCreator("PUBLIC KEY HEX: $hexString", "RSA_DEBUG", DEBUG)

            val handshake = mkCryptManager.encryptHandshakeRSA("Pass123", aesKey, aesIv, pubKey)


            // handshake to MK
            val gatt = sharedViewModel.bluetoothGatt
            val char = sharedViewModel.uartChar
            char?.value = handshake
            gatt!!.writeCharacteristic(char)
        }






    }

    private fun setButtonsEnabled(enabled: Boolean, vararg buttons: Button) {
        buttons.forEach { it.isEnabled = enabled }
    }


    private fun startBleConnection() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val device = bluetoothAdapter.getRemoteDevice(sharedViewModel.deviceMac)
        // Разрешение на использование блюпупа на андроидах выше 12
        if (!isAdded){
            return
        }
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        ) {
            sharedViewModel.bluetoothGatt =
                device.connectGatt(requireContext(), false, gattCallback)
        }
    }


    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val func = "onConnectionStateChange"
            try {
                // Если устройство подключено, то ставим в приложении статус "Connected"
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        sndCmd("GET_KEY")
                        logCreator("Запросил публичный ключ у МК", tag = "RSA")
                    }, 1500)

                    logCreator(
                        message = "Connected to ${sharedViewModel.deviceMac}",
                        tag = "Connect"
                    )
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

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
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
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
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
        @Deprecated("Deprecated in Java")
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val func = "onCharacteristicChanged"
            try {
                // Получаем байты + лог
                val data = characteristic.value ?: return
                val hexString = data.joinToString(" ") { String.format("%02x", it) }
                Log.d("RSA_KEY_DEBUG", "Received Bytes (HEX):$isCollectingKey  $hexString ")


                // Ключ пришёл или нет
                if (data[0] == 0x30.toByte() && !isCollectingKey) {
                    rsaKeyBuffer.clear()
                    isCollectingKey = true
                    logCreator("Start collecting RSA Key...", tag = "RSA", level = INFO)
                }

                if (isCollectingKey) {
                    rsaKeyBuffer.addAll(data.toList())

                    if (rsaKeyBuffer.size >= 290) {
                        isCollectingKey = false
                        try{
                            // Инициализируем криптографию

                            val pubKey = mkCryptManager.getPublicKeyFromBytes(rsaKeyBuffer.toByteArray())
                            val aesKey = mkCryptManager.generateRandomBytes(32)
                            val aesIv = mkCryptManager.generateRandomBytes(16)
                            mkCryptManager.initSession(pubKey, aesKey, aesIv)
                            val finalKey = rsaKeyBuffer.toByteArray()
                            val hexString = finalKey.joinToString(" ") { String.format("%02x", it) }
                            logCreator("PUBLIC KEY HEX: $hexString", "RSA_DEBUG", DEBUG)

                            val handshake = mkCryptManager.encryptHandshakeRSA("Pass123", aesKey, aesIv, pubKey)


                            // handshake to MK
                            Thread.sleep(100)
                            characteristic.value = handshake
                            gatt.writeCharacteristic(characteristic)
                            //isEncryptionReady = true
                            logCreator("Handshake отправлен на МК", tag = "RSA", level = INFO)

                        }
                        catch (e:Exception){
                            logCreator("Ошибка крипто-рукопожатия: ${e.message}", tag = "RSA", level = ERROR)
                        }

                        activity?.runOnUiThread {
                            Toast.makeText(context, "Ключ получен, шифрование готово", Toast.LENGTH_SHORT).show()
                        }
                        return // Выходим, чтобы не обрабатывать ключ как текстовую команду
                    }
                }


                // Конвертируем в строку

                sharedViewModel.bleBuffer += data
                while (sharedViewModel.bleBuffer.size >= 16) {
                    val packet = sharedViewModel.bleBuffer.copyOfRange(0, 16)
                    sharedViewModel.bleBuffer = sharedViewModel.bleBuffer.copyOfRange(16, sharedViewModel.bleBuffer.size)

                    val txt = mkCryptManager.decryptWithAESMK(packet).trim()
                    if (txt.isNotEmpty()) {
                        logCreator(
                            message = "MCU says: $txt",
                            level = DEBUG,
                            tag = "ECHO"
                        )

                        sharedViewModel.setMkData(txt)
                        sharedViewModel.sendEncryptedDataToServer(txt, source = "MK")

                        activity?.runOnUiThread {
                            showNotification(txt)
                        }
                    }
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
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sndCmd(cmd: String) {
        val gatt = sharedViewModel.bluetoothGatt
        val char = sharedViewModel.uartChar

        if (gatt == null || char == null) {
            logCreator("Cannot send: Not connected", level = WARN, tag = "BT")
            Toast.makeText(context, "Нет подключения к МК", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val dataToSend: ByteArray? = if (cmd == "GET_KEY") {
                (cmd + "!").toByteArray(Charsets.UTF_8)
            } else if (isEncryptionReady) {
                mkCryptManager.encryptWithAESMK(cmd)
            } else {
                (cmd + "!").toByteArray(Charsets.UTF_8)
            }

            // Отправка данных
            char.value = dataToSend
            gatt.writeCharacteristic(char)

            val aboba = dataToSend?.joinToString(" ") { String.format("%02x", it) }
            logCreator("Sent command (hex): $aboba", tag = "BT")
            logCreator("Sent command: $cmd (Encrypted: $isEncryptionReady)", tag = "BT")

        } catch (e: Exception) {
            logCreator("Error encrypting/sending: ${e.message}", level = ERROR, tag = "BT")
        }

    }


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "MCU Messages"
                val descriptionText = "Уведомления от вашего контроллера"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(sharedViewModel.CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val manager =
                requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
        val allGranted = permissions.all {
            ActivityCompat.checkSelfPermission(
                requireContext(),
                it
            ) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            // Если разрешения есть, сразу запускаем подключение
            startBleConnection()
        } else {
            // Если нет — запрашиваем
            ActivityCompat.requestPermissions(requireActivity(), permissions.toTypedArray(), 100)
        }
    }

}
