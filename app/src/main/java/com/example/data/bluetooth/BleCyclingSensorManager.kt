package com.example.data.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.domain.model.BleConnectionStatus
import com.example.domain.model.BleSensorUiState
import com.example.domain.model.DiscoveredBleSensor
import com.example.domain.model.PairedBleSensor
import com.example.domain.model.WheelCircumferencePreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

data class CscSensorReading(
    val sensorName: String,
    val wheelDataPresent: Boolean,
    val crankDataPresent: Boolean,
    val speedMps: Double?,
    val incrementalDistanceMeters: Double,
    val cadenceRpm: Int?,
    val timestampMillis: Long
)

class BleCyclingSensorManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _sensorState = MutableStateFlow(buildInitialState())
    val sensorState: StateFlow<BleSensorUiState> = _sensorState.asStateFlow()

    private val _readings = MutableSharedFlow<CscSensorReading>(extraBufferCapacity = 32)
    val readings: SharedFlow<CscSensorReading> = _readings.asSharedFlow()

    private var activeGatt: BluetoothGatt? = null
    private var activeScanner: BluetoothLeScanner? = null
    private var scanTimeoutJob: Job? = null
    private var staleWatchdogJob: Job? = null
    private val parserLock = Any()
    private var parserState = CscParserState()

    @Volatile
    private var lastWheelPacketAtMillis: Long = 0L

    @Volatile
    private var lastCrankPacketAtMillis: Long = 0L

    private fun buildInitialState(): BleSensorUiState {
        val supported = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) &&
            bluetoothAdapter != null
        val savedAddress = prefs.getString(KEY_SENSOR_ADDRESS, null)
        val savedName = prefs.getString(KEY_SENSOR_NAME, null)
        val wheelMm = prefs.getInt(KEY_WHEEL_CIRC_MM, WheelCircumferencePreset.MTB_29.circumferenceMm)
        val paired = if (!savedAddress.isNullOrBlank()) {
            PairedBleSensor(
                name = savedName?.takeIf { it.isNotBlank() } ?: "BLE Cycling Sensor",
                address = savedAddress,
                wheelCircumferenceMm = wheelMm
            )
        } else {
            null
        }
        return BleSensorUiState(
            isBluetoothSupported = supported,
            isBluetoothEnabled = bluetoothAdapter?.isEnabled == true,
            hasBluetoothPermissions = hasRequiredPermissions(),
            pairedSensor = paired
        )
    }

    fun hasRequiredPermissions(): Boolean {
        return requiredPermissions().all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun refreshCapabilities() {
        val supported = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) &&
            bluetoothAdapter != null
        val enabled = bluetoothAdapter?.isEnabled == true
        val permsGranted = hasRequiredPermissions()
        _sensorState.update {
            it.copy(
                isBluetoothSupported = supported,
                isBluetoothEnabled = enabled,
                hasBluetoothPermissions = permsGranted
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        refreshCapabilities()
        val state = _sensorState.value
        if (!state.isBluetoothSupported) {
            _sensorState.update { it.copy(statusMessage = "Bluetooth Low Energy is not supported on this device.") }
            return
        }
        if (!hasRequiredPermissions()) {
            _sensorState.update { it.copy(statusMessage = "Bluetooth permission is required to scan for sensors.") }
            return
        }
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _sensorState.update { it.copy(statusMessage = "Turn on Bluetooth in system settings to scan for cycling sensors.") }
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: run {
            _sensorState.update { it.copy(statusMessage = "BLE scanner unavailable.") }
            return
        }

        stopScanInternal()

        activeScanner = scanner
        _sensorState.update {
            it.copy(
                isScanning = true,
                discoveredSensors = emptyList(),
                statusMessage = "Scanning for Bluetooth Speed & Cadence (CSC) sensors…"
            )
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(CSC_SERVICE_UUID))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(filters, settings, scanCallback)
        } catch (e: SecurityException) {
            _sensorState.update {
                it.copy(
                    isScanning = false,
                    statusMessage = "Missing Bluetooth scan permission."
                )
            }
            return
        }

        scanTimeoutJob?.cancel()
        scanTimeoutJob = scope.launch {
            delay(SCAN_TIMEOUT_MS)
            stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        stopScanInternal()
        _sensorState.update { current ->
            val msg = if (current.discoveredSensors.isEmpty() && current.connectionStatus == BleConnectionStatus.DISCONNECTED) {
                "No CSC speed or cadence sensors found. Wake your sensor by spinning the wheel or crank."
            } else {
                null
            }
            current.copy(isScanning = false, statusMessage = msg ?: current.statusMessage)
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScanInternal() {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        val scanner = activeScanner ?: bluetoothAdapter?.bluetoothLeScanner
        if (scanner != null && hasScanPermission()) {
            runCatching { scanner.stopScan(scanCallback) }
        }
        activeScanner = null
    }

    fun pairAndConnect(sensor: DiscoveredBleSensor) {
        stopScanInternal()
        val wheelMm = _sensorState.value.pairedSensor?.wheelCircumferenceMm
            ?: prefs.getInt(KEY_WHEEL_CIRC_MM, WheelCircumferencePreset.MTB_29.circumferenceMm)
        val config = PairedBleSensor(
            name = sensor.name,
            address = sensor.address,
            wheelCircumferenceMm = wheelMm
        )
        prefs.edit {
            putString(KEY_SENSOR_ADDRESS, config.address)
            putString(KEY_SENSOR_NAME, config.name)
            putInt(KEY_WHEEL_CIRC_MM, config.wheelCircumferenceMm)
        }

        _sensorState.update {
            it.copy(
                isScanning = false,
                pairedSensor = config,
                statusMessage = "Paired ${config.name}"
            )
        }
        connectPairedSensor()
    }

    fun updateWheelCircumference(circumferenceMm: Int) {
        prefs.edit { putInt(KEY_WHEEL_CIRC_MM, circumferenceMm) }
        _sensorState.update { state ->
            state.copy(
                pairedSensor = state.pairedSensor?.copy(wheelCircumferenceMm = circumferenceMm)
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun connectPairedSensor() {
        refreshCapabilities()
        val paired = _sensorState.value.pairedSensor ?: return
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled || !hasConnectPermission()) {
            _sensorState.update {
                it.copy(
                    connectionStatus = BleConnectionStatus.DISCONNECTED,
                    statusMessage = if (!adapter.isEnabled) {
                        "Enable Bluetooth to connect ${paired.name}"
                    } else {
                        "Grant Bluetooth permission to connect ${paired.name}"
                    }
                )
            }
            return
        }

        if (_sensorState.value.connectionStatus == BleConnectionStatus.CONNECTED && activeGatt != null) {
            return
        }

        disconnectGattOnly()
        synchronized(parserLock) {
            parserState = CscParserState()
        }

        val device = runCatching { adapter.getRemoteDevice(paired.address) }.getOrNull()
        if (device == null) {
            _sensorState.update {
                it.copy(
                    connectionStatus = BleConnectionStatus.ERROR,
                    statusMessage = "Saved sensor address is invalid."
                )
            }
            return
        }

        _sensorState.update {
            it.copy(
                connectionStatus = BleConnectionStatus.CONNECTING,
                statusMessage = "Connecting to ${paired.name}…"
            )
        }

        try {
            activeGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            startStaleWatchdog()
        } catch (_: SecurityException) {
            _sensorState.update {
                it.copy(
                    connectionStatus = BleConnectionStatus.ERROR,
                    statusMessage = "Bluetooth Connect permission required."
                )
            }
        }
    }

    fun forgetPairedSensor() {
        disconnect()
        prefs.edit {
            remove(KEY_SENSOR_ADDRESS)
            remove(KEY_SENSOR_NAME)
        }
        _sensorState.update {
            it.copy(
                pairedSensor = null,
                connectionStatus = BleConnectionStatus.DISCONNECTED,
                liveSpeedMph = null,
                liveCadenceRpm = null,
                hasWheelCapability = false,
                hasCadenceCapability = false,
                statusMessage = null
            )
        }
    }

    fun disconnect() {
        stopScanInternal()
        staleWatchdogJob?.cancel()
        staleWatchdogJob = null
        disconnectGattOnly()
        synchronized(parserLock) {
            parserState = CscParserState()
        }
        _sensorState.update {
            it.copy(
                isScanning = false,
                connectionStatus = BleConnectionStatus.DISCONNECTED,
                liveSpeedMph = null,
                liveCadenceRpm = null
            )
        }
    }

    fun getWheelCircumferenceMm(): Int {
        return _sensorState.value.pairedSensor?.wheelCircumferenceMm
            ?: prefs.getInt(KEY_WHEEL_CIRC_MM, WheelCircumferencePreset.MTB_29.circumferenceMm)
    }

    fun hasFreshWheelReading(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return _sensorState.value.connectionStatus == BleConnectionStatus.CONNECTED &&
            _sensorState.value.hasWheelCapability &&
            (nowMillis - lastWheelPacketAtMillis) <= READING_FRESH_WINDOW_MS
    }

    fun getLatestFreshCadence(nowMillis: Long = System.currentTimeMillis()): Int? {
        if (_sensorState.value.connectionStatus != BleConnectionStatus.CONNECTED) return null
        if ((nowMillis - lastCrankPacketAtMillis) > READING_FRESH_WINDOW_MS) return null
        return _sensorState.value.liveCadenceRpm
    }

    fun release() {
        disconnect()
        scope.cancel()
    }

    @SuppressLint("MissingPermission")
    private fun disconnectGattOnly() {
        val gatt = activeGatt
        activeGatt = null
        if (gatt != null && hasConnectPermission()) {
            runCatching {
                gatt.disconnect()
                gatt.close()
            }
        }
    }

    private fun startStaleWatchdog() {
        staleWatchdogJob?.cancel()
        staleWatchdogJob = scope.launch {
            while (isActive) {
                delay(1500L)
                val now = System.currentTimeMillis()
                val clearWheel = lastWheelPacketAtMillis > 0L && (now - lastWheelPacketAtMillis) > READING_FRESH_WINDOW_MS
                val clearCrank = lastCrankPacketAtMillis > 0L && (now - lastCrankPacketAtMillis) > READING_FRESH_WINDOW_MS
                if (clearWheel || clearCrank) {
                    _sensorState.update { state ->
                        state.copy(
                            liveSpeedMph = if (clearWheel) null else state.liveSpeedMph,
                            liveCadenceRpm = if (clearCrank) null else state.liveCadenceRpm
                        )
                    }
                }
            }
        }
    }

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val address = device.address ?: return
            val deviceName = if (hasConnectPermission()) {
                result.scanRecord?.deviceName ?: device.name ?: "Cycling Sensor (${address.takeLast(5)})"
            } else {
                result.scanRecord?.deviceName ?: "Cycling Sensor (${address.takeLast(5)})"
            }
            val candidate = DiscoveredBleSensor(
                name = deviceName,
                address = address,
                rssi = result.rssi
            )
            _sensorState.update { current ->
                val updatedList = (current.discoveredSensors.filterNot { it.address == address } + candidate)
                    .sortedByDescending { it.rssi }
                current.copy(
                    discoveredSensors = updatedList,
                    statusMessage = "Found ${updatedList.size} BLE cycling sensor(s)"
                )
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _sensorState.update {
                it.copy(
                    isScanning = false,
                    statusMessage = "BLE sensor scan failed (code $errorCode)."
                )
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                if (hasConnectPermission()) {
                    gatt.discoverServices()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (activeGatt == gatt && hasConnectPermission()) {
                    runCatching { gatt.close() }
                    activeGatt = null
                }
                _sensorState.update {
                    it.copy(
                        connectionStatus = BleConnectionStatus.DISCONNECTED,
                        liveSpeedMph = null,
                        liveCadenceRpm = null,
                        statusMessage = if (status != BluetoothGatt.GATT_SUCCESS) {
                            "Sensor disconnected (status $status)"
                        } else {
                            "Sensor disconnected"
                        }
                    )
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _sensorState.update {
                    it.copy(
                        connectionStatus = BleConnectionStatus.ERROR,
                        statusMessage = "Could not read cycling sensor services."
                    )
                }
                return
            }

            val cscService = gatt.getService(CSC_SERVICE_UUID)
            val cscChar = cscService?.getCharacteristic(CSC_MEASUREMENT_CHAR_UUID)
            if (cscChar == null) {
                _sensorState.update {
                    it.copy(
                        connectionStatus = BleConnectionStatus.ERROR,
                        statusMessage = "Device does not expose CSC Speed/Cadence service (0x1816)."
                    )
                }
                return
            }

            if (!hasConnectPermission()) return
            gatt.setCharacteristicNotification(cscChar, true)
            val cccd = cscChar.getDescriptor(CCCD_UUID)
            if (cccd != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(cccd)
                }
            }

            val sensorName = _sensorState.value.pairedSensor?.name ?: "Cycling Sensor"
            _sensorState.update {
                it.copy(
                    connectionStatus = BleConnectionStatus.CONNECTED,
                    statusMessage = "Connected to $sensorName"
                )
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleCscPacket(characteristic.uuid, value)
        }

        @Suppress("DEPRECATION")
        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                val value = characteristic.value ?: return
                handleCscPacket(characteristic.uuid, value)
            }
        }
    }

    private fun handleCscPacket(charUuid: UUID, bytes: ByteArray) {
        if (charUuid != CSC_MEASUREMENT_CHAR_UUID) return

        val wheelMm = getWheelCircumferenceMm()
        val parsed = synchronized(parserLock) {
            val result = CscMeasurementParser.parse(
                bytes = bytes,
                previousState = parserState,
                wheelCircumferenceMm = wheelMm
            ) ?: return
            parserState = result.nextState
            result
        }

        val now = System.currentTimeMillis()
        if (parsed.wheelDataPresent && parsed.speedMps != null) {
            lastWheelPacketAtMillis = now
        }
        if (parsed.crankDataPresent && parsed.cadenceRpm != null) {
            lastCrankPacketAtMillis = now
        }

        val speedMph = parsed.speedMps?.let { it * 2.23694 }
        val sensorName = _sensorState.value.pairedSensor?.name ?: "BLE Sensor"

        _sensorState.update { current ->
            current.copy(
                connectionStatus = BleConnectionStatus.CONNECTED,
                hasWheelCapability = current.hasWheelCapability || parsed.wheelDataPresent,
                hasCadenceCapability = current.hasCadenceCapability || parsed.crankDataPresent,
                liveSpeedMph = speedMph ?: current.liveSpeedMph,
                liveCadenceRpm = parsed.cadenceRpm ?: current.liveCadenceRpm
            )
        }

        _readings.tryEmit(
            CscSensorReading(
                sensorName = sensorName,
                wheelDataPresent = parsed.wheelDataPresent,
                crankDataPresent = parsed.crankDataPresent,
                speedMps = parsed.speedMps,
                incrementalDistanceMeters = parsed.incrementalDistanceMeters,
                cadenceRpm = parsed.cadenceRpm,
                timestampMillis = now
            )
        )
    }

    companion object {
        private const val PREFS_NAME = "singletrack_ble_prefs"
        private const val KEY_SENSOR_ADDRESS = "paired_sensor_address"
        private const val KEY_SENSOR_NAME = "paired_sensor_name"
        private const val KEY_WHEEL_CIRC_MM = "wheel_circumference_mm"
        private const val SCAN_TIMEOUT_MS = 12_000L
        const val READING_FRESH_WINDOW_MS = 4_500L

        val CSC_SERVICE_UUID: UUID = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb")
        val CSC_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002a5b-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        fun requiredPermissions(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }
}
