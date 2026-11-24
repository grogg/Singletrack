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
import com.example.domain.model.PairedHrSensor
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

data class HrSensorReading(
    val sensorName: String,
    val heartRateBpm: Int,
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

    private val _hrReadings = MutableSharedFlow<HrSensorReading>(extraBufferCapacity = 32)
    val hrReadings: SharedFlow<HrSensorReading> = _hrReadings.asSharedFlow()

    private var activeGatt: BluetoothGatt? = null
    private var activeHrGatt: BluetoothGatt? = null
    private var activeScanner: BluetoothLeScanner? = null
    private var activeHrScanner: BluetoothLeScanner? = null
    private var scanTimeoutJob: Job? = null
    private var hrScanTimeoutJob: Job? = null
    private var staleWatchdogJob: Job? = null
    private var staleHrWatchdogJob: Job? = null
    private val parserLock = Any()
    private var parserState = CscParserState()

    @Volatile
    private var lastWheelPacketAtMillis: Long = 0L

    @Volatile
    private var lastCrankPacketAtMillis: Long = 0L

    @Volatile
    private var lastHrPacketAtMillis: Long = 0L

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
        val savedHrAddress = prefs.getString(KEY_HR_SENSOR_ADDRESS, null)
        val savedHrName = prefs.getString(KEY_HR_SENSOR_NAME, null)
        val pairedHr = if (!savedHrAddress.isNullOrBlank()) {
            PairedHrSensor(
                name = savedHrName?.takeIf { it.isNotBlank() } ?: "Heart Rate Monitor",
                address = savedHrAddress
            )
        } else {
            null
        }
        return BleSensorUiState(
            isBluetoothSupported = supported,
            isBluetoothEnabled = bluetoothAdapter?.isEnabled == true,
            hasBluetoothPermissions = hasRequiredPermissions(),
            pairedSensor = paired,
            pairedHrSensor = pairedHr
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
        stopHrScanInternal()

        activeScanner = scanner
        _sensorState.update {
            it.copy(
                isScanning = true,
                isScanningHr = false,
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
        val wasScanning = _sensorState.value.isScanning
        stopScanInternal()
        if (!wasScanning) return
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
        val scanner = activeScanner
        activeScanner = null
        if (scanner != null && hasScanPermission()) {
            runCatching { scanner.stopScan(scanCallback) }
        }
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
        lastWheelPacketAtMillis = 0L
        lastCrankPacketAtMillis = 0L
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

    fun getLatestFreshHeartRate(nowMillis: Long = System.currentTimeMillis()): Int? {
        if (_sensorState.value.hrConnectionStatus != BleConnectionStatus.CONNECTED) return null
        if ((nowMillis - lastHrPacketAtMillis) > READING_FRESH_WINDOW_MS) return null
        return _sensorState.value.liveHeartRateBpm
    }

    @SuppressLint("MissingPermission")
    fun startHrScan() {
        refreshCapabilities()
        val state = _sensorState.value
        if (!state.isBluetoothSupported) {
            _sensorState.update { it.copy(hrStatusMessage = "Bluetooth Low Energy is not supported on this device.") }
            return
        }
        if (!hasRequiredPermissions()) {
            _sensorState.update { it.copy(hrStatusMessage = "Bluetooth permission is required to scan for HR monitors.") }
            return
        }
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _sensorState.update { it.copy(hrStatusMessage = "Turn on Bluetooth in system settings to scan for HR monitors.") }
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: run {
            _sensorState.update { it.copy(hrStatusMessage = "BLE scanner unavailable.") }
            return
        }

        stopScanInternal()
        stopHrScanInternal()

        activeHrScanner = scanner
        _sensorState.update {
            it.copy(
                isScanning = false,
                isScanningHr = true,
                discoveredHrSensors = emptyList(),
                hrStatusMessage = "Scanning for Bluetooth Heart Rate (HR) monitors…"
            )
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(HR_SERVICE_UUID))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(filters, settings, hrScanCallback)
        } catch (_: SecurityException) {
            _sensorState.update {
                it.copy(
                    isScanningHr = false,
                    hrStatusMessage = "Missing Bluetooth scan permission."
                )
            }
            return
        }

        hrScanTimeoutJob?.cancel()
        hrScanTimeoutJob = scope.launch {
            delay(SCAN_TIMEOUT_MS)
            stopHrScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopHrScan() {
        val wasScanning = _sensorState.value.isScanningHr
        stopHrScanInternal()
        if (!wasScanning) return
        _sensorState.update { current ->
            val msg = if (current.discoveredHrSensors.isEmpty() && current.hrConnectionStatus == BleConnectionStatus.DISCONNECTED) {
                "No Heart Rate monitors found. Ensure your chest strap or armband is worn and awake."
            } else {
                null
            }
            current.copy(isScanningHr = false, hrStatusMessage = msg ?: current.hrStatusMessage)
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopHrScanInternal() {
        hrScanTimeoutJob?.cancel()
        hrScanTimeoutJob = null
        val scanner = activeHrScanner
        activeHrScanner = null
        if (scanner != null && hasScanPermission()) {
            runCatching { scanner.stopScan(hrScanCallback) }
        }
    }

    fun pairAndConnectHr(sensor: DiscoveredBleSensor) {
        stopHrScanInternal()
        val config = PairedHrSensor(
            name = sensor.name,
            address = sensor.address
        )
        prefs.edit {
            putString(KEY_HR_SENSOR_ADDRESS, config.address)
            putString(KEY_HR_SENSOR_NAME, config.name)
        }
        _sensorState.update {
            it.copy(
                isScanningHr = false,
                pairedHrSensor = config,
                hrStatusMessage = "Paired ${config.name}"
            )
        }
        connectPairedHrSensor()
    }

    @SuppressLint("MissingPermission")
    fun connectPairedHrSensor() {
        refreshCapabilities()
        val paired = _sensorState.value.pairedHrSensor ?: return
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled || !hasConnectPermission()) {
            _sensorState.update {
                it.copy(
                    hrConnectionStatus = BleConnectionStatus.DISCONNECTED,
                    hrStatusMessage = if (!adapter.isEnabled) {
                        "Enable Bluetooth to connect ${paired.name}"
                    } else {
                        "Grant Bluetooth permission to connect ${paired.name}"
                    }
                )
            }
            return
        }

        if (_sensorState.value.hrConnectionStatus == BleConnectionStatus.CONNECTED && activeHrGatt != null) {
            return
        }

        disconnectHrGattOnly()

        val device = runCatching { adapter.getRemoteDevice(paired.address) }.getOrNull()
        if (device == null) {
            _sensorState.update {
                it.copy(
                    hrConnectionStatus = BleConnectionStatus.ERROR,
                    hrStatusMessage = "Saved HR sensor address is invalid."
                )
            }
            return
        }

        _sensorState.update {
            it.copy(
                hrConnectionStatus = BleConnectionStatus.CONNECTING,
                hrStatusMessage = "Connecting to ${paired.name}…"
            )
        }

        try {
            activeHrGatt = device.connectGatt(context, false, hrGattCallback, BluetoothDevice.TRANSPORT_LE)
            startHrStaleWatchdog()
        } catch (_: SecurityException) {
            _sensorState.update {
                it.copy(
                    hrConnectionStatus = BleConnectionStatus.ERROR,
                    hrStatusMessage = "Bluetooth Connect permission required."
                )
            }
        }
    }

    fun forgetPairedHrSensor() {
        disconnectHr()
        prefs.edit {
            remove(KEY_HR_SENSOR_ADDRESS)
            remove(KEY_HR_SENSOR_NAME)
        }
        _sensorState.update {
            it.copy(
                pairedHrSensor = null,
                hrConnectionStatus = BleConnectionStatus.DISCONNECTED,
                liveHeartRateBpm = null,
                hrStatusMessage = null
            )
        }
    }

    fun disconnectHr() {
        stopHrScanInternal()
        staleHrWatchdogJob?.cancel()
        staleHrWatchdogJob = null
        lastHrPacketAtMillis = 0L
        disconnectHrGattOnly()
        _sensorState.update {
            it.copy(
                isScanningHr = false,
                hrConnectionStatus = BleConnectionStatus.DISCONNECTED,
                liveHeartRateBpm = null
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectHrGattOnly() {
        val gatt = activeHrGatt
        activeHrGatt = null
        if (gatt != null && hasConnectPermission()) {
            runCatching {
                gatt.disconnect()
                gatt.close()
            }
        }
    }

    private fun startHrStaleWatchdog() {
        staleHrWatchdogJob?.cancel()
        staleHrWatchdogJob = scope.launch {
            while (isActive) {
                delay(1500L)
                val now = System.currentTimeMillis()
                val clearHr = lastHrPacketAtMillis > 0L && (now - lastHrPacketAtMillis) > READING_FRESH_WINDOW_MS
                if (clearHr) {
                    _sensorState.update { state ->
                        if (state.liveHeartRateBpm != null) {
                            state.copy(liveHeartRateBpm = null)
                        } else {
                            state
                        }
                    }
                }
            }
        }
    }

    fun release() {
        disconnect()
        disconnectHr()
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

    @SuppressLint("MissingPermission")
    private fun resolveScanDeviceName(
        result: ScanResult,
        device: BluetoothDevice,
        address: String,
        fallbackPrefix: String
    ): String {
        val advertised = result.scanRecord?.deviceName
        if (!advertised.isNullOrBlank()) return advertised
        val deviceName = if (hasConnectPermission()) device.name else null
        return deviceName?.takeIf { it.isNotBlank() } ?: "$fallbackPrefix (${address.takeLast(5)})"
    }

    @SuppressLint("MissingPermission")
    private fun enableCharacteristicNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        if (!hasConnectPermission()) return
        gatt.setCharacteristicNotification(characteristic, true)
        val cccd = characteristic.getDescriptor(CCCD_UUID) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(cccd)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val address = device.address ?: return
            val deviceName = resolveScanDeviceName(result, device, address, "Cycling Sensor")
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
            if (activeGatt != null && activeGatt !== gatt) {
                if (hasConnectPermission()) {
                    runCatching { gatt.close() }
                }
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                if (hasConnectPermission()) {
                    gatt.discoverServices()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (activeGatt == gatt && hasConnectPermission()) {
                    runCatching { gatt.close() }
                    activeGatt = null
                }
                staleWatchdogJob?.cancel()
                staleWatchdogJob = null
                lastWheelPacketAtMillis = 0L
                lastCrankPacketAtMillis = 0L
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
            enableCharacteristicNotifications(gatt, cscChar)

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

    private val hrScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val address = device.address ?: return
            val deviceName = resolveScanDeviceName(result, device, address, "HR Monitor")
            val candidate = DiscoveredBleSensor(
                name = deviceName,
                address = address,
                rssi = result.rssi
            )
            _sensorState.update { current ->
                val updatedList = (current.discoveredHrSensors.filterNot { it.address == address } + candidate)
                    .sortedByDescending { it.rssi }
                current.copy(
                    discoveredHrSensors = updatedList,
                    hrStatusMessage = "Found ${updatedList.size} BLE heart rate sensor(s)"
                )
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _sensorState.update {
                it.copy(
                    isScanningHr = false,
                    hrStatusMessage = "BLE HR sensor scan failed (code $errorCode)."
                )
            }
        }
    }

    private val hrGattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (activeHrGatt != null && activeHrGatt !== gatt) {
                if (hasConnectPermission()) {
                    runCatching { gatt.close() }
                }
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                if (hasConnectPermission()) {
                    gatt.discoverServices()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (activeHrGatt == gatt && hasConnectPermission()) {
                    runCatching { gatt.close() }
                    activeHrGatt = null
                }
                staleHrWatchdogJob?.cancel()
                staleHrWatchdogJob = null
                lastHrPacketAtMillis = 0L
                _sensorState.update {
                    it.copy(
                        hrConnectionStatus = BleConnectionStatus.DISCONNECTED,
                        liveHeartRateBpm = null,
                        hrStatusMessage = if (status != BluetoothGatt.GATT_SUCCESS) {
                            "HR sensor disconnected (status $status)"
                        } else {
                            "HR sensor disconnected"
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
                        hrConnectionStatus = BleConnectionStatus.ERROR,
                        hrStatusMessage = "Could not read heart rate sensor services."
                    )
                }
                return
            }

            val hrService = gatt.getService(HR_SERVICE_UUID)
            val hrChar = hrService?.getCharacteristic(HR_MEASUREMENT_CHAR_UUID)
            if (hrChar == null) {
                _sensorState.update {
                    it.copy(
                        hrConnectionStatus = BleConnectionStatus.ERROR,
                        hrStatusMessage = "Device does not expose Heart Rate service (0x180D)."
                    )
                }
                return
            }

            if (!hasConnectPermission()) return
            enableCharacteristicNotifications(gatt, hrChar)

            val sensorName = _sensorState.value.pairedHrSensor?.name ?: "Heart Rate Monitor"
            _sensorState.update {
                it.copy(
                    hrConnectionStatus = BleConnectionStatus.CONNECTED,
                    hrStatusMessage = "Connected to $sensorName"
                )
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleHrPacket(characteristic.uuid, value)
        }

        @Suppress("DEPRECATION")
        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                val value = characteristic.value ?: return
                handleHrPacket(characteristic.uuid, value)
            }
        }
    }

    private fun handleHrPacket(charUuid: UUID, bytes: ByteArray) {
        if (charUuid != HR_MEASUREMENT_CHAR_UUID) return
        val parsed = HrMeasurementParser.parse(bytes) ?: return

        val now = System.currentTimeMillis()
        lastHrPacketAtMillis = now

        val sensorName = _sensorState.value.pairedHrSensor?.name ?: "Heart Rate Monitor"

        _sensorState.update { current ->
            current.copy(
                hrConnectionStatus = BleConnectionStatus.CONNECTED,
                liveHeartRateBpm = parsed.heartRateBpm
            )
        }

        _hrReadings.tryEmit(
            HrSensorReading(
                sensorName = sensorName,
                heartRateBpm = parsed.heartRateBpm,
                timestampMillis = now
            )
        )
    }

    companion object {
        private const val PREFS_NAME = "singletrack_ble_prefs"
        private const val KEY_SENSOR_ADDRESS = "paired_sensor_address"
        private const val KEY_SENSOR_NAME = "paired_sensor_name"
        private const val KEY_WHEEL_CIRC_MM = "wheel_circumference_mm"
        private const val KEY_HR_SENSOR_ADDRESS = "paired_hr_sensor_address"
        private const val KEY_HR_SENSOR_NAME = "paired_hr_sensor_name"
        private const val SCAN_TIMEOUT_MS = 12_000L
        const val READING_FRESH_WINDOW_MS = 4_500L

        val CSC_SERVICE_UUID: UUID = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb")
        val CSC_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002a5b-0000-1000-8000-00805f9b34fb")
        val HR_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
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
