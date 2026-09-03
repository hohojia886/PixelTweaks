package io.github.hohojia886.pixeltweaks.hooks.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.TrafficStats
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.Logger
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import io.github.hohojia886.pixeltweaks.utils.hookAfter
import io.github.hohojia886.pixeltweaks.utils.hookBefore
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference

/**
 * NetworkTrafficHook: Adds a real-time network speed indicator to the status bar.
 * Injects a custom View into the SystemUI status bar layout and uses a background 
 * polling thread to calculate RX/TX speeds via TrafficStats.
 */
object NetworkTrafficHook {

    private const val KB = 1000
    private const val MB = KB * KB
    private const val TAG = "Traffic"

    // Thread-safe list of active traffic views across different system bar instances
    private val trafficViews = java.util.Collections.synchronizedList(mutableListOf<WeakReference<TrafficView>>())

    private var isEnabled = true // Feature master toggle
    private var updateInterval = 1000L // Polling frequency in milliseconds
    private var autoHideThreshold = 1024L // Minimum speed to show the indicator
    private var fontSizeSp = 8f // Visual scale of the text
    private var receiverRegistered = false
    @Volatile private var currentTint = Color.WHITE // Adaptive color based on status bar theme

    private val uiHandler = Handler(Looper.getMainLooper())
    private var workerHandler: Handler? = null // Background thread handler
    @Volatile private var isPolling = false // Tracking status of the worker thread

    // Background task that calculates speeds and updates UI views
    private val poller = object : Runnable {
        private var lastRxBytes = -1L
        private var lastTxBytes = -1L
        private var lastTime = -1L
        private var lastRxSpeed = -1L
        private var lastTxSpeed = -1L

        fun reset() {
            lastRxBytes = TrafficStats.getTotalRxBytes()
            lastTxBytes = TrafficStats.getTotalTxBytes()
            lastTime = SystemClock.elapsedRealtime()
            lastRxSpeed = -1L
            lastTxSpeed = -1L
        }

        override fun run() {
            if (!isEnabled || !isPolling) return
            
            var firstView: TrafficView? = null
            synchronized(trafficViews) {
                val iterator = trafficViews.iterator()
                while (iterator.hasNext()) {
                    val view = iterator.next().get()
                    if (view != null) {
                        firstView = view
                        break
                    } else {
                        iterator.remove()
                    }
                }
            }
            
            if (firstView == null) {
                stopPolling()
                return
            }

            // Logic to suppress polling and hide views when the device is locked
            val km = firstView!!.context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
            if (km?.isKeyguardLocked == true) {
                uiHandler.post {
                    iterateViews { view -> if (view.visibility != View.GONE) view.visibility = View.GONE }
                }
                workerHandler?.postDelayed(this, updateInterval)
                return
            }

            val now = SystemClock.elapsedRealtime()
            val newRx = TrafficStats.getTotalRxBytes()
            val newTx = TrafficStats.getTotalTxBytes()
            val deltaTimeMs = (now - lastTime).coerceAtLeast(1)
            
            val rxSpeed = ((newRx - lastRxBytes) * 1000L / deltaTimeMs).coerceAtLeast(0L)
            val txSpeed = ((newTx - lastTxBytes) * 1000L / deltaTimeMs).coerceAtLeast(0L)
            
            lastRxBytes = newRx
            lastTxBytes = newTx
            lastTime = now

            if (rxSpeed != lastRxSpeed || txSpeed != lastTxSpeed) {
                lastRxSpeed = rxSpeed
                lastTxSpeed = txSpeed
                
                uiHandler.post {
                    iterateViews { view ->
                        if (autoHideThreshold > 0 && rxSpeed < autoHideThreshold && txSpeed < autoHideThreshold) {
                            if (view.visibility != View.GONE) view.visibility = View.GONE
                        } else if (isEnabled) {
                            if (view.visibility != View.VISIBLE) view.visibility = View.VISIBLE
                            view.setSpeeds(txSpeed, rxSpeed)
                        }
                    }
                }
            }
            workerHandler?.postDelayed(this, updateInterval)
        }
    }

