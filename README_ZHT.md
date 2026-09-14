# PixelTweaks

**版本：** v1.0.5 (穩定版)
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

## ✨ 功能特點 (v1.0.5)

### 🎨 雙擊熄屏 (Double Tap To Sleep)
- **桌面空白處**：支援在桌面空白處雙擊熄屏（已針對靈敏度優化：放寬觸控公差 `1.5f` 與 `400ms` 時間窗口）。
- **鎖定畫面區域**：支援在鎖定畫面區域雙擊熄屏。
- **狀態列**：支援在狀態列雙擊熄屏。

### 📞 Google 電話 (Google Dialer)
- **啟用通話錄音**：透過背景 DexKit 掃描解鎖 Google 電話原生通話錄音。
- **停用通話錄音語音提示**：阻擋通話錄音開始時的語音警告（採用跨語言資源 ID 與時間窗口 TTS 攔截）。
- **停用 Call Notes 語音提示**：靜音 AI 錄音與轉錄公告（與通话錄音功能互斥）。

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
- **啟用主日誌開關**：標準化、低負載的日誌系統，完整涵蓋所有 10 個功能模組與滑桿（**僅限 Debug Build**）。

## 📝 更新日誌 (Changelog)

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
- 🔓 **全模組 DE 儲存區統一載入**：全專案 10 個 Hook 模組統一改用 DE 儲存區優先查詢，徹底解決解鎖前（Direct Boot）因 FBE 加密導致 Easy Unlock 與 Quick Settings 讀不到設定的問題。
- ⚙️ **預設值對齊**：將通話錄音與 Call Notes 相關設定全數對齊為預設關閉，提供更乾淨的初次安裝體驗。

### v1.0.1
- 🔀 **功能互斥機制**：設定中通話錄音與 Call Notes 靜音無法同時啟用，以防止共用音訊管線發生衝突。
- 🔓 **輕鬆解鎖韌性**：修復 App 更新或重開機後因過期廣播導致已學習 PIN 碼長度被重置的問題。
- 🎨 **DT2S 觸控微調**：使桌面雙擊熄屏更加靈敏且易於觸發。
- 🐞 **日誌完整覆蓋**：為所有開關與滑桿補全實時日誌輸出，方便 Logcat 除錯。

## 📦 版本差異 (Editions)

| 功能 | `lite` (推薦) | `full` |
|---|:---:|:---:|
| Material 3 & 邊緣到邊緣 (Edge-to-Edge) | ✅ | ✅ |
| 清除全部按鈕 & 網路流量指示器 | ✅ | ✅ |
| 安全設定 & 輕鬆解鎖 | ✅ | ✅ |
| 雙擊熄屏 (Double Tap To Sleep) | ✅ | ✅ |
| 啟用通話錄音 | - | ✅ |
| 停用通話錄音語音提示 | - | ✅ |
| 停用 Call Notes 語音提示 | - | ✅ |
| 依賴套件大小 | 最小 | 標準 (含 DexKit) |

## 🛠️ 系統需求與安裝

- **Root + LSPosed**（或任何支援 `libxposed` API 102 的管理器）。
- **Android 17 (API 37)** 或更新版本。
- **靜態作用域 (Scope)**：系統框架 (System Framework)、電話 (Phone)、Pixel Launcher、System UI。

### 安裝步驟
1. 編譯或下載 `pixel-tweaks-<flavor>-v1.0.3-<buildType>.apk`。
2. 安裝 APK 並在 LSPosed 管理器中啟用模組。
3. 開啟 **PixelTweaks** App 一次以初始化設定（解除 Android `STOPPED` 停止狀態）。
4. **重新開機您的裝置**。

## 📦 編譯與簽名說明 (Build & Signing)

### 1. 如何編譯
您可以使用專案內附的 Windows 批次腳本一鍵編譯所有版本：
- 雙擊根目錄下的 **`Build_APKs.bat`**。
- 或在終端機執行 `./gradlew assemble`。
編譯出的 APK 將會輸出至 `app/build/outputs/apk/` 資料夾中。

### 2. 使用自定義簽名金鑰
- **Debug 版本**：自動使用您電腦本地的 debug keystore（免設定）。
- **Release 版本 (`release.jks`)**：
  1. 將您的簽名金鑰檔案 (`release.jks`) 放入 **`app/keystore/`** 資料夾中 (`app/keystore/release.jks`)。
  2. 在根目錄建立或開啟 **`local.properties`** 檔案並加入您的金鑰憑證：
     ```properties
     keystore.storePassword=your_store_password
     keystore.keyAlias=your_key_alias
     keystore.keyPassword=your_key_password
     ```
  3. 若無 `release.jks`，Release 建置會自動安全回退（Fallback）使用本地 debug 金鑰進行簽名。

## 📋 待辦事項 (To Do)

- 將通話錄音與 Call Notes 功能剝離成獨立的單一專案，以簡化程式碼結構並便於長期維護。

## 📄 授權條款 (License)

本專案採用 **GNU General Public License v3.0 (GPL-3.0)** 授權條款釋出。

詳情請參閱 [LICENSE](./LICENSE) 檔案。

## 📚 致謝與參考

- **靈感來源**：部分早期實作構想啟發自 @siavash79 與 @ElTifo 的 [PixelXpert](https://github.com/siavash79/PixelXpert)。目前實作採用與原專案完全不同的技術架構。
- **技術分析**：特別感謝 [vvb2060/CallRecording](https://github.com/vvb2060/CallRecording) 提供 Dialer 內部運作機制的深入技術解析。

### 感謝
- Android Team
- @topjohnwu (Magisk)
- @rovo89 (Xposed)
- LSPosed Team
- @luckypray (DexKit)
