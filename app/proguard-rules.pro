# Minimum protection to test R8
-keepattributes Signature,Exceptions,*Annotation*

# Keep Xposed module entry point
-keep class io.github.hohojia886.pixeltweaks.MainHook { *; }

# Keep Provider and Utils for stable IPC
-keep class io.github.hohojia886.pixeltweaks.providers.** { *; }
-keep class io.github.hohojia886.pixeltweaks.utils.** { *; }

# Keep BuildConfig to avoid issues with product flavor checks
-keep class io.github.hohojia886.pixeltweaks.BuildConfig { *; }

# Keep native methods for DexKit
-keepclasseswithmembernames class * {
    native <methods>;
}

# General Material/AndroidX rules (Library handles most)
-dontwarn com.google.android.material.**
-dontwarn androidx.**