    // Helper: Safely applies an action to all active traffic view instances
    private fun iterateViews(action: (TrafficView) -> Unit) {
        synchronized(trafficViews) {
            val iterator = trafficViews.iterator()
            while (iterator.hasNext()) {
                val view = iterator.next().get()
                if (view == null) {
                    iterator.remove()
                } else {
                    action(view)
                }
            }
        }
    }

    // Starts the specialized background thread for speed monitoring
    @Synchronized
    private fun startPolling() {
        if (isPolling) return
        if (workerHandler == null) {
            val thread = HandlerThread("PXTK_TrafficWorker")
            thread.start()
            workerHandler = Handler(thread.looper)
        }
        isPolling = true
        workerHandler?.post { poller.reset() }
        workerHandler?.post(poller)
    }

    // Stops background processing to conserve system resources
    @Synchronized
    private fun stopPolling() {
        isPolling = false
        workerHandler?.removeCallbacks(poller)
    }

    // Entry point: Decides when and where to inject the traffic view
    fun hook(module: XposedModule, classLoader: ClassLoader) {
        Logger.i(TAG, "Started", "Initializing NetworkTrafficHook (Robust Edition)")
        try {
            val prefs = module.getRemotePreferences(IpcManager.PREF_NAME)
            val moduleUid = module.getModuleApplicationInfo().uid
            isEnabled = prefs.getBoolean(PreferenceKeys.ENABLE_NETWORK_TRAFFIC, true)
            fontSizeSp = prefs.getFloat(PreferenceKeys.NETWORK_TRAFFIC_FONT_SIZE, 8f)
            updateInterval = prefs.getInt(PreferenceKeys.NETWORK_TRAFFIC_INTERVAL, 1) * 1000L
            autoHideThreshold = prefs.getInt(PreferenceKeys.NETWORK_TRAFFIC_THRESHOLD, 1) * 1024L

            // Injection: Targets the 'Clock' view attachment to anchor the indicator
            val clockClass = classLoader.loadClass("com.android.systemui.statusbar.policy.Clock")
            module.hookAfter(clockClass.getDeclaredMethod("onAttachedToWindow")) { chain, _ ->
                runCatching {
                    val clock = chain.thisObject as View
                    if (isStatusBarClock(clock)) {
                        val parent = clock.parent as? ViewGroup
                        if (parent != null) {
                            attachToClock(clock, parent)
                        }
                    }
                }.onFailure { e ->
                    Logger.e(TAG, "Error", "Hitchhiker injection failed", e)
                }
            }

            // Dark Mode Sync: Intercepts color changes to keep text visible against backgrounds
            val dispatcherClasses = listOf(
                "com.android.systemui.plugins.DarkIconDispatcher",
                "com.android.systemui.statusbar.phone.DarkIconDispatcherImpl"
            )
            
            for (clsName in dispatcherClasses) {
                try {
                    val dispatcherClass = classLoader.loadClass(clsName)
                    dispatcherClass.declaredMethods.filter { it.name == "applyDark" }.forEach { m ->
                        module.hookBefore(m) { chain ->
                            if (chain.args.size >= 3) {
                                val tint = chain.args[2] as? Int
                                if (tint != null && tint != 0) {
                                    applyTint(tint)
                                }
                            }
                        }
                    }
                    Logger.i(TAG, "Success", "Hooked color dispatcher: $clsName")
                    break
                } catch (_: Throwable) {}
            }

            runCatching {
                val appClass = classLoader.loadClass("android.app.Application")
                module.hookBefore(appClass.getDeclaredMethod("onCreate")) { chain ->
                    registerReceiver(chain.thisObject as Context, moduleUid)
                }
            }

        } catch (e: Throwable) {
            Logger.e(TAG, "Error", "Hook setup failed", e)
        }
    }

    // Filters out notification clocks and other non-status bar clock instances
    private fun isStatusBarClock(clock: View): Boolean {
        var current = clock.parent
        while (current != null) {
            val name = current.javaClass.name
            if (name.contains("PhoneStatusBarView") || 
                name.contains("CollapsedStatusBarFragment") || 
                name.contains("status_bar_container")) {
                return true
            }
            current = current.parent
        }
        return false
    }

