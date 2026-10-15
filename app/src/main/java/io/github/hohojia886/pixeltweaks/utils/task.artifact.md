# Tasks

- `[x]` Create `SystemVersion.kt` utility object with CP2A, CP3A, and CD1A detection
- `[/]` Update `HookUtils.kt` to delegate `isQsDataFixSupported()` to `SystemVersion.isCp2a()`
- `[ ]` Update `SettingsScreen.kt` to use `SystemVersion.display`
- `[ ]` Update unit tests in `HookUtilsTest.kt` and `QuickSettingsHookTest.kt`
- `[ ]` Verify build and unit tests (`app:compileDebugKotlin` and `app:testDebugUnitTest`)
