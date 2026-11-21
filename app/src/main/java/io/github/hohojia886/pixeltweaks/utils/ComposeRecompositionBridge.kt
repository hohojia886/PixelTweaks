@file:Suppress("DiscouragedPrivateApi", "PrivateApi")

package io.github.hohojia886.pixeltweaks.utils

import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * R8-resilient reflective bridge into a host process's Jetpack Compose runtime (e.g., SystemUI).
 *
 * Subscribes hooked `@Composable` invocations to an injected `MutableState` and active `RecomposeScope`
 * so external Xposed preference toggles can schedule immediate Compose recomposition even when no
 * native system state (such as WiFi or VPN) changes.
 *
 * @param logTag Logger subsystem tag used for diagnostic messages.
 */
class ComposeRecompositionBridge(private val logTag: String) {

    private val stateVersion = AtomicInteger(0)
    private val trackedRecomposeScopes = Collections.synchronizedList(mutableListOf<WeakReference<Any>>())

    @Volatile private var composeTriggerState: Any? = null
    @Volatile private var stateGetValueMethod: Method? = null
    @Volatile private var stateSetValueMethod: Method? = null
    @Volatile private var snapshotCompanion: Any? = null
    @Volatile private var sendApplyNotificationsMethod: Method? = null
    @Volatile private var composerClass: Class<*>? = null
    @Volatile private var getRecomposeScopeMethod: Method? = null
    @Volatile private var recordUsedMethod: Method? = null
    @Volatile private var setScopeUsedMethod: Method? = null
    @Volatile private var invalidateScopeMethod: Method? = null

    /**
     * Current monotonically increasing trigger version counter.
     */
    internal val currentVersion: Int
        get() = stateVersion.get()

    /**
     * Returns true when either the SnapshotState trigger or RecomposeScope invalidation handle is ready.
     *
     * @return True if recomposition can be scheduled; false otherwise.
     */
    fun isAvailable(): Boolean = composeTriggerState != null || invalidateScopeMethod != null

    /**
     * Returns true when the primary SnapshotState trigger was initialized.
     *
     * @return True if the reflective MutableState instance and accessors are bound.
     */
    internal fun hasStateBridge(): Boolean =
        composeTriggerState != null && stateGetValueMethod != null && stateSetValueMethod != null

    /**
     * Returns true when the secondary Composer RecomposeScope invalidation bridge was initialized.
     *
     * @return True if scope capture and invalidation methods are bound.
     */
    internal fun hasScopeBridge(): Boolean =
        composerClass != null && getRecomposeScopeMethod != null && invalidateScopeMethod != null &&
            (recordUsedMethod != null || setScopeUsedMethod != null)

    /**
     * Clears the secondary RecomposeScope handles to simulate R8 stripping in unit tests.
     */
    internal fun clearScopeBridgeForTest() {
        composerClass = null
        getRecomposeScopeMethod = null
        recordUsedMethod = null
        setScopeUsedMethod = null
        invalidateScopeMethod = null
        trackedRecomposeScopes.clear()
    }

