# PixelTweaks Commenting & Documentation Standard

This document establishes the official commenting and documentation guidelines for the PixelTweaks codebase. It serves as a reference for present and future maintainers to ensure code readability, maintainability, and educational value.

---

## 1. Core Philosophy

In PixelTweaks, comments are written from the perspective of a **Senior Professional Engineer mentoring Junior Developers**. 

- **Explain "Why", Not "What"**: Code shows *what* is happening; comments must explain *why* it was designed this way, highlighting system limitations, Xposed/LSPosed quirks, and architectural decisions.
- **No Mechanical Code Translation**: Do not write comments that merely rephrase Kotlin syntax into English.
- **Educational Value**: Highlight non-obvious Android framework internals (e.g., Direct Boot storage isolation, SystemUI Compose container hierarchies, IPC security mechanisms).

---

## 2. Public API Documentation (KDoc)

All **public classes, objects, interfaces, composables, and functions** must be documented using standard **KDoc (`/** ... */`)**.

### Requirements for KDoc:
1. **Purpose & Architecture**: Describe what the class or function does and how it fits into the broader module architecture.
2. **Parameters (`@param`)**: Document every parameter, explaining its expected values and constraints.
3. **Return Values (`@return`)**: Document return values, including edge cases (e.g., `null` on failure, empty lists).
4. **Limitations & Warnings**: Note any thread-safety considerations, process-specific restrictions, or SystemUI version constraints.

### Example KDoc Format:

```kotlin
/**
 * Loads module preferences with multi-layer fallback strategy.
 * 
 * Prioritizes LSPosed's [XposedModule.getRemotePreferences] for low-latency IPC.
 * If RemotePreferences is unavailable (e.g., during early boot before credential unlock),
 * falls back to querying the DE (Device-Protected) storage ContentProvider.
 *
 * @param module The active Xposed module instance used for RemotePreferences retrieval.
 * @param classLoader Optional target process ClassLoader required for ContentProvider calls.
 * @param packageName Optional target package name to resolve process-safe Context.
 * @return A [Bundle] containing all loaded key-value pairs, or an empty Bundle on failure.
 */
fun loadPreferences(
    module: XposedModule, 
    classLoader: ClassLoader? = null, 
    packageName: String? = null
): Bundle
```

---

## 3. Inline Comments (`//`) for Complex Logic

Use double-slash `//` comments inside function bodies for **complex logic, workarounds, and non-intuitive implementation details**.

### Guidelines for Inline Comments:
- **Design Rationale**: Explain *why* a specific approach was chosen over a naive implementation.
- **System Quirks**: Document SystemUI or Android OS version-specific quirks (e.g., Android 17 CP3 layout changes).
- **Security & Workarounds**: Detail security precautions (e.g., UID validation, anti-tapjacking) and reflection workarounds.
- **Avoid Over-commenting**: Self-explanatory standard Kotlin lines should not have inline comments.

### Good Inline Comment Examples:

```kotlin
// On Android 17 CP3A.*.*, the outer ComposeView is a UNIFIED CONTAINER for VPN + Battery + WiFi + Cellular.
// We MUST NOT set view.visibility = View.GONE on the outer ComposeView, because doing so
// hides VPN, WiFi and Cellular along with the battery!
// Battery hiding is handled inside Compose by cancelling UnifiedBatteryKt execution.
if (isAndroid17CP3) {
    return
}
```

```kotlin
// Verify sender UID against authorized App IDs (uid % 100000) to prevent unauthorized broadcast spoofing
// from third-party non-root applications attempting to manipulate module settings.
val isTrusted = senderUid > 0 && (senderAppId == 0 || senderAppId == 1000 || senderAppId == moduleAppId)
```

---

## 4. Private Functions & Internal Helpers

Private functions do **not** require verbose KDoc if their purpose is obvious from their signature and naming.

- **Obvious Helper**: No KDoc needed (e.g., `private fun dp(context: Context, value: Int): Int`).
- **Complex Private Logic**: Add KDoc with `@param` / `@return` if the private method contains non-trivial reflection, mathematical calculations, or IPC handling (e.g., `private fun isSignatureBypassActiveForInstall(): Boolean`).

---

## 5. Summary Checklist for Code Reviews

- [ ] All public classes, interfaces, and methods have KDocs.
- [ ] KDocs include `@param` and `@return` tags where applicable.
- [ ] Inline `//` comments explain *why* and *design choices*, not syntactic *what*.
- [ ] Comments are written in clear, professional English.
- [ ] Android framework and Xposed-specific constraints are documented.
