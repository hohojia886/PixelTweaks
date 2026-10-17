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
 * Hides the system VPN icon from the SystemUI status bar across legacy and modern Compose architectures.
 *
 * Utilizes Settings.Secure icon blacklists, `StatusBarIconController` reflection, and dynamic Compose View tree scanning.
 */
object HideVpnIconHook {

    private const val TAG = "HideVpn"

    @Volatile private var isEnabled = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val trackedStatusIconContainers = Collections.synchronizedList(mutableListOf<WeakReference<ViewGroup>>())
    private val trackedVpnViews = Collections.synchronizedList(mutableListOf<WeakReference<View>>())
    private val trackedStatusBarViews = Collections.synchronizedList(mutableListOf<WeakReference<ViewGroup>>())
    private val trackedIconControllers = Collections.synchronizedList(mutableListOf<WeakReference<Any>>())
    private val originalLayoutParamMap = Collections.synchronizedMap(WeakHashMap<View, Pair<Int, Int>>())

    /**
     * Primary entry point syncing settings and applying VPN hiding hooks.
     *
     * @param module Active LSPosed [XposedModule] instance.
     * @param classLoader SystemUI ClassLoader.
     */
    fun hook(module: XposedModule, classLoader: ClassLoader) {
        syncSettings(module, classLoader)

        IpcDispatcher.addListener { intent ->
            handleBroadcast(intent, classLoader)
        }

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
     * Loads feature settings safely from DE or CE storage fallback.
     * Queries ContentProvider or RemotePreferences for hide VPN toggle.
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
     * Responds to IPC intents and dynamically adjusts VPN icon visibility.
     * Updates isEnabled toggle and refreshes all tracked status bar views.
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
     * Adds or removes "vpn" from system Settings.Secure icon_blacklist.
     * Updates icon_blacklist setting string unconditionally on UI thread.
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
     * Continuously scans status bar hierarchies for late-bound VPN views.
     * Hooks onAttachedToWindow on PhoneStatusBarView and KeyguardStatusBarView.
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

                        root.viewTreeObserver.addOnGlobalLayoutListener {
                            scanContainerForVpn(root)
                        }
                    }.onFailure { e ->
                        Logger.e(TAG, "DynamicVpn", "Failed during onAttachedToWindow hook", e)
                    }
                    chain.proceed()
                }
            }.onFailure {
            }
        }
    }

    /**
     * Intercepts VpnIconViewModel calls on CP3A+ to prevent VPN icon render.
     * Hooks getVisible to return false and getIcon to return null.
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
     * Hooks StatusBarIconController methods to suppress VPN visibility.
     * Intercepts constructors and setIconVisibility calls for vpn slot.
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
            }.onFailure { e ->
                Logger.e(TAG, "ComposeVpn", "Failed class probe for $className", e)
            }
        }
    }

    /**
     * Emits events to existing controllers to aggressively hide VPN slots.
     * Invokes setIconVisibility and removeAllIconsForSlot via reflection.
     */
    private fun forceHideVpnViaController(controller: Any) {
        runCatching {
            val clazz = controller.javaClass
            runCatching {
                val method = clazz.getMethod("setIconVisibility", String::class.java, Boolean::class.javaPrimitiveType)
                method.invoke(controller, "vpn", false)
            }.onFailure { e ->
                Logger.e(TAG, "Controller", "Failed to invoke setIconVisibility", e)
            }
            runCatching {
                val method = clazz.getMethod("removeAllIconsForSlot", String::class.java)
                method.invoke(controller, "vpn")
            }.onFailure { e ->
                Logger.e(TAG, "Controller", "Failed to invoke removeAllIconsForSlot", e)
            }
        }.onFailure { e ->
            Logger.e(TAG, "Controller", "Failed to force hide VPN via controller", e)
        }
    }

    /**
     * Hooks StatusBarIconView instantiation to track and hide VPN views.
     * Intercepts constructors and set/setNotification methods on view.
     */
    private fun applyStatusBarIconViewConstructorHooks(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val sbIconViewClass = classLoader.loadClass("com.android.systemui.statusbar.StatusBarIconView")

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
                    }.onFailure { e ->
                        Logger.e(TAG, "StatusBarIconView", "Failed to process constructor arguments", e)
                    }
                    res
                }
            }

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
                    }.onFailure { e ->
                        Logger.e(TAG, "StatusBarIconView", "Failed to process set/setNotification arguments", e)
                    }
                    res
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "StatusBarIconView", "Failed to hook StatusBarIconView class", e)
        }
    }

    /**
     * Uses various view properties to detect if representing a VPN icon.
     * Checks slot name, content description, tag, class name, and drawable.
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
     * Verifies if a given string slot name is a recognized VPN slot.
     * Evaluates slot string against vpn, vpn_status, and vpn_icon tags.
     */
    private fun isVpnSlot(slot: String?): Boolean {
        if (slot == null) return false
        val s = slot.lowercase()
        return s == "vpn" || s == "vpn_status" || s == "vpn_icon" || s == "status_bar_vpn"
    }

    /**
     * Caches a VPN view for persistent visibility management.
     * Adds reference to trackedVpnViews list and applies visibility.
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
     * Applies visibility state, alpha, and dimensions to hide/show VPN view.
     * Sets GONE visibility, zero alpha, and 0px width/height layout params.
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
            }.onFailure { e ->
                Logger.e(TAG, "Visibility", "Failed to apply visibility to VPN view", e)
            }
        }
    }

    /**
     * Intercepts ModernStatusBarView methods to suppress VPN visibility state.
     * Hooks initView and setVisibleState methods for VPN candidate views.
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
                    }.onFailure { e ->
                        Logger.e(TAG, "ModernVpn", "Failed to process initView", e)
                    }
                    chain.proceed()
                }
            }

            modernViewClass.declaredMethods.filter { it.name == "setVisibleState" }.forEach { method ->
                module.hook(method).intercept { chain ->
                    val view = chain.thisObject as? View ?: return@intercept chain.proceed()
                    if (isEnabled) {
                        if (isVpnViewByFeatures(view) || trackedVpnViews.any { it.get() == view }) {
                            chain.args[0] = 2
                        }
                    }
                    chain.proceed()
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "ModernVpn", "Failed to hook ModernStatusBarView for VPN", e)
        }
    }

    /**
     * Intercepts legacy compose icon views to suppress VPN visibility state.
     * Hooks initView, setVisibleState, and setVisibility for VPN candidate views.
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
                        }.onFailure { e ->
                            Logger.e(TAG, "SingleBindableVpn", "Failed to process initView", e)
                        }
                        chain.proceed()
                    }
                }

                clazz.declaredMethods.filter { it.name == "setVisibleState" }.forEach { method ->
                    module.hook(method).intercept { chain ->
                        val view = chain.thisObject as? View ?: return@intercept chain.proceed()
                        if (isEnabled) {
                            if (isVpnViewByFeatures(view) || trackedVpnViews.any { it.get() == view }) {
                                chain.args[0] = 2
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
            }.onFailure { e ->
                Logger.e(TAG, "SingleBindableVpn", "Failed to hook $className for VPN", e)
            }
        }
    }

    /**
     * Prevents VPN addition and ensures proper ignore-slots on containers.
     * Intercepts StatusIconContainer onAttachedToWindow and addView calls.
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
                    }.onFailure { e ->
                        Logger.e(TAG, "IconContainer", "Failed to track StatusIconContainer onAttachedToWindow", e)
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
            }.onFailure { e ->
                Logger.e(TAG, "IconContainer", "Failed to hook $className for VPN", e)
            }
        }
    }

    /**
     * Scans through the hierarchy tracking any detected VPN views.
     * Recursively traverses ViewGroup children calling trackVpnView.
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
     * Reflectively modifies the container's ignored slots array.
     * Adds or removes "vpn" from mIgnoredSlots collection field.
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
        }.onFailure { e ->
            Logger.e(TAG, "IconContainer", "Failed to update mIgnoredSlots for VPN", e)
        }
    }

    /**
     * Triggers a global visibility refresh of all tracked views and controllers.
     * Invalidates status bar views, compose roots, and icon controllers.
     */
    private fun updateVpnIconVisibility(classLoader: ClassLoader? = null) {
        if (classLoader != null) {
            updateSystemSettingIconBlacklist(classLoader)
        }

        mainHandler.post {
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
