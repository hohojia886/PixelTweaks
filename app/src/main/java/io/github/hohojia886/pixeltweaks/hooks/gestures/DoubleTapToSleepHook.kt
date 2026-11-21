/*
 * This file is a derivative work based on siavash79/PixelXpert (GPL-3.0),
 * originally written in Java, translated and adapted to Kotlin.
 *
 * Copyright (C) siavash79 & ElTifo (original authors, PixelXpert)
 * Copyright (C) 2026 PixelTweaks (modifications)
 *
 * Modified on 2026-08-25: Added double tap to sleep gestures.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */

@file:Suppress("DiscouragedPrivateApi", "PrivateApi", "DiscouragedApi")

package io.github.hohojia886.pixeltweaks.hooks.gestures

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import io.github.hohojia886.pixeltweaks.utils.IpcDispatcher
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.Logger
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import io.github.hohojia886.pixeltweaks.utils.hookBefore
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Handles double-tap gestures on status bar, keyguard, and launcher empty space to put device to sleep.
 * Uses touch delta distance math, MotionEvent slop thresholds, and Wake Guard timing to prevent false triggers.
 */
object DoubleTapToSleepHook {

    private const val TAG = "DT2S"
    @Volatile private var isDtLauncherEnabled = true
    @Volatile private var isDtLockscreenEnabled = true
    @Volatile private var isDtStatusbarEnabled = true

    @Volatile private var isBouncerShowing = false
    @Volatile private var lastUnlockInteractionTime = 0L
    @Volatile private var lastWakeTime = 0L

    private var lastDownTime = 0L
    private var lastDownX = 0f
    private var lastDownY = 0f

