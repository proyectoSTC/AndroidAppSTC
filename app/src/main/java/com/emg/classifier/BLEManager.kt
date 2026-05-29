package com.emg.classifier

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.UUID

private const val TAG = "BLEManager"
private const val SCAN_TIMEOUT_MS = 15_000L

/**
 * BLEManager
 *
 * Handles: scan → connect → service discovery → enable notifications.
 * All callbacks are delivered on the main thread via Handler.
 *
 * Usage:
 *   val ble = BLEManager(context)
 *   ble.onSampleReceived = { sample -> ... }   // FloatArray(3)
 *   ble.onStatusChanged  = { msg -> ... }
 *   ble.startScan()
 */
class BLEManager(private val context: Context) {

    // ── Public callbacks ───────────────────────────────────────────────────────
    var onSampleReceived: ((FloatArray) -> Unit)? = null
    var onStatusChanged:  ((String)    -> Unit)? = null
    var onRawBytesReceived: ((Int) -> Unit)? = null   // total byte count for debug

    // ── Internal ───────────────────────────────────────────────────────────────
    private val mainHandler = Handler(Looper.getMainLooper())
    private val parser      = EMGParser()

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    private var gatt: BluetoothGatt? = null
    private var scanning = false
    private var totalBytes = 0

    private val serviceUUID        = UUID.fromString(Constants.SERVICE_UUID)
    private val characteristicUUID = UUID.fromString(Constants.CHARACTERISTIC_UUID)
    private val cccdUUID           = UUID.fromString(Constants.CCCD_UUID)

    // ── Scan ───────────────────────────────────────────────────────────────────

    fun startScan() {
        if (!hasPermissions()) {
            postStatus("Faltan permisos BLE")
            return
        }
        val adapter = bluetoothAdapter ?: run {
            postStatus("Bluetooth no disponible")
            return
        }
        if (!adapter.isEnabled) {
            postStatus("Bluetooth apagado")
            return
        }

        disconnect()
        parser.reset()
        totalBytes = 0

        val scanner = adapter.bluetoothLeScanner ?: run {
            postStatus("BLE scanner no disponible")
            return
        }

        val filter = ScanFilter.Builder()
            .setDeviceName(Constants.DEVICE_NAME)
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanning = true
        postStatus("Escaneando…")
        scanner.startScan(listOf(filter), settings, scanCallback)

        // Auto-stop scan after timeout
        mainHandler.postDelayed({
            if (scanning) {
                scanner.stopScan(scanCallback)
                scanning = false
                postStatus("Scan completado — dispositivo no encontrado")
            }
        }, SCAN_TIMEOUT_MS)
    }

    fun disconnect() {
        gatt?.let {
            it.disconnect()
            it.close()
        }
        gatt = null
        postStatus("Desconectado")
    }

    // ── ScanCallback ───────────────────────────────────────────────────────────

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!scanning) return

            val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
            scanner.stopScan(this)
            scanning = false

            val device = result.device
            Log.d(TAG, "Found device: ${device.name} — ${device.address}")
            postStatus("Dispositivo encontrado. Conectando…")

            mainHandler.post {
                connectToDevice(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            postStatus("Error en scan: $errorCode")
        }
    }

    // ── GATT connection ────────────────────────────────────────────────────────

    private fun connectToDevice(device: BluetoothDevice) {
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "GATT connected — discovering services")
                    postStatus("Conectado. Descubriendo servicios…")
                    mainHandler.postDelayed({ gatt.discoverServices() }, 600)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "GATT disconnected (status=$status)")
                    postStatus("Desconectado (status=$status)")
                    gatt.close()
                    this@BLEManager.gatt = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                postStatus("Error al descubrir servicios: $status")
                return
            }

            val service = gatt.getService(serviceUUID)
            if (service == null) {
                postStatus("Servicio EMG no encontrado")
                return
            }

            val characteristic = service.getCharacteristic(characteristicUUID)
            if (characteristic == null) {
                postStatus("Característica EMG no encontrada")
                return
            }

            // Enable local notifications
            gatt.setCharacteristicNotification(characteristic, true)

            // Enable remote notifications (write CCCD descriptor)
            val descriptor = characteristic.getDescriptor(cccdUUID)
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                mainHandler.postDelayed({ gatt.writeDescriptor(descriptor) }, 300)
            } else {
                postStatus("CCCD descriptor no encontrado")
            }

            postStatus("¡Suscripción activada! Recibiendo EMG…")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = characteristic.value ?: return

            totalBytes += data.size
            mainHandler.post { onRawBytesReceived?.invoke(totalBytes) }

            val samples = parser.feed(data)
            for (sample in samples) {
                mainHandler.post { onSampleReceived?.invoke(sample) }
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun postStatus(msg: String) {
        Log.d(TAG, msg)
        mainHandler.post { onStatusChanged?.invoke(msg) }
    }

    private fun hasPermissions(): Boolean {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        return needed.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
