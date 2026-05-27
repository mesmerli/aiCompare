# aiCompare

`aiCompare` 是一款結合極速 **Rust 差異比對引擎** 與 **Compose Desktop GUI** (Kotlin) 的高質感、高效能檔案與資料夾比對工具，底層採用 JNI/FFI (JNA) 技術進行動態對接。

本軟體以極簡黑標視覺美學為核心，提供超越傳統比對工具的智慧化對齊與過濾體驗。

---

## ✨ 核心特色

### 1. Myers 演算法與次要差異降階過濾 (Unimportant Diff Demotion)
- 基於經典 Myers 差異演算法進行初步比對。
- 內建二次過濾管線（`UnimportantDiffClassifier`），能自動過濾不影響執行邏輯的次要變更：
  - 程式碼註解（單行 `//`、多行 `/* ... */` 註解內容變更）。
  - 排版與空白字元（排版縮排、空格數量差異）。
- 將次要差異降階為 `UNIMPORTANT` 狀態（以次要藍灰著色），讓開發者能一眼專注在核心邏輯變更。

### 2. 智慧檔名模糊對齊 (Fuzzy Folder Alignment)
- 利用 Rust 的 Rayon 進行平行化目錄結構掃描與比對。
- 採用 **Sorensen-Dice 相似度演算法**，自動偵測並對齊不同檔名但相似度高於 60% 的改名檔案。

### 3. 手動拖曳對齊機制 (Drag-and-Drop)
- 使用者可直接以滑鼠拖曳左側的孤兒檔案（Left Orphan），並將其放置於右側的孤兒檔案上進行手動對齊。
- 支援流暢的微動畫視覺回饋：
  - 拖曳中的行呈現半透明效果。
  - 當滑鼠移至符合條件的右側目標時，目標行會亮起耀眼的紫色光暈，引導使用者完成對齊。

### 4. 同目錄備份檔案之自我交叉對齊機制 (Self-Cross Backup Alignment)
- 當使用者在兩側選擇比對「同一個資料夾路徑」時，系統會自動啟動此特殊管線。
- 自動解析經典備份後綴（例如 `.old`, `.bak`, `_v1`, `-copy`, ` - Copy` 等），將備份檔與其原版主檔案對齊（例如 `hello.c` ➔ `hello.c.old`）。
- 採用專屬的**低調青藍色 (`0xFF569CD6`)** 字體，並在中央軌道繪製象徵歷史回溯的**「黃色時鐘/回溯圖示 (↩)」**，雙擊即可直接進入雙欄文字比對。

### 5. 極簡黑標美學設計
- 採用深色調設計系統（精緻灰、琥珀黃、科技紫與青藍色）。
- 資料夾圖示採用由 Compose `Canvas` 即時繪製的 **斜向雙色調資料夾**，能精確呈現「子目錄內部分檔案變更、部分相同」的 Beyond Compare 經典語意。

---

## 🛠️ 技術棧

- **核心計算層 (Rust)**: 負責檔案與資料夾平行比對、Myers Diff 演算法、SHA256 哈希值計算以及 Rayon 平行化調度。
- **介面呈現層 (Kotlin / Compose Desktop)**: 基於聲明式 UI 架構的跨平台桌面端，並結合硬體加速 Canvas 繪圖。
- **橋接層 (UniFFI & JNA)**: 自動生成 FFI 綁定以在執行期動態載入 Rust DLL 連結庫。

---

## 🚀 快速建置與執行說明

### 環境需求
- **Rust Toolchain**: 確保已安裝 Cargo 與 rustc。
- **JDK 21+**: Compose Desktop 需要 JDK 21 或以上版本。（推薦使用 Android Studio 的 JBR）。

### 1. 編譯 Rust 核心庫
進入 Rust 核心庫目錄並編譯出 Release DLL 檔：
```powershell
cd rust_core
cargo build --release
```

### 2. 重新生成 Kotlin 綁定（選用）
若有修改 Rust 端的 FFI 接口，需執行以下指令來重新生成 Kotlin 綁定：
```powershell
cargo run --features=uniffi/cli --bin uniffi-bindgen generate --library target/release/compare_core.dll --language kotlin --out-dir ../src/main/kotlin/bindings
```

### 3. 執行 Compose Desktop 應用程式
回到專案根目錄，執行以下指令：
```powershell
# 設定 Java 環境變數指向您的 JBR/JDK 21 路徑
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

# 啟動應用程式
.\gradlew.bat run
```

---

## 📂 專案結構說明

- `rust_core/`: 包含 VFS 與比對核心算法的 Rust 庫。
- `src/main/kotlin/`: Compose Desktop 的 UI 與控制邏輯。
  - `bindings/uniffi/`: 自動生成的 UniFFI 橋接代碼。
- `test_data/`: 比對演算法所需的測試用資料夾與範例檔案。
- `Documents/`: 專案架構、Git 整合、效能優化與編譯發行之技術文檔。
