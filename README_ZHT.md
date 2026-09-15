# PixelTweaks

**版本：** v1.0.6 (穩定版)  
**目標：** Android 17 (Pixel), `libxposed` API 102 (LSPosed)

專為 Google Pixel 裝置量身打造的高效能專業 Xposed 模組。

---

[English README](README.md) | **繁體中文說明**

## 📸 預覽圖

<p align="center">
  <a href="art/screenshot1.png" target="_blank"><img src="art/screenshot1.png" alt="Screenshot Part 1" width="200" /></a>
  &nbsp;&nbsp;
  <a href="art/screenshot2.png" target="_blank"><img src="art/screenshot2.png" alt="Screenshot Part 2" width="200" /></a>
  &nbsp;&nbsp;
  <a href="art/screenshot3.png" target="_blank"><img src="art/screenshot3.png" alt="Screenshot Part 3" width="200" /></a>
</p>
<p align="center">
  <em>(點擊任意圖片檢視原始大小)</em>
</p>

## ✨ 功能特點 (v1.0.6)

### 🎨 雙擊熄屏 (Double Tap To Sleep)
- **桌面空白處**：支援在桌面空白處雙擊熄屏（已針對靈敏度優化：放寬觸控公差 `1.5f` 與 `400ms` 時間窗口）。
- **鎖定畫面區域**：支援在鎖定畫面區域雙擊熄屏。
- **狀態列**：支援在狀態列雙擊熄屏。

### ⚙️ 快捷設定 (Quick Settings)
- **行動數據直接切換**：切換行動數據時直接生效，移除確認對話框。
- **強制關閉 Wi-Fi**：繞過「暫停 Wi-Fi」行為，切換時強制完全關閉。

### 🛡️ 安全設定 (Security Settings)
- **允許應用降級**：在不遺失資料的情況下覆蓋安裝較舊版本的 APK（3 分鐘後自動重設）。
- **繞過簽名驗證**：允許安裝具備不同簽名的修改版 APK（3 分鐘後自動重設）。
- **輕鬆解鎖 (Easy Unlock)**：當輸入的 PIN 碼/密碼長度符合已學習的模式時，自動確認解鎖（具備 DE 儲存備援與跨進程同步保護）。
- **重開機繞過限制**：選擇性設定，允許開機後立即自動解鎖。
- **解除截圖限制**：強制在受限制的應用程式（如銀行 App、無痕視窗）中啟用截圖與錄影。

### 📱 系統介面設定 (System UI Settings)
- **清除全部按鈕**：在 Pixel Launcher 的近期任務（Recents）畫面中新增原生風格的「清除全部」按鈕。
- **網路流量指示器**：狀態列即時網速監控，支援隨狀態列主題自動調整色彩。

### 🐞 偵錯與日誌 (Debug & Logs)
- **啟用主日誌開關**：標準化、低負載的日誌系統，完整涵蓋所有功能模組與滑桿（**僅限 Debug Build**）。

## 📝 更新日誌 (Changelog)

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
- 🎨 **雙擊熄屏優化**：優化雙擊觸控視窗與手勢響應。
- 🐞 **Log 覆蓋**：補齊所有開關與滑桿的除錯日誌。

## 🛠️ 需求與安裝

- **Root + LSPosed**（或任何支援 `libxposed` API 102 的框架管理器）。
- **Android 17 (API 37)** 或更高版本。
- **作用域**：System Framework, Pixel Launcher, System UI。

### 安裝步驟
1. 編譯或下載 `pixel-tweaks-v1.0.6-<buildType>.apk`。
2. 安裝 APK 並在 LSPosed 管理器中啟用模組。
3. 開啟 **PixelTweaks** App 一次以初始化設定（消除 Android 的 `STOPPED` 狀態）。
4. 重新啟動您的裝置。

## 📦 編譯與簽名說明

### 1. 如何編譯
您可以在 Windows 上使用提供的批次檔輕鬆編譯所有變體 (`debug` / `release`)：
- 雙擊專案根目錄下的 **`Build_APKs.bat`**。
- 或在終端機中執行 `./gradlew assemble`。
編譯完成的 APK 將輸出至 `app/build/outputs/apk/`。

### 2. 使用自訂簽名金鑰
- **Debug Builds**：自動使用電腦本地的 debug 金鑰進行簽名（無需設定）。
- **Release Builds (`release.jks`)**：
  1. Place your release keystore file (`release.jks`) into the **`app/keystore/`** directory (`app/keystore/release.jks`).
  2. Create or open **`local.properties`** in the root project directory and add your keystore credentials:
     ```properties
     keystore.storePassword=your_store_password
     keystore.keyAlias=your_key_alias
     keystore.keyPassword=your_key_password
     ```
  3. If `release.jks` is not present, release builds will automatically fall back to signing with your local debug key.

## 📄 License

本專案採用 **GNU General Public License v3.0 (GPL-3.0)** 授權條款釋出。

詳情請參閱 [LICENSE](./LICENSE) 檔案。

## 📚 相關專案與致謝

- **[DialerTweaks](https://github.com/hohojia886/DialerTweaks)**：專為 Google Dialer 通話錄音與 Call Notes 語音提示打造的獨立 Xposed 模組。
- **靈感來源**：部分早期實作構想啟發自 @siavash79 與 @ElTifo 的 [PixelXpert](https://github.com/siavash79/PixelXpert)。目前實作採用與原專案完全不同的技術架構。

### 感謝
- Android Team
- @topjohnwu (Magisk)
- @rovo89 (Xposed)
- LSPosed Team
