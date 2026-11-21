package io.github.hohojia886.pixeltweaks.utils

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.currentComposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComposeRecompositionBridgeTest {

    @Test
    fun testBridgeInitializesAndTriggersRecomposition() {
        val bridge = ComposeRecompositionBridge("TestCompose")
        bridge.initialize(javaClass.classLoader!!)

        assertTrue(bridge.isAvailable(), "Bridge should be available after initialization")
        assertTrue(bridge.hasStateBridge(), "SnapshotState bridge should be initialized")
        assertTrue(bridge.hasScopeBridge(), "RecomposeScope bridge should be initialized")

        var enabled = true
        var composeCount = 0
        var lastRenderedStockBattery = true

        val frameClock = BroadcastFrameClock()
        val job = Job()
        val coroutineContext = Dispatchers.Unconfined + frameClock + job
        val recomposer = Recomposer(coroutineContext)
        val recomposerJob = CoroutineScope(coroutineContext).launch {
            recomposer.runRecomposeAndApplyChanges()
        }

        val composition = Composition(noOpApplier(), recomposer)
        composition.setContent {
            composeCount++
            bridge.observe(listOf(currentComposer))
            lastRenderedStockBattery = !enabled
        }

        assertTrue(composeCount == 1, "Expected initial composeCount == 1, got $composeCount")
        assertFalse(lastRenderedStockBattery, "Stock battery should be hidden when enabled=true")

        enabled = false
        bridge.trigger("enabled=false")
        frameClock.sendFrame(16_000_000L)
        assertTrue(composeCount == 2, "Expected composeCount == 2 after toggling OFF, got $composeCount")
        assertTrue(lastRenderedStockBattery, "Stock battery should reappear immediately when enabled=false")

        enabled = true
        bridge.trigger("enabled=true")
        frameClock.sendFrame(32_000_000L)
        assertTrue(composeCount == 3, "Expected composeCount == 3 after toggling ON, got $composeCount")
        assertFalse(lastRenderedStockBattery, "Stock battery should hide immediately when enabled=true")

        composition.dispose()
        recomposer.cancel()
        recomposerJob.cancel()
        job.cancel()
    }

    @Test
    fun testSnapshotStateAloneDrivesRecompositionWhenRecomposeScopeIsStripped() {
        val bridge = ComposeRecompositionBridge("TestCompose")
        bridge.initialize(javaClass.classLoader!!)
        bridge.clearScopeBridgeForTest()

        assertTrue(bridge.hasStateBridge(), "SnapshotState bridge should remain active")
        assertFalse(bridge.hasScopeBridge(), "RecomposeScope bridge should be cleared to simulate R8 stripping")

        var enabled = true
        var parentComposeCount = 0
        var stockBatteryRendered = false

        val frameClock = BroadcastFrameClock()
        val job = Job()
        val coroutineContext = Dispatchers.Unconfined + frameClock + job
        val recomposer = Recomposer(coroutineContext)
        val recomposerJob = CoroutineScope(coroutineContext).launch {
            recomposer.runRecomposeAndApplyChanges()
        }

        val composition = Composition(noOpApplier(), recomposer)
        composition.setContent {
            parentComposeCount++
            bridge.observe(listOf(currentComposer))
            stockBatteryRendered = !enabled
        }

        assertTrue(parentComposeCount == 1, "Expected initial parentComposeCount == 1, got $parentComposeCount")
        assertFalse(stockBatteryRendered, "Stock battery should be hidden initially")

        enabled = false
        bridge.trigger("enabled=false")
        frameClock.sendFrame(16_000_000L)
        assertTrue(parentComposeCount == 2, "Expected parentComposeCount == 2 via SnapshotState alone, got $parentComposeCount")
        assertTrue(stockBatteryRendered, "Stock battery should reappear offline via SnapshotState alone")

        enabled = true
        bridge.trigger("enabled=true")
        frameClock.sendFrame(32_000_000L)
        assertTrue(parentComposeCount == 3, "Expected parentComposeCount == 3 via SnapshotState alone, got $parentComposeCount")
        assertFalse(stockBatteryRendered, "Stock battery should hide offline via SnapshotState alone")

        composition.dispose()
        recomposer.cancel()
        recomposerJob.cancel()
        job.cancel()
    }

    private fun noOpApplier() = object : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) {}
        override fun insertBottomUp(index: Int, instance: Unit) {}
        override fun remove(index: Int, count: Int) {}
        override fun move(from: Int, to: Int, count: Int) {}
        override fun onClear() {}
    }
}
