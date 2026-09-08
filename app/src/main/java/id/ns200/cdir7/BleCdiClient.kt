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
import java.util.UUID

class BleCdiClient(private val context: Context, private val listener: Listener) {
    interface Listener {
        fun onState(text: String, connected: Boolean)
        fun onTelemetry(value: Telemetry)
        fun onResponse(value: String)
    }

    private val main = Handler(Looper.getMainLooper())
    private val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var gatt: BluetoothGatt? = null
    private var command: BluetoothGattCharacteristic? = null
    private var sequence = 0
    private val descriptorQueue = ArrayDeque<BluetoothGattDescriptor>()
    private var descriptorWriteActive = false
    private var subscriptionsStarted = false
    private var gattReady = false
    private var manualStop = false
    private var retryCount = 0
    private var lastDevice: BluetoothDevice? = null
    private var mtuTimeout: Runnable? = null
    private var receiverRegistered = false

    private val bondReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(owner: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
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
                BluetoothDevice.BOND_BONDING -> listener.onState("Masukkan PIN BLE 123456", false)
                BluetoothDevice.BOND_BONDED -> main.postDelayed({ connectDevice(device) }, 350)
                BluetoothDevice.BOND_NONE -> if (previous == BluetoothDevice.BOND_BONDING) {
                    listener.onState("Pairing ditolak. Lupakan device lama lalu coba PIN 123456", false)
                }
            }
        }
    }

    init {
        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(bondReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            context.registerReceiver(bondReceiver, filter)
        }
        receiverRegistered = true
    }

    private val scan = object : ScanCallback() {
        override fun onScanResult(type: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device.name ?: return
            if (name == "NS200-CDI-R7") {
                adapter.bluetoothLeScanner.stopScan(this)
                lastDevice = result.device
                if (result.device.bondState == BluetoothDevice.BOND_BONDED) connectDevice(result.device)
                else beginBond(result.device)
            }
        }
        override fun onScanFailed(code: Int) = listener.onState("Scan gagal $code", false)
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, state: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && state == BluetoothProfile.STATE_CONNECTED) {
                main.post { listener.onState("Connected, membuka service", false) }
                main.postDelayed({
                    if (gatt === g && !gattReady && !g.discoverServices()) {
                        listener.onState("Service discovery gagal dimulai", false)
                        g.disconnect()
                    }
                }, 250)
            } else {
                resetGattState()
                g.close()
                if (gatt === g) gatt = null
                val retry = !manualStop && status == 8 && retryCount < 2 && lastDevice != null
                if (retry) {
                    retryCount++
                    main.post {
                        listener.onState("GATT 8 timeout; ulang $retryCount/2. Jika tetap gagal: Lupakan device", false)
                        main.postDelayed({ lastDevice?.let(::connectDevice) }, 1200)
                    }
                } else {
                    val detail = if (status == BluetoothGatt.GATT_SUCCESS) "" else " (GATT $status)"
                    main.post { listener.onState("Disconnected$detail", false) }
                }
            }
        }
        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (gatt !== g || subscriptionsStarted) return
            if (status == BluetoothGatt.GATT_SUCCESS && mtu >= 35) {
                beginSubscriptions(g)
            } else {
                main.post { listener.onState("MTU BLE gagal ($mtu/status $status)", false) }
                g.disconnect()
            }
        }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                main.post { listener.onState("Service discovery gagal: GATT $status", false) }
                g.disconnect()
                return
            }
            val service = g.getService(UUID.fromString(CdiProtocol.SERVICE))
            command = service?.getCharacteristic(UUID.fromString(CdiProtocol.COMMAND))
            if (command == null) {
                main.post { listener.onState("Service R7 tidak ditemukan", false) }
                g.disconnect()
                return
            }
            main.post { listener.onState("Service ditemukan, negosiasi MTU 64", false) }
            if (!g.requestMtu(64)) {
                main.post { listener.onState("Permintaan MTU gagal dimulai", false) }
                g.disconnect()
                return
            }
            mtuTimeout?.let(main::removeCallbacks)
            mtuTimeout = Runnable {
                if (gatt === g && !subscriptionsStarted) {
                    listener.onState("MTU tidak merespons; reconnect", false)
                    g.disconnect()
                }
            }.also { main.postDelayed(it, 3000) }
        }
        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            descriptorWriteActive = false
            if (status == BluetoothGatt.GATT_SUCCESS) writeNextDescriptor(g)
            else {
                main.post { listener.onState("Aktivasi telemetry gagal: GATT $status", false) }
                g.disconnect()
            }
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
        mtuTimeout?.let(main::removeCallbacks); mtuTimeout = null
        val service = g.getService(UUID.fromString(CdiProtocol.SERVICE))
        val telemetry = service?.getCharacteristic(UUID.fromString(CdiProtocol.TELEMETRY))
        val response = service?.getCharacteristic(UUID.fromString(CdiProtocol.RESPONSE))
        descriptorQueue.clear()
        if (telemetry == null || response == null || !subscribe(g, telemetry) || !subscribe(g, response)) {
            main.post { listener.onState("Characteristic/CCCD R7 tidak lengkap", false) }
            g.disconnect()
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
                main.post { listener.onState("Connected • GATT siap • paired", true) }
            }
            return
        }
        descriptorWriteActive = true
        if (!g.writeDescriptor(descriptorQueue.removeFirst())) {
            descriptorWriteActive = false
            main.post { listener.onState("Penulisan CCCD gagal dimulai", false) }
            g.disconnect()
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectDevice(device: BluetoothDevice) {
        if (manualStop) return
        listener.onState("Menghubungkan NS200-CDI-R7", false)
        resetGattState()
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    private fun beginBond(device: BluetoothDevice) {
        listener.onState("Pairing pertama: masukkan PIN 123456", false)
        if (!device.createBond()) listener.onState("Pairing gagal dimulai; lupakan device lalu coba lagi", false)
    }

    private fun resetGattState() {
        mtuTimeout?.let(main::removeCallbacks); mtuTimeout = null
        command = null
        descriptorQueue.clear()
        descriptorWriteActive = false
        subscriptionsStarted = false
        gattReady = false
    }

    private fun consume(uuid: UUID, value: ByteArray) = main.post {
        if (uuid.toString() == CdiProtocol.TELEMETRY) {
            CdiProtocol.telemetry(value)?.let(listener::onTelemetry)
                ?: listener.onResponse("TELEMETRY CRC/length error")
        } else listener.onResponse(value.toString(Charsets.US_ASCII).trim())
    }

    @SuppressLint("MissingPermission")
    fun connect() {
        manualStop = false
        retryCount = 0
        lastDevice = null
        gatt?.disconnect(); gatt?.close(); gatt = null
        resetGattState()
        listener.onState("Mencari NS200-CDI-R7", false)
        adapter.bluetoothLeScanner.startScan(scan)
        main.postDelayed({ adapter.bluetoothLeScanner.stopScan(scan) }, 12_000)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        manualStop = true
        adapter.bluetoothLeScanner?.stopScan(scan)
        resetGattState()
        gatt?.disconnect(); gatt?.close(); gatt = null
        if (receiverRegistered) {
            try { context.unregisterReceiver(bondReceiver) } catch (_: IllegalArgumentException) { }
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
        return g.writeCharacteristic(c)
    }
}
