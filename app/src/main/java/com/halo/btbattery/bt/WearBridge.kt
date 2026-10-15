package com.halo.btbattery.bt

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Wear OS watches (Galaxy Watch, Pixel Watch, OPPO/OnePlus Watch…) don't report their battery over
 * Bluetooth, and their makers' apps use private channels. The Halo companion on the watch answers
 * a battery request over Google's Wear OS data layer instead.
 */
object WearBridge {
    const val CAPABILITY = "halo_battery"
    const val PATH_REQUEST = "/halo/battery/request"
    const val PATH_REPORT = "/halo/battery/report"

    data class Reading(val nodeId: String, val name: String, val level: Int, val charging: Boolean)

    /** [readings] from watches running Halo; [withoutApp] names connected watches that don't have it yet. */
    data class Result(val readings: List<Reading>, val withoutApp: List<String>)

    suspend fun query(context: Context, timeoutMs: Long = 5_000): Result = runCatching {
        val nodes = withTimeoutOrNull(3_000) { Wearable.getNodeClient(context).connectedNodes.await() }.orEmpty()
        if (nodes.isEmpty()) return Result(emptyList(), emptyList())
        val capable = withTimeoutOrNull(3_000) {
            Wearable.getCapabilityClient(context).getCapability(CAPABILITY, CapabilityClient.FILTER_REACHABLE).await().nodes
        }.orEmpty().map { it.id }.toSet()

        val replies = ConcurrentHashMap<String, Reading>()
        val messages = Wearable.getMessageClient(context)
        val listener = MessageClient.OnMessageReceivedListener { event ->
            if (event.path != PATH_REPORT) return@OnMessageReceivedListener
            parse(event.data)?.let { (level, charging) ->
                val name = nodes.firstOrNull { it.id == event.sourceNodeId }?.displayName ?: "Watch"
                replies[event.sourceNodeId] = Reading(event.sourceNodeId, name, level, charging)
            }
        }
        messages.addListener(listener).await()
        try {
            val targets = nodes.filter { it.id in capable }
            targets.forEach { runCatching { messages.sendMessage(it.id, PATH_REQUEST, ByteArray(0)).await() } }
            withTimeoutOrNull(timeoutMs) { while (replies.size < targets.size) delay(100) }
        } finally {
            runCatching { messages.removeListener(listener).await() }
        }
        Result(replies.values.toList(), nodes.filter { it.id !in capable }.map { it.displayName })
    }.getOrElse { Result(emptyList(), emptyList()) } // no Google Play services, or the API is unavailable

    /** Payload is "level,charging", e.g. "67,1". */
    fun encode(level: Int, charging: Boolean) = "$level,${if (charging) 1 else 0}".toByteArray()

    fun parse(data: ByteArray): Pair<Int, Boolean>? {
        val parts = String(data).split(",")
        val level = parts.getOrNull(0)?.toIntOrNull()?.takeIf { it in 0..100 } ?: return null
        return level to (parts.getOrNull(1) == "1")
    }

    /** "Galaxy Watch7 (A1B2)" ≈ "Galaxy Watch7 (A1B2) LE": compare letters and digits only. */
    fun sameDevice(a: String, b: String): Boolean {
        fun norm(s: String) = s.lowercase().replace(Regex("\\s*le$"), "").filter { it.isLetterOrDigit() }
        val x = norm(a)
        val y = norm(b)
        return x.isNotEmpty() && y.isNotEmpty() && (x == y || x.startsWith(y) || y.startsWith(x))
    }
}
