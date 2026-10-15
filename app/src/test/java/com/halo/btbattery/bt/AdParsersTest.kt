package com.halo.btbattery.bt

import com.halo.btbattery.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdParsersTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test fun fastPairBatteryWithAccountKeyAndSalt() {
        // flags | account-key filter (len 3, type 0) | salt (len 1, type 1) | battery (len 3, type 3)
        val data = bytes(0x00, 0x30, 0xAA, 0xBB, 0xCC, 0x11, 0x42, 0x33, 85, 0x80 or 80, 42)
        val ad = FastPairParser.parse(data, rssi = -50)!!
        assertEquals(Source.FAST_PAIR, ad.source)
        assertEquals(85, ad.left)
        assertEquals(80, ad.right)
        assertEquals(42, ad.case)
        assertFalse(ad.leftCharging)
        assertTrue(ad.rightCharging)
        assertFalse(ad.caseCharging)
    }

    @Test fun fastPairUnknownCaseAndHideUiType() {
        val data = bytes(0x00, 0x00, 0x21, 0x01, 0x02, 0x34, 60, 61, 0x7F)
        val ad = FastPairParser.parse(data)!!
        assertEquals(60, ad.left)
        assertEquals(61, ad.right)
        assertNull(ad.case)
    }

    @Test fun fastPairDiscoverableModelIdHasNoBattery() {
        assertNull(FastPairParser.parse(bytes(0x2C, 0xA1, 0x05)))
    }

    @Test fun fastPairTruncatedIsIgnored() {
        assertNull(FastPairParser.parse(bytes(0x00, 0x30, 0xAA, 0xBB, 0xCC, 0x33, 50)))
    }

    private fun apple(status: Int, buds: Int, caseByte: Int, model: Int = 0x1420): ByteArray {
        val d = ByteArray(27)
        d[0] = 0x07; d[1] = 0x19; d[2] = 0x01
        d[3] = (model shr 8).toByte(); d[4] = model.toByte()
        d[5] = status.toByte(); d[6] = buds.toByte(); d[7] = caseByte.toByte()
        return d
    }

    @Test fun airPodsNotFlipped() {
        // status bit 5 set → not flipped: left = low nibble, right = high nibble
        val ad = AppleProximityParser.parse(apple(status = 0x20 or 0x01, buds = 0x89, caseByte = 0x46))!!
        assertEquals("AirPods Pro 2", ad.model)
        assertEquals(95, ad.left)   // 9 → 95%
        assertEquals(85, ad.right)  // 8 → 85%
        assertEquals(65, ad.case)   // 6 → 65%
        assertTrue(ad.caseCharging) // flag 0b100
        assertFalse(ad.leftCharging)
    }

    @Test fun airPodsFlippedAndFull() {
        val ad = AppleProximityParser.parse(apple(status = 0x01, buds = 0xA3, caseByte = 0x1F))!!
        assertEquals(100, ad.left)  // high nibble when flipped
        assertEquals(35, ad.right)
        assertNull(ad.case)         // 0xF = unknown
        assertFalse(ad.leftCharging)
        assertTrue(ad.rightCharging) // flipped: bit0 is right
    }

    @Test fun airPodsMaxIsSingleBattery() {
        val ad = AppleProximityParser.parse(apple(status = 0x20, buds = 0xF7, caseByte = 0xFF, model = 0x0A20))!!
        assertEquals(75, ad.single)
        assertNull(ad.left)
    }

    @Test fun nonProximityAppleMessageIgnored() {
        val d = apple(0x20, 0x55, 0x05); d[0] = 0x10
        assertNull(AppleProximityParser.parse(d))
    }
}
