package io.github.hohojia886.pixeltweaks.hooks

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * PixelHook: Standard interface for modular Xposed feature implementations.
 *
 * Provides a unified abstraction for process-matching logic and entry-point execution.
 * Each distinct feature (e.g. EnergyRing, DoubleTapToSleep) implements this interface
 * to allow dynamic dispatching inside [MainHook].
 */
interface PixelHook {
    /** Unique string identifier used for logging and debugging purposes. */
    val name: String

    /**
     * Determines whether this hook should be applied to the target package/process.
     *
     * @param packageName The package name of the process currently being loaded by LSPosed.
     * @param isRootSystemServer True if the current process is the Android system_server (UID 1000/root).
     * @return True if the feature should execute in this process context; false otherwise.
     */
    fun matches(packageName: String, isRootSystemServer: Boolean): Boolean

    /**
     * Main execution entry point for applying reflection hooks and setup logic.
     *
     * @param module The active LSPosed [XposedModule] instance providing hook API primitives.
     * @param classLoader The target process's default [ClassLoader] used to resolve framework classes.
     * @param param Detailed parameters provided by LSPosed for the loaded package.
     */
    fun apply(module: XposedModule, classLoader: ClassLoader, param: PackageLoadedParam)
}
