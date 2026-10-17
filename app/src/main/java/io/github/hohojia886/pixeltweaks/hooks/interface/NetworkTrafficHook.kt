@file:Suppress("DiscouragedPrivateApi", "PrivateApi", "DiscouragedApi", "DEPRECATION", "SameParameterValue")

package io.github.hohojia886.pixeltweaks.hooks.`interface`

import android.annotation.SuppressLint
import android.app.KeyguardManager
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
import io.github.hohojia886.pixeltweaks.utils.IpcDispatcher
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.Logger
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import io.github.hohojia886.pixeltweaks.utils.StatusBarTintManager
import io.github.hohojia886.pixeltweaks.utils.getFlexibleFloatExtra
import io.github.hohojia886.pixeltweaks.utils.getFlexibleIntExtra
import io.github.hohojia886.pixeltweaks.utils.hookAfter
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.Collections

/**
 * Adds a real-time upload and download network speed indicator to the SystemUI status bar.
 *
 * Injects a custom 2-line [TrafficView] next to the status bar Clock, powered by a background [TrafficStats] polling thread.
 */
object NetworkTrafficHook {

    private const val KB = 1000
    private const val MB = KB * KB
    private const val TAG = "Traffic"

    private val trafficViews = Collections.synchronizedList(mutableListOf<WeakReference<TrafficView>>())

    private var isEnabled = true
    private var updateInterval = 1000L
    private var autoHideThreshold = 1024L
    private var fontSizeSp = 8f
    @Volatile private var currentTint = Color.WHITE

    private val uiHandler = Handler(Looper.getMainLooper())
    private var workerHandler: Handler? = null
    @Volatile private var isPolling = false

