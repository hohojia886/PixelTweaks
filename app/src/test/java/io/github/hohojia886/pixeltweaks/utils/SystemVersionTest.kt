package io.github.hohojia886.pixeltweaks.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class SystemVersionTest {

    @Test
    fun testSystemVersionPropertiesNotNull() {
        assertNotNull(SystemVersion.display)
        assertNotNull(SystemVersion.id)
    }

    @Test
    fun testBuildChecksOnDefaultRobolectricOrUnitTest() {
        // In unit test environment, Build.DISPLAY and Build.ID are empty or default
        val isCp2 = SystemVersion.isCp2a()
        val isCp3 = SystemVersion.isCp3a()
        val isCd1 = SystemVersion.isCd1a()
        val isCd1Old = SystemVersion.isCd1aBefore260905()

        // When Build.DISPLAY is empty, all version checks return false
        if (SystemVersion.display.isEmpty() && SystemVersion.id.isEmpty()) {
            assertFalse(isCp2)
            assertFalse(isCp3)
            assertFalse(isCd1)
            assertFalse(isCd1Old)
        }
    }
}