    /**
     * Initializes reflective handles into the target process's Jetpack Compose runtime.
     * Gracefully tolerates R8 tree-shaking and interface merging (e.g., `RecomposeScope` -> `RecomposeScopeImpl`).
     *
     * @param classLoader Host process [ClassLoader] containing `androidx.compose.runtime.*` classes.
     */
    fun initialize(classLoader: ClassLoader) {
        runCatching {
            val state = createComposeTriggerState(classLoader)
            if (state != null) {
                val getMethod = state.javaClass.methods.find { it.name == "getIntValue" && it.parameterCount == 0 }
                    ?: state.javaClass.methods.find { it.name == "getValue" && it.parameterCount == 0 }
                val setMethod = state.javaClass.methods.find {
                    it.name == "setIntValue" && it.parameterCount == 1 && it.parameterTypes[0] == Int::class.javaPrimitiveType
                } ?: state.javaClass.methods.find {
                    it.name == "setValue" && it.parameterCount == 1
                }

                if (getMethod != null && setMethod != null) {
                    getMethod.isAccessible = true
                    setMethod.isAccessible = true
                    composeTriggerState = state
                    stateGetValueMethod = getMethod
                    stateSetValueMethod = setMethod
                    Logger.i(logTag, "Init", "Initialized SystemUI Compose SnapshotState bridge (${state.javaClass.simpleName})")
                } else {
                    Logger.e(logTag, "Error", "Compose trigger state missing get/set methods on ${state.javaClass.name}")
                }
            } else {
                Logger.e(logTag, "Error", "Unable to construct Compose trigger state in SystemUI ClassLoader")
            }
        }.onFailure { e ->
            Logger.e(logTag, "Error", "Failed to initialize Compose SnapshotState bridge", e)
        }

        // Snapshot.Companion.sendApplyNotifications is optional because Android's GlobalSnapshotManager
        // also dispatches apply notifications automatically on main-thread state mutations.
        runCatching {
            val snapshotClass = classLoader.loadClass("androidx.compose.runtime.snapshots.Snapshot")
            val companion = snapshotClass.getDeclaredField("Companion").apply { isAccessible = true }.get(null)
            val sendMethod = companion?.javaClass?.methods?.find {
                it.name == "sendApplyNotifications" && it.parameterCount == 0
            }
            if (companion != null && sendMethod != null) {
                sendMethod.isAccessible = true
                snapshotCompanion = companion
                sendApplyNotificationsMethod = sendMethod
                Logger.i(logTag, "Init", "Bound Snapshot.Companion.sendApplyNotifications()")
            }
        }.onFailure { e ->
            Logger.d(logTag, "Init", "Optional Snapshot.sendApplyNotifications not found: ${e.message}")
        }

        runCatching {
            val cClass = classLoader.loadClass("androidx.compose.runtime.Composer")
            val cImplClass = runCatching { classLoader.loadClass("androidx.compose.runtime.ComposerImpl") }.getOrNull()
            // R8 may merge RecomposeScope interface into RecomposeScopeImpl in SystemUIGoogle.
            val scopeClass = runCatching { classLoader.loadClass("androidx.compose.runtime.RecomposeScope") }.getOrNull()
                ?: classLoader.loadClass("androidx.compose.runtime.RecomposeScopeImpl")

            val getScope = cClass.methods.find { it.name == "getRecomposeScope" && it.parameterCount == 0 }
                ?: cImplClass?.declaredMethods?.find {
                    (it.name == "getRecomposeScope" || it.name.startsWith("getCurrentRecomposeScope")) && it.parameterCount == 0
                }
            val recUsed = cClass.methods.find { it.name == "recordUsed" && it.parameterCount == 1 }
                ?: cImplClass?.declaredMethods?.find { it.name == "recordUsed" && it.parameterCount == 1 }
            val setUsed = scopeClass.declaredMethods.find {
                it.name == "setUsed" && it.parameterCount == 1 && it.parameterTypes[0] == Boolean::class.javaPrimitiveType
            }
            val invMethod = scopeClass.methods.find { it.name == "invalidate" && it.parameterCount == 0 }
                ?: scopeClass.declaredMethods.find { it.name == "invalidateForResult" && it.parameterCount == 1 }

            if (getScope != null && invMethod != null && (recUsed != null || setUsed != null)) {
                getScope.isAccessible = true
                recUsed?.isAccessible = true
                setUsed?.isAccessible = true
                invMethod.isAccessible = true
                composerClass = cClass
                getRecomposeScopeMethod = getScope
                recordUsedMethod = recUsed
                setScopeUsedMethod = setUsed
                invalidateScopeMethod = invMethod
                Logger.i(logTag, "Init", "Initialized SystemUI Composer RecomposeScope bridge (${scopeClass.simpleName})")
            } else {
                Logger.d(logTag, "Init", "Composer RecomposeScope methods stripped by R8; relying on SnapshotState bridge")
            }
        }.onFailure { e ->
            Logger.d(logTag, "Init", "Optional Composer RecomposeScope bridge unavailable: ${e.message}")
        }
    }

