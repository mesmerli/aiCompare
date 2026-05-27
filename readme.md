# aiCompare

`aiCompare` is a modern, high-performance, and visually stunning file and folder comparison and merging tool. It integrates a blazing-fast **Rust Diff Engine** with an ultra-premium **Compose Desktop GUI** (Kotlin) using JNI/FFI JNA bindings.

Designed with a premium minimalist aesthetic, `aiCompare` delivers an elegant and intelligent user experience that outclasses traditional tools.

---

## ✨ Key Features

### 1. Smart Myers Diff Engine & Unimportant Diff Demotion
- Computes standard line-level differences using Myers diff.
- Runs a **post-processing secondary filter** (via `UnimportantDiffClassifier`) to identify insignificant modifications (e.g., whitespace variations, code comments inside or outside block comments).
- Safely demotes minor differences to `UNIMPORTANT` (colored in secondary blue/gray text) while keeping primary structural variations highlighted.

### 2. Fuzzy Folder Alignment
- Scans left and right directory structures in parallel using Rayon.
- Utilizes the **Sorensen-Dice coefficient similarity metric** to automatically detect and pair renamed files or folders (similarity threshold $\ge$ 60%).

### 3. Drag-and-Drop Manual Alignment
- Allows users to interactively drag a left orphan row and drop it onto a right orphan row of matching type.
- Features high-fidelity micro-interactions:
  - Dragged rows display with partial transparency.
  - Hovering over a valid right target lights it up with a vivid purple glow, providing immediate visual feedback.

### 4. Self-Cross Backup Alignment
- Detects when comparing a directory to itself (`leftFolderPath == rightFolderPath`).
- Automatically filters and pairs backup files (e.g., matching `hello.c` with backups like `hello.c.old`, `hello.c.bak`, `hello_v1.c`, or `hello-copy.c`).
- Visualized with a custom low-key cyan/blue color scheme and a dedicated **yellow historical backup icon (`↩`)** in the central tracking lane.

### 5. Premium UI Design System
- Built with a dark mode color palette (Harmonious grays, accent amber, and violet tones).
- Displays folder comparison nodes with **slant duotone folder icons** rendered live via Compose Canvas to represent mixed status directories ("some files changed, some identical").
- Fast rendering with micro-animations and smooth transitions.

---

## 🛠️ Technology Stack

- **Computation Core (Rust)**: High-performance parallel comparisons using Rayon, SHA256 hashing, and Myers diff.
- **UI Layer (Kotlin & Compose Desktop)**: Modern declarative interface with hardware-accelerated Canvas drawing.
- **Bridge (UniFFI & JNA)**: Auto-generated FFI bindings to load the compiled Rust DLL/dylib dynamically.

---

## 🚀 Quick Start & Build Instructions

### Prerequisites
- **Rust Toolchain**: Cargo and rustc installed.
- **JDK 21+**: Java Development Kit version 21 or later. (Android Studio's JBR is recommended).

### 1. Build the Rust Library
Navigate to the Rust core project and build the release target:
```powershell
cd rust_core
cargo build --release
```

### 2. Generate UniFFI Bindings (Optional)
If you modify the FFI surface inside Rust, regenerate the Kotlin bindings:
```powershell
cargo run --features=uniffi/cli --bin uniffi-bindgen generate --library target/release/compare_core.dll --language kotlin --out-dir ../src/main/kotlin/bindings
```

### 3. Run the App
From the root workspace directory, run:
```powershell
# Set Java Home to JBR or JDK 21 path
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

# Launch Compose Desktop
.\gradlew.bat run
```

---

## 📂 Project Structure

- `rust_core/`: Rust library containing diff and VFS implementations.
- `src/main/kotlin/`: Compose Desktop UI and ViewModel.
  - `bindings/uniffi/`: Automatically generated UniFFI bridge code.
- `test_data/`: Sample files and directories for testing diff scenarios.
- `Documents/`: Detailed technical guides and design documentation.
