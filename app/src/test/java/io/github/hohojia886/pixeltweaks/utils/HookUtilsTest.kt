package io.github.hohojia886.pixeltweaks.utils

import android.content.Intent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HookUtilsTest {

    @Test
    fun testGetFlexibleIntExtraWithIntExtra() {
        val intent = Intent().apply { putExtra("key", 5) }
        assertEquals(5, intent.getFlexibleIntExtra("key", 1))
    }

    @Test
    fun testGetFlexibleIntExtraWithFloatExtra() {
        val intent = Intent().apply { putExtra("key", 8.0f) }
        assertEquals(8, intent.getFlexibleIntExtra("key", 1))
    }

    @Test
    fun testGetFlexibleIntExtraWithStringExtra() {
        val intent = Intent().apply { putExtra("key", "12") }
        assertEquals(12, intent.getFlexibleIntExtra("key", 1))
    }

    @Test
    fun testGetFlexibleIntExtraWithMissingExtraReturnsDefault() {
        val intent = Intent()
        assertEquals(1, intent.getFlexibleIntExtra("missing", 1))
    }

    @Test
    fun testGetFlexibleFloatExtraWithFloatExtra() {
        val intent = Intent().apply { putExtra("key", 2.5f) }
        assertEquals(2.5f, intent.getFlexibleFloatExtra("key", 1.0f))
    }

    @Test
    fun testGetFlexibleFloatExtraWithIntExtra() {
        val intent = Intent().apply { putExtra("key", 4) }
        assertEquals(4.0f, intent.getFlexibleFloatExtra("key", 1.0f))
    }

    @Test
    fun testGetFlexibleFloatExtraWithStringExtra() {
        val intent = Intent().apply { putExtra("key", "3.14") }
        assertEquals(3.14f, intent.getFlexibleFloatExtra("key", 1.0f))
    }

    @Test
    fun testGetFlexibleFloatExtraWithMissingExtraReturnsDefault() {
        val intent = Intent()
        assertEquals(1.0f, intent.getFlexibleFloatExtra("missing", 1.0f))
    }

    @Test
    fun testIsQsDataFixSupportedReturnsTrueForTargetBuilds() {
        assertTrue(isQsDataFixSupported("CP2A.260605.001", "ID1"))
        assertTrue(isQsDataFixSupported("CP2A.260705.002", "ID2"))
        assertTrue(isQsDataFixSupported("CP2A.260805.003", "ID3"))
        assertTrue(isQsDataFixSupported("DISPLAY", "BUILD_CP2A.260605_ID"))
    }

    @Test
    fun testIsQsDataFixSupportedReturnsFalseForOtherBuilds() {
        assertFalse(isQsDataFixSupported("CP3A.260905.001", "ID4"))
        assertFalse(isQsDataFixSupported("BP1A.260605.001", "ID5"))
        assertFalse(isQsDataFixSupported("260605", "260605"))
        assertFalse(isQsDataFixSupported("AP1A.240305.019.A1", "AP1A"))
        assertFalse(isQsDataFixSupported("BUILD_DISPLAY", "BUILD_ID"))
    }
}
