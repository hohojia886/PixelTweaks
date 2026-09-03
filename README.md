# PixelTweaks

**Version:** 1.0.0 (Master Release)
**Target:** Android 16 / 17 (Pixel), `libxposed` API 102 (LSPosed)

A professional, high-performance Xposed module tailored specifically for Google Pixel devices. This version consolidates all previous development optimizations into a stable v1.0.0 baseline.

## ✨ Features (v1.0.0)

### 🎨 UI & UX Excellence
- **Adaptive Visuals**: Precision controls for UI elements.
- **Edge-to-Edge**: Full immersive layout where the background flows behind system bars.
- **Material 3 Design**: Redesigned settings interface with Dynamic Color (Material You) support.

### 🛡️ Security & Unlock Mods
- **Allow App Downgrade**: Install older APKs over newer ones without data loss.
- **Bypass Signature**: Install modified APKs with different signatures.
- **Easy Unlock**: Automatically dismisses the keyguard when the entered PIN/Password length matches the learned pattern.
  - **Bypass Reboot Restriction**: Optional setting to allow auto-unlock immediately after system boot.
- **Safety Auto-Reset**: High-risk security bypasses automatically disable after 3 minutes for protection.
- **Unrestricted Screenshots**: Force-enable screenshots and recordings in restricted apps (Banking, Incognito).

### ⚙️ System & Interaction
- **Clear All Button**: Adds a native-style "Clear all" button to the Pixel Launcher recents screen.
- **Double Tap to Sleep**: Integrated support for double-tap gestures on Launcher Workspace, Lockscreen, and Status Bar.
- **Mobile Data Direct Toggle**: Removes the "Turn off WiFi" confirmation dialog when switching to mobile data (**Android 17**).
- **WiFi Force-Off**: Bypasses the "Pause WiFi" behavior, forcing a complete shutdown when toggled (**Android 17**).

### 🚀 Performance & Utility
- **Call Recording (Full Edition)**: Unlocks native recording in Google Dialer via background DexKit scanning and silences voice announcements.
- **Call Notes Muter (Full Edition)**: Silences AI recording and transcription announcements (Fermat/SODA/Call Notes) by intercepting media and audio track streams (**Android 14+**).
- **Network Traffic Indicator**: Real-time speed monitor in status bar with intensity-aware color syncing.
- **Professional Debugging**: Standardized, low-overhead logging system with per-module toggles (**Debug build only**).

## 📦 Editions

| Feature | `lite` | `full` |
|---|:---:|:---:|
| Material 3 & Edge-to-Edge | ✅ | ✅ |
| Clear All & Traffic Indicator | ✅ | ✅ |
| Security & Easy Unlock | ✅ | ✅ |
| Double Tap to Sleep | ✅ | ✅ |
| Call Recording Unlock | - | ✅ |
| Voice Announcement Mute | - | ✅ |
| Call Notes Muter | - | ✅ |
| Dependency Size | Minimum | Standard (DexKit) |

## 🛠️ Requirements & Installation

- **Root + LSPosed** (or any manager supporting `libxposed` API 102).
- **Android 16 (API 36)** or newer.
- **Static Scope Enforcement**: Automatically applies to `system`, SystemUI, Launcher, and Dialer.

### Install
1. Build or download `pixel-tweaks-<flavor>-v1.0.0-<buildType>.apk`.
2. Install the APK and enable in LSPosed Manager.
3. Reboot your device.

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
