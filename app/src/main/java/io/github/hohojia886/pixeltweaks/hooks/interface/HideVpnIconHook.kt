@file:Suppress("DiscouragedPrivateApi", "PrivateApi", "DiscouragedApi")

package io.github.hohojia886.pixeltweaks.hooks.`interface`

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import io.github.hohojia886.pixeltweaks.utils.IpcDispatcher
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.Logger
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import io.github.hohojia886.pixeltweaks.utils.SystemVersion
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

/**
 * HideVpnIconHook: Responsible for hiding the system VPN status bar icon in Android SystemUI.
 *
 * Underlying Principles & Dual Strategy:
 * 1. Traditional Blacklist Mechanism (Unconditional):
 *    - Updates Settings.Secure.icon_blacklist and StatusIconContainer.mIgnoredSlots
 *      to exclude the "vpn" slot across all Android versions.
 * 2. Modern Compose Interception Strategy (CP3A+):
 *    - Intercepts VpnIconViewModel.getVisible() to return false and getIcon() to return null.
 *    - Dynamically scans view hierarchies to hide delayed VPN connections (e.g. AdGuard).
 */
object HideVpnIconHook {

    private const val TAG = "HideVpn" // Maps to Logcat tag PXTK_HideVpn

    @Volatile private var isEnabled = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val trackedStatusIconContainers = Collections.synchronizedList(mutableListOf<WeakReference<ViewGroup>>())
    private val trackedVpnViews = Collections.synchronizedList(mutableListOf<WeakReference<View>>())
    private val trackedStatusBarViews = Collections.synchronizedList(mutableListOf<WeakReference<ViewGroup>>())
    private val trackedIconControllers = Collections.synchronizedList(mutableListOf<WeakReference<Any>>())
    private val originalLayoutParamMap = Collections.synchronizedMap(WeakHashMap<View, Pair<Int, Int>>())

    /**
     * Entry point for HideVpnIconHook:
     * - Synchronizes initial settings.
     * - Registers IPC broadcast listeners for real-time preference updates.
     * - Applies dynamic view hiders conditionally on CP3A+ builds, and applies core view/controller hooks.
     */
    fun hook(module: XposedModule, classLoader: ClassLoader) {
        syncSettings(module, classLoader)

        // Subscribe to process-level IPC broadcasts via IpcDispatcher
        IpcDispatcher.addListener { intent ->
            handleBroadcast(intent, classLoader)
        }

        // Apply dynamic view tree scanning conditionally on modern Compose-based SystemUI builds (CP3A+)
        if (!(SystemVersion.isCp2a() || SystemVersion.isCd1aBefore260905())) {
            applyDynamicVpnViewHidingHooks(module, classLoader)
        }

        applyComposeVpnHooks(module, classLoader)
        applyStatusBarIconControllerVpnHooks(module, classLoader)
        applyStatusBarIconViewConstructorHooks(module, classLoader)
        applyModernStatusBarViewVpnHooks(module, classLoader)
        applySingleBindableIconViewVpnHooks(module, classLoader)
        applyStatusIconContainerVpnHooks(module, classLoader)
    }

    /**
     * Synchronizes initial settings from DE ContentProvider first (for Direct Boot / pre-unlock availability),
     * falling back to CE RemotePreferences.
     */
    private fun syncSettings(module: XposedModule, classLoader: ClassLoader) {
        val loadedFromDe = runCatching {
            val ctx = IpcManager.getSafeContext(classLoader, "com.android.systemui") ?: IpcManager.getSystemContext(classLoader) ?: return@runCatching false
            val uri = "content://io.github.hohojia886.pixeltweaks".toUri()
            val bundle = ctx.contentResolver.call(uri, "get", null, null) ?: return@runCatching false
            
            isEnabled = bundle.getBoolean(PreferenceKeys.ENABLE_HIDE_VPN_ICON, false)
            Logger.i(TAG, "Sync", "Settings loaded from DE: enableHideVpnIcon=$isEnabled")
            updateVpnIconVisibility(classLoader)
            true
        }.getOrDefault(false)

        if (!loadedFromDe) {
            runCatching {
                val prefs = module.getRemotePreferences(IpcManager.PREF_NAME)
                isEnabled = prefs.getBoolean(PreferenceKeys.ENABLE_HIDE_VPN_ICON, false)
                Logger.i(TAG, "Sync", "Settings loaded from CE (fallback): enableHideVpnIcon=$isEnabled")
                updateVpnIconVisibility(classLoader)
            }.onFailure { e ->
                Logger.e(TAG, "Error", "Failed to sync enableHideVpnIcon settings", e)
            }
        }
    }

