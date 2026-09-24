# PixelTweaks 更新日誌 (Change Logs)

**語言：** [English](ChangeLogs.md) | **繁體中文**

---

### v1.1.0
- 📱 **Tablet 模式與動態 DPI 計算**：新增 Tablet 模式切換開關，根據裝置螢幕物理解析度動態計算目標 DPI ($sw \ge 600dp$)，並透過純 LSPosed 系統層 `system_server` 實現免 Root 切換。
- ⚡ **效能優化與 IPC 同步機制補齊**：使用 `remember(context)` 優化 Compose 介面重繪效能、快取 `PackageManagerHook` 中的 Java 反射方法，並補齊 `ENABLE_TABLET_MODE` 於開機自癒與背景同步流程。

### v1.0.9
- 📱 **Tablet 模式與動態 DPI 計算**：新增 Tablet 模式切換開關，根據裝置螢幕物理解析度動態計算目標 DPI ($sw \ge 600dp$)，並透過純 LSPosed 系統層 `system_server` 實現免 Root 切換。
- 📱 **Tablet 模式相容性修復**：修正 `ClearAllButtonHook` 的 Fallback 備援觸發邏輯（加入 `smallestScreenWidthDp >= 600` 判斷），確保在平板模式或調低 DPI 時能準確呼叫右側中間的「清除全部」膠囊按鈕版型。

### v1.0.8
- 🎨 **Compose UI 佈局與間距微調**：優化說明文字自動換行與 16dp 安全間距、開關內建勾勾與叉叉圖示色彩、頂部 TopAppBar 高度與選單標題。
- 🧪 **ProGuard/R8 瘦身規則與測試優化**：優化 `proguard-rules.pro` 程式碼裁切規則與 R8 混淆，並擴充補全單元測試。

### v1.0.7
- 🎨 **Jetpack Compose + Material 3 全面重構**：將設定介面由傳統 XML 重構為現代化 Jetpack Compose，完美支援 Material You 與動態配色主題。
- 📂 **套件與目錄結構重構**：將所有 Hook 檔案與單元測試重新劃分至 4 大分類套件 (`security`, `interface`, `gestures`, `quicksettings`)，實現程式碼與 UI 選單 1:1 精準對齊。
- 🐞 **除錯日誌介面優化**：精簡日誌選單並明確標示各模組的 Logcat 搜尋關鍵字。

### v1.0.6
- 🚀 **專案架構簡化與構建風味統一**：移除 Lite/Full 構建風味，統一為單一 PixelTweaks 產出，顯著降低 APK 體積。
- 🗑️ **功能完全剝離**：將通話錄音與 Call Notes 功能完全移至獨立專案 **[DialerTweaks](https://github.com/hohojia886/DialerTweaks)** 維護。

### v1.0.5
- 🛡️ **IPC 安全性強化與白名單修正**：在 `RemotePrefProvider` 實作嚴格的 UID 存取控制，並將 Launcher 系列包名補齊至信任白名單中。
- ⚡ **Java 反射與高頻事件攔截效能優化**：預先快取高頻 Hook（雙擊熄屏、快速設定、截圖解除限制）中的 Java 反射欄位與方法，消除 UI 操作延遲與 GC 負擔。
- 🧹 **無效程式碼清理與 Log 重複輸出過濾**：清理無用程式碼，並對 IPC 廣播日誌進行 Debouncing 去顫過濾，提供更乾淨的 Logcat 除錯體驗。

### v1.0.4
- 🛠️ **設定儲存架構重構與自我修復**：將 LSPosed RemotePreferences 設為唯一權威來源，徹底解決安裝其他模組後設定消失或重開機被預設值覆蓋的問題。
- 🔄 **雙向同步與 Direct Boot 防護**：實作 SettingsActivity 啟動時 CE/DE 雙向自我修復與 `BootReceiver` 解鎖時同步機制，確保設定永不丟失且跨進程即時生效。

### v1.0.3
- 🐛 **LSPosed API 102 崩潰修復**：修復 `ScreenshotHook` 直接修改不可變引數清單造成的 `UnsupportedOperationException` 例外崩潰，改用合規的 `chain.proceed(args)`。
- 🛡️ **Native 庫載入安全防護**：將 `libdexkit.so` 的載入包覆於 `runCatching` 保護區塊，避免多模組並存時背景掃描線程意外中斷。
- 📶 **網速指示器與 IPC 偏好護航**：修復初次安裝時字型與輪詢週期歸零導致顯示隱形的問題，強化 SystemUI 跨進程偏好數據防護。

### v1.0.2
- 🔓 **Direct Boot DE 儲存區統一載入器**：將所有 Hook 模組改為開機初期優先經由 `RemotePrefProvider` 存取 DE 儲存區，徹底解鎖 FBE 加密下首解前的設定綁定。
- ⚙️ **預設設定重新微調**：將通話錄音與 Call Notes 預設狀態調整為關閉，保持乾淨的初始安裝體驗。

### v1.0.1
- 🔀 **功能互斥保護**：設定介面中通話錄音與 Call Notes 無法同時開啟，防止音訊衝突。
- 🔓 **Easy Unlock 修正**：修復更新或重開機後 PIN 碼長度被重置的問題。
- 🎨 **DT2S 雙擊熄屏優化**：優化雙擊觸控視窗與手勢響應。
- 🐞 **Log 覆蓋**：補齊所有開關與滑桿的除錯日誌。