    // Handles the actual view creation and layout parameter adjustments for injection
    private fun attachToClock(clock: View, clockParent: ViewGroup) {
        val context = clock.context
        val res = context.resources
        val pkg = "com.android.systemui"
        
        val targetIds = listOf(
            res.getIdentifier("status_bar_start_side_content", "id", pkg),
            res.getIdentifier("status_bar_start_side_except_heads_up", "id", pkg)
        ).filter { it != 0 }

        var current: View = clock
        var targetContainer: ViewGroup? = null
        
        while (current.parent is ViewGroup) {
            val parent = current.parent as ViewGroup
            val name = parent.javaClass.name
            if (targetIds.contains(parent.id)) {
                targetContainer = parent
                break
            }
            if (name.contains("PhoneStatusBarView")) {
                runCatching {
                    val fieldNames = listOf("mStartSideContent", "mStartSideContainer")
                    for (fName in fieldNames) {
                        try {
                            val field = parent.javaClass.getDeclaredField(fName).apply { isAccessible = true }
                            targetContainer = field.get(parent) as? ViewGroup
                            if (targetContainer != null) break
                        } catch (_: NoSuchFieldException) {}
                    }
                }
                break
            }
            current = parent
        }

        val container = targetContainer ?: clockParent
        val existingView = container.findViewWithTag<View>("pixeltweaks_traffic") as? TrafficView
        if (existingView != null) {
            synchronized(trafficViews) {
                if (trafficViews.none { it.get() == existingView }) {
                    trafficViews.add(WeakReference(existingView))
                }
            }
            return
        }
        
        val trafficView = TrafficView(context).apply {
            tag = "pixeltweaks_traffic"
            visibility = if (isEnabled) View.VISIBLE else View.GONE
            updateStyle(fontSizeSp, currentTint)
        }

        val params = try {
            val layoutClass = when {
                container is LinearLayout -> LinearLayout.LayoutParams::class.java
                container is FrameLayout -> FrameLayout.LayoutParams::class.java
                else -> ViewGroup.MarginLayoutParams::class.java
            }
            val lp = layoutClass.getConstructor(Int::class.java, Int::class.java)
                .newInstance(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            
            (lp as ViewGroup.MarginLayoutParams).apply {
                leftMargin = dp(context, 4)
                rightMargin = dp(context, 4)
            }
            (lp as? LinearLayout.LayoutParams)?.apply { gravity = Gravity.CENTER_VERTICAL; weight = 0f }
            (lp as? FrameLayout.LayoutParams)?.apply { gravity = Gravity.CENTER_VERTICAL }
            lp as ViewGroup.LayoutParams
        } catch (_: Throwable) {
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_VERTICAL; leftMargin = dp(context, 4); rightMargin = dp(context, 4)
            }
        }

        container.addView(trafficView, -1, params)
        synchronized(trafficViews) { trafficViews.add(WeakReference(trafficView)) }
        if (clock is TextView) applyTint(clock.currentTextColor)
        startPolling()
        Logger.i(TAG, "Success", "Precision-injected to ${container.javaClass.simpleName}")
    }

    // Updates the text color of all active traffic views to match the status bar
    private fun applyTint(tint: Int) {
        if (currentTint == tint) return
        currentTint = tint
        uiHandler.post { iterateViews { it.updateColor(currentTint) } }
    }

    // Registers a secure IPC receiver to handle real-time configuration updates
    private fun registerReceiver(context: Context, moduleUid: Int) {
        if (receiverRegistered) return
        IpcManager.registerSecureReceiver(context, moduleUid) { intent ->
            when (intent.action) {
                IpcManager.ACTION_SETTINGS_SYNC -> handleSync(intent)
                IpcManager.ACTION_SETTING_CHANGED -> {
                    val key = intent.getStringExtra(PreferenceKeys.EXTRA_KEY) ?: return@registerSecureReceiver
                    when (key) {
                        PreferenceKeys.ENABLE_NETWORK_TRAFFIC -> {
                            isEnabled = intent.getBooleanExtra(PreferenceKeys.EXTRA_VALUE, true)
                            updateState()
                        }
                        PreferenceKeys.NETWORK_TRAFFIC_INTERVAL -> {
                            updateInterval = intent.getIntExtra(PreferenceKeys.EXTRA_VALUE, 1) * 1000L
                            workerHandler?.post { poller.reset() }
                        }
                        PreferenceKeys.NETWORK_TRAFFIC_THRESHOLD -> {
                            autoHideThreshold = intent.getIntExtra(PreferenceKeys.EXTRA_VALUE, 1) * 1024L
                        }
                        PreferenceKeys.NETWORK_TRAFFIC_FONT_SIZE -> {
                            fontSizeSp = intent.getFloatExtra(PreferenceKeys.EXTRA_VALUE, 8f)
                            uiHandler.post { iterateViews { it.updateFontSize(fontSizeSp) } }
                        }
                    }
                }
            }
        }
        receiverRegistered = true
    }

