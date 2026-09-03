package io.github.hohojia886.pixeltweaks.utils

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable

/**
 * HookUtils: DSL extensions for modern LSPosed API 100+.
 * Provides streamlined 'Before' and 'After' syntax to reduce boilerplate 
 * by automatically handling execution chains and result propagation.
 */

// Wraps a method hook to execute logic BEFORE the original implementation
inline fun XposedModule.hookBefore(
    method: Executable,
    crossinline block: (XposedInterface.Chain) -> Unit
): XposedInterface.HookHandle {
    return this.hook(method).intercept { chain ->
        block(chain) // Execute side-effect logic
        chain.proceed() // Continue original execution
    }
}

// Wraps a method hook to execute logic AFTER the original implementation
inline fun XposedModule.hookAfter(
    method: Executable,
    crossinline block: (chain: XposedInterface.Chain, result: Any?) -> Unit
): XposedInterface.HookHandle {
    return this.hook(method).intercept { chain ->
        val res = chain.proceed() // Capture original result
        block(chain, res) // Execute post-processing logic
        res // Return original result
    }
}
