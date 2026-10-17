@file:Suppress("DEPRECATION")

package io.github.hohojia886.pixeltweaks.utils

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable

/**
 * DSL extensions and helper functions for modern LSPosed API 100+.
 * Provides streamlined `hookBefore` and `hookAfter` syntax to eliminate interceptor boilerplate.
 */

/**
 * Executes custom logic strictly BEFORE the original target method execution.
 *
 * @param method Target reflection method or constructor [Executable].
 * @param block Lambda block receiving [XposedInterface.Chain] context before invocation.
 * @return Handle instance representing the registered hook.
 */
inline fun XposedModule.hookBefore(
    method: Executable,
    crossinline block: (XposedInterface.Chain) -> Unit
): XposedInterface.HookHandle {
    return this.hook(method).intercept { chain ->
        block(chain)
        chain.proceed()
    }
}

/**
 * Executes custom logic strictly AFTER the original target method execution.
 *
 * @param method Target reflection method or constructor [Executable].
 * @param block Lambda block receiving [XposedInterface.Chain] and original return value.
 * @return Handle instance representing the registered hook.
 */
inline fun XposedModule.hookAfter(
    method: Executable,
    crossinline block: (chain: XposedInterface.Chain, result: Any?) -> Unit
): XposedInterface.HookHandle {
    return this.hook(method).intercept { chain ->
        val res = chain.proceed()
        block(chain, res)
        res
    }
}

/**
 * Safely extracts an Int extra regardless of primitive type or String packaging in Intent extras.
 *
 * @param name Extra key string identifier.
 * @param defaultValue Fallback value if key is missing or unparseable.
 * @return Parsed integer value or [defaultValue].
 */
fun android.content.Intent.getFlexibleIntExtra(name: String, defaultValue: Int): Int {
    if (!hasExtra(name)) return defaultValue
    val extras = extras ?: return defaultValue
    val value = extras[name] ?: return defaultValue
    return when (value) {
        is Int -> value
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: defaultValue
        else -> defaultValue
    }
}

/**
 * Safely extracts a Float extra regardless of primitive type or String packaging in Intent extras.
 *
 * @param name Extra key string identifier.
 * @param defaultValue Fallback value if key is missing or unparseable.
 * @return Parsed float value or [defaultValue].
 */
fun android.content.Intent.getFlexibleFloatExtra(name: String, defaultValue: Float): Float {
    if (!hasExtra(name)) return defaultValue
    val extras = extras ?: return defaultValue
    val value = extras[name] ?: return defaultValue
    return when (value) {
        is Float -> value
        is Number -> value.toFloat()
        is String -> value.toFloatOrNull() ?: defaultValue
        else -> defaultValue
    }
}

/**
 * Checks if Mobile Data Direct Toggle is supported based on current Android build numbers.
 *
 * @param display Android Build DISPLAY string.
 * @param id Android Build ID string.
 * @return True if system build supports Mobile Data direct toggle fix; false otherwise.
 */
fun isQsDataFixSupported(
    display: String = android.os.Build.DISPLAY,
    id: String = android.os.Build.ID
): Boolean {
    val targetTags = listOf("CP2A.260605", "CP2A.260705", "CP2A.260805")
    return targetTags.any { display.contains(it) || id.contains(it) }
}