    /**
     * Processes incoming IPC broadcasts to update feature states and refresh VPN icon visibility in real-time.
     */
    private fun handleBroadcast(intent: Intent, classLoader: ClassLoader) {
        when (intent.action) {
            IpcManager.ACTION_SETTINGS_SYNC -> {
                isEnabled = intent.getBooleanExtra(PreferenceKeys.ENABLE_HIDE_VPN_ICON, false)
                Logger.i(TAG, "Sync", "Full sync received: enableHideVpnIcon=$isEnabled")
                updateVpnIconVisibility(classLoader)
            }
            IpcManager.ACTION_SETTING_CHANGED -> {
                val key = intent.getStringExtra(PreferenceKeys.EXTRA_KEY)
                if (key == PreferenceKeys.ENABLE_HIDE_VPN_ICON) {
                    isEnabled = intent.getBooleanExtra(PreferenceKeys.EXTRA_VALUE, false)
                    Logger.i(TAG, "Sync", "Setting [enable_hide_vpn_icon] updated to $isEnabled")
                    updateVpnIconVisibility(classLoader)
                }
            }
        }
    }

    /**
     * Unconditionally updates Settings.Secure "icon_blacklist" to include/exclude "vpn" across all Android versions.
     */
    private fun updateSystemSettingIconBlacklist(classLoader: ClassLoader) {
        Logger.i(TAG, "Blacklist", "Updating icon_blacklist unconditionally across all Android versions")
        mainHandler.post {
            runCatching {
                val ctx = IpcManager.getSafeContext(classLoader, "com.android.systemui") ?: IpcManager.getSystemContext(classLoader) ?: return@runCatching
                val resolver = ctx.contentResolver
                val rawBlacklist = android.provider.Settings.Secure.getString(resolver, "icon_blacklist") ?: ""
                val slots = rawBlacklist.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()

                val changed = if (isEnabled) {
                    if ("vpn" !in slots) {
                        slots.add("vpn")
                        true
                    } else false
                } else {
                    if ("vpn" in slots) {
                        slots.remove("vpn")
                        true
                    } else false
                }

                if (changed) {
                    val newBlacklist = slots.joinToString(",")
                    android.provider.Settings.Secure.putString(resolver, "icon_blacklist", newBlacklist)
                    Logger.i(TAG, "CP2Settings", "Updated icon_blacklist in Settings.Secure: $newBlacklist")
                }
            }.onFailure { e ->
                Logger.e(TAG, "CP2Settings", "Failed to update icon_blacklist in Settings.Secure", e)
            }
        }
    }

