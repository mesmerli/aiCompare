# Rust 核心層整合與 UniFFI 跨平台綁定指南

本指南詳細說明如何編譯 [`rust_core`](file:///c:/Users/mesme/workspace/theCompare/rust_core) 中的 Rust 核心代碼、生成 Kotlin 綁定，以及如何將生成的動態連結庫（`.dll`、`.dylib`、`.so`）整合進 Compose Multiplatform 專案中。

---

## 1. Rust 核心層的編譯與 bindings 生成

當您在系統上安裝 Rust 後，請依照以下步驟編譯 Rust 核心層並生成 UniFFI 的 Kotlin 檔案：

### 1.1 編譯 Rust 動態庫
切換至 `rust_core` 目錄並執行 release 編譯：
```bash
cd rust_core
cargo build --release
```
編譯完成後，會在以下路徑生成動態程式庫：
- **Windows**: `rust_core/target/release/compare_core.dll`
- **macOS**: `rust_core/target/release/libcompare_core.dylib`
- **Linux**: `rust_core/target/release/libcompare_core.so`

### 1.2 使用 UniFFI 生成 Kotlin 綁定代碼
UniFFI 提供了一個命令列工具 `uniffi-bindgen`。您可以使用 Cargo 直接執行它來生成 Kotlin 代碼：
```bash
# 安裝與執行 uniffi-bindgen
cargo run --features=uniffi/cli --bin uniffi-bindgen generate src/lib.rs --language kotlin --out-dir ../src/main/kotlin/bindings
```
這會在專案的 `src/main/kotlin/bindings` 底下自動生成兩個檔案：
1. `compare_core.kt`（包含所有 Kotlin 端的類別、列舉、與呼叫 Rust 的底層 JNI/JNA 對接代碼）。
2. 對應的 C 標頭檔或二進位對接中繼資料。

---

## 2. Kotlin 端的對接與呼叫範例

在 UniFFI 生成 `compare_core.kt` 後，您可以直接在 Compose 專案中呼叫它。

以下是 Kotlin 端如何修改既有的 `DiffViewModel` 以呼叫 Rust 引擎的程式碼範例：

```kotlin
package com.example.compare

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 匯入由 UniFFI 自動產生的 Rust 綁定命名空間
import uniffi.compare_core.DiffEngine
import uniffi.compare_core.AlgorithmType as RustAlgoType
import uniffi.compare_core.DiffStatus as RustDiffStatus

class DiffViewModel {
    // 實例化 Rust 匯出的核心引擎
    private val rustEngine = DiffEngine()

    var diffLines by mutableStateOf<List<DiffRowData>>(emptyList())
        private set

    var currentAlgorithm by mutableStateOf(AlgorithmType.MYERS)
        private set

    // 非同步呼叫 Rust 引擎比對檔案
    suspend fun compareFiles(pathA: String, pathB: String, algo: AlgorithmType) = withContext(Dispatchers.IO) {
        currentAlgorithm = algo
        
        // 1. 設定演算法類型
        val rustAlgo = when (algo) {
            AlgorithmType.MYERS -> RustAlgoType.MYERS
            AlgorithmType.PATIENCE -> RustAlgoType.SIMPLE_LINEAR
        }
        rustEngine.setAlgorithm(rustAlgo)

        try {
            // 2. 呼叫 Rust 進行檔案比對 (磁碟 I/O 與 CPU 密集計算在 Rust 執行)
            val rustResults = rustEngine.compareFiles(pathA, pathB)

            // 3. 將 Rust 的資料模型對應為 Compose UI 所需的 DiffRowData
            diffLines = rustResults.map { line ->
                DiffRowData(
                    leftLineNum = line.leftLineNum?.toInt(),
                    leftText = line.leftText,
                    rightLineNum = line.rightLineNum?.toInt(),
                    rightText = line.rightText,
                    status = when (line.status) {
                        RustDiffStatus.UNCHANGED -> LineStatus.UNCHANGED
                        RustDiffStatus.ADDED -> LineStatus.ADDED
                        RustDiffStatus.DELETED -> LineStatus.DELETED
                        RustDiffStatus.MODIFIED -> LineStatus.MODIFIED
                    }
                )
            }
        } catch (e: Exception) {
            println("呼叫 Rust 核心失敗: ${e.message}")
        }
    }
}
```

---

## 3. 將動態連結庫整合進 Compose Multiplatform 專案

在 Compose Desktop (JVM) 中，Java 需要載入 Rust 編譯出的動態連結庫。我們有兩種整合方案：

### 方案 A：手動放置於系統庫路徑（開發期最快）
在執行 JVM 時，透過系統屬性指定 `java.library.path`：
1. 將編譯好的 `compare_core.dll`（或 `.dylib` / `.so`）複製到專案根目錄下的 `libs/` 目錄。
2. 修改 `build.gradle.kts` 中的 `run` 任務：
   ```kotlin
   tasks.withType<org.jetbrains.kotlin.gradle.tasks.JavaExec> {
       systemProperty("java.library.path", file("libs/").absolutePath)
   }
   ```

### 方案 B：打包進 JAR 資源目錄（正式發布與打包）
為了使打包後的應用程式能在沒有安裝 Rust 的電腦上執行，動態連結庫必須作為資源打包進 JAR 檔案中：
1. 將動態庫放到對應平台的資源目錄：
   - Windows: `src/main/resources/win32-x86-64/compare_core.dll`
   - macOS: `src/main/resources/darwin-aarch64/libcompare_core.dylib` 或 `darwin-x86-64/`
   - Linux: `src/main/resources/linux-x86-64/libcompare_core.so`
2. **UniFFI 底層會自動透過 JNA (Java Native Access) 的資源加載器**，根據目前執行的作業系統架構，自動從 JAR 的 Resources 中解壓並載入對應的動態連結庫，不需要寫任何手動載入邏輯。

---

## 4. 效能優化建議

1. **Rayon 並行化處理**：
   比對大型專案或整個資料夾時，可在 Rust 側使用 `rayon`：
   ```rust
   // 範例：並行處理多個檔案比對
   use rayon::prelude::*;
   let results: Vec<FileDiff> = file_pairs.par_iter().map(|(a, b)| {
       // 自動在 Thread Pool 中多執行緒並行計算
       engine.compare_files(a, b)
   }).collect();
   ```
2. **I/O 與線程切換**：
   在 Kotlin 端，請一律使用 `withContext(Dispatchers.IO)` 呼叫 Rust FFI 函式，以確保檔案讀取與比對演算法不會阻塞 Compose Desktop 的 UI 主執行緒（Main UI Thread），從而獲得極致順暢的流暢體驗。