    /**
     * Subscribes the currently executing Compose scope to future [trigger] calls.
     * Reads the injected `MutableState` and marks the active `RecomposeScope` as used so it stays invalidatable.
     *
     * @param args Hooked composable invocation arguments containing the live `Composer` instance, if any.
     */
    fun observe(args: List<Any?>) {
        composeTriggerState?.let { state ->
            runCatching {
                stateGetValueMethod?.invoke(state)
            }.onFailure { e ->
                Logger.e(logTag, "Error", "Failed to read Compose trigger state during composition", e)
            }
        }

        val cClass = composerClass ?: return
        val getScope = getRecomposeScopeMethod ?: return

        val composer = args.firstOrNull { it != null && cClass.isInstance(it) } ?: return
        runCatching {
            val scope = getScope.invoke(composer) ?: return@runCatching
            // Marking scope as used matches currentRecomposeScope semantics and ensures
            // endRestartGroup() retains the restart lambda for future scope.invalidate() calls.
            val recUsed = recordUsedMethod
            if (recUsed != null) {
                recUsed.invoke(composer, scope)
            } else {
                setScopeUsedMethod?.invoke(scope, true)
            }
            synchronized(trackedRecomposeScopes) {
                trackedRecomposeScopes.removeAll { it.get() == null }
                if (trackedRecomposeScopes.none { it.get() === scope }) {
                    trackedRecomposeScopes.add(WeakReference(scope))
                }
            }
        }.onFailure { e ->
            Logger.e(logTag, "Error", "Failed to capture Compose RecomposeScope", e)
        }
    }

    /**
     * Schedules an immediate Jetpack Compose recomposition for all observed scopes.
     * Mutates the injected `MutableState`, flushes Snapshot notifications if bound, and invalidates tracked scopes.
     *
     * @param contextInfo Optional diagnostic label appended to the active log line.
     */
    fun trigger(contextInfo: String = "") {
        composeTriggerState?.let { state ->
            runCatching {
                val nextVersion = stateVersion.incrementAndGet()
                stateSetValueMethod?.invoke(state, nextVersion)
                snapshotCompanion?.let { companion ->
                    sendApplyNotificationsMethod?.invoke(companion)
                }
                val suffix = if (contextInfo.isNotEmpty()) ", $contextInfo" else ""
                Logger.i(logTag, "Active", "Triggered Compose SnapshotState recomposition (version=$nextVersion$suffix)")
            }.onFailure { e ->
                Logger.e(logTag, "Error", "Failed to update Compose SnapshotState", e)
            }
        }

        val invalidateMethod = invalidateScopeMethod ?: return
        synchronized(trackedRecomposeScopes) {
            val iterator = trackedRecomposeScopes.iterator()
            while (iterator.hasNext()) {
                val scope = iterator.next().get()
                if (scope != null) {
                    runCatching {
                        if (invalidateMethod.parameterCount == 0) {
                            invalidateMethod.invoke(scope)
                        } else {
                            invalidateMethod.invoke(scope, null)
                        }
                    }.onFailure { e ->
                        Logger.e(logTag, "Error", "Failed to invalidate Compose RecomposeScope", e)
                    }
                } else {
                    iterator.remove()
                }
            }
        }
    }

