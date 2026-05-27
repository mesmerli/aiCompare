# BeyondDiff 建置與執行說明 (Build & Run Guide)

本專案是一個結合 **Rust 核心庫** 與 **Compose Desktop GUI** 的高硬度檔案比對與合併工具。以下為在 Windows 系統下的建置與執行步驟。

---

## 🛠️ 環境需求 (Prerequisites)

1.  **Rust Toolchain**
    *   請確保系統已安裝 Rust (包含 `cargo` 與 `rustc`)。
    *   若未安裝，請透過 [rustup.rs](https://rustup.rs/) 安裝。
2.  **Java Development Kit (JDK) 21+**
    *   Compose Desktop 需要 JDK 21 或以上版本。
    *   建議使用 Android Studio 內建的 JBR (JetBrains Runtime)：
        `C:\Program Files\Android\Android Studio\jbr`

---

## 🚀 建置與執行步驟

### 第一步：設定 Java 環境變數 (PowerShell)

在執行任何 Gradle 指令前，請在 PowerShell 終端機設定 `JAVA_HOME` 指向您的 JDK/JBR 路徑：

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

---

### 第二步：編譯 Rust 核心庫 (rust_core)

專案底下的 `rust_core` 為原生 Rust 庫，需要先編譯出 release 版本的動態連結檔 (`.dll`)：

```powershell
cd rust_core
cargo build --release
```

> [!TIP]
> **Windows 檔案鎖定衝突排除：**
> 如果您在 VS Code / IntelliJ / Rust Analyzer 開啟專案時編譯遇到 `os error 32` (檔案由另一個程序使用)，這是因為 IDE 背景掃描鎖定了暫存檔。請使用以下指令進行單線程獨立編譯：
> 
> ```powershell
> # 設定獨立輸出目錄，使用單執行緒編譯，並將產出的 DLL 複製回 target/release
> $env:CARGO_TARGET_DIR = "target_build"
> cargo build --release --jobs 1
> New-Item -ItemType Directory -Force target/release
> Copy-Item target_build/release/compare_core.dll target/release/compare_core.dll -Force
> ```

編譯完成後，返回專案根目錄：
```powershell
cd ..
```

---

### 第三步：執行 Compose Desktop 應用程式

Gradle 會在建置時自動執行 `copyRustLib` 任務，將剛才編譯好的 Rust 連結庫複製到 Compose Desktop 的資源目錄中：

```powershell
# 執行應用程式
.\gradlew.bat run
```

---

### 📦 封裝與產出執行檔 (Packaging)

若您想產出不需依賴 Gradle 即可獨立執行的專案執行檔或安裝包，請執行以下步驟：

#### 1. 產出綠色免安裝版 (Standalone Folder)
執行以下指令會將應用程式、依賴庫及精簡版 JRE 封裝在同一個資料夾中：
```powershell
.\gradlew.bat createDistributable
```
*   **產出路徑**：`build\compose\binaries\main\app\theCompare\`
*   **執行檔**：在該資料夾底下的 `theCompare.exe` 即為綠色執行檔。

#### 2. 產出安裝套件 (Installer)
執行以下指令會編譯並封裝出系統原生的安裝套件（Windows 平台預設為 `.msi`）：
```powershell
.\gradlew.bat packageDistribution
```
*   **產出路徑**：`build\compose\binaries\main\msi\`
*   **安裝檔**：`theCompare-1.0.0.msi`

> [!NOTE]
> 專案根目錄下的 `my-diff.exe` 是一個用 Rust 撰寫的命令列啟動器 (CLI Launcher)，它會負責偵測並呼叫本機 Gradle 或已封裝的 `theCompare.exe`。

---

## 🧹 清除建置快取 (Clean)

若您需要完整清除快取並重新建置，請執行：

```powershell
# 清除 Rust 建置快取
cd rust_core
cargo clean
cd ..

# 清除 Gradle 建置快取
.\gradlew.bat clean
```
