package io.github.hohojia886.pixeltweaks.utils

import android.content.Intent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

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
}