    /**
     * Constructs an R8-resilient Jetpack Compose MutableState inside the host ClassLoader.
     * Falls back across zero-policy IntState classes, `mutableStateOf$default`, built-in policy objects,
     * and dynamic `SnapshotMutationPolicy` proxies so R8 method inlining cannot break initialization.
     *
     * @param classLoader Host process ClassLoader containing the target Compose runtime.
     * @return Instantiated Compose MutableState object, or null if unavailable.
     */
    private fun createComposeTriggerState(classLoader: ClassLoader): Any? {
        val initialVersion = stateVersion.get()

        // Tier 1: Primitive Int state implementations require no SnapshotMutationPolicy argument.
        for (intStateName in listOf(
            "androidx.compose.runtime.ParcelableSnapshotMutableIntState",
            "androidx.compose.runtime.SnapshotMutableIntStateImpl",
        )) {
            val state = runCatching {
                val cls = classLoader.loadClass(intStateName)
                val ctor = cls.getDeclaredConstructor(Int::class.javaPrimitiveType).apply { isAccessible = true }
                ctor.newInstance(initialVersion)
            }.getOrNull()
            if (state != null) {
                Logger.i(logTag, "Init", "Created Compose trigger state via $intStateName")
                return state
            }
        }

        // Tier 2: Kotlin default-parameter bridge uses Compose's internal StructuralEqualityPolicy directly.
        val defaultBridgeState = runCatching {
            val snapshotStateKt = classLoader.loadClass("androidx.compose.runtime.SnapshotStateKt")
            val defaultMethod = snapshotStateKt.declaredMethods.find {
                it.name == "mutableStateOf\$default" && it.parameterCount == 4
            } ?: return@runCatching null
            defaultMethod.isAccessible = true
            defaultMethod.invoke(null, initialVersion, null, 2, null)
        }.getOrNull()
        if (defaultBridgeState != null) {
            Logger.i(logTag, "Init", "Created Compose trigger state via SnapshotStateKt.mutableStateOf\$default")
            return defaultBridgeState
        }

        // Tier 3: Resolve or synthesize a SnapshotMutationPolicy and instantiate MutableState.
        val policyClass = runCatching {
            classLoader.loadClass("androidx.compose.runtime.SnapshotMutationPolicy")
        }.getOrNull() ?: return null

        val policy = runCatching {
            val snapshotStateKt = classLoader.loadClass("androidx.compose.runtime.SnapshotStateKt")
            snapshotStateKt.declaredMethods.find {
                (it.name == "structuralEqualityPolicy" || it.name == "neverEqualPolicy") && it.parameterCount == 0
            }?.apply { isAccessible = true }?.invoke(null)
        }.getOrNull() ?: listOf(
            "androidx.compose.runtime.StructuralEqualityPolicy",
            "androidx.compose.runtime.NeverEqualPolicy",
            "androidx.compose.runtime.ReferentialEqualityPolicy",
        ).firstNotNullOfOrNull { policyObjectName ->
            runCatching {
                val cls = classLoader.loadClass(policyObjectName)
                cls.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
            }.getOrNull()
        } ?: runCatching {
            if (!policyClass.isInterface) return@runCatching null
            Proxy.newProxyInstance(classLoader, arrayOf(policyClass)) { _, method, proxyArgs ->
                when (method.name) {
                    "equivalent" -> proxyArgs?.getOrNull(0) == proxyArgs?.getOrNull(1)
                    "merge" -> null
                    "toString" -> "PixelTweaksMutationPolicy"
                    "hashCode" -> 0
                    "equals" -> false
                    else -> null
                }
            }
        }.getOrNull() ?: return null

        val facadeState = runCatching {
            val snapshotStateKt = classLoader.loadClass("androidx.compose.runtime.SnapshotStateKt")
            val factoryMethod = snapshotStateKt.declaredMethods.find {
                it.name == "mutableStateOf" && it.parameterCount == 2
            } ?: return@runCatching null
            factoryMethod.isAccessible = true
            factoryMethod.invoke(null, initialVersion, policy)
        }.getOrNull()
        if (facadeState != null) {
            Logger.i(logTag, "Init", "Created Compose trigger state via SnapshotStateKt.mutableStateOf")
            return facadeState
        }

        for (stateClassName in listOf(
            "androidx.compose.runtime.ParcelableSnapshotMutableState",
            "androidx.compose.runtime.SnapshotMutableStateImpl",
        )) {
            val state = runCatching {
                val cls = classLoader.loadClass(stateClassName)
                val ctor = cls.declaredConstructors.find { it.parameterCount == 2 }?.apply { isAccessible = true }
                    ?: return@runCatching null
                ctor.newInstance(initialVersion, policy)
            }.getOrNull()
            if (state != null) {
                Logger.i(logTag, "Init", "Created Compose trigger state via $stateClassName")
                return state
            }
        }

        return null
    }
}