    // Batch updates all configuration variables during a full sync event
    private fun handleSync(intent: Intent) {
        isEnabled = intent.getBooleanExtra(PreferenceKeys.ENABLE_NETWORK_TRAFFIC, true)
        fontSizeSp = intent.getFloatExtra(PreferenceKeys.NETWORK_TRAFFIC_FONT_SIZE, 8f)
        updateInterval = intent.getIntExtra(PreferenceKeys.NETWORK_TRAFFIC_INTERVAL, 1) * 1000L
        autoHideThreshold = intent.getIntExtra(PreferenceKeys.NETWORK_TRAFFIC_THRESHOLD, 1) * 1024L
        uiHandler.post { iterateViews { it.updateFontSize(fontSizeSp) } }
        updateState()
    }

    // Adjusts polling and view visibility based on the current feature state
    private fun updateState() {
        if (isEnabled) startPolling() else stopPolling()
        uiHandler.post {
            val visibility = if (isEnabled) View.VISIBLE else View.GONE
            iterateViews { it.visibility = visibility }
        }
    }

    private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    /**
     * TrafficView: A specialized custom view for rendering the two-line RX/TX text.
     */
    @SuppressLint("ViewConstructor")
    private class TrafficView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        
        private var txText = "0 B"
        private var rxText = "0 B"
        private var fontSizePx = 0f

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            startPolling()
        }
        
        fun updateStyle(sizeSp: Float, color: Int) {
            fontSizePx = sizeSp * resources.displayMetrics.scaledDensity
            paint.textSize = fontSizePx
            paint.color = color
            requestLayout()
            invalidate()
        }

        fun updateColor(color: Int) {
            paint.color = color
            invalidate()
        }

        fun updateFontSize(sizeSp: Float) {
            fontSizePx = sizeSp * resources.displayMetrics.scaledDensity
            paint.textSize = fontSizePx
            requestLayout()
            invalidate()
        }

        fun setSpeeds(tx: Long, rx: Long) {
            txText = formatSpeed(tx)
            rxText = formatSpeed(rx)
            requestLayout()
            invalidate()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val txWidth = paint.measureText(txText)
            val rxWidth = paint.measureText(rxText)
            val maxWidth = maxOf(txWidth, rxWidth).toInt()
            val fm = paint.fontMetrics
            val fontHeight = fm.descent - fm.ascent
            setMeasuredDimension(maxWidth + 8, (fontHeight * 2.2).toInt())
        }

        override fun onDraw(canvas: Canvas) {
            val centerX = width / 2f
            val fm = paint.fontMetrics
            val singleLineHeight = fm.descent - fm.ascent
            val totalHeight = singleLineHeight * 2
            val startY = (height - totalHeight) / 2f - fm.ascent
            
            canvas.drawText(txText, centerX, startY, paint)
            canvas.drawText(rxText, centerX, startY + singleLineHeight, paint)
        }

        private fun formatSpeed(bytesPerSec: Long): String {
            return when {
                bytesPerSec >= MB -> formatValue(bytesPerSec, MB, "M")
                bytesPerSec >= KB -> formatValue(bytesPerSec, KB, "K")
                else -> "$bytesPerSec B"
            }
        }

        private fun formatValue(bytes: Long, unit: Int, suffix: String): String {
            val integral = bytes / unit
            return if (integral >= 10) "$integral$suffix" else {
                val decimal = (bytes * 10 / unit) % 10
                "$integral.$decimal$suffix"
            }
        }
    }
}