    /**
     * Initializes DT2S hooks for the given process applying process-specific touch interceptors.
     *
     * @param module Active LSPosed [XposedModule] instance.
     * @param classLoader Target process ClassLoader.
     */
    fun hook(module: XposedModule, classLoader: ClassLoader) {
        val proc = getProcessName()
        Logger.i("Hook", "Started", "Initializing DoubleTapToSleepHook (Raw Math + WakeGuard) in process: $proc")

        runCatching {
            val bundle = IpcManager.loadPreferences(module, classLoader, proc)
            isDtLauncherEnabled = bundle.getBoolean(PreferenceKeys.ENABLE_DT_LAUNCHER, true)
            isDtLockscreenEnabled = bundle.getBoolean(PreferenceKeys.ENABLE_DT_LOCKSCREEN, true)
            isDtStatusbarEnabled = bundle.getBoolean(PreferenceKeys.ENABLE_DT_STATUSBAR, true)
        }.onFailure { e ->
            Logger.e(TAG, "Error", "Failed to load initial settings via RemotePrefProvider", e)
        }

        IpcDispatcher.addListener { intent -> handleSync(intent) }
        IpcDispatcher.initializeOnce(module, classLoader) { app ->
            val moduleUid = module.getModuleApplicationInfo().uid
            if (proc == "com.android.systemui") {
                IpcManager.registerSleepReceiver(app, moduleUid) {
                    if (isDtLauncherEnabled) {
                        Logger.i(TAG, "Running", "Executing sleep request from Launcher")
                        triggerSleep(app)
                    }
                }

                runCatching {
                    val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
                    app.registerReceiver(object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            lastWakeTime = SystemClock.uptimeMillis()
                        }
                    }, filter)
                }.onFailure { e ->
                    Logger.e(TAG, "Error", "Failed to register ACTION_SCREEN_ON receiver", e)
                }
            }
        }

        if (proc == "com.android.systemui") {
            applySystemUIHooks(module, classLoader)
        } else if (proc.contains("launcher") || proc.contains("nexuslauncher")) {
            applyLauncherHooks(module, classLoader)
        }
    }

    /**
     * Applies DT2S hooks to SystemUI status bar and lockscreen components.
     * Monitors user interaction, bouncer visibility, and gesture events.
     */
    private fun applySystemUIHooks(module: XposedModule, classLoader: ClassLoader) {
        val keyguardInputCandidates = listOf(
            "com.android.keyguard.KeyguardAbsKeyInputViewController",
            "com.android.keyguard.KeyguardInputViewController"
        )
        var keyguardInputHooked = false
        for (className in keyguardInputCandidates) {
            runCatching {
                val ctrlClass = classLoader.loadClass(className)
                ctrlClass.declaredMethods.find {
                    it.name == "onUserInput" && !Modifier.isAbstract(it.modifiers)
                }?.let { m ->
                    module.hookBefore(m) { _ ->
                        lastUnlockInteractionTime = SystemClock.uptimeMillis()
                    }
                    keyguardInputHooked = true
                }
            }.onFailure { e ->
                if (e is ClassNotFoundException) {
                    Logger.d(TAG, "Probe", "Legacy keyguard input controller not present: $className")
                } else {
                    Logger.e(TAG, "Error", "Failed to hook $className for interaction monitoring", e)
                }
            }
            if (keyguardInputHooked) break
        }

        runCatching {
            val managerClass = classLoader.loadClass("com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager")
            managerClass.declaredMethods.find {
                it.name == "showPrimaryBouncer" && !Modifier.isAbstract(it.modifiers)
            }?.let { m ->
                module.hookBefore(m) { _ ->
                    isBouncerShowing = true
                }
            }
            managerClass.declaredMethods.find {
                (it.name == "reset" || it.name == "hideBouncer") && !Modifier.isAbstract(it.modifiers)
            }?.let { m ->
                module.hookBefore(m) { _ ->
                    isBouncerShowing = false
                }
            }
        }.onFailure { e ->
            if (e is ClassNotFoundException) {
                Logger.d(TAG, "Probe", "StatusBarKeyguardViewManager not present on this build")
            } else {
                Logger.e(TAG, "Error", "Failed to hook StatusBarKeyguardViewManager for Bouncer visibility", e)
            }
        }

        runCatching {
            val sbClass = classLoader.loadClass("com.android.systemui.statusbar.phone.PhoneStatusBarView")
            val touchMethod = resolveStatusBarTouchMethod(sbClass)
            if (touchMethod != null) {
                module.hook(touchMethod).intercept { chain ->
                    if (isDtStatusbarEnabled) {
                        val event = chain.args.firstOrNull { it is MotionEvent } as? MotionEvent
                        val view = chain.thisObject as? View
                        if (event != null && view != null && processTouch(event, view, false)) {
                            return@intercept true
                        }
                    }
                    chain.proceed()
                }
                Logger.i(TAG, "Applied", "Hooked PhoneStatusBarView#${touchMethod.name} for Status Bar DT2S")
            } else {
                Logger.w(TAG, "Error", "No suitable touch method found on PhoneStatusBarView for Status Bar DT2S")
            }
        }.onFailure { e ->
            Logger.e(TAG, "Error", "Failed to hook PhoneStatusBarView for double tap", e)
        }

        runCatching {
            val pulsingClass = classLoader.loadClass("com.android.systemui.shade.PulsingGestureListener")
            val stateControllerClass = classLoader.loadClass("com.android.systemui.plugins.statusbar.StatusBarStateController")
            
            val controllerField = pulsingClass.getDeclaredField("statusBarStateController").apply { isAccessible = true }
            val isDozingMethod = stateControllerClass.getMethod("isDozing")
            val sysContext = IpcManager.getSystemContext(classLoader)
            val powerManager = sysContext?.getSystemService(Context.POWER_SERVICE) as? PowerManager
            
            pulsingClass.declaredMethods.find { it.name == "onDoubleTapEvent" && it.parameterTypes.isEmpty() }?.let { m ->
                module.hook(m).intercept { chain ->
                    if (isDtLockscreenEnabled) {
                        val listener = chain.thisObject
                        val controller = controllerField.get(listener)
                        val isDozing = isDozingMethod.invoke(controller) as Boolean
                        
                        val isInteractive = powerManager?.isInteractive ?: false
                        val isUserTyping = (SystemClock.uptimeMillis() - lastUnlockInteractionTime) < 2000
                        val isRecentlyWoken = (SystemClock.uptimeMillis() - lastWakeTime) < 500

                        if (isInteractive && !isDozing && !isBouncerShowing && !isUserTyping && !isRecentlyWoken) {
                            Logger.i(TAG, "Success", "DT2S triggered on Lockscreen")
                            triggerSleep(sysContext)
                            return@intercept true
                        }
                    }
                    chain.proceed()
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "Error", "Failed to hook PulsingGestureListener for lockscreen double tap", e)
        }
    }

    /**
     * Applies DT2S hooks to Launcher workspace for home screen sleep.
     * Hooks Workspace touch events to trigger sleep on double tap.
     */
    private fun applyLauncherHooks(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val wsClass = classLoader.loadClass("com.android.launcher3.Workspace")
            wsClass.declaredMethods.find { it.name == "onInterceptTouchEvent" }?.let { m ->
                module.hook(m).intercept { chain ->
                    if (isDtLauncherEnabled) {
                        if (processTouch(chain.args[0] as MotionEvent, chain.thisObject as View, true)) {
                            return@intercept true
                        }
                    }
                    chain.proceed()
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "Error", "Failed to hook Launcher Workspace for double tap", e)
        }
    }

    /**
     * Resolves the preferred concrete touch callback declared on `PhoneStatusBarView`.
     * Prioritizes `onTouchEvent` (Android 17+ / modern AOSP) before falling back to
     * `dispatchTouchEvent` or `onInterceptTouchEvent`.
     */
    internal fun resolveStatusBarTouchMethod(clazz: Class<*>): Method? {
        val preferredNames = listOf("onTouchEvent", "dispatchTouchEvent", "onInterceptTouchEvent")
        for (name in preferredNames) {
            val match = clazz.declaredMethods.find { m ->
                m.name == name &&
                    !Modifier.isAbstract(m.modifiers) &&
                    m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == MotionEvent::class.java
            }
            if (match != null) return match
        }
        return null
    }

    /**
     * Processes motion events to detect double tap gestures on views.
     * Evaluates touch delta math and double-tap slop distance against thresholds.
     */
    private fun processTouch(ev: MotionEvent, view: View, isLauncher: Boolean): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            val now = SystemClock.uptimeMillis()

            if ((now - lastWakeTime) < 500) {
                return false
            }

            val x = ev.rawX
            val y = ev.rawY

            val dx = x - lastDownX
            val dy = y - lastDownY
            val distance = sqrt((dx * dx + dy * dy).toDouble())

            val viewConfig = ViewConfiguration.get(view.context)
            val maxDoubleTapDistance = max(
                viewConfig.scaledDoubleTapSlop.toFloat(),
                viewConfig.scaledTouchSlop * 3f
            )
            if (now - lastDownTime < 400 && distance < maxDoubleTapDistance) {
                lastDownTime = 0
                if (isLauncher) {
                    Logger.i(TAG, "Running", "Sending sleep request from Launcher")
                    IpcManager.sendSleepRequest(view.context)
                } else {
                    Logger.i(TAG, "Success", "DT2S triggered on Status Bar")
                    triggerSleep(view.context)
                }
                return true
            }

            lastDownTime = now
            lastDownX = x
            lastDownY = y
        }
        return false
    }

    /**
     * Synchronizes setting states from IPC broadcast intents dynamically.
     * Updates launcher, lockscreen, and statusbar toggle variables.
     */
    private fun handleSync(intent: Intent) {
        if (intent.action == IpcManager.ACTION_SETTINGS_SYNC) {
            val newLauncher = intent.getBooleanExtra(PreferenceKeys.ENABLE_DT_LAUNCHER, true)
            val newLockscreen = intent.getBooleanExtra(PreferenceKeys.ENABLE_DT_LOCKSCREEN, true)
            val newStatusbar = intent.getBooleanExtra(PreferenceKeys.ENABLE_DT_STATUSBAR, true)
            if (newLauncher == isDtLauncherEnabled &&
                newLockscreen == isDtLockscreenEnabled &&
                newStatusbar == isDtStatusbarEnabled
            ) {
                return
            }
            isDtLauncherEnabled = newLauncher
            isDtLockscreenEnabled = newLockscreen
            isDtStatusbarEnabled = newStatusbar
            Logger.i(TAG, "Success", "Full sync: L=$isDtLauncherEnabled, LS=$isDtLockscreenEnabled, SB=$isDtStatusbarEnabled")
        } else {
            val key = intent.getStringExtra(PreferenceKeys.EXTRA_KEY)
            val value = intent.getBooleanExtra(PreferenceKeys.EXTRA_VALUE, true)
            when (key) {
                PreferenceKeys.ENABLE_DT_LAUNCHER -> {
                    if (isDtLauncherEnabled == value) return
                    isDtLauncherEnabled = value
                    Logger.i(TAG, "Success", "Launcher DT2S updated to $value")
                }
                PreferenceKeys.ENABLE_DT_LOCKSCREEN -> {
                    if (isDtLockscreenEnabled == value) return
                    isDtLockscreenEnabled = value
                    Logger.i(TAG, "Success", "Lockscreen DT2S updated to $value")
                }
                PreferenceKeys.ENABLE_DT_STATUSBAR -> {
                    if (isDtStatusbarEnabled == value) return
                    isDtStatusbarEnabled = value
                    Logger.i(TAG, "Success", "Statusbar DT2S updated to $value")
                }
            }
        }
    }

    /**
     * Triggers actual screen off command using PowerManager goToSleep.
     * Invokes goToSleep via reflection if screen is currently interactive.
     */
    private fun triggerSleep(context: Context) {
        runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isInteractive) return
            val goToSleep = pm.javaClass.getMethod("goToSleep", Long::class.javaPrimitiveType)
            goToSleep.invoke(pm, SystemClock.uptimeMillis())
        }.onFailure { e ->
            Logger.e(TAG, "Error", "Failed to invoke PowerManager.goToSleep", e)
        }
    }

    /**
     * Helper to safely retrieve current process name via ActivityThread.
     * Reflectively calls currentProcessName returning unknown on error.
     */
    private fun getProcessName(): String {
        return runCatching {
            val activityThread = Class.forName("android.app.ActivityThread")
            val getProcessName = activityThread.getDeclaredMethod("currentProcessName")
            getProcessName.invoke(null) as String
        }.getOrDefault("unknown")
    }
}
