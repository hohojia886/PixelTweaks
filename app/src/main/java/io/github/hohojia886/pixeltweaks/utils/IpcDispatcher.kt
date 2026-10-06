package io.github.hohojia886.pixeltweaks.utils

import android.content.Context
import android.content.Intent
import io.github.libxposed.api.XposedModule
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/**
 * IpcDispatcher: Centralized process-level IPC Broadcast Dispatcher and Application Lifecycle Manager.
 * Ensures that each target process (SystemUI, Launcher, SystemServer) registers ONLY ONE BroadcastReceiver.
 * Supports both cold boot (Application.onCreate hook) and LSPosed autoHotReload/late hook load (ActivityThread.currentApplication).
 * 
 * Individual hooks subscribe to IpcDispatcher via addListener { intent -> ... }.
 */
object IpcDispatcher {

    private val isInitialized = AtomicBoolean(false)
    private val listeners = Collections.synchronizedList(mutableListOf<(Intent) -> Unit>())
    private val contextListeners = Collections.synchronizedList(mutableListOf<(Context) -> Unit>())
    @Volatile private var cachedAppContext: Context? = null

    /**
     * Subscribes a listener to receive process-level IPC broadcast updates.
     */
    fun addListener(listener: (Intent) -> Unit) {
        listeners.add(listener)
    }

    /**
     * Initializes process-level Application.onCreate hook or immediate receiver registration
     * if Application is already active (supports autoHotReload / late hook loading).
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
                    // Dispatch to all subscribed hooks in this process
                    synchronized(listeners) {
                        val iterator = listeners.iterator()
                        while (iterator.hasNext()) {
                            runCatching { iterator.next().invoke(intent) }
                        }
                    }
                }
            }
        }

        val currentApp = runCatching {
            val atClass = classLoader.loadClass("android.app.ActivityThread")
            atClass.getDeclaredMethod("currentApplication").invoke(null) as? Context
        }.getOrNull()

        if (currentApp != null) {
            Logger.i("Ipc", "Dispatcher", "Application already active (Hot Reload/Late Load). Registering IPC receiver immediately.")
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
