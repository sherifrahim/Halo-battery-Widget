package com.halo.btbattery.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.halo.btbattery.model.AccentStyle
import com.halo.btbattery.model.Cell
import com.halo.btbattery.model.DeviceBattery
import com.halo.btbattery.model.DeviceType
import com.halo.btbattery.model.Settings
import com.halo.btbattery.model.Snapshot
import com.halo.btbattery.model.Source
import com.halo.btbattery.model.SurfaceStyle
import com.halo.btbattery.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private val Context.haloDataStore: DataStore<Preferences> by preferencesDataStore(name = "halo")

class HaloStore private constructor(context: Context) {
    private val ds = context.haloDataStore

    private val data: Flow<Preferences> = ds.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val snapshot: Flow<Snapshot> = data
        .map { prefs -> prefs[SNAPSHOT]?.let { runCatching { decodeSnapshot(it) }.getOrNull() } ?: Snapshot() }
        .distinctUntilChanged()

    val settings: Flow<Settings> = data.map(::readSettings).distinctUntilChanged()

    suspend fun saveSnapshot(snapshot: Snapshot) {
        ds.edit { it[SNAPSHOT] = encodeSnapshot(snapshot) }
    }

    suspend fun updateSettings(transform: (Settings) -> Settings) {
        ds.edit { prefs -> writeSettings(prefs, transform(readSettings(prefs))) }
    }

    private fun readSettings(p: Preferences) = Settings(
        theme = enumOr(p[THEME], ThemeMode.SYSTEM),
        surface = enumOr(p[SURFACE], SurfaceStyle.SOLID),
        accent = enumOr(p[ACCENT], AccentStyle.FLUID),
        showPhone = p[SHOW_PHONE] ?: true,
        showRecent = p[SHOW_RECENT] ?: true,
        liveMode = p[LIVE] ?: false,
        hidden = p[HIDDEN] ?: emptySet(),
        typeOverrides = p[OVERRIDES]?.let { raw ->
            runCatching {
                val o = JSONObject(raw)
                o.keys().asSequence().mapNotNull { k ->
                    enumOrNull<DeviceType>(o.getString(k))?.let { k to it }
                }.toMap()
            }.getOrNull()
        } ?: emptyMap(),
    )

    private fun writeSettings(p: MutablePreferences, s: Settings) {
        p[THEME] = s.theme.name
        p[SURFACE] = s.surface.name
        p[ACCENT] = s.accent.name
        p[SHOW_PHONE] = s.showPhone
        p[SHOW_RECENT] = s.showRecent
        p[LIVE] = s.liveMode
        p[HIDDEN] = s.hidden
        p[OVERRIDES] = JSONObject().apply { s.typeOverrides.forEach { (k, v) -> put(k, v.name) } }.toString()
    }

    companion object {
        private val SNAPSHOT = stringPreferencesKey("snapshot")
        private val THEME = stringPreferencesKey("theme")
        private val SURFACE = stringPreferencesKey("surface")
        private val ACCENT = stringPreferencesKey("accent")
        private val SHOW_PHONE = booleanPreferencesKey("show_phone")
        private val SHOW_RECENT = booleanPreferencesKey("show_recent")
        private val LIVE = booleanPreferencesKey("live_mode")
        private val HIDDEN = stringSetPreferencesKey("hidden")
        private val OVERRIDES = stringPreferencesKey("type_overrides")

        @Volatile private var instance: HaloStore? = null

        fun get(context: Context): HaloStore =
            instance ?: synchronized(this) {
                instance ?: HaloStore(context.applicationContext).also { instance = it }
            }
    }
}

private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } }

private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E = enumOrNull<E>(name) ?: default

// --- JSON codec -------------------------------------------------------------------------------

private fun Cell.toJson() = JSONObject().put("l", level).put("c", charging).put("t", at)

private fun JSONObject.cell(key: String): Cell? = optJSONObject(key)?.let {
    Cell(it.getInt("l"), it.optBoolean("c"), it.optLong("t"))
}

private fun encodeSnapshot(s: Snapshot): String {
    val arr = JSONArray()
    s.devices.forEach { d ->
        arr.put(JSONObject().apply {
            put("a", d.address); put("n", d.name); put("ty", d.type.name); put("on", d.connected)
            put("seen", d.lastSeen)
            d.main?.let { put("m", it.toJson()) }
            d.left?.let { put("L", it.toJson()) }
            d.right?.let { put("R", it.toJson()) }
            d.case?.let { put("C", it.toJson()) }
            put("src", JSONArray(d.sources.map { it.name }))
        })
    }
    return JSONObject()
        .put("devices", arr)
        .put("updated", s.updatedAt)
        .put("bt", s.bluetoothOn)
        .put("perm", s.permissionMissing)
        .toString()
}

private fun decodeSnapshot(raw: String): Snapshot {
    val o = JSONObject(raw)
    val arr = o.optJSONArray("devices") ?: JSONArray()
    val devices = (0 until arr.length()).map { i ->
        val d = arr.getJSONObject(i)
        val src = d.optJSONArray("src")
        DeviceBattery(
            address = d.getString("a"),
            name = d.optString("n"),
            type = enumOr(d.optString("ty"), DeviceType.OTHER),
            connected = d.optBoolean("on"),
            main = d.cell("m"),
            left = d.cell("L"),
            right = d.cell("R"),
            case = d.cell("C"),
            sources = src?.let { a -> (0 until a.length()).mapNotNull { enumOrNull<Source>(a.getString(it)) }.toSet() }
                ?: emptySet(),
            lastSeen = d.optLong("seen"),
        )
    }
    return Snapshot(devices, o.optLong("updated"), o.optBoolean("bt", true), o.optBoolean("perm"))
}
