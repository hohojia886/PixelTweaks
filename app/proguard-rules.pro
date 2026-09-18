# Minimum protection to test R8
-keepattributes Signature,Exceptions,*Annotation*,InnerClasses,EnclosingMethod

# Keep LibXposed API & Service
-keep class io.github.libxposed.** { *; }
-dontwarn io.github.libxposed.**

# Keep PixelTweaks module entry point, Hooks, UI, Providers, Receivers, and Utilities
-keep class io.github.hohojia886.pixeltweaks.MainHook { *; }
-keep class io.github.hohojia886.pixeltweaks.hooks.** { *; }
-keep class io.github.hohojia886.pixeltweaks.providers.** { *; }
-keep class io.github.hohojia886.pixeltweaks.receivers.** { *; }
-keep class io.github.hohojia886.pixeltweaks.ui.** { *; }
-keep class io.github.hohojia886.pixeltweaks.utils.** { *; }
-keep class io.github.hohojia886.pixeltweaks.BuildConfig { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Material Design & AndroidX Compose
-dontwarn com.google.android.material.**
-dontwarn androidx.**
