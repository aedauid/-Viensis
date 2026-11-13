package com.example.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BluetoothDeviceUi(
    val address: String,
    val name: String,
    val isBonded: Boolean,
    val isActiveAudioRoute: Boolean,
    val isConnectedProfile: Boolean,
    val deviceTypeLabel: String
)

data class BluetoothAudioState(
    val isHardwareSupported: Boolean = false,
    val isAdapterEnabled: Boolean = false,
    val hasConnectPermission: Boolean = false,
    val hasScanPermission: Boolean = false,
    val isScanning: Boolean = false,
    val activeAudioOutputName: String = "Phone Speaker",
    val isBluetoothAudioActive: Boolean = false,
    val pairedDevices: List<BluetoothDeviceUi> = emptyList(),
    val discoveredDevices: List<BluetoothDeviceUi> = emptyList(),
    val statusMessage: String? = null
)

class BluetoothAudioController(private val appContext: Context) {
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _state = MutableStateFlow(BluetoothAudioState())
    val state: StateFlow<BluetoothAudioState> = _state.asStateFlow()

    private val discoveredMap = LinkedHashMap<String, BluetoothDevice>()
    private var a2dpConnectedAddresses: Set<String> = emptySet()

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshState()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshState()
        }
    }

    private val btReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED,
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED,
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> refreshState()
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null && hasConnectPermission()) {
                        val addr = device.address ?: return
                        discoveredMap[addr] = device
                        refreshState()
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _state.value = _state.value.copy(isScanning = false)
                }
            }
        }
    }

    init {
        try {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
            val filter = IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            ContextCompat.registerReceiver(
                appContext,
                btReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            queryA2dpProxy()
        } catch (e: Exception) {
            Log.w(TAG, "Bluetooth initialization warning: ${e.message}")
        }
        refreshState()
    }

    fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_ADMIN) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun queryA2dpProxy() {
        val adapter = bluetoothAdapter ?: return
        if (!hasConnectPermission()) return
        try {
            adapter.getProfileProxy(
                appContext,
                object : BluetoothProfile.ServiceListener {
                    @SuppressLint("MissingPermission")
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                        if (profile == BluetoothProfile.A2DP && proxy != null) {
                            a2dpConnectedAddresses = proxy.connectedDevices.mapNotNull { it.address }.toSet()
                            refreshState()
                            adapter.closeProfileProxy(BluetoothProfile.A2DP, proxy)
                        }
                    }

                    override fun onServiceDisconnected(profile: Int) = Unit
                },
                BluetoothProfile.A2DP
            )
        } catch (e: Exception) {
            Log.w(TAG, "A2DP proxy query skipped: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshState() {
        val hwSupported = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH) &&
            bluetoothAdapter != null
        val enabled = bluetoothAdapter?.isEnabled == true
        val connectGranted = hasConnectPermission()
        val scanGranted = hasScanPermission()

        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val activeBtDevice = outputs.firstOrNull { info ->
            info.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                info.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.type == AudioDeviceInfo.TYPE_BLE_HEADSET) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
        }
        val activeWired = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }

        val activeRouteName = when {
            activeBtDevice != null -> activeBtDevice.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth Audio"
            activeWired != null -> activeWired.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Wired Headphones"
            else -> "Phone Speaker"
        }

        val bondedList: List<BluetoothDeviceUi> = if (hwSupported && enabled && connectGranted) {
            try {
                bluetoothAdapter?.bondedDevices.orEmpty().map { dev ->
                    val addr = dev.address ?: ""
                    val name = dev.name?.takeIf { it.isNotBlank() } ?: addr
                    val isActiveRoute = activeBtDevice != null &&
                        (activeBtDevice.productName?.toString()?.equals(name, ignoreCase = true) == true ||
                            addr in a2dpConnectedAddresses)
                    BluetoothDeviceUi(
                        address = addr,
                        name = name,
                        isBonded = true,
                        isActiveAudioRoute = isActiveRoute,
                        isConnectedProfile = addr in a2dpConnectedAddresses || isActiveRoute,
                        deviceTypeLabel = "PAIRED"
                    )
                }.sortedWith(compareByDescending<BluetoothDeviceUi> { it.isActiveAudioRoute || it.isConnectedProfile }.thenBy { it.name })
            } catch (_: SecurityException) {
                emptyList()
            }
        } else emptyList()

        val bondedAddresses = bondedList.map { it.address }.toSet()
        val nearbyList: List<BluetoothDeviceUi> = if (hwSupported && enabled && connectGranted) {
            discoveredMap.values
                .filter { it.address !in bondedAddresses }
                .map { dev ->
                    val addr = dev.address ?: ""
                    val name = try { dev.name?.takeIf { it.isNotBlank() } ?: addr } catch (_: SecurityException) { addr }
                    BluetoothDeviceUi(
                        address = addr,
                        name = name,
                        isBonded = false,
                        isActiveAudioRoute = false,
                        isConnectedProfile = false,
                        deviceTypeLabel = "NEARBY"
                    )
                }
        } else emptyList()

        _state.value = BluetoothAudioState(
            isHardwareSupported = hwSupported,
            isAdapterEnabled = enabled,
            hasConnectPermission = connectGranted,
            hasScanPermission = scanGranted,
            isScanning = _state.value.isScanning && enabled,
            activeAudioOutputName = activeRouteName,
            isBluetoothAudioActive = activeBtDevice != null,
            pairedDevices = bondedList,
            discoveredDevices = nearbyList,
            statusMessage = _state.value.statusMessage
        )
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _state.value = _state.value.copy(statusMessage = "Turn on Bluetooth in Android settings first.")
            return
        }
        if (!hasScanPermission() || !hasConnectPermission()) {
            _state.value = _state.value.copy(statusMessage = "Allow Nearby Devices permission to scan.")
            return
        }
        try {
            discoveredMap.clear()
            if (adapter.isDiscovering) adapter.cancelDiscovery()
            val started = adapter.startDiscovery()
            _state.value = _state.value.copy(
                isScanning = started,
                statusMessage = if (started) "Scanning for nearby Bluetooth speakers & headphones..." else "Scan could not start."
            )
            queryA2dpProxy()
        } catch (e: SecurityException) {
            _state.value = _state.value.copy(isScanning = false, statusMessage = "Bluetooth permission denied.")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        try {
            if (hasScanPermission()) bluetoothAdapter?.cancelDiscovery()
        } catch (_: Exception) {
        }
        _state.value = _state.value.copy(isScanning = false)
    }

    @SuppressLint("MissingPermission")
    fun pairDevice(address: String) {
        val adapter = bluetoothAdapter ?: return
        if (!hasConnectPermission()) return
        try {
            stopScan()
            val device = discoveredMap[address] ?: adapter.getRemoteDevice(address)
            val initiated = device.createBond()
            _state.value = _state.value.copy(
                statusMessage = if (initiated) "Pairing request sent to ${device.name ?: address}..." else "Could not initiate pairing."
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(statusMessage = "Pairing error: ${e.localizedMessage}")
        }
    }

    fun routeAudioToBluetoothOrSpeaker(preferBluetooth: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val outputs = audioManager.availableCommunicationDevices
                val target = if (preferBluetooth) {
                    outputs.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    }
                } else {
                    outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                }
                if (target != null) {
                    audioManager.setCommunicationDevice(target)
                } else if (!preferBluetooth) {
                    audioManager.clearCommunicationDevice()
                }
            } else {
                @Suppress("DEPRECATION")
                if (preferBluetooth && audioManager.isBluetoothScoAvailableOffCall) {
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.stopBluetoothSco()
                    @Suppress("DEPRECATION")
                    audioManager.isBluetoothScoOn = false
                }
            }
            refreshState()
        } catch (e: Exception) {
            _state.value = _state.value.copy(statusMessage = "Audio route notice: ${e.localizedMessage}")
        }
    }

    fun openSystemBluetoothSettings() {
        try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        } catch (e: Exception) {
            _state.value = _state.value.copy(statusMessage = "Open Android Settings > Bluetooth to connect.")
        }
    }

    fun clearStatusMessage() {
        _state.value = _state.value.copy(statusMessage = null)
    }

    fun release() {
        stopScan()
        runCatching { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback) }
        runCatching { appContext.unregisterReceiver(btReceiver) }
    }

    private companion object {
        const val TAG = "BluetoothAudioCtrl"
    }
}
