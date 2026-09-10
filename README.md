# PixelTweaks

**Version:** v1.0.4 (Stable Release)
**Target:** Android 17 (Pixel), `libxposed` API 102 (LSPosed)

**Language:** **English** | [繁體中文 (Traditional Chinese)](README_ZHT.md)

A professional, high-performance Xposed module tailored specifically for Google Pixel devices.

## 📸 Preview

<p align="center">
  <a href="art/screenshot1.png" target="_blank"><img src="art/screenshot1.png" alt="Screenshot Part 1" width="200" /></a>
  &nbsp;&nbsp;
  <a href="art/screenshot2.png" target="_blank"><img src="art/screenshot2.png" alt="Screenshot Part 2" width="200" /></a>
  &nbsp;&nbsp;
  <a href="art/screenshot3.png" target="_blank"><img src="art/screenshot3.png" alt="Screenshot Part 3" width="200" /></a>
</p>
<p align="center">
  <em>(Click any image to view full size)</em>
</p>

## ✨ Features (v1.0.4)

### 🎨 Double Tap To Sleep
- **Launcher Workspace**: Integrated support for double-tap gestures on the launcher workspace to sleep (optimized with relaxed touch slop `1.5f` and `400ms` time window).
- **Lockscreen Area**: Integrated support for double-tap gestures on the lockscreen area to sleep.
- **Status Bar**: Integrated support for double-tap gestures on the status bar to sleep.

### 📞 Google Dialer
- **Enable Call Recording**: Unlocks native recording in Google Dialer via background DexKit scanning.
- **Disable Voice Announcement**: Blocks the voice warning at the start of call recording (language-agnostic resource ID & time-window TTS interception).
- **Disable Call Notes Announcement**: Silences AI recording and transcription announcements (mutually exclusive with Call Recording).

### ⚙️ Quick Settings
- **Mobile Data Direct Toggle**: Removes the confirmation dialog when switching to mobile data.
- **WiFi Force Off**: Bypasses the "Pause WiFi" behavior, forcing a complete shutdown when toggled.

### 🛡️ Security Settings
- **Allow App Downgrade**: Install older APKs over newer ones without data loss (auto-resets after 3 minutes).
- **Bypass Signature Verification**: Install modified APKs with different signatures (auto-resets after 3 minutes).
- **Easy Unlock**: Automatically dismisses the keyguard when the entered PIN/Password length matches the learned pattern (with DE storage fallback & cross-process sync protection).
- **Bypass Restriction**: Optional setting to allow auto-unlock immediately after system boot.
- **Unrestricted Screenshots**: Force-enable screenshots and recordings in restricted apps (Banking, Incognito).

### 📱 System UI Settings
- **Clear All button**: Adds a native-style "Clear all" button to the Pixel Launcher recents screen.
- **Network Traffic Indicator**: Real-time speed monitor in status bar with intensity-aware color syncing.

### 🐞 Debug & Logs
- **Enable Master Logging**: Standardized, low-overhead logging system with complete coverage across all 10 functional modules and sliders (**Debug build only**).

## 📝 Changelog

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

## 📦 Editions

| Feature | `lite` (Recommended) | `full` |
|---|:---:|:---:|
| Material 3 & Edge-to-Edge | ✅ | ✅ |
| Clear All button & Network Traffic Indicator | ✅ | ✅ |
| Security Settings & Easy Unlock | ✅ | ✅ |
| Double Tap To Sleep | ✅ | ✅ |
| Enable Call Recording | - | ✅ |
| Disable Voice Announcement | - | ✅ |
| Disable Call Notes Announcement | - | ✅ |
| Dependency Size | Minimum | Standard (DexKit) |

## 🛠️ Requirements & Installation

- **Root + LSPosed** (or any manager supporting `libxposed` API 102).
- **Android 17 (API 37)** or newer.
- **Static Scope Enforcement**: System Framework, Phone, Pixel Launcher, System UI.

### Install
1. Build or download `pixel-tweaks-<flavor>-v1.0.3-<buildType>.apk`.
2. Install the APK and enable in LSPosed Manager.
3. Open the **PixelTweaks** app once to initialize settings (clears Android `STOPPED` state).
4. Reboot your device.

## 📦 Build & Signing Instructions

### 1. How to Build
You can build all variants (`lite` / `full` x `debug` / `release`) easily using the provided batch script on Windows:
- Double-click **`Build_APKs.bat`** in the root directory.
- Or run `./gradlew assemble` from your terminal.
Built APKs will be output to `app/build/outputs/apk/`.

### 2. Using Custom Signing Keys
- **Debug Builds**: Automatically signed using your computer's local debug keystore (no setup required).
- **Release Builds (`release.jks`)**:
  1. Place your release keystore file (`release.jks`) into the **`app/keystore/`** directory (`app/keystore/release.jks`).
  2. Create or open **`local.properties`** in the root project directory and add your keystore credentials:
     ```properties
     keystore.storePassword=your_store_password
     keystore.keyAlias=your_key_alias
     keystore.keyPassword=your_key_password
     ```
  3. If `release.jks` is not present, release builds will automatically fall back to signing with your local debug key.

## 📋 To Do

- Extract Call Recording & Call Notes features into a standalone, separate project to simplify codebase structure and ease long-term maintenance.

## 📄 License

This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**.

GPL-3.0 was chosen deliberately, not because this project is a derivative work of
any GPL-licensed codebase — it isn't. It was chosen because its goals line up with
this project's own:

- Anyone can freely use, study, modify, and redistribute this code.
- Anyone is welcome to fork and continue maintaining this project if I ever stop.
- Any distributed modified version must also be released as open source under
  GPL-3.0 — this is the mechanism that keeps the project from being repackaged
  into a closed-source commercial product. This project is intended to remain
  free, both as in "freedom" and as in "no cost," and GPL-3.0's copyleft clause
  is what makes that durable even if I'm no longer the one maintaining it.

See [LICENSE](./LICENSE) for the full text.

## 📚 Credits & Acknowledgments

- **Inspiration**: Some early implementation ideas were inspired by
  [PixelXpert](https://github.com/siavash79/PixelXpert) by @siavash79 & @ElTifo.
  The current implementations use different technical approaches from the
  original project.
- **Technical Analysis**: Special thanks to
  [vvb2060/CallRecording](https://github.com/vvb2060/CallRecording) for the
  in-depth technical breakdown of Dialer internals.

### Thanks
- **Android Team**
- **@topjohnwu** for Magisk
- **@rovo89** for Xposed
- **LSPosed Team**
- **@luckypray** for DexKit
