package io.github.hohojia886.pixeltweaks.hooks

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * PixelHook: The standard interface for modular feature implementations.
 * Enables automated dispatching in MainHook by abstracting process-matching 
 * logic and execution entry points for each distinct feature.
 */
interface PixelHook {
    val name: String // Unique identifier for logging purposes

    // Determines if this hook applies to the target package and process context
    fun matches(packageName: String, isRootSystemServer: Boolean): Boolean

    // Entry point for applying the hook logic using the provided module and class loader
    fun apply(module: XposedModule, classLoader: ClassLoader, param: PackageLoadedParam)
}
