# PixelTweaks Change Logs

**Language:** **English** | [繁體中文 (Traditional Chinese)](CHANGELOG_ZHT.md)

---

### v1.1.0
- 🔋 **Battery Info Suite & Real-Time Monitoring**: Added on-demand Battery Info card (requires Root) featuring 16 advanced hardware and real-time battery metrics (instant wattage W, voltage V, current mA, stored charge mAh, charge cycles, health capacity/impedance scores, temperature °C, internal resistance mΩ, serial number, manufacture date & age, and system max charging limits) with a 2s foreground auto-refresh timer and clean two-line layout.
- 📱 **Tablet Mode & Dynamic Density**: Added Tablet Mode toggle that dynamically calculates the required display density ($\text{sw} \ge 600\text{dp}$) from screen physical resolution and applies it via LSPosed system server integration.
- ⚡ **Performance & Synchronization Optimizations**: Optimized Compose UI recomposition performance with `remember(context)`, cached reflection methods in `PackageManagerHook`, and aligned `ENABLE_TABLET_MODE` IPC synchronization across boot and pause events.

### v1.0.9
- 📱 **Tablet Mode & Dynamic DPI Calculation**: Added Tablet Mode toggle with dynamic DPI calculation based on screen physical resolution ($sw \ge 600dp$) and pure LSPosed system server integration.
- 📱 **Tablet Mode Fallback Fix**: Refined `ClearAllButtonHook`'s fallback trigger logic (adding `smallestScreenWidthDp >= 600` detection) to ensure tablet mode or lowered DPI setups reliably invoke the floating right-center "Clear all" button layout.

### v1.0.8
- 🎨 **Compose UI Layout & Spacing Polish**: Refined switch summary text constraints (auto-wrapping, 16dp spacing), switch thumb check/cross icons and colors, top app bar height, and title wording.
- 🧪 **ProGuard/R8 Rules & Test Suite Optimization**: Optimized R8 keep rules (`proguard-rules.pro`) for Compose minification and expanded unit test validations.

### v1.0.7
- 🎨 **Jetpack Compose + Material 3 Refactoring**: Upgraded the Settings UI from legacy XML layouts to modern Jetpack Compose with full Material You / Material 3 dynamic color support.
- 📂 **Package & Directory Alignment**: Restructured all Kotlin hook files into 4 clean package categories (`security`, `interface`, `gestures`, `quicksettings`) matching the 1:1 structure of the UI.
- 🐞 **Debug Logging Streamlining**: Organized debug logging options in UI with clear Logcat tag search identifiers.

### v1.0.6
- 🚀 **Simplified Architecture & Flavor Unification**: Removed Full/Lite build variants into a unified PixelTweaks build with reduced APK size.
- 🗑️ **Feature Deprecation**: Completely extracted and removed Call Recording & Call Notes features into [DialerTweaks](https://github.com/hohojia886/DialerTweaks).

### v1.0.5
- 🛡️ **IPC Security & Whitelist Enforcement**: Enforced strict UID access controls in `RemotePrefProvider` and updated trusted package whitelists for launcher components.
- ⚡ **Reflection & Event Interception Performance Optimizations**: Cached Java reflection fields/methods in high-frequency hooks (DoubleTapToSleep, QuickSettings, ScreenshotHook) to eliminate UI latency and reduce GC overhead.
- 🧹 **Dead Code Removal & Log Debouncing**: Cleaned up uncalled code blocks and debounced duplicated IPC broadcast log outputs for a cleaner Logcat experience.

### v1.0.4
- 🛠️ **Settings Persistence & Self-Healing Architecture**: Refactored preference storage to establish LSPosed RemotePreferences as the primary source of truth, resolving preference loss and boot override issues when installing other modules.
- 🔄 **Bidirectional Sync & Direct Boot Protection**: Implemented robust startup CE/DE self-healing and `ACTION_USER_UNLOCKED` synchronization to ensure user settings are never overwritten and sync instantly across processes.

### v1.0.3
- 🐛 **LSPosed API 102 Exception Fix**: Resolved an `UnsupportedOperationException` in `ScreenshotHook` by replacing immutable argument list mutations with standard `chain.proceed(args)` calls.
- 🛡️ **Native Library Load Protection**: Wrapped DexKit native library loading (`libdexkit.so`) in `runCatching` safety blocks to prevent thread crashes in multi-module environments.
- 📶 **Network Traffic & IPC Guard**: Fixed zero-value defaults for traffic font size/polling interval and improved SystemUI IPC preference fallback reliability across initial installs.

### v1.0.2
- 🔓 **Direct Boot DE Storage Unified Loader**: Unified all 10 hook modules to prioritize reading settings directly from DE (Device-Protected) storage via `RemotePrefProvider` on early boot, completely resolving FBE encryption barriers prior to the first unlock.
- ⚙️ **Default Settings Realignment**: Realigned Call Recording and Call Notes default states to OFF for a cleaner initial installation experience.

### v1.0.1
- 🔀 **Feature Mutual Exclusion**: Call Recording and Call Notes cannot be enabled at the same time in Settings to prevent audio conflict.
- 🔓 **Easy Unlock Fix**: Fixed PIN length reset issues after app updates or reboots by adding robust DE storage fallback queries.
- 🎨 **DT2S Tuning**: Made double-tap to sleep more responsive and easier to trigger on the launcher.
- 🐞 **Log Coverage**: Added complete log output for all switches and sliders.
