# PixelTweaks

<p align="center">
  <img src="https://img.shields.io/badge/Android-17%20(API%2037)-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Compose" />
  <img src="https://img.shields.io/badge/Architecture-MVVM%20%2B%20UDF-FF6F00?style=for-the-badge&logo=android&logoColor=white" alt="Architecture" />
  <img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=for-the-badge" alt="License" />
</p>

**版本：** v1.2.6 (穩定版)  
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

## ✨ 功能特點 (v1.2.6)

### 🎨 雙擊熄屏 (Double Tap To Sleep)
- **雙擊桌面熄屏**：支援在桌面空白處雙擊熄屏（已針對靈敏度優化：放寬觸控公差 `1.5f` 與 `400ms` 時間窗口）。
- **雙擊鎖定畫面熄屏**：支援在鎖定畫面區域雙擊熄屏。
- **雙擊狀態列熄屏**：支援在狀態列雙擊熄屏。

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
- **前鏡頭環形電量光環**：圍繞前置鏡頭挖孔 (`DisplayCutout`) 的動態電量光環，支援三段式電量色彩變化 (<=20% 黃色, <=10% 紅色, >20% 青綠色)、充電 3 秒 HSV 動態彩虹漸層脈衝動畫、系統省電模式橘黃色彩連動 (`#FF9500`)、隨時間平滑旋轉、大小與線條粗細微調滑桿、「僅在充電時顯示」選項及螢幕熄滅自動暫停繪製省電。
- **狀態欄右側電量百分比**：於狀態列最右側顯示純文字電量百分比（如 "100%"），自動隱藏 Android 17 原廠電池圖示，並動態同頻對齊狀態列時鐘字體樣式、主題配色與充電脈衝漸層。
- **清除全部按鈕**：在 Pixel Launcher 的近期任務（Recents）畫面中新增原生風格的「清除全部」按鈕。
- **Tablet 模式**：動態計算並切換全系統平板模式版型 (`sw >= 600dp`)，透過純 LSPosed 系統層切換，免 Root。
- **網路流量指示器**：狀態列即時網速監控，支援隨狀態列主題自動調整色彩。

### 🔋 電池資訊 (Battery Info)
- **顯示電池資訊**：手動開關，展出 18 項進階硬體與即時電池數據（包含即時瓦數 W、電壓 V、電流 mA、充電次數、計算預估容量健康度、晶片容量與阻抗健康度評分、溫度 °C、內阻 mΩ、生產日期與年齡、電池序號及當前最高充電限制），支援前台 2 秒自動定時刷新（**需 Root 權限**）。

### 🌐 多國語言切換
- **多國語系支援**：採用 Android 13+ 原生 Per-App Language 機制，支援全球 18 種 Pixel 販售市場語系與可滾動的單選對話框選單。

### 🐞 偵錯與日誌 (Debug & Logs)
- **啟用主日誌開關**：標準化、低負載的日誌系統，完整涵蓋所有功能模組（**僅限 Debug Build**）。

## 🏗️ 專案架構 (v1.2.6)

PixelTweaks 採用現代化 **MVVM + 單向資料流 (UDF)** 架構建構：
- **`presentation/`**：`SettingsUiState` (不可變 State)、`SettingsEvent` (Sealed Event 意圖)、`SettingsViewModel` (StateFlow 與協程定時器)。
- **`data/repository/`**：`SettingsRepository` (負責 CE/DE SharedPreferences 持久化與 IPC 廣播)。
- **`ui/`**：`SettingsActivity` 與 `SettingsScreen` (純粹 Compose View 視圖層)。

## 📝 更新日誌 (Changelog)

請參閱 [CHANGELOG_ZHT.md](CHANGELOG_ZHT.md) 檢視完整的版本更新歷史紀錄與詳細變更說明。

## 🛠️ 需求與安裝

- **Root + LSPosed**（或任何支援 `libxposed` API 102 的框架管理器）。
- **Android 17 (API 37)** 或更高版本。
- **作用域**：System Framework, Pixel Launcher, System UI。

### 安裝步驟
1. 編譯或下載 `pixel-tweaks-v1.2.6-<buildType>.apk`。
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
