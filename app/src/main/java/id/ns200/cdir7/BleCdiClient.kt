package id.ns200.cdir7

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class DiscoveredBleDevice(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val rssi: Int,
    val isCdiCandidate: Boolean
)

class BleCdiClient(private val context: Context, private val listener: Listener) {
    interface Listener {
        fun onState(text: String, connected: Boolean)
        fun onTelemetry(value: Telemetry)
        fun onRawPacket(bytes: ByteArray)
        fun onResponse(value: String)
    }

    private val main = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private var gatt: BluetoothGatt? = null
    private var command: BluetoothGattCharacteristic? = null
    private var sequence = 0
    private val descriptorQueue = ArrayDeque<BluetoothGattDescriptor>()
    private var descriptorWriteActive = false
    private var subscriptionsStarted = false
    var gattReady = false
        private set
    private var manualStop = false
    private var retryCount = 0
    private var gatt8Retries = 0
    private var lastDevice: BluetoothDevice? = null
    private var mtuTimeout: Runnable? = null
    private var descriptorTimeout: Runnable? = null
    private var reconnectRunnable: Runnable? = null
    private var connectPendingRunnable: Runnable? = null
    private var isConnecting = false
    private val maxAutoRetries = 3
    private var receiverRegistered = false

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredBleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredBleDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    var autoReconnect = true

