# PixelTweaks Logging & Logcat Standardization Guidelines

This document defines the official logging rules and standards for the PixelTweaks codebase. It ensures log consistency, privacy, performance, and compatibility with Logcat analysis tools.

---

## 1. Core Rules

1. **100% English Language Requirement**:
   - Every log message, status bracket, error context, and exception explanation MUST be written in clear, concise English.
2. **Mandatory Tag Prefix**:
   - All log messages dispatched through [Logger] automatically prepend `PXTK_` to the specified tag string (e.g., `Logger.i("Security", ...)` outputs logcat tag `PXTK_Security`).
3. **Structured Payload Format (`[Status] Message`)**:
   - Log payloads MUST follow the status bracket convention: `Logger.x(TAG, "Status", "Message")`.
   - The status tag string is automatically enclosed in brackets `[Status]` in logcat output.
4. **No Silent Exception Swallowing**:
   - All `runCatching { ... }.onFailure { e -> ... }` and `try { ... } catch (e: Exception) { ... }` blocks MUST log the failure via `Logger.e(TAG, "Error", "Failure message context", e)`. Never leave failure handlers silent.
5. **Debouncing High-Frequency Events**:
   - High-frequency loops (e.g., 30 FPS drawing, continuous byte polling) MUST NOT print repetitive log messages on every iteration. Use debouncing timers (minimum 2000ms interval).

---

## 2. Standardized Category Tags

| Category Tag | Functional Area | Enabled Sub-toggle |
| :--- | :--- | :--- |
| `Security` | Package Manager, Downgrade, Signature Bypasses | `logSecurity` |
| `EasyUnlock` | Auto PIN Confirm & PIN Length Learning | `logSecurity` |
| `Screenshot` | System-wide FLAG_SECURE Bypass | `logSecurity` |
| `HideVpn` | Status Bar VPN Icon Hiding | `logSecurity` / `logInterface` |
| `ClearAll` | Recents Overview "Clear All" Button | `logInterface` |
| `Traffic` | Network Traffic Speed Indicator | `logInterface` |
| `Battery` | Status Bar Text Battery & Sysfs Battery Info | `logInterface` |
| `EnergyRing` | Camera Cutout Battery Energy Ring Overlay | `logInterface` |
| `DT2S` | Double Tap To Sleep Gestures | `logGestures` |
| `QuickSettings` | QS Tile Fixes (WiFi & Mobile Data) | `logQuickSettings` |
| `Hook` | Module Loading & Reflection Dispatching | Core (`isMasterEnabled`) |
| `IpcManager` / `Dispatcher` | IPC Registration & Broadcast Dispatching | Core (`isMasterEnabled`) |
| `BootReceiver` | System Boot & Direct Boot Self-Healing Sync | Core (`isMasterEnabled`) |
| `Density` | System Density & Tablet Mode Calculations | Core (`isMasterEnabled`) |

---

## 3. Standardized Status Bracket Keywords

When calling `Logger.x(TAG, "Status", "Message")`, use one of the following standardized status keywords:

- `[Init]` / `[Started]` — Module, hook, or receiver initialization.
- `[Sync]` — Preference loading or IPC broadcast synchronization.
- `[Active]` / `[Running]` / `[Applied]` — Dynamic feature activation or hook interception.
- `[Success]` — Successful operation completion or bypass execution.
- `[Blocked]` — Unauthorized IPC attempt or disallowed write key blocked.
- `[Error]` / `[SectionA]` / `[SectionB]` — Exception or failure context.

---

## 4. API Usage Examples

```kotlin
// Info level log with status bracket
Logger.i("Security", "Active", "Bypassed version check for package: $pkg")

// Debug level log with status bracket
Logger.d("Traffic", "Running", "Updated traffic view speeds: TX=$txText, RX=$rxText")

// Warning level log
Logger.w("EasyUnlock", "Blocked", "Incorrect PIN attempt during learning mode")

// Error level log with exception stacktrace
Logger.e("QuickSettings", "Error", "Failed to hook Dialog.show method", e)
```
