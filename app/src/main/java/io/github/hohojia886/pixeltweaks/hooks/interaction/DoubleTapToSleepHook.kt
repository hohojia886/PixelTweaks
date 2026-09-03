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

package io.github.hohojia886.pixeltweaks.hooks.interaction

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.Logger
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import io.github.hohojia886.pixeltweaks.utils.hookBefore
import io.github.libxposed.api.XposedModule
import kotlin.math.sqrt

/**
 * Double Tap to Sleep Hook (Raw Math Edition + Wake Guard).
 * Uses lightweight coordinate/timestamp delta calculation and includes anti-wake-up protection.
 * Unified Tag: DT2S
 */
object DoubleTapToSleepHook {

    private const val TAG = "DT2S"
    @Volatile private var isDtLauncherEnabled = true
    @Volatile private var isDtLockscreenEnabled = true
    @Volatile private var isDtStatusbarEnabled = true

    @Volatile private var isBouncerShowing = false
    @Volatile private var lastUnlockInteractionTime = 0L
    @Volatile private var lastWakeTime = 0L // Wake Guard: Prevent accidental sleep right after wake up

    // State for micro-gesture detection
    private var lastDownTime = 0L
    private var lastDownX = 0f
    private var lastDownY = 0f

    fun hook(module: XposedModule, classLoader: ClassLoader) {
        val proc = getProcessName()
        Logger.i("Hook", "Started", "Initializing DoubleTapToSleepHook (Raw Math + WakeGuard) in process: $proc")

        // 1. Initial State Load
        runCatching {
            val prefs = module.getRemotePreferences(IpcManager.PREF_NAME)
            isDtLauncherEnabled = prefs.getBoolean(PreferenceKeys.ENABLE_DT_LAUNCHER, true)
            isDtLockscreenEnabled = prefs.getBoolean(PreferenceKeys.ENABLE_DT_LOCKSCREEN, true)
            isDtStatusbarEnabled = prefs.getBoolean(PreferenceKeys.ENABLE_DT_STATUSBAR, true)
        }.onFailure { e ->
            Logger.e(TAG, "Error", "Failed to load initial settings via RemotePrefProvider", e)
        }

        // 2. Register Receivers
        runCatching {
            val appClass = classLoader.loadClass("android.app.Application")
            module.hookBefore(appClass.getDeclaredMethod("onCreate")) { chain ->
                val app = chain.thisObject as? Context
                if (app != null) {
                    val moduleUid = module.getModuleApplicationInfo().uid
                    if (proc == "com.android.systemui") {
                        IpcManager.registerSleepReceiver(app, moduleUid) {
                            if (isDtLauncherEnabled) {
                                Logger.i(TAG, "Running", "Executing sleep request from Launcher")
                                triggerSleep(app)
                            }
                        }
                        
                        // Register ACTION_SCREEN_ON receiver for reliable Wake Guard
                        runCatching {
                            val filter = android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_ON)
                            app.registerReceiver(object : android.content.BroadcastReceiver() {
                                override fun onReceive(context: Context, intent: android.content.Intent) {
                                    lastWakeTime = SystemClock.uptimeMillis()
                                }
                            }, filter)
                        }
                    }
                    IpcManager.registerSecureReceiver(app, moduleUid) { intent -> handleSync(intent) }
                }
            }
        }

