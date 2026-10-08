@file:Suppress("DiscouragedPrivateApi", "PrivateApi", "DiscouragedApi", "SetTextI18n", "UseCompatLoadingForDrawables")

package io.github.hohojia886.pixeltweaks.hooks.`interface`

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import io.github.hohojia886.pixeltweaks.utils.IpcDispatcher
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.Logger
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import io.github.hohojia886.pixeltweaks.utils.hookAfter
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Method

/**
 * ClearAllButtonHook: Adds a "Clear all" button to the Pixel Launcher's Recents screen.
 * Injects a custom TextView into the OverviewActionsView and wires it to the 
 * RecentsView's dismissAllTasks method for a unified cleanup experience.
 */
object ClearAllButtonHook {

    private var clearAllButtonRef: WeakReference<TextView>? = null // Reference to the injected button
    private var isEnabled = true // Feature toggle
    private var dismissAllTasksMethod: Method? = null // Cached reflection method
    private const val TAG = "ClearAll"

    // Entry point: Resolves target methods and hooks Launcher lifecycle
    fun hook(module: XposedModule, classLoader: ClassLoader) {
        Logger.i(TAG, "Init", "Initializing ClearAllButtonHook")
        try {
            isEnabled = IpcManager.loadPreferences(module, classLoader)
                .getBoolean(PreferenceKeys.ENABLE_CLEAR_ALL, true)

            val recentsViewClass = classLoader.loadClass("com.android.quickstep.views.RecentsView")
            val actionsViewClass = classLoader.loadClass("com.android.quickstep.views.OverviewActionsView")

            // Locates the method responsible for clearing the tasks list
            dismissAllTasksMethod = (findMethod(recentsViewClass, "dismissAllTasks", View::class.java)
                ?: findMethod(recentsViewClass, "dismissAllTasks"))?.apply { isAccessible = true }

            IpcDispatcher.addListener { intent ->
                when (intent.action) {
                    IpcManager.ACTION_SETTINGS_SYNC -> {
                        isEnabled = intent.getBooleanExtra(PreferenceKeys.ENABLE_CLEAR_ALL, true)
                        Logger.i(TAG, "Sync", "Full sync received: enabled=$isEnabled")
                    }
                    IpcManager.ACTION_SETTING_CHANGED -> {
                        if (intent.getStringExtra(PreferenceKeys.EXTRA_KEY) == PreferenceKeys.ENABLE_CLEAR_ALL) {
                            isEnabled = intent.getBooleanExtra(PreferenceKeys.EXTRA_VALUE, true)
                            Logger.i(TAG, "Sync", "Setting [enable_clear_all] updated to $isEnabled")
                        }
                    }
                }
                clearAllButtonRef?.get()?.post { updateButtonVisibility() }
            }
            IpcDispatcher.initializeOnce(module, classLoader)

            // Syncs our button's visibility with the RecentsView container's state
            findMethod(recentsViewClass, "setVisibility", Int::class.java)?.let { method ->
                module.hookAfter(method) { chain, _ ->
                    updateButtonVisibility(chain.args[0] as Int)
                }
            }

            // Hook that triggers the actual button injection after layout inflation
            findMethod(actionsViewClass, "onFinishInflate")?.let { method ->
                module.hookAfter(method) { chain, _ ->
                    val parent = chain.thisObject as FrameLayout
                    injectButton(parent, recentsViewClass)
                }
            }
        } catch (e: Throwable) {
            Logger.e(TAG, "Error", "Hook failed", e)
        }
    }

    // Helper: Detects tablet mode (sw >= 600dp or lowered DPI e.g. 480->320dpi) where Launcher action buttons are absent
    private fun isTabletMode(context: Context): Boolean =
        context.resources.configuration.smallestScreenWidthDp >= 600

