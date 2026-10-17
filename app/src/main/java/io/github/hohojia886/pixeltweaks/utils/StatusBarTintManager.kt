@file:Suppress("DiscouragedPrivateApi", "PrivateApi")

package io.github.hohojia886.pixeltweaks.utils

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import io.github.libxposed.api.XposedModule
import java.util.Collections

/**
 * Centralized helper for status bar theme tint interception and dispatching.
 *
 * Hooks SystemUI Clock View and DarkIconDispatcher to detect dark/light status bar theme transitions.
 * Notifies registered observers (e.g. BatteryPercent, NetworkTraffic) in real-time when tint color changes.
 */
object StatusBarTintManager {

    private const val TAG = "Interface"
    @Volatile private var _currentTint = Color.WHITE
    val currentTint: Int get() = _currentTint
    @Volatile private var isHooked = false

    private val listeners = Collections.synchronizedList(mutableListOf<(tint: Int) -> Unit>())
    private val uiHandler = Handler(Looper.getMainLooper())

    /**
     * Registers an observer lambda to receive status bar theme tint updates.
     * Hooks SystemUI Clock and DarkIconDispatcher on first registration call.
     *
     * @param module Active LSPosed [XposedModule] instance.
     * @param classLoader Target process ClassLoader.
     * @param onTintChanged Callback lambda invoked with the new integer ARGB color when tint updates.
     */
    @Synchronized
    fun register(module: XposedModule, classLoader: ClassLoader, onTintChanged: (tint: Int) -> Unit) {
        if (!listeners.contains(onTintChanged)) {
            listeners.add(onTintChanged)
        }
        
        uiHandler.post { onTintChanged(currentTint) }

        if (isHooked) return

        runCatching {
            val clockClass = classLoader.loadClass("com.android.systemui.statusbar.policy.Clock")
            
            clockClass.declaredMethods.filter { it.name == "setTextColor" && it.parameterTypes.size == 1 && it.parameterTypes[0] == Int::class.javaPrimitiveType }.forEach { method ->
                module.hookBefore(method) { chain ->
                    val color = chain.args.getOrNull(0) as? Int
                    if (color != null && color != 0) {
                        notifyTintChanged(color)
                    }
                }
            }

            val onAttachedMethod = clockClass.declaredMethods.find { it.name == "onAttachedToWindow" }
            if (onAttachedMethod != null) {
                module.hookBefore(onAttachedMethod) { chain ->
                    val clock = chain.thisObject as? TextView
                    if (clock != null && clock.currentTextColor != 0) {
                        notifyTintChanged(clock.currentTextColor)
                    }
                }
            }

            clockClass.declaredMethods.filter { it.name == "onDarkChanged" }.forEach { method ->
                module.hookBefore(method) { chain ->
                    val lastArg = chain.args.lastOrNull() as? Int
                    if (lastArg != null && lastArg != 0) {
                        notifyTintChanged(lastArg)
                    }
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "Error", "Failed to hook Clock for status bar tint", e)
        }

        val dispatcherClasses = listOf(
            "com.android.systemui.plugins.DarkIconDispatcher",
            "com.android.systemui.statusbar.phone.DarkIconDispatcherImpl"
        )

        for (clsName in dispatcherClasses) {
            runCatching {
                val dispatcherClass = classLoader.loadClass(clsName)
                dispatcherClass.declaredMethods.filter { it.name == "applyDark" }.forEach { method ->
                    module.hookBefore(method) { chain ->
                        if (chain.args.size >= 3) {
                            val tint = chain.args[2] as? Int
                            if (tint != null && tint != 0) {
                                notifyTintChanged(tint)
                            }
                        }
                    }
                }
                Logger.i(TAG, "Success", "StatusBarTintManager hooked $clsName successfully")
            }
        }

        isHooked = true
    }

    /**
     * Notifies all registered listeners of a status bar tint change.
     * Posts updates to UI thread to ensure safe view color changes.
     */
    private fun notifyTintChanged(tint: Int) {
        _currentTint = tint
        uiHandler.post {
            synchronized(listeners) {
                val iterator = listeners.iterator()
                while (iterator.hasNext()) {
                    val callback = iterator.next()
                    runCatching { callback(_currentTint) }
                }
            }
        }
    }
}
