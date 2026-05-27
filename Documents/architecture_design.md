# Side-by-Side Diff Viewer 架構設計說明書

此文件詳細說明了 **BeyondDiff** 雙欄程式碼比對檢視器（Side-by-Side Diff Viewer）的架構設計、狀態管理、UI 效能優化考量，以及未來與 Rust 底層演算法引擎（透過 UniFFI）對接的規劃。

---

## 1. 系統架構與模組設計 (System Architecture)

為了確保未來能無縫抽換或接入 Rust 實作的比對演算法，本原型在架構上採用 **MVVM**（Model-View-ViewModel）模式，並將 **資料合約（UI Contract）** 與 **演算法邏輯** 完全解耦。

```mermaid
graph TD
    subgraph UI 呈現層 (Compose Desktop)
        View[SideBySideDiffViewer] -->|觀察狀態| VM[DiffViewModel]
        View -->|調用函數| VM
    end

    subgraph 狀態控制層 (State Management)
        VM -->|管理數據| DiffLines[diffLines: List of DiffRowData]
        VM -->|切換演算法| changeAlgorithm[changeAlgorithm]
    end

    subgraph 核心算法層 (Future Rust Engine via UniFFI)
        VM -->|橋接合約| EngineBridge[Engine Bridge]
        EngineBridge -->|調用| RustMyers[Rust Myers Engine]
        EngineBridge -->|調用| RustPatience[Rust Patience Engine]
    end
```

---

## 2. 核心資料結構與合約 (Core Data Contract)

在 `Main.kt` 中定義的資料結構是 UI 與比對引擎之間的橋樑：

### 2.1 行狀態 (LineStatus)
定義了單行程式碼的異動狀態：
- `UNCHANGED`: 左右兩側對應行無異動。
- `ADDED`: 右側（新檔）新增行，左側（舊檔）為空。
- `DELETED`: 左側（舊檔）刪除行，右側（新檔）為空。
- `MODIFIED`: 左右兩側對應行有內容修改。

### 2.2 雙欄列資料 (DiffRowData)
代表畫面上一橫列的資料。這也是維持左右對齊的關鍵結構：
```kotlin
data class DiffRowData(
    val leftLineNum: Int?,  // 左側行號，若為空行則為 null
    val leftText: String,   // 左側代碼，若為空行則為空字串 ""
    val rightLineNum: Int?, // 右側行號，若為空行則為 null
    val rightText: String,  // 右側代碼，若為空行則為空字串 ""
    val status: LineStatus  // 該行的比對狀態
)
```

---

## 3. UI 元件與效能優化 (UI & Performance Optimizations)

比對檢視器常需要載入數萬行的程式碼，因此流暢度與對齊是效能設計的重中之重。

### 3.1 完美同步滾動（Single LazyColumn）
* **傳統做法的缺點**：若使用兩個獨立的 `LazyColumn`，並透過副作用來同步 scroll offset，在快速滾動時極易產生畫面延遲（Gutter lag）與錯位，且雙欄的 item 高度不一致時，對齊邏輯會變得異常複雜。
* **本專案優化做法**：將左右兩欄代碼封裝於同一個 `DiffRowView` 中，並由一個統一的 `LazyColumn` 呈現。左右兩側共用同一個 `LazyListState`。這項設計帶來了以下優勢：
  - **100% 物理對齊**：不論滾動多快，左右兩側永遠不會產生時間差與位置錯位。
  - **極致虛擬化效能**：只渲染畫面上看得到的行，萬行程式碼依然保持 60+ FPS。
  - **單一滾動條**：搭配 `VerticalScrollbar` 即可達到 VS Code / Beyond Compare 的流暢滾動體驗。

### 3.2 響應式高度自適應（IntrinsicSize.Min）
當某一側代碼過長而換行時，若另一側為 placeholder，我們需要兩側的高度依然維持一致：
- 使用 `Row(modifier = Modifier.height(IntrinsicSize.Min))`，這會強制子元件（左欄、中線、右欄）的高度皆等於其中最長/最高元件的高度，使背景色高亮與分割線能完美填滿整行。

### 3.3 字型與版面規範
- **等寬字型**：行號與代碼均使用 `FontFamily.Monospace`，確保每個字元寬度一致，方便排版與比對。
- **固定行號寬度**：行號欄設定為固定的 `42.dp` 寬度並靠右對齊（`TextAlign.End`），維持精確的代碼對齊線。

---

## 4. 未來與 Rust (UniFFI) 對接之設計

本設計為未來的 Rust 效能引擎預留了清晰的對接點：

1. **Rust 演算法實作**：以 Rust 實作 Myers 與 Patience 演算法，對比兩組 `Vec<String>`（左檔案與右檔案的行列表）。
2. **UniFFI 介面定義 (UDL)**：
   ```rust
   // Rust side
   pub enum LineStatus {
       Unchanged,
       Added,
       Deleted,
       Modified,
   }
   
   pub struct DiffRowData {
       pub left_line_num: Option<i32>,
       pub left_text: String,
       pub right_line_num: Option<i32>,
       pub right_text: String,
       pub status: LineStatus,
   }
   
   pub fn compare_files(left: Vec<String>, right: Vec<String>, algo: AlgorithmType) -> Vec<DiffRowData> {
       // 演算法計算...
   }
   ```
3. **ViewModel 無縫對接**：
   在 `DiffViewModel` 中，只需將 Mock Data 產生器更換為呼叫 UniFFI 自動生成的 Kotlin 綁定類別：
   ```kotlin
   fun changeAlgorithm(algo: AlgorithmType) {
       currentAlgorithm = algo
       // 未來直接調用 Rust UniFFI 函式庫
       diffLines = RustEngine.compareFiles(leftLines, rightLines, algo.toRustAlgo())
   }
   ```
