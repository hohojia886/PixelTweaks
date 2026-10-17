@file:Suppress("DiscouragedPrivateApi", "PrivateApi", "DiscouragedApi")

package io.github.hohojia886.pixeltweaks.utils

import android.content.Context
import android.content.Intent
import io.github.libxposed.api.XposedModule
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Centralized process-level IPC Broadcast Dispatcher and Manager.
 * Ensures that each target process registers ONLY ONE BroadcastReceiver to prevent duplicate listeners.
 */
object IpcDispatcher {

    private val isInitialized = AtomicBoolean(false)
    private val listeners = Collections.synchronizedList(mutableListOf<(Intent) -> Unit>())
    private val contextListeners = Collections.synchronizedList(mutableListOf<(Context) -> Unit>())
    @Volatile private var cachedAppContext: Context? = null

    /**
     * Subscribes a listener lambda to receive process-level IPC broadcast updates.
     *
     * @param listener Callback lambda invoked when an IPC broadcast arrives in this process.
     */
    fun addListener(listener: (Intent) -> Unit) {
        listeners.add(listener)
    }

    /**
     * Initializes process-level hook or immediate receiver registration.
     * Guarantees single-receiver registration per process lifecycle using atomic flags.
     *
     * @param module Active LSPosed [XposedModule] instance.
     * @param classLoader Target process ClassLoader.
     * @param extraActions Optional additional broadcast action strings to register.
     * @param onContextReady Optional callback invoked as soon as Application Context is acquired.
     */
    fun initializeOnce(
        module: XposedModule,
        classLoader: ClassLoader,
        extraActions: List<String> = emptyList(),
        onContextReady: ((Context) -> Unit)? = null
    ) {
        if (onContextReady != null) {
            val app = cachedAppContext
            if (app != null) {
                runCatching { onContextReady(app) }
            } else {
                contextListeners.add(onContextReady)
            }
        }

        if (!isInitialized.compareAndSet(false, true)) {
            return
        }

        fun setupReceiver(context: Context) {
            runCatching {
                cachedAppContext = context

                synchronized(contextListeners) {
                    val iterator = contextListeners.iterator()
                    while (iterator.hasNext()) {
                        runCatching { iterator.next().invoke(context) }
                    }
                }

                IpcManager.registerSecureReceiver(
                    context = context,
                    moduleUid = module.getModuleApplicationInfo().uid,
                    extraActions = extraActions
                ) { intent ->
                    synchronized(listeners) {
                        val iterator = listeners.iterator()
                        while (iterator.hasNext()) {
                            runCatching { iterator.next().invoke(intent) }
                        }
                    }
                }
            }
        }

        val isSystemServer = runCatching { android.os.Process.myUid() }.getOrDefault(-1) == 1000

        val currentApp = runCatching {
            val atClass = classLoader.loadClass("android.app.ActivityThread")
            val at = atClass.getDeclaredMethod("currentActivityThread").invoke(null) ?: return@runCatching null
            val app = atClass.getDeclaredMethod("currentApplication").invoke(at) as? Context
            if (app != null) return@runCatching app

            if (isSystemServer) {
                (atClass.getDeclaredMethod("getSystemContext").invoke(at) as? Context)
            } else {
                null
            }
        }.getOrNull()

        if (currentApp != null) {
            Logger.i("Ipc", "Dispatcher", "Active Context found (${currentApp.packageName}). Registering IPC receiver immediately.")
            setupReceiver(currentApp)
        } else {
            runCatching {
                val appClass = classLoader.loadClass("android.app.Application")
                module.hookBefore(appClass.getDeclaredMethod("onCreate")) { chain ->
                    val app = chain.thisObject as? Context ?: return@hookBefore
                    setupReceiver(app)
                }
            }.onFailure { e ->
                Logger.e("Ipc", "Dispatcher", "Failed to initialize Application.onCreate hook", e)
            }
        }
    }
}