        // 3. APPLY FUNCTIONAL HOOKS
        if (proc == "com.android.systemui") {
            applySystemUIHooks(module, classLoader)
        } else if (proc.contains("launcher") || proc.contains("nexuslauncher")) {
            applyLauncherHooks(module, classLoader)
        }
    }

    private fun applySystemUIHooks(module: XposedModule, classLoader: ClassLoader) {
        // 1. Protection: Monitor Interaction
        runCatching {
            val ctrlClass = classLoader.loadClass("com.android.keyguard.KeyguardAbsKeyInputViewController")
            ctrlClass.declaredMethods.find { it.name == "onUserInput" }?.let { m ->
                module.hookBefore(m) { _ ->
                    lastUnlockInteractionTime = SystemClock.uptimeMillis()
                }
            }
        }

        // 3. Monitor Bouncer visibility
        runCatching {
            val managerClass = classLoader.loadClass("com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager")
            managerClass.getDeclaredMethods().find { it.name == "showPrimaryBouncer" }?.let { m ->
                module.hookBefore(m) { _ ->
                    isBouncerShowing = true
                }
            }
            managerClass.getDeclaredMethods().find { it.name == "reset" || it.name == "hideBouncer" }?.let { m ->
                module.hookBefore(m) { _ ->
                    isBouncerShowing = false
                }
            }
        }

        // 4. Status Bar Hook (Using onInterceptTouchEvent for priority)
        runCatching {
            val sbClass = classLoader.loadClass("com.android.systemui.statusbar.phone.PhoneStatusBarView")
            sbClass.declaredMethods.find { it.name == "onInterceptTouchEvent" }?.let { m ->
                module.hook(m).intercept { chain ->
                    if (isDtStatusbarEnabled) {
                        if (processTouch(chain.args[0] as MotionEvent, chain.thisObject as View, false)) {
                            return@intercept true
                        }
                    }
                    chain.proceed()
                }
            }
        }

        // 5. Lockscreen Hook
        runCatching {
            val pulsingClass = classLoader.loadClass("com.android.systemui.shade.PulsingGestureListener")
            val stateControllerClass = classLoader.loadClass("com.android.systemui.plugins.statusbar.StatusBarStateController")
            
            pulsingClass.declaredMethods.find { it.name == "onDoubleTapEvent" && it.parameterTypes.isEmpty() }?.let { m ->
                module.hook(m).intercept { chain ->
                    if (isDtLockscreenEnabled) {
                        val listener = chain.thisObject
                        val controllerField = pulsingClass.getDeclaredField("statusBarStateController").apply { isAccessible = true }
                        val controller = controllerField.get(listener)
                        val isDozing = stateControllerClass.getMethod("isDozing").invoke(controller) as Boolean
                        
                        val pm = IpcManager.getSystemContext(classLoader)?.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                        val isInteractive = pm?.isInteractive ?: false
                        val isUserTyping = (SystemClock.uptimeMillis() - lastUnlockInteractionTime) < 2000
                        val isRecentlyWoken = (SystemClock.uptimeMillis() - lastWakeTime) < 500

                        if (isInteractive && !isDozing && !isBouncerShowing && !isUserTyping && !isRecentlyWoken) {
                            Logger.i(TAG, "Success", "DT2S triggered on Lockscreen")
                            triggerSleep(IpcManager.getSystemContext(classLoader)!!)
                            return@intercept true
                        }
                    }
                    chain.proceed()
                }
            }
        }
    }

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
        }
    }

    private fun processTouch(ev: MotionEvent, view: View, isLauncher: Boolean): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val now = SystemClock.uptimeMillis()
            
            // Wake Guard Check: Ignore touches within 500ms of screen wakeup
            if ((now - lastWakeTime) < 500) {
                return false
            }

            val x = ev.x
            val y = ev.y

            val dx = x - lastDownX
            val dy = y - lastDownY
            val distance = sqrt((dx * dx + dy * dy).toDouble())

            val slop = ViewConfiguration.get(view.context).scaledTouchSlop
            if (now - lastDownTime < 300 && distance < slop) {
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

    private fun handleSync(intent: android.content.Intent) {
        if (intent.action == IpcManager.ACTION_SETTINGS_SYNC) {
            isDtLauncherEnabled = intent.getBooleanExtra(PreferenceKeys.ENABLE_DT_LAUNCHER, true)
            isDtLockscreenEnabled = intent.getBooleanExtra(PreferenceKeys.ENABLE_DT_LOCKSCREEN, true)
            isDtStatusbarEnabled = intent.getBooleanExtra(PreferenceKeys.ENABLE_DT_STATUSBAR, true)
            Logger.i(TAG, "Success", "Full sync: L=$isDtLauncherEnabled, LS=$isDtLockscreenEnabled, SB=$isDtStatusbarEnabled")
        } else {
            val key = intent.getStringExtra(PreferenceKeys.EXTRA_KEY)
            val value = intent.getBooleanExtra(PreferenceKeys.EXTRA_VALUE, true)
            when (key) {
                PreferenceKeys.ENABLE_DT_LAUNCHER -> {
                    isDtLauncherEnabled = value
                    Logger.i(TAG, "Success", "Launcher DT2S updated to $value")
                }
                PreferenceKeys.ENABLE_DT_LOCKSCREEN -> {
                    isDtLockscreenEnabled = value
                    Logger.i(TAG, "Success", "Lockscreen DT2S updated to $value")
                }
                PreferenceKeys.ENABLE_DT_STATUSBAR -> {
                    isDtStatusbarEnabled = value
                    Logger.i(TAG, "Success", "Statusbar DT2S updated to $value")
                }
            }
        }
    }

    private fun triggerSleep(context: Context) {
        runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!pm.isInteractive) return
            val goToSleep = pm.javaClass.getMethod("goToSleep", Long::class.javaPrimitiveType)
            goToSleep.invoke(pm, SystemClock.uptimeMillis())
        }
    }

    private fun getProcessName(): String {
        return runCatching {
            val activityThread = Class.forName("android.app.ActivityThread")
            val getProcessName = activityThread.getDeclaredMethod("currentProcessName")
            getProcessName.invoke(null) as String
        }.getOrDefault("unknown")
    }
}
