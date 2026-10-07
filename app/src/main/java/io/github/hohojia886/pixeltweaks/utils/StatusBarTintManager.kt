@file:Suppress("DiscouragedPrivateApi", "PrivateApi")

package io.github.hohojia886.pixeltweaks.utils

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import io.github.libxposed.api.XposedModule
import java.util.Collections

/**
 * StatusBarTintManager: Centralized helper that intercepts status bar theme tint changes
 * (Clock.setTextColor, Clock.onDarkChanged, DarkIconDispatcher) and dispatches real-time ARGB tint
 * updates to active status bar hooks (NetworkTrafficHook, BatteryPercentHook, etc.).
 */
object StatusBarTintManager {

    private const val TAG = "Interface"
    @Volatile private var _currentTint = Color.WHITE
    val currentTint: Int get() = _currentTint
    @Volatile private var isHooked = false

    private val listeners = Collections.synchronizedList(mutableListOf<(tint: Int) -> Unit>())
    private val uiHandler = Handler(Looper.getMainLooper())

    @Synchronized
    fun register(module: XposedModule, classLoader: ClassLoader, onTintChanged: (tint: Int) -> Unit) {
        if (!listeners.contains(onTintChanged)) {
            listeners.add(onTintChanged)
        }
        
        // Immediately dispatch current known tint to new listener on UI thread
        uiHandler.post { onTintChanged(currentTint) }

        if (isHooked) return

        // 1. Clock Color & Lifecycle Hooks (com.android.systemui.statusbar.policy.Clock)
        runCatching {
            val clockClass = classLoader.loadClass("com.android.systemui.statusbar.policy.Clock")
            
            // A. Hook setTextColor(int)
            clockClass.declaredMethods.filter { it.name == "setTextColor" && it.parameterTypes.size == 1 && it.parameterTypes[0] == Int::class.javaPrimitiveType }.forEach { method ->
                module.hookBefore(method) { chain ->
                    val color = chain.args.getOrNull(0) as? Int
                    if (color != null && color != 0) {
                        notifyTintChanged(color)
                    }
                }
            }

            // B. Hook onAttachedToWindow()
            val onAttachedMethod = clockClass.declaredMethods.find { it.name == "onAttachedToWindow" }
            if (onAttachedMethod != null) {
                module.hookBefore(onAttachedMethod) { chain ->
                    val clock = chain.thisObject as? TextView
                    if (clock != null && clock.currentTextColor != 0) {
                        notifyTintChanged(clock.currentTextColor)
                    }
                }
            }

            // C. Hook onDarkChanged
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

        // 2. DarkIconDispatcher / DarkIconDispatcherImpl Hooks
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
