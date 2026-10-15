package com.halo.btbattery.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.halo.btbattery.data.BatteryRepository
import com.halo.btbattery.data.HaloStore
import com.halo.btbattery.model.DeviceType
import com.halo.btbattery.model.Settings
import com.halo.btbattery.model.Snapshot
import com.halo.btbattery.service.LiveMonitorService
import com.halo.btbattery.widget.HaloWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = HaloStore.get(app)

    val snapshot: StateFlow<Snapshot> = store.snapshot.stateIn(viewModelScope, SharingStarted.Eagerly, Snapshot())
    val settings: StateFlow<Settings> = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                BatteryRepository.refresh(getApplication(), scan = true)
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun update(transform: (Settings) -> Settings) {
        viewModelScope.launch {
            store.updateSettings(transform)
            HaloWidget.updateAll(getApplication())
        }
    }

    fun setLive(on: Boolean) {
        update { it.copy(liveMode = on) }
        if (on) LiveMonitorService.start(getApplication()) else LiveMonitorService.stop(getApplication())
    }

    fun setHidden(address: String, hidden: Boolean) =
        update { s -> s.copy(hidden = if (hidden) s.hidden + address else s.hidden - address) }

    fun setType(address: String, type: DeviceType) {
        viewModelScope.launch {
            store.updateSettings { s -> s.copy(typeOverrides = s.typeOverrides + (address to type)) }
            val snap = store.snapshot.first()
            store.saveSnapshot(snap.copy(devices = snap.devices.map { if (it.address == address) it.copy(type = type) else it }))
            HaloWidget.updateAll(getApplication())
        }
    }

    fun forget(address: String) {
        viewModelScope.launch {
            val snap = store.snapshot.first()
            store.saveSnapshot(snap.copy(devices = snap.devices.filterNot { it.address == address }))
            store.updateSettings { s -> s.copy(hidden = s.hidden - address, typeOverrides = s.typeOverrides - address) }
            HaloWidget.updateAll(getApplication())
        }
    }
}