    // Creates and adds the "Clear all" button to the Launcher's UI hierarchy
    private fun injectButton(parent: FrameLayout, recentsViewClass: Class<*>) {
        val context = parent.context
        if (parent.findViewWithTag<View>("pxtk_clear_all") != null) return

        val isTablet = isTabletMode(context)
        val res = context.resources
        val screenshotId = res.getIdentifier("action_screenshot", "id", context.packageName)
        val selectId = res.getIdentifier("action_select", "id", context.packageName)
        
        val screenshotBtn = if (screenshotId != 0) parent.findViewById<View>(screenshotId) else null
        val selectBtn = if (selectId != 0) parent.findViewById<View>(selectId) else null

        // In tablet mode (or low DPI setups), Launcher removes or restructures default action buttons (action_screenshot/select).
        // Force fallback pill layout fixed at Gravity.END | Gravity.CENTER_VERTICAL to ensure visibility and prevent disappearing hook points.
        val usePillStyle = isTablet || (screenshotBtn !is TextView)

        val clearAllStrId = res.getIdentifier("recents_clear_all", "string", context.packageName)
            .let { if (it == 0) res.getIdentifier("clear_all", "string", "android") else it }
        val clearAllText = if (clearAllStrId != 0) runCatching { res.getString(clearAllStrId) }.getOrNull() ?: "Clear all" else "Clear all"

        val button = TextView(context).apply {
            tag = "pxtk_clear_all"
            text = clearAllText
            gravity = Gravity.CENTER
            isAllCaps = false
            
            if (!usePillStyle) {
                // Stylist: Clones the visual appearance of the factory "Screenshot" button (Phone mode)
                background = screenshotBtn.background?.constantState?.newDrawable()?.mutate()
                setTextColor(screenshotBtn.textColors)
                typeface = screenshotBtn.typeface
                setTextSize(TypedValue.COMPLEX_UNIT_PX, screenshotBtn.textSize)
                setPadding(screenshotBtn.paddingLeft, screenshotBtn.paddingTop, screenshotBtn.paddingRight, screenshotBtn.paddingBottom)
                minHeight = screenshotBtn.minHeight
                minWidth = screenshotBtn.minWidth
                minimumHeight = screenshotBtn.minimumHeight
                minimumWidth = screenshotBtn.minimumWidth
                includeFontPadding = screenshotBtn.includeFontPadding
                compoundDrawablePadding = screenshotBtn.compoundDrawablePadding

                val iconId = res.getIdentifier("ic_close", "drawable", "android")
                    .let { if (it == 0) res.getIdentifier("ic_delete", "drawable", "android") else it }
                
                if (iconId != 0) {
                    val icon = ResourcesCompat.getDrawable(res, iconId, context.theme)?.mutate()
                    icon?.let {
                        val size = (textSize * 1.2).toInt()
                        it.setBounds(0, 0, size, size)
                        it.setTintList(screenshotBtn.textColors)
                        setCompoundDrawablesRelative(it, null, null, null)
                    }
                }
            } else {
                // Legacy / Tablet mode: Floating rounded pill design fixed at middle-right
                setTextColor(Color.WHITE)
                textSize = 14f
                elevation = 10f
                setPadding(dp(context, 18), dp(context, 12), dp(context, 18), dp(context, 12))
                background = GradientDrawable().apply {
                    setColor(Color.argb(170, 60, 60, 60))
                    cornerRadius = dp(context, 22).toFloat()
                }
            }

            setOnClickListener { v ->
                Logger.i(TAG, "Action", "Clear All button clicked")
                val recentsView = findRecentsView(v, recentsViewClass)
                if ((recentsView != null) && (dismissAllTasksMethod != null)) {
                    runCatching {
                        if (dismissAllTasksMethod!!.parameterCount == 1) {
                            dismissAllTasksMethod!!.invoke(recentsView, v)
                        } else {
                            dismissAllTasksMethod!!.invoke(recentsView)
                        }
                        Logger.i(TAG, "Success", "Tasks dismissed")
                    }
                }
            }
        }

        if (usePillStyle) {
            val lp = parent.layoutParams
            if ((lp != null) && (lp.height != ViewGroup.LayoutParams.MATCH_PARENT)) {
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT
                parent.layoutParams = lp
            }
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginEnd = dp(context, 16)
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }
            parent.addView(button, params)
            Logger.i(TAG, "Success", "Button integrated with tablet layout (sw=${context.resources.configuration.smallestScreenWidthDp}dp)")
        } else {
            val container = (selectBtn?.parent as? ViewGroup) ?: parent
            if ((container is FrameLayout) || (container is LinearLayout)) {
                val lp = container.layoutParams
                if ((lp != null) && (lp.height != ViewGroup.LayoutParams.MATCH_PARENT)) {
                    lp.height = ViewGroup.LayoutParams.MATCH_PARENT
                    container.layoutParams = lp
                }
            }

            val params = if (selectBtn != null) {
                val oldParams = selectBtn.layoutParams
                if (oldParams is ViewGroup.MarginLayoutParams) {
                    val newParams = runCatching {
                        oldParams.javaClass.getConstructor(Int::class.java, Int::class.java)
                            .newInstance(ViewGroup.LayoutParams.WRAP_CONTENT, oldParams.height) as ViewGroup.MarginLayoutParams
                    }.getOrElse { 
                        FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, oldParams.height)
                    }
                    val targetMargin = if (oldParams.marginStart > 0) oldParams.marginStart else dp(context, 8)
                    newParams.setMargins(targetMargin, oldParams.topMargin, 0, oldParams.bottomMargin)
                    (newParams as? FrameLayout.LayoutParams)?.gravity = (oldParams as? FrameLayout.LayoutParams)?.gravity ?: Gravity.CENTER_VERTICAL
                    newParams
                } else {
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        gravity = Gravity.CENTER_VERTICAL; marginStart = dp(context, 8)
                    }
                }
            } else {
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL; marginEnd = dp(context, 16)
                }
            }

            container.addView(button, params)
            Logger.i(TAG, "Success", "Button integrated with style from [action_screenshot]")
        }

        clearAllButtonRef = WeakReference(button)
        button.visibility = if (isEnabled && parent.isVisible) View.VISIBLE else View.GONE
    }

    // Climbs the view tree to locate the RecentsView instance needed to trigger dismissal
    private fun findRecentsView(view: View, recentsViewClass: Class<*>): Any? {
        var current: View? = view
        while (current != null) {
            if (recentsViewClass.isInstance(current)) return current
            val parent = current.parent
            (parent as? ViewGroup)?.let { group ->
                for (i in 0 until group.childCount) {
                    val child = group.getChildAt(i)
                    if (recentsViewClass.isInstance(child)) return child
                }
            }
            current = parent as? View
        }
        return null
    }

    // Updates the visibility of the custom button based on current settings and system UI state
    private fun updateButtonVisibility(systemVisibility: Int? = null) {
        val button = clearAllButtonRef?.get() ?: return
        val visibility = systemVisibility ?: (button.parent as? View)?.visibility ?: View.VISIBLE
        button.visibility = if (isEnabled && (visibility == View.VISIBLE)) View.VISIBLE else View.GONE
    }

    // Helper: Safely locates a method in a class or its superclasses
    private fun findMethod(clazz: Class<*>, name: String, vararg parameterTypes: Class<*>): Method? {
        var c: Class<*>? = clazz
        while (c != null) {
            try { return c.getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true } }
            catch (_: NoSuchMethodException) { c = c.superclass }
        }
        return null
    }

    // Helper: Converts DP values to pixels for layout adjustments
    private fun dp(context: Context, value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()
}