    private val bondReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(owner: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                    val device = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    } ?: return
                    if (device.address == lastDevice?.address) {
                        try {
                            val pinBytes = CdiProtocol.BLE_PIN.toByteArray(Charsets.UTF_8)
                            device.setPin(pinBytes)
                            device.setPairingConfirmation(true)
                            abortBroadcast()
                        } catch (_: Exception) {}
                        listener.onState("Otomatis pairing PIN 123456...", false)
                    }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    } ?: return
                    if (device.address != lastDevice?.address) return
                    val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                    val previous = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)
                    when (state) {
                        BluetoothDevice.BOND_BONDING -> listener.onState("Pairing dengan PIN 123456...", false)
                        BluetoothDevice.BOND_BONDED -> main.postDelayed({ connectDevice(device) }, 350)
                        BluetoothDevice.BOND_NONE -> if (previous == BluetoothDevice.BOND_BONDING) {
                            listener.onState("Pairing ditolak/gagal. Lupakan device lalu coba PIN 123456", false)
                        }
                    }
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(bondReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(bondReceiver, filter)
            }
            receiverRegistered = true
        } catch (_: Exception) {}
    }

    private val scan = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(type: Int, result: ScanResult) {
            val dev = result.device
            val rawName = result.scanRecord?.deviceName ?: dev.name ?: "Unknown BLE"
            val isCdi = rawName.contains("NS200", ignoreCase = true) ||
                    rawName.contains("CDI", ignoreCase = true) ||
                    rawName.contains("R7", ignoreCase = true) ||
                    rawName.contains("WeAct", ignoreCase = true) ||
                    rawName.contains("STM32", ignoreCase = true) ||
                    result.scanRecord?.serviceUuids?.any { it.uuid.toString().equals(CdiProtocol.SERVICE, ignoreCase = true) } == true

            val item = DiscoveredBleDevice(
                device = dev,
                name = rawName,
                address = dev.address,
                rssi = result.rssi,
                isCdiCandidate = isCdi
            )

            val current = _discoveredDevices.value.toMutableList()
            val idx = current.indexOfFirst { it.address == dev.address }
            if (idx >= 0) {
                current[idx] = item
            } else {
                current.add(item)
            }
            _discoveredDevices.value = current.sortedWith(
                compareByDescending<DiscoveredBleDevice> { it.isCdiCandidate }
                    .thenByDescending { it.rssi }
            )

            if ((rawName == "NS200-CDI-R7" || rawName.contains("CDI-R7", ignoreCase = true)) &&
                !gattReady && !isConnecting && gatt == null && lastDevice == null) {
                stopScanInternal()
                connectDeviceExplicit(dev)
            }
        }
        override fun onScanFailed(code: Int) {
            _isScanning.value = false
            listener.onState("Scan gagal ($code)", false)
        }
    }

    private fun cancelPendingReconnects() {
        reconnectRunnable?.let(main::removeCallbacks)
        reconnectRunnable = null
        connectPendingRunnable?.let(main::removeCallbacks)
        connectPendingRunnable = null
    }

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, state: Int) {
            isConnecting = false
            if (status == BluetoothGatt.GATT_SUCCESS && state == BluetoothProfile.STATE_CONNECTED) {
                cancelPendingReconnects()
                retryCount = 0
                gatt8Retries = 0
                main.post { listener.onState("Connected, membuka service R7...", false) }
                main.postDelayed({
                    if (gatt === g && !gattReady) {
                        if (!g.discoverServices()) {
                            listener.onState("Service discovery gagal", false)
                            scheduleReconnect()
                        }
                    }
                }, 350)
            } else {
                resetGattState()
                try { g.disconnect() } catch (_: Exception) {}
                try { g.close() } catch (_: Exception) {}
                if (gatt === g) gatt = null

                if (manualStop) {
                    main.post { listener.onState("Disconnected (Manual)", false) }
                    return
                }

                // GATT 8 is GATT_CONN_TIMEOUT (status 8 or 0x08)
                if (status == 8) {
                    if (gatt8Retries < 2) {
                        gatt8Retries++
                        cancelPendingReconnects()
                        main.post {
                            listener.onState("GATT 8 (Connection Timeout). Mencoba ulang ($gatt8Retries/2)...", false)
                            reconnectRunnable = Runnable {
                                if (!manualStop && lastDevice != null) {
                                    connectDevice(lastDevice!!)
                                }
                            }.also { main.postDelayed(it, 1500) }
                        }
                        return
                    } else {
                        gatt8Retries = 0
                    }
                }

                if (autoReconnect && lastDevice != null && retryCount < maxAutoRetries) {
                    scheduleReconnect()
                } else {
                    val detail = if (status == BluetoothGatt.GATT_SUCCESS) "" else " (Status $status)"
                    val finalMsg = if (retryCount >= maxAutoRetries) {
                        "Koneksi terputus. Batas percobaan tercapai. Periksa jarak CDI & kontak lalu tekan Hubungkan."
                    } else {
                        "Disconnected$detail"
                    }
                    main.post { listener.onState(finalMsg, false) }
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (gatt !== g || subscriptionsStarted) return
            mtuTimeout?.let(main::removeCallbacks)
            mtuTimeout = null
            beginSubscriptions(g)
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                main.post { listener.onState("Service discovery status $status", false) }
                scheduleReconnect()
                return
            }
            val service = g.getService(UUID.fromString(CdiProtocol.SERVICE))
            command = service?.getCharacteristic(UUID.fromString(CdiProtocol.COMMAND))
            if (command == null) {
                main.post { listener.onState("Service CDI-R7 tidak ditemukan", false) }
                scheduleReconnect()
                return
            }
            main.post { listener.onState("Service ditemukan, mengaktifkan telemetry...", false) }
            val mtuOk = try { g.requestMtu(64) } catch (_: Exception) { false }
            if (!mtuOk) {
                beginSubscriptions(g)
            } else {
                mtuTimeout?.let(main::removeCallbacks)
                mtuTimeout = Runnable {
                    if (gatt === g && !subscriptionsStarted) {
                        beginSubscriptions(g)
                    }
                }.also { main.postDelayed(it, 1500) }
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            descriptorTimeout?.let(main::removeCallbacks)
            descriptorTimeout = null
            descriptorWriteActive = false
            writeNextDescriptor(g)
        }

        @Deprecated("API callback retained for Android 10")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            consume(c.uuid, c.value)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            consume(c.uuid, value)
        }
    }

    @SuppressLint("MissingPermission")
    private fun subscribe(g: BluetoothGatt, c: BluetoothGattCharacteristic): Boolean {
        if (!g.setCharacteristicNotification(c, true)) return false
        val descriptor = c.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            ?: return false
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        descriptorQueue.addLast(descriptor)
        return true
    }

    @SuppressLint("MissingPermission")
    private fun beginSubscriptions(g: BluetoothGatt) {
        if (subscriptionsStarted || gatt !== g) return
        mtuTimeout?.let(main::removeCallbacks)
        mtuTimeout = null
        val service = g.getService(UUID.fromString(CdiProtocol.SERVICE))
        val telemetry = service?.getCharacteristic(UUID.fromString(CdiProtocol.TELEMETRY))
        val response = service?.getCharacteristic(UUID.fromString(CdiProtocol.RESPONSE))
        descriptorQueue.clear()
        if (telemetry == null || response == null || !subscribe(g, telemetry) || !subscribe(g, response)) {
            main.post { listener.onState("Karakteristik BLE tidak lengkap", false) }
            scheduleReconnect()
            return
        }
        subscriptionsStarted = true
        writeNextDescriptor(g)
    }

    @SuppressLint("MissingPermission")
    private fun writeNextDescriptor(g: BluetoothGatt) {
        if (descriptorWriteActive) return
        if (descriptorQueue.isEmpty()) {
            if (subscriptionsStarted && !gattReady) {
                gattReady = true
                retryCount = 0
                main.post { listener.onState("Connected • Telemetry 20Hz Aktif", true) }
            }
            return
        }
        val desc = descriptorQueue.removeFirst()
        descriptorWriteActive = true
        descriptorTimeout = Runnable {
            descriptorWriteActive = false
            writeNextDescriptor(g)
        }.also { main.postDelayed(it, 1200) }

        val success = try {
            g.writeDescriptor(desc)
        } catch (_: Exception) { false }

        if (!success) {
            descriptorTimeout?.let(main::removeCallbacks)
            descriptorWriteActive = false
            writeNextDescriptor(g)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectDevice(device: BluetoothDevice) {
        if (manualStop) return
        cancelPendingReconnects()
        resetGattState()

        val oldGatt = gatt
        if (oldGatt != null) {
            try { oldGatt.disconnect() } catch (_: Exception) {}
            try { oldGatt.close() } catch (_: Exception) {}
            gatt = null
        }

        listener.onState("Menghubungkan ke ${device.name ?: "NS200-CDI-R7"}...", false)
        isConnecting = true

        // Delay 300ms before connectGatt to let Android BT stack clear HCI connection state
        connectPendingRunnable = Runnable {
            if (manualStop) {
                isConnecting = false
                return@Runnable
            }
            try {
                gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            } catch (e: Exception) {
                isConnecting = false
                scheduleReconnect()
            }
        }.also { main.postDelayed(it, 300) }
    }

    private fun scheduleReconnect() {
        if (manualStop || !autoReconnect || lastDevice == null) return
        if (retryCount >= maxAutoRetries) {
            main.post {
                listener.onState("Koneksi terputus. Batas retry tercapai. Tekan Hubungkan.", false)
            }
            return
        }
        cancelPendingReconnects()
        retryCount++
        val delayMs = 1500L * retryCount
        main.post {
            listener.onState("Koneksi terputus. Mencoba reconnect ($retryCount/$maxAutoRetries) dalam ${delayMs / 1000}s...", false)
            reconnectRunnable = Runnable {
                if (!manualStop && lastDevice != null) {
                    connectDevice(lastDevice!!)
                }
            }.also { main.postDelayed(it, delayMs) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun beginBond(device: BluetoothDevice) {
        listener.onState("Pairing BLE: Masukkan PIN 123456", false)
        if (!device.createBond()) {
            listener.onState("Gagal memulai pairing BLE", false)
        }
    }

    private fun resetGattState() {
        cancelPendingReconnects()
        mtuTimeout?.let(main::removeCallbacks); mtuTimeout = null
        descriptorTimeout?.let(main::removeCallbacks); descriptorTimeout = null
        command = null
        descriptorQueue.clear()
        descriptorWriteActive = false
        subscriptionsStarted = false
        gattReady = false
        isConnecting = false
    }

    private fun consume(uuid: UUID, value: ByteArray) = main.post {
        if (uuid.toString().equals(CdiProtocol.TELEMETRY, ignoreCase = true)) {
            listener.onRawPacket(value)
            val t = CdiProtocol.telemetry(value)
            if (t != null) {
                listener.onTelemetry(t)
            } else {
                listener.onResponse("Paket ${value.size}B: CRC16/Panjang invalid")
            }
        } else {
            listener.onResponse(value.toString(Charsets.US_ASCII).trim())
        }
    }

    @SuppressLint("MissingPermission")
    fun connect() {
        startScan()
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        manualStop = false
        cancelPendingReconnects()
        retryCount = 0
        gatt8Retries = 0
        isConnecting = false
        lastDevice = null
        val oldGatt = gatt
        if (oldGatt != null) {
            try { oldGatt.disconnect() } catch (_: Exception) {}
            try { oldGatt.close() } catch (_: Exception) {}
            gatt = null
        }
        resetGattState()
        val scanner = adapter?.bluetoothLeScanner
        if (adapter == null || !adapter.isEnabled || scanner == null) {
            listener.onState("Bluetooth tidak aktif / tidak tersedia", false)
            return
        }
        _discoveredDevices.value = emptyList()
        _isScanning.value = true
        listener.onState("Memindai perangkat BLE CDI...", false)
        try {
            scanner.startScan(scan)
            main.postDelayed({
                stopScanInternal()
            }, 15_000)
        } catch (e: Exception) {
            _isScanning.value = false
            listener.onState("Gagal memulai scan BLE: ${e.message}", false)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanInternal() {
        _isScanning.value = false
        try { adapter?.bluetoothLeScanner?.stopScan(scan) } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    fun connectDeviceExplicit(device: BluetoothDevice) {
        stopScanInternal()
        cancelPendingReconnects()
        lastDevice = device
        manualStop = false
        retryCount = 0
        gatt8Retries = 0
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            connectDevice(device)
        } else {
            beginBond(device)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        manualStop = true
        cancelPendingReconnects()
        stopScanInternal()
        resetGattState()
        val oldGatt = gatt
        if (oldGatt != null) {
            try { oldGatt.disconnect() } catch (_: Exception) {}
            try { oldGatt.close() } catch (_: Exception) {}
            gatt = null
        }
        isConnecting = false
        listener.onState("Disconnected (Manual)", false)
    }

    fun release() {
        disconnect()
        if (receiverRegistered) {
            try { context.unregisterReceiver(bondReceiver) } catch (_: Exception) {}
            receiverRegistered = false
        }
    }

    @SuppressLint("MissingPermission")
    fun send(body: String): Boolean {
        if (!gattReady) return false
        val g = gatt ?: return false
        val c = command ?: return false
        c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        c.value = CdiProtocol.command((++sequence) and 0xffff, body)
        return try { g.writeCharacteristic(c) } catch (_: Exception) { false }
    }
}
