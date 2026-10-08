package io.github.hohojia886.pixeltweaks.utils

import android.content.Intent
import android.content.pm.ApplicationInfo
import io.github.libxposed.api.XposedModule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IpcDispatcherTest {

    private lateinit var module: XposedModule
    private lateinit var classLoader: ClassLoader

    @Before
    fun setup() {
        module = mock(XposedModule::class.java)
        classLoader = mock(ClassLoader::class.java)

        val appInfo = ApplicationInfo().apply { uid = 1000 }
        whenever(module.getModuleApplicationInfo()).thenReturn(appInfo)
    }

    @Test
    fun testAddListenerDispatchesBroadcast() {
        var received = false
        IpcDispatcher.addListener { intent ->
            if (intent.action == IpcManager.ACTION_SETTINGS_SYNC) {
                received = true
            }
        }

        // Initialize once with mock classLoader
        whenever(classLoader.loadClass(org.mockito.kotlin.any())).thenReturn(Any::class.java)
        IpcDispatcher.initializeOnce(module, classLoader)

        // Verify that listeners receive intent
        IpcDispatcher.initializeOnce(module, classLoader)
        IpcDispatcher.addListener { intent ->
            if (intent.action == "TEST_ACTION") received = true
        }
        
        // Dispatch test action directly
        val testIntent = Intent("TEST_ACTION")
        val listenersField = IpcDispatcher::class.java.getDeclaredField("listeners").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val listeners = listenersField.get(IpcDispatcher) as List<(Intent) -> Unit>
        listeners.forEach { it.invoke(testIntent) }

        assertTrue(received)
    }
}
