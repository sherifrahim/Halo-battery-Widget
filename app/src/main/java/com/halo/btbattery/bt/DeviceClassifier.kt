package com.halo.btbattery.bt

import android.bluetooth.BluetoothClass
import com.halo.btbattery.model.DeviceType

/**
 * Works out what kind of device something is from its name and Bluetooth class, and drops
 * things that have no battery worth showing: car head units, other phones, laptops, TVs.
 */
object DeviceClassifier {

    data class Result(val type: DeviceType, val excluded: Boolean)

    private val carPattern = Regex(
        "\\b(car|my ?car|car ?kit|carplay|android auto|sync|uconnect|mylink|entune|mbux|idrive|" +
            "mmi|sensus|intellilink|media ?nav|toyota|lexus|honda|acura|nissan|infiniti|hyundai|kia|" +
            "bmw|mini|mercedes|benz|audi|vw|volkswagen|skoda|seat|cupra|porsche|ford|lincoln|chevrolet|" +
            "chevy|gmc|cadillac|buick|jeep|dodge|chrysler|tesla|subaru|mazda|mitsubishi|suzuki|volvo|" +
            "polestar|peugeot|renault|citroen|opel|vauxhall|fiat|alfa romeo|land rover|range rover|jaguar|" +
            "tata|mahindra|maruti|byd|geely|mg|proton|perodua)\\b",
        RegexOption.IGNORE_CASE,
    )

    private val earbudWords = listOf(
        "buds", "earbud", "airpods pro", "airpods", "pods", "enco", "freebuds", "tws", "wf-", "liberty",
        "elite", "nothing ear", "ear (", "earfun", "beats fit", "powerbeats", "studio buds",
        "momentum true", "true wireless", "earphone", "in-ear", "linkbuds", "soundpeats",
    )
    private val headphoneWords = listOf(
        "airpods max", "wh-", "headphone", "headset", "quietcomfort", "qc", "momentum", "studio pro",
        "solo", "xm4", "xm5", "xm6", "bose 700", "ath-", "maxwell", "arctis", "hyperx", "kraken", "major",
        "monitor ii", "space one", "tune 7", "live 6", "q30", "q35", "q45", "space q",
    )
    private val watchWords = listOf(
        "watch", "band", "amazfit", "garmin", "fenix", "forerunner", "venu", "vivoactive", "instinct",
        "fitbit", "versa", "sense", "charge 5", "charge 6", "inspire", "ticwatch", "gear s", "suunto",
        "polar", "coros", "whoop", "gt 2", "gt 3", "gt 4", "gt 5", "gt 6", "ring",
    )
    private val speakerWords = listOf(
        "speaker", "flip", "charge", "boombox", "jbl go", "clip", "xtreme", "pulse", "soundlink",
        "megaboom", "wonderboom", "boom", "motion", "srs-", "ult field", "soundcore", "partybox",
        "emberton", "stanmore", "acton", "willen", "middleton", "tufton", "sonos", "roam", "bose home",
        "beosound", "beolit", "echo", "nest",
    )
    private val keyboardWords = listOf("keyboard", "keys", "k380", "k780", "k400", "magic keyboard")
    private val mouseWords = listOf("mouse", "mx master", "mx anywhere", "trackpad", "trackball", "m720", "m337")
    private val gamepadWords = listOf(
        "controller", "gamepad", "xbox", "dualsense", "dualshock", "joy-con", "8bitdo", "pro controller",
        "gamesir", "razer kishi", "backbone",
    )

    fun classify(name: String, btClass: BluetoothClass?): Result {
        val n = name.lowercase()
        val major = btClass?.majorDeviceClass ?: BluetoothClass.Device.Major.UNCATEGORIZED
        val device = btClass?.deviceClass ?: 0

        val byName = typeFromName(n)

        // Cars: never shown. Name match wins over class so "JBL Charge" never trips this.
        if (device == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO) return Result(DeviceType.OTHER, true)
        if (byName == null && carPattern.containsMatchIn(n)) return Result(DeviceType.OTHER, true)
        // Hands-free class is used by most car kits, but also some mono headsets.
        if (device == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE && byName == null) {
            return Result(DeviceType.HEADPHONES, carPattern.containsMatchIn(n))
        }

        if (byName != null) return Result(byName, false)

        return when (major) {
            BluetoothClass.Device.Major.PHONE,
            BluetoothClass.Device.Major.COMPUTER,
            BluetoothClass.Device.Major.NETWORKING,
            BluetoothClass.Device.Major.IMAGING -> Result(DeviceType.OTHER, true)

            BluetoothClass.Device.Major.AUDIO_VIDEO -> when (device) {
                BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
                BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO,
                BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO -> Result(DeviceType.SPEAKER, false)
                BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
                BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> Result(DeviceType.HEADPHONES, false)
                BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER,
                BluetoothClass.Device.AUDIO_VIDEO_VIDEO_MONITOR,
                BluetoothClass.Device.AUDIO_VIDEO_SET_TOP_BOX,
                BluetoothClass.Device.AUDIO_VIDEO_VCR,
                BluetoothClass.Device.AUDIO_VIDEO_VIDEO_CONFERENCING -> Result(DeviceType.OTHER, true)
                else -> Result(DeviceType.HEADPHONES, false)
            }

            BluetoothClass.Device.Major.WEARABLE ->
                Result(if (device == BluetoothClass.Device.WEARABLE_WRIST_WATCH) DeviceType.WATCH else DeviceType.OTHER, false)

            BluetoothClass.Device.Major.PERIPHERAL -> Result(peripheralType(device), false)

            else -> Result(DeviceType.OTHER, false)
        }
    }

    private fun typeFromName(n: String): DeviceType? = when {
        // Order matters: "AirPods Max" is headphones, "Beats Fit Pro" is earbuds not a fitness band.
        n.contains("airpods max") -> DeviceType.HEADPHONES
        earbudWords.any { n.contains(it) } -> DeviceType.EARBUDS
        headphoneWords.any { n.contains(it) } -> DeviceType.HEADPHONES
        gamepadWords.any { n.contains(it) } -> DeviceType.GAMEPAD
        keyboardWords.any { n.contains(it) } -> DeviceType.KEYBOARD
        mouseWords.any { n.contains(it) } -> DeviceType.MOUSE
        watchWords.any { n.contains(it) } -> DeviceType.WATCH
        speakerWords.any { n.contains(it) } -> DeviceType.SPEAKER
        else -> null
    }

    // Peripheral minor class bits: 0x40 keyboard, 0x80 pointing, low bits 0x04/0x08 joystick/gamepad.
    private fun peripheralType(deviceClass: Int): DeviceType = when {
        deviceClass and 0x3C == 0x04 || deviceClass and 0x3C == 0x08 -> DeviceType.GAMEPAD
        deviceClass and 0xC0 == 0x40 -> DeviceType.KEYBOARD
        deviceClass and 0xC0 == 0x80 -> DeviceType.MOUSE
        deviceClass and 0xC0 == 0xC0 -> DeviceType.KEYBOARD
        else -> DeviceType.OTHER
    }

    fun isAppleAudioName(name: String): Boolean {
        val n = name.lowercase()
        return n.contains("airpods") || n.contains("beats") || n.contains("powerbeats")
    }
}