    private val poller = object : Runnable {
        private var lastRxBytes = -1L
        private var lastTxBytes = -1L
        private var lastTime = -1L
        private var lastRxSpeed = -1L
        private var lastTxSpeed = -1L

        /**
         * Resets tracking variables for network speed calculation.
         * Obtains current total RX/TX bytes from TrafficStats API.
         */
        fun reset() {
            lastRxBytes = TrafficStats.getTotalRxBytes()
            lastTxBytes = TrafficStats.getTotalTxBytes()
            lastTime = SystemClock.elapsedRealtime()
            lastRxSpeed = -1L
            lastTxSpeed = -1L
        }

        /**
         * Computes transfer speeds and triggers UI updates on handler.
         * Evaluates delta bytes over time interval and updates views.
         */
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

            val km = firstView.context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
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
            
            val rxSpeed = (((newRx - lastRxBytes) * 1000L) / deltaTimeMs).coerceAtLeast(0L)
            val txSpeed = (((newTx - lastTxBytes) * 1000L) / deltaTimeMs).coerceAtLeast(0L)
            
            lastRxBytes = newRx
            lastTxBytes = newTx
            lastTime = now

            if ((rxSpeed != lastRxSpeed) || (txSpeed != lastTxSpeed)) {
                lastRxSpeed = rxSpeed
                lastTxSpeed = txSpeed
                
                uiHandler.post {
                    iterateViews { view ->
                        if ((autoHideThreshold > 0) && (rxSpeed < autoHideThreshold) && (txSpeed < autoHideThreshold)) {
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

    /**
     * Utility to cleanly iterate active traffic views.
     * Safely processes weak references removing stale entries.
     */
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

    /**
     * Initiates background polling thread for speed monitoring.
     * Spawns worker handler thread and posts poller runnable.
     */
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

    /**
     * Halts background polling thread to conserve system resources.
     * Removes poller callbacks from background worker handler.
     */
    @Synchronized
    private fun stopPolling() {
        isPolling = false
        workerHandler?.removeCallbacks(poller)
    }

    /**
     * Entry point for initializing NetworkTraffic hooks inside SystemUI.
     *
     * @param module Active LSPosed [XposedModule] instance.
     * @param classLoader SystemUI ClassLoader.
     */
    fun hook(module: XposedModule, classLoader: ClassLoader) {
        Logger.i(TAG, "Started", "Initializing NetworkTrafficHook (Robust Edition)")
        try {
            val bundle = IpcManager.loadPreferences(module, classLoader, "com.android.systemui")
            isEnabled = bundle.getBoolean(PreferenceKeys.ENABLE_NETWORK_TRAFFIC, true)
            val rawFont = bundle.getFloat(PreferenceKeys.NETWORK_TRAFFIC_FONT_SIZE, 8f)
            fontSizeSp = if (rawFont > 0f) rawFont.coerceAtLeast(6f) else 8f
            val rawInterval = bundle.getInt(PreferenceKeys.NETWORK_TRAFFIC_INTERVAL, 1)
            updateInterval = (if (rawInterval > 0) rawInterval else 1) * 1000L
            val rawThreshold = bundle.getInt(PreferenceKeys.NETWORK_TRAFFIC_THRESHOLD, 1)
            autoHideThreshold = (if (rawThreshold >= 0) rawThreshold else 1) * 1024L

            val clockClass = classLoader.loadClass("com.android.systemui.statusbar.policy.Clock")
            val onAttachedMethod = findMethod(clockClass, "onAttachedToWindow")
            if (onAttachedMethod != null) {
                module.hookAfter(onAttachedMethod) { chain, _ ->
                    runCatching {
                        val clock = chain.thisObject as View
                        if (isStatusBarClock(clock)) {
                            (clock.parent as? ViewGroup)?.let { parent ->
                                attachToClock(clock, parent)
                            }
                        }
                    }.onFailure { e ->
                        Logger.e(TAG, "Error", "Hitchhiker injection failed", e)
                    }
                }
            } else {
                Logger.e(TAG, "Error", "onAttachedToWindow method not found on Clock class hierarchy")
            }

            StatusBarTintManager.register(module, classLoader) { tint ->
                applyTint(tint)
            }

            IpcDispatcher.addListener { intent ->
                when (intent.action) {
                    IpcManager.ACTION_SETTINGS_SYNC -> handleSync(intent)
                    IpcManager.ACTION_SETTING_CHANGED -> {
                        val key = intent.getStringExtra(PreferenceKeys.EXTRA_KEY) ?: return@addListener
                        when (key) {
                            PreferenceKeys.ENABLE_NETWORK_TRAFFIC -> {
                                isEnabled = intent.getBooleanExtra(PreferenceKeys.EXTRA_VALUE, true)
                                Logger.i(TAG, "Sync", "Setting [enable_network_traffic] updated to $isEnabled")
                                updateState()
                            }
                            PreferenceKeys.NETWORK_TRAFFIC_INTERVAL -> {
                                val intervalSec = intent.getFlexibleIntExtra(PreferenceKeys.EXTRA_VALUE, 1)
                                updateInterval = intervalSec * 1000L
                                Logger.i(TAG, "Sync", "Setting [network_traffic_interval] updated to ${intervalSec}s")
                                workerHandler?.post { poller.reset() }
                            }
                            PreferenceKeys.NETWORK_TRAFFIC_THRESHOLD -> {
                                val threshKb = intent.getFlexibleIntExtra(PreferenceKeys.EXTRA_VALUE, 1)
                                autoHideThreshold = threshKb * 1024L
                                Logger.i(TAG, "Sync", "Setting [network_traffic_threshold] updated to ${threshKb}KB")
                            }
                            PreferenceKeys.NETWORK_TRAFFIC_FONT_SIZE -> {
                                fontSizeSp = intent.getFlexibleFloatExtra(PreferenceKeys.EXTRA_VALUE, 8f)
                                Logger.i(TAG, "Sync", "Setting [network_traffic_font_size] updated to ${fontSizeSp}sp")
                                uiHandler.post { iterateViews { it.updateFontSize(fontSizeSp) } }
                            }
                        }
                    }
                }
            }
            IpcDispatcher.initializeOnce(module, classLoader)

        } catch (e: Throwable) {
            Logger.e(TAG, "Error", "Hook setup failed", e)
        }
    }

    /**
     * Determines whether view belongs to main status bar.
     * Traverses parent hierarchy looking for status bar views.
     */
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

    /**
     * Instantiates and attaches traffic indicator into UI hierarchy.
     * Creates TrafficView instance and inserts into parent container.
     */
    private fun attachToClock(clock: View, clockParent: ViewGroup) {
        val context = clock.context
        val res = context.resources
        val pkg = "com.android.systemui"
        
        val targetIds = listOf(
            res.getIdentifier("status_bar_start_side_content", "id", pkg),
            res.getIdentifier("status_bar_start_side_except_heads_up", "id", pkg),
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
                            targetContainer = field[parent] as? ViewGroup
                            if (targetContainer != null) break
                        } catch (_: NoSuchFieldException) {}
                    }
                }.onFailure { e ->
                    Logger.e(TAG, "Error", "Failed to parse PhoneStatusBarView StartSideContainer", e)
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
            val layoutClass = when (container) {
                is LinearLayout -> LinearLayout.LayoutParams::class.java
                is FrameLayout -> FrameLayout.LayoutParams::class.java
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
        (clock as? TextView)?.let { applyTint(it.currentTextColor) }
        startPolling()
        Logger.i(TAG, "Success", "Precision-injected to ${container.javaClass.simpleName}")
    }

    /**
     * Recolors traffic views with dynamic status bar theme tint.
     * Updates current tint color and refreshes active views.
     */
    private fun applyTint(tint: Int) {
        currentTint = tint
        uiHandler.post { iterateViews { it.updateColor(currentTint) } }
    }

    /**
     * Handles bulk property synchronization from IPC events.
     * Updates enabled state, font size, interval, and threshold.
     */
    private fun handleSync(intent: Intent) {
        isEnabled = intent.getBooleanExtra(PreferenceKeys.ENABLE_NETWORK_TRAFFIC, true)
        val rawFont = intent.getFlexibleFloatExtra(PreferenceKeys.NETWORK_TRAFFIC_FONT_SIZE, 8f)
        fontSizeSp = if (rawFont > 0f) rawFont.coerceAtLeast(6f) else 8f
        val rawInterval = intent.getFlexibleIntExtra(PreferenceKeys.NETWORK_TRAFFIC_INTERVAL, 1)
        updateInterval = (if (rawInterval > 0) rawInterval else 1) * 1000L
        val rawThreshold = intent.getFlexibleIntExtra(PreferenceKeys.NETWORK_TRAFFIC_THRESHOLD, 1)
        autoHideThreshold = (if (rawThreshold >= 0) rawThreshold else 1) * 1024L
        Logger.i(TAG, "Sync", "Full sync received: enabled=$isEnabled, interval=${updateInterval/1000}s, font=${fontSizeSp}sp, thresh=${autoHideThreshold/1024}KB")
        uiHandler.post { iterateViews { it.updateFontSize(fontSizeSp) } }
        updateState()
    }

    /**
     * Toggles visibility and background polling based on enablement.
     * Starts or stops polling thread and updates view visibility.
     */
    private fun updateState() {
        if (isEnabled) startPolling() else stopPolling()
        uiHandler.post {
            val visibility = if (isEnabled) View.VISIBLE else View.GONE
            iterateViews { it.visibility = visibility }
        }
    }

    /**
     * Converts DP units to physical pixels based on display metrics.
     * Applies COMPLEX_UNIT_DIP dimension transformation via Resources.
     */
    private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    /**
     * Locates declared method within class hierarchy via reflection.
     * Traverses superclasses returning Accessible method instance.
     */
    private fun findMethod(clazz: Class<*>, name: String, vararg parameterTypes: Class<*>): Method? {
        var c: Class<*>? = clazz
        while (c != null) {
            try { return c.getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true } }
            catch (_: NoSuchMethodException) { c = c.superclass }
        }
        return null
    }

    /**
     * Specialized custom view for rendering two-line RX/TX speeds.
     * Draws upload and download transfer rate text using Paint.
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

        /**
         * Reapplies tint and starts polling when view is attached.
         * Updates paint color and initiates background speed polling.
         */
        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            updateColor(StatusBarTintManager.currentTint)
            startPolling()
        }
        
        /**
         * Refreshes text size and coloring based on user preferences.
         * Sets paint size and color before requesting layout and invalidate.
         */
        fun updateStyle(sizeSp: Float, color: Int) {
            fontSizePx = sizeSp * resources.displayMetrics.scaledDensity
            paint.textSize = fontSizePx
            paint.color = color
            requestLayout()
            invalidate()
        }

        /**
         * Directly sets internal paint color for system tint changes.
         * Updates paint color and triggers view invalidation.
         */
        fun updateColor(color: Int) {
            paint.color = color
            invalidate()
        }

        /**
         * Updates physical text size and requests view relayout.
         * Calculates font size in pixels and requests layout and invalidate.
         */
        fun updateFontSize(sizeSp: Float) {
            fontSizePx = sizeSp * resources.displayMetrics.scaledDensity
            paint.textSize = fontSizePx
            requestLayout()
            invalidate()
        }

        /**
         * Pushes newly computed transfer speeds to traffic view.
         * Formats RX and TX byte rates and requests layout and invalidate.
         */
        fun setSpeeds(tx: Long, rx: Long) {
            txText = formatSpeed(tx)
            rxText = formatSpeed(rx)
            requestLayout()
            invalidate()
        }

        /**
         * Calculates required view dimensions based on text length.
         * Measures max text width and font height to set measured dimension.
         */
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val txWidth = paint.measureText(txText)
            val rxWidth = paint.measureText(rxText)
            val maxWidth = maxOf(txWidth, rxWidth).toInt()
            val fm = paint.fontMetrics
            val fontHeight = fm.descent - fm.ascent
            setMeasuredDimension(maxWidth + 8, (fontHeight * 2.2).toInt())
        }

        /**
         * Renders two lines of RX and TX speed text on canvas.
         * Draws upload and download speed strings using paint alignment.
         */
        override fun onDraw(canvas: Canvas) {
            val centerX = width / 2f
            val fm = paint.fontMetrics
            val singleLineHeight = fm.descent - fm.ascent
            val totalHeight = singleLineHeight * 2
            val startY = ((height - totalHeight) / 2f) - fm.ascent
            
            canvas.drawText(txText, centerX, startY, paint)
            canvas.drawText(rxText, centerX, startY + singleLineHeight, paint)
        }

        /**
         * Formats raw byte counts into human-readable speed strings.
         * Evaluates rate magnitude and converts to B, KB, or MB format.
         */
        private fun formatSpeed(bytesPerSec: Long): String {
            return when {
                bytesPerSec >= MB -> formatValue(bytesPerSec, MB, "M")
                bytesPerSec >= KB -> formatValue(bytesPerSec, KB, "K")
                else -> "$bytesPerSec B"
            }
        }

        /**
         * Formats numerical value with units like MB or KB.
         * Calculates integral and decimal digits with unit suffix.
         */
        private fun formatValue(bytes: Long, unit: Int, suffix: String): String {
            val integral = bytes / unit
            return if (integral >= 10) "$integral$suffix" else {
                val decimal = ((bytes * 10) / unit) % 10
                "$integral.$decimal$suffix"
            }
        }
    }
}