    /**
     * Dynamic View Tree Scanning Strategy (CP3A+):
     * Hooks PhoneStatusBarView and KeyguardStatusBarView attached state to continuously monitor
     * status bar children and hierarchy changes on-the-fly, catching late-bound VPN icons (e.g. AdGuard).
     */
    private fun applyDynamicVpnViewHidingHooks(module: XposedModule, classLoader: ClassLoader) {
        val classNames = listOf(
            "com.android.systemui.statusbar.phone.PhoneStatusBarView",
            "com.android.systemui.statusbar.phone.KeyguardStatusBarView"
        )

        classNames.forEach { className ->
            runCatching {
                val clazz = classLoader.loadClass(className)
                val onAttachedMethod = clazz.getDeclaredMethod("onAttachedToWindow")
                module.hook(onAttachedMethod).intercept { chain ->
                    runCatching {
                        val root = chain.thisObject as? ViewGroup ?: return@intercept chain.proceed()
                        
                        synchronized(trackedStatusBarViews) {
                            if (trackedStatusBarViews.none { it.get() == root }) {
                                trackedStatusBarViews.add(WeakReference(root))
                            }
                        }

                        // Attach an OnHierarchyChangeListener to catch newly added VPN views immediately
                        root.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                            override fun onChildViewAdded(parent: View?, child: View?) {
                                if (child is ViewGroup) {
                                    scanContainerForVpn(child)
                                } else if (child != null) {
                                    if (isVpnViewByFeatures(child)) {
                                        trackVpnView(child)
                                    }
                                }
                            }
                            override fun onChildViewRemoved(parent: View?, child: View?) {}
                        })

                        // Scan the whole tree on global layout to catch deeply nested or late-bound Compose views
                        root.viewTreeObserver.addOnGlobalLayoutListener {
                            scanContainerForVpn(root)
                        }
                    }
                    chain.proceed()
                }
            }.onFailure {
                // Class not found, safe to ignore
            }
        }
    }

    /**
     * Modern Compose ViewModel Interception Strategy (CP3A+):
     * Hooks VpnIconViewModel to force getVisible() -> false and getIcon() -> null.
     */
    private fun applyComposeVpnHooks(module: XposedModule, classLoader: ClassLoader) {
        val className = "com.android.systemui.statusbar.systemstatusicons.vpn.ui.viewmodel.VpnIconViewModel"
        runCatching {
            val clazz = classLoader.loadClass(className)
            Logger.i(TAG, "ComposeVpn", "PROBE SUCCESS: Found VpnIconViewModel class!")

            clazz.declaredMethods.forEach { method ->
                if (method.name == "getVisible" && (method.returnType == Boolean::class.javaPrimitiveType || method.returnType == java.lang.Boolean.TYPE)) {
                    module.hook(method).intercept { chain ->
                        if (isEnabled) {
                            return@intercept false
                        }
                        chain.proceed()
                    }
                } else if (method.name == "getIcon") {
                    module.hook(method).intercept { chain ->
                        if (isEnabled) {
                            return@intercept null
                        }
                        chain.proceed()
                    }
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "ComposeVpn", "Failed to hook VpnIconViewModel", e)
        }
    }

    /**
     * Hooks StatusBarIconController methods to intercept and suppress visibility changes for VPN icons.
     */
    private fun applyStatusBarIconControllerVpnHooks(module: XposedModule, classLoader: ClassLoader) {
        val classNames = listOf(
            "com.android.systemui.statusbar.phone.StatusBarIconControllerImpl",
            "com.android.systemui.statusbar.phone.StatusBarIconControllerImpl" + '$' + "Factory",
            "com.android.systemui.statusbar.phone.StatusBarIconController"
        )

        classNames.forEach { className ->
            runCatching {
                val clazz = classLoader.loadClass(className)

                clazz.declaredConstructors.forEach { constructor ->
                    module.hook(constructor).intercept { chain ->
                        val controller = chain.thisObject
                        synchronized(trackedIconControllers) {
                            if (trackedIconControllers.none { it.get() == controller }) {
                                trackedIconControllers.add(WeakReference(controller))
                            }
                        }
                        if (isEnabled) {
                            forceHideVpnViaController(controller)
                        }
                        chain.proceed()
                    }
                }

                clazz.declaredMethods.forEach { method ->
                    if (method.name == "setIconVisibility" || method.name == "setIcon" || method.name == "removeAllIconsForSlot" || method.name == "loadIcons") {
                        module.hook(method).intercept { chain ->
                            val slot = chain.args.getOrNull(0)?.toString() ?: ""
                            if (slot.isNotEmpty() && (slot == "vpn" || slot.contains("vpn", ignoreCase = true))) {
                                if (isEnabled && method.name == "setIconVisibility" && chain.args.size >= 2) {
                                    chain.args[1] = false
                                }
                            }
                            chain.proceed()
                        }
                    }
                }
            }.onFailure {
                // Safe to ignore
            }
        }
    }

    /**
     * Actively invokes setIconVisibility("vpn", false) and removeAllIconsForSlot("vpn") on active icon controllers.
     */
    private fun forceHideVpnViaController(controller: Any) {
        runCatching {
            val clazz = controller.javaClass
            runCatching {
                val method = clazz.getMethod("setIconVisibility", String::class.java, Boolean::class.javaPrimitiveType)
                method.invoke(controller, "vpn", false)
            }
            runCatching {
                val method = clazz.getMethod("removeAllIconsForSlot", String::class.java)
                method.invoke(controller, "vpn")
            }
        }
    }

    /**
     * Hooks StatusBarIconView constructors and update methods to identify and track VPN icon views.
     */
    private fun applyStatusBarIconViewConstructorHooks(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val sbIconViewClass = classLoader.loadClass("com.android.systemui.statusbar.StatusBarIconView")

            // Hook all constructors of StatusBarIconView
            sbIconViewClass.declaredConstructors.forEach { constructor ->
                module.hook(constructor).intercept { chain ->
                    val res = chain.proceed()
                    runCatching {
                        val view = chain.thisObject as? View ?: return@runCatching
                        val slot = chain.args.getOrNull(1)?.toString() ?: ""
                        val sbn = chain.args.getOrNull(2) as? android.service.notification.StatusBarNotification
                        val pkg = sbn?.packageName ?: ""

                        if (isVpnViewByFeatures(view) || pkg == "com.android.vpndialogs" || (slot.isEmpty() && pkg.contains("vpn", ignoreCase = true))) {
                            trackVpnView(view)
                        }
                    }
                    res
                }
            }

            // Hook set(StatusBarIcon) and setNotification(StatusBarNotification)
            sbIconViewClass.declaredMethods.filter { it.name == "set" || it.name == "setNotification" }.forEach { method ->
                module.hook(method).intercept { chain ->
                    val res = chain.proceed()
                    runCatching {
                        val view = chain.thisObject as? View ?: return@runCatching
                        val sbn = runCatching {
                            val getNotificationMethod = view.javaClass.getMethod("getNotification")
                            getNotificationMethod.invoke(view) as? android.service.notification.StatusBarNotification
                        }.getOrNull()
                        val pkg = sbn?.packageName ?: ""

                        if (isVpnViewByFeatures(view) || pkg == "com.android.vpndialogs" || trackedVpnViews.any { it.get() == view }) {
                            trackVpnView(view)
                        }
                    }
                    res
                }
            }
        }.onFailure {
            // Safe to ignore
        }
    }

    /**
     * Evaluates a View against multiple feature dimensions (slot, content description, tag, class name, resource name, drawable)
     * to determine if it represents a VPN status bar icon.
     */
    private fun isVpnViewByFeatures(view: View): Boolean {
        val slot = runCatching {
            val getSlotMethod = view.javaClass.getMethod("getSlot")
            getSlotMethod.invoke(view)?.toString()
        }.getOrNull()

        val contentDesc = view.contentDescription?.toString() ?: ""
        val tag = view.tag?.toString() ?: ""
        val cName = view.javaClass.name

        val resName = runCatching {
            if (view.id != View.NO_ID && view.context != null) {
                view.context.resources.getResourceEntryName(view.id)
            } else ""
        }.getOrDefault("")

        val drawableName = runCatching {
            if (view is android.widget.ImageView && view.drawable != null) {
                view.drawable.javaClass.name
            } else ""
        }.getOrDefault("")

        return isVpnSlot(slot) ||
               contentDesc.contains("vpn", ignoreCase = true) ||
               tag.contains("vpn", ignoreCase = true) ||
               cName.contains("vpn", ignoreCase = true) ||
               resName.contains("vpn", ignoreCase = true) ||
               drawableName.contains("vpn", ignoreCase = true)
    }

    /**
     * Checks if a given slot identifier string corresponds to a known VPN slot.
     */
    private fun isVpnSlot(slot: String?): Boolean {
        if (slot == null) return false
        val s = slot.lowercase()
        return s == "vpn" || s == "vpn_status" || s == "vpn_icon" || s == "status_bar_vpn"
    }

    /**
     * Adds a VPN view to the tracking list and immediately applies hiding visibility logic.
     */
    private fun trackVpnView(view: View) {
        synchronized(trackedVpnViews) {
            if (trackedVpnViews.none { it.get() == view }) {
                trackedVpnViews.add(WeakReference(view))
            }
        }
        applyVisibilityToVpnView(view)
    }

    /**
     * Forces visibility, alpha, layout dimensions, and state of a tracked VPN view, caching original layout parameters.
     */
    private fun applyVisibilityToVpnView(view: View) {
        mainHandler.post {
            runCatching {
                val hide = isEnabled

                val lp = view.layoutParams
                if (lp != null) {
                    synchronized(originalLayoutParamMap) {
                        if (originalLayoutParamMap[view] == null && lp.width > 0 && lp.height > 0) {
                            originalLayoutParamMap[view] = Pair(lp.width, lp.height)
                        }
                    }
                }

                runCatching {
                    val setVisibleStateMethod = view.javaClass.getMethod("setVisibleState", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                    setVisibleStateMethod.invoke(view, if (hide) 2 else 0, false)
                }

                view.visibility = if (hide) View.GONE else View.VISIBLE
                view.alpha = if (hide) 0f else 1f

                if (lp != null) {
                    if (hide) {
                        lp.width = 0
                        lp.height = 0
                    } else {
                        val original = originalLayoutParamMap[view]
                        if (original != null && original.first > 0 && original.second > 0) {
                            lp.width = original.first
                            lp.height = original.second
                        } else {
                            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
                            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        }
                    }
                    view.layoutParams = lp
                }

                if (hide) {
                    view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                        if (isEnabled && trackedVpnViews.any { it.get() == v }) {
                            if (v.visibility != View.GONE || v.alpha != 0f) {
                                v.visibility = View.GONE
                                v.alpha = 0f
                                val currentLp = v.layoutParams
                                if (currentLp != null && currentLp.width != 0) {
                                    currentLp.width = 0
                                    currentLp.height = 0
                                    v.layoutParams = currentLp
                                }
                            }
                        }
                    }
                }

                view.requestLayout()
                view.invalidate()
            }
        }
    }

    /**
     * Hooks ModernStatusBarView initView and setVisibleState methods.
     */
    private fun applyModernStatusBarViewVpnHooks(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val modernViewClass = classLoader.loadClass("com.android.systemui.statusbar.pipeline.shared.ui.view.ModernStatusBarView")

            modernViewClass.declaredMethods.filter { it.name == "initView" }.forEach { method ->
                module.hook(method).intercept { chain ->
                    val view = chain.thisObject as? View ?: return@intercept chain.proceed()
                    runCatching {
                        if (isVpnViewByFeatures(view)) {
                            trackVpnView(view)
                        }
                    }
                    chain.proceed()
                }
            }

            modernViewClass.declaredMethods.filter { it.name == "setVisibleState" }.forEach { method ->
                module.hook(method).intercept { chain ->
                    val view = chain.thisObject as? View ?: return@intercept chain.proceed()
                    if (isEnabled) {
                        if (isVpnViewByFeatures(view) || trackedVpnViews.any { it.get() == view }) {
                            chain.args[0] = 2 // 2 = STATE_HIDDEN
                        }
                    }
                    chain.proceed()
                }
            }
        }.onFailure {
            // Safe to ignore
        }
    }

    /**
     * Hooks SingleBindableStatusBarComposeIconView and related compose icon views.
     */
    private fun applySingleBindableIconViewVpnHooks(module: XposedModule, classLoader: ClassLoader) {
        val classNames = listOf(
            "com.android.systemui.statusbar.pipeline.shared.ui.view.SingleBindableStatusBarComposeIconView",
            "com.android.systemui.statusbar.pipeline.shared.ui.view.SingleBindableStatusBarIconView",
            "com.android.systemui.statusbar.pipeline.shared.ui.view.ModernStatusBarView",
            "com.android.systemui.statusbar.StatusBarIconView"
        )

        classNames.forEach { className ->
            runCatching {
                val clazz = classLoader.loadClass(className)
                
                clazz.declaredMethods.filter { it.name == "initView" }.forEach { method ->
                    module.hook(method).intercept { chain ->
                        val view = chain.thisObject as? View ?: return@intercept chain.proceed()
                        runCatching {
                            if (isVpnViewByFeatures(view)) {
                                trackVpnView(view)
                            }
                        }
                        chain.proceed()
                    }
                }

                clazz.declaredMethods.filter { it.name == "setVisibleState" }.forEach { method ->
                    module.hook(method).intercept { chain ->
                        val view = chain.thisObject as? View ?: return@intercept chain.proceed()
                        if (isEnabled) {
                            if (isVpnViewByFeatures(view) || trackedVpnViews.any { it.get() == view }) {
                                chain.args[0] = 2 // 2 = STATE_HIDDEN
                            }
                        }
                        chain.proceed()
                    }
                }

                clazz.declaredMethods.filter { it.name == "setVisibility" }.forEach { method ->
                    module.hook(method).intercept { chain ->
                        val view = chain.thisObject as? View ?: return@intercept chain.proceed()
                        if (isEnabled) {
                            if (isVpnViewByFeatures(view) || trackedVpnViews.any { it.get() == view }) {
                                chain.args[0] = View.GONE
                            }
                        }
                        chain.proceed()
                    }
                }
            }.onFailure {
                // Safe to ignore
            }
        }
    }

    /**
     * Hooks StatusIconContainer and NotificationIconContainer to manage ignored slots and suppress VPN view additions.
     */
    private fun applyStatusIconContainerVpnHooks(module: XposedModule, classLoader: ClassLoader) {
        val containerNames = listOf(
            "com.android.systemui.statusbar.phone.StatusIconContainer",
            "com.android.systemui.statusbar.phone.NotificationIconContainer"
        )

        containerNames.forEach { className ->
            runCatching {
                val containerClass = classLoader.loadClass(className)
                val onAttachedMethod = containerClass.getDeclaredMethod("onAttachedToWindow")

                module.hook(onAttachedMethod).intercept { chain ->
                    val container = chain.thisObject as? ViewGroup ?: return@intercept chain.proceed()
                    runCatching {
                        synchronized(trackedStatusIconContainers) {
                            if (trackedStatusIconContainers.none { it.get() == container }) {
                                trackedStatusIconContainers.add(WeakReference(container))
                            }
                        }
                        updateIgnoredSlotsForContainer(container)
                    }
                    chain.proceed()
                }

                containerClass.declaredMethods.filter { it.name == "addView" }.forEach { method ->
                    module.hook(method).intercept { chain ->
                        val view = chain.args.getOrNull(0) as? View ?: return@intercept chain.proceed()
                        if (isEnabled) {
                            if (isVpnViewByFeatures(view) || trackedVpnViews.any { it.get() == view }) {
                                view.visibility = View.GONE
                                view.alpha = 0f
                                val lp = view.layoutParams
                                if (lp != null) {
                                    lp.width = 0
                                    lp.height = 0
                                    view.layoutParams = lp
                                }
                            }
                        }
                        chain.proceed()
                    }
                }
            }.onFailure {
                // Safe to ignore
            }
        }
    }

    /**
     * Recursively scans a view group container to identify and track any VPN views present.
     */
    private fun scanContainerForVpn(root: ViewGroup) {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i) ?: continue
            if (isVpnViewByFeatures(child)) {
                trackVpnView(child)
            } else if (child is ViewGroup) {
                scanContainerForVpn(child)
            }
        }
    }

    /**
     * Updates mIgnoredSlots collection on StatusIconContainer instances to ignore the "vpn" slot.
     */
    @Suppress("UNCHECKED_CAST")
    private fun updateIgnoredSlotsForContainer(container: ViewGroup) {
        runCatching {
            val field = container.javaClass.getDeclaredField("mIgnoredSlots").apply { isAccessible = true }
            val ignoredSlots = field.get(container) as? MutableCollection<String> ?: return@runCatching

            synchronized(ignoredSlots) {
                if (isEnabled) {
                    if ("vpn" !in ignoredSlots) {
                        ignoredSlots.add("vpn")
                    }
                } else {
                    ignoredSlots.remove("vpn")
                }
            }

            mainHandler.post {
                container.requestLayout()
                container.invalidate()
            }
        }.onFailure {
            // Safe to ignore
        }
    }

    /**
     * Updates and refreshes all tracked status bar views, VPN views, containers, and icon controllers.
     */
    private fun updateVpnIconVisibility(classLoader: ClassLoader? = null) {
        if (classLoader != null) {
            updateSystemSettingIconBlacklist(classLoader)
        }

        mainHandler.post {
            // Re-layout & invalidate all active status bar views & Compose roots to force Recomposition
            synchronized(trackedStatusBarViews) {
                val sbIt = trackedStatusBarViews.iterator()
                while (sbIt.hasNext()) {
                    val sbView = sbIt.next().get()
                    if (sbView != null && sbView.isAttachedToWindow) {
                        scanContainerForVpn(sbView)
                        sbView.requestLayout()
                        sbView.invalidate()

                        fun invalidateComposeViews(v: View) {
                            if (v.javaClass.name.contains("AndroidComposeView") || v.javaClass.name.contains("ComposeView")) {
                                v.requestLayout()
                                v.invalidate()
                            }
                            if (v is ViewGroup) {
                                for (i in 0 until v.childCount) {
                                    invalidateComposeViews(v.getChildAt(i) ?: continue)
                                }
                            }
                        }
                        invalidateComposeViews(sbView)
                    } else if (sbView == null) {
                        sbIt.remove()
                    }
                }
            }

            // Update all tracked VPN Views
            synchronized(trackedVpnViews) {
                val vpnIt = trackedVpnViews.iterator()
                while (vpnIt.hasNext()) {
                    val view = vpnIt.next().get()
                    if (view != null) {
                        applyVisibilityToVpnView(view)
                    } else {
                        vpnIt.remove()
                    }
                }
            }

            // Update all tracked StatusIconContainers
            synchronized(trackedStatusIconContainers) {
                val containerIt = trackedStatusIconContainers.iterator()
                while (containerIt.hasNext()) {
                    val container = containerIt.next().get()
                    if (container != null) {
                        updateIgnoredSlotsForContainer(container)
                    } else {
                        containerIt.remove()
                    }
                }
            }

            // Update all tracked StatusBarIconControllers
            synchronized(trackedIconControllers) {
                val ctrlIt = trackedIconControllers.iterator()
                while (ctrlIt.hasNext()) {
                    val ctrl = ctrlIt.next().get()
                    if (ctrl != null) {
                        if (isEnabled) {
                            forceHideVpnViaController(ctrl)
                        }
                    } else {
                        ctrlIt.remove()
                    }
                }
            }
        }
    }
}
