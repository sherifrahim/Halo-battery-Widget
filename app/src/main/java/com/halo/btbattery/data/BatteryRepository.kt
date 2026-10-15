package com.halo.btbattery.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import com.halo.btbattery.bt.AdMatcher
import com.halo.btbattery.bt.BleBatteryScanner
import com.halo.btbattery.bt.BudsAd
import com.halo.btbattery.bt.DeviceClassifier
import com.halo.btbattery.bt.GattBatteryReader
import com.halo.btbattery.bt.Permissions
import com.halo.btbattery.bt.SystemBluetooth
import com.halo.btbattery.model.Cell
import com.halo.btbattery.model.DeviceBattery
import com.halo.btbattery.model.DeviceType
import com.halo.btbattery.model.Snapshot
import com.halo.btbattery.model.Source
import com.halo.btbattery.widget.HaloWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Gathers every battery source, merges it with what we knew before, saves it, redraws widgets. */
@SuppressLint("MissingPermission")
object BatteryRepository {
    private val mutex = Mutex()
    private const val FORGET_AFTER_MS = 7L * 24 * 60 * 60_000
    private const val GATT_EVERY_MS = 10 * 60_000L

    suspend fun refresh(context: Context, scan: Boolean = true) = mutex.withLock {
        withContext(Dispatchers.IO) { runCatching { doRefresh(context.applicationContext, scan) } }
        Unit
    }

    private suspend fun doRefresh(context: Context, scan: Boolean) {
        val store = HaloStore.get(context)
        val settings = store.settings.first()
        val previous = store.snapshot.first()
        val now = System.currentTimeMillis()

        if (!Permissions.canConnect(context)) {
            store.saveSnapshot(previous.copy(permissionMissing = true))
            HaloWidget.updateAll(context)
            return
        }

        val btOn = SystemBluetooth.isOn(context)
        val before = previous.devices.associateBy { it.address }
        val handles = HashMap<String, BluetoothDevice>()
        val live = mutableListOf<DeviceBattery>()

        if (btOn) for (device in SystemBluetooth.connectedDevices(context)) {
            val name = SystemBluetooth.safeName(device)
            val cls = DeviceClassifier.classify(name, runCatching { device.bluetoothClass }.getOrNull())
            if (cls.excluded) continue
            val old = before[device.address]
            val sys = SystemBluetooth.batteryLevel(device)
            val learnedBuds = old?.hasBuds == true && cls.type.isAudio
            handles[device.address] = device
            live += DeviceBattery(
                address = device.address,
                name = name,
                type = settings.typeOverrides[device.address]
                    ?: if (learnedBuds) DeviceType.EARBUDS else cls.type,
                connected = true,
                main = sys?.let { Cell(it, at = now) } ?: old?.main,
                left = old?.left,
                right = old?.right,
                case = old?.case,
                sources = if (sys != null) setOf(Source.SYSTEM) else old?.sources.orEmpty(),
                lastSeen = now,
            )
        }

        // Individual buds + case from BLE adverts.
        if (scan && live.any { it.type.isAudio }) {
            val ads = BleBatteryScanner.scan(context)
            AdMatcher.match(ads, live).forEach { (address, ad) ->
                val i = live.indexOfFirst { it.address == address }
                if (i >= 0) live[i] = live[i].withAd(ad, now, settings.typeOverrides.containsKey(address))
            }
        }

        // Standard battery service for BLE wearables that Android doesn't report.
        for (i in live.indices) {
            val d = live[i]
            val device = handles[d.address] ?: continue
            val isLe = runCatching { device.type == BluetoothDevice.DEVICE_TYPE_LE }.getOrDefault(false)
            val fresh = d.main != null && Source.SYSTEM in d.sources
            val recentGatt = Source.GATT in d.sources && d.main != null && now - d.main.at < GATT_EVERY_MS
            if (!isLe || fresh || d.hasBuds || recentGatt) continue
            GattBatteryReader.read(context, device)?.let { level ->
                live[i] = d.copy(main = Cell(level, at = now), sources = d.sources + Source.GATT)
            }
        }

        val liveAddresses = live.map { it.address }.toSet()
        val offline = previous.devices
            .filter { it.address !in liveAddresses && now - it.lastSeen < FORGET_AFTER_MS }
            .map { it.copy(connected = false) }

        store.saveSnapshot(Snapshot(live + offline, now, btOn, permissionMissing = false))
        HaloWidget.updateAll(context)
    }

    private fun DeviceBattery.withAd(ad: BudsAd, now: Long, typeLocked: Boolean): DeviceBattery {
        if (ad.single != null) {
            return copy(
                main = main?.takeIf { Source.SYSTEM in sources } ?: Cell(ad.single, ad.singleCharging, now),
                sources = sources + ad.source,
            )
        }
        return copy(
            type = if (typeLocked) type else DeviceType.EARBUDS,
            left = ad.left?.let { Cell(it, ad.leftCharging, now) } ?: left,
            right = ad.right?.let { Cell(it, ad.rightCharging, now) } ?: right,
            case = ad.case?.let { Cell(it, ad.caseCharging, now) } ?: case,
            sources = sources + ad.source,
        )
    }
}
