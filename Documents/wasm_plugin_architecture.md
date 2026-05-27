# Wasm Plugin Sandbox Architecture & Project Directory Tree

This document describes how to implement a secure, cross-platform plugin system inside the Rust core layer using WebAssembly (Wasm), and outlines the final project workspace structure.

---

## 1. WebAssembly Plugin Sandbox Architecture

To enable custom text transformations (e.g., extracting pure text from PDF or converting Excel tables to CSV) before running diff algorithms, `theCompare` can load custom sandboxed WebAssembly plugins.

Using `wasmtime`, the host application instantiates modules, writes file binary buffers to guest Wasm memory, calls the Wasm `transform` function, and retrieves the text result.

### 1.1 Host-Side Wasm Manager (`rust_core/src/wasm_plugin.rs`)
Add this module to load and execute Wasm transforms safely:

```rust
use wasmtime::*;
use std::path::Path;

pub struct WasmPlugin {
    engine: Engine,
    module: Module,
}

impl WasmPlugin {
    /// Load a compiled .wasm plugin file
    pub fn new(wasm_path: &Path) -> Result<Self, anyhow::Error> {
        let engine = Engine::default();
        let module = Module::from_file(&engine, wasm_path)?;
        Ok(Self { engine, module })
    }

    /// Run the transform function of the Wasm plugin
    pub fn transform(&self, input_bytes: &[u8]) -> Result<String, anyhow::Error> {
        let mut store = Store::new(&self.engine, ());
        let linker = Linker::new(&self.engine);
        let instance = linker.instantiate(&mut store, &self.module)?;

        // Retrieve linear memory export
        let memory = instance
            .get_memory(&mut store, "memory")
            .ok_or_else(|| anyhow::anyhow!("Wasm plugin does not export 'memory'"))?;

        // Retrieve Guest functions
        let alloc_fn = instance.get_typed_func::<u32, u32>(&mut store, "alloc")?;
        let dealloc_fn = instance.get_typed_func::<(u32, u32), ()>(&mut store, "dealloc")?;
        let transform_fn = instance.get_typed_func::<(u32, u32), u64>(&mut store, "transform")?;

        // 1. Allocate memory buffer inside Wasm module
        let input_len = input_bytes.len() as u32;
        let ptr = alloc_fn.call(&mut store, input_len)?;

        // 2. Write file bytes to guest memory
        memory.write(&mut store, ptr as usize, input_bytes)?;

        // 3. Call Wasm transform (input_ptr, input_len) -> returns u64 packing (out_ptr, out_len)
        let packed_result = transform_fn.call(&mut store, (ptr, input_len))?;
        let out_ptr = (packed_result & 0xFFFF_FFFF) as u32;
        let out_len = (packed_result >> 32) as u32;

        // 4. Read result string out of guest memory
        let mut buffer = vec![0u8; out_len as usize];
        memory.read(&store, out_ptr as usize, &mut buffer)?;
        let result_str = String::from_utf8(buffer)?;

        // 5. Safely deallocate buffers inside Wasm
        dealloc_fn.call(&mut store, (ptr, input_len))?;
        dealloc_fn.call(&mut store, (out_ptr, out_len))?;

        Ok(result_str)
    }
}
```

### 1.2 Guest Wasm Plugin Template (Rust / WebAssembly target)
Plugins compiled to `wasm32-unknown-unknown` implement this template:

```rust
// wasm_plugin_excel/src/lib.rs

#[no_mangle]
pub extern "C" fn alloc(size: usize) -> *mut u8 {
    let mut buf = Vec::with_capacity(size);
    let ptr = buf.as_mut_ptr();
    std::mem::forget(buf); // Retain memory for host to write to
    ptr
}

#[no_mangle]
pub extern "C" fn dealloc(ptr: *mut u8, size: usize) {
    unsafe {
        let _ = Vec::from_raw_parts(ptr, 0, size);
    }
}

#[no_mangle]
pub extern "C" fn transform(ptr: *mut u8, len: usize) -> u64 {
    let input = unsafe { std::slice::from_raw_parts(ptr, len) };
    
    // Parse binary input (e.g. read Excel sheets) and format to CSV
    let output_str = convert_excel_to_csv(input);
    
    let out_len = output_str.len();
    let out_ptr = output_str.into_bytes().leak().as_ptr() as u32;
    
    // Pack pointer (lower 32-bits) and length (upper 32-bits) into single u64
    ((out_len as u64) << 32) | (out_ptr as u64)
}

fn convert_excel_to_csv(_bytes: &[u8]) -> String {
    // Parsing implementation returns raw text
    "Col1,Col2\nVal1,Val2".to_string()
}
```

### 1.3 Preprocessing Integration in DiffEngine
Before running the diff, the host checks file extensions to invoke any registered Wasm plugins:

```rust
fn preprocess_file(&self, path: &str) -> String {
    let extension = Path::new(path).extension().and_then(|ext| ext.to_str()).unwrap_or("");
    let file_bytes = std::fs::read(path).unwrap_or_default();

    if extension == "xlsx" {
        if let Some(plugin) = self.get_plugin_for_extension("xlsx") {
            if let Ok(csv_text) = plugin.transform(&file_bytes) {
                return csv_text;
            }
        }
    }
    String::from_utf8_lossy(&file_bytes).into_owned()
}
```

---

## 2. Final Project Directory Tree Layout

The following structure illustrates the full architecture of the unified workspace including Compose code, Rust core FFI bindings, configurations, GitHub Actions pipelines, documents, and plugin spaces.

```
theCompare/
├── .github/
│   └── workflows/
│       └── release.yml                 # Matrix-based CI/CD build script
├── build.gradle.kts                    # Main JVM project configuration with copy tasks
├── gradlew.bat                         # Gradle wrapper launcher (Windows)
├── gradlew                             # Gradle wrapper launcher (Mac/Linux)
├── settings.gradle.kts                 # JVM project naming settings
├── my-diff.exe                         # Compiled platform CLI release binary
├── compare_core.dll                    # Compiled Rust FFI core DLL
├── Documents/                          # Software architectures and integration guides
│   ├── architecture_design.md
│   ├── large_file_optimization_guide.md
│   ├── rust_integration_guide.md
│   ├── git_integration_guide.md
│   ├── release_setup.md
│   └── wasm_plugin_architecture.md
├── test_data/                          # Test scenarios for differential/merge workflows
│   ├── base.txt
│   ├── local.txt
│   ├── remote.txt
│   └── output.txt
├── plugins/                            # Sandboxed external plugin binaries
│   ├── pdf_transform.wasm
│   └── excel_transform.wasm
├── rust_core/                          # Rust native core workspace
│   ├── Cargo.toml                      # Package settings (clap, rayon, uniffi, etc.)
│   ├── src/
│   │   ├── lib.rs                      # FFI entry point, Myers/Linear algos, VFS scan
│   │   ├── bin/
│   │   │   └── my-diff.rs              # CLI executable wrapper
│   │   └── wasm_plugin.rs              # Sandbox Wasm plugin manager
│   └── target/                         # Rust release and target binaries
└── src/
    └── main/
        ├── kotlin/
        │   ├── Main.kt                 # Compose UI windows, states, ViewModels (Diff & Merge)
        │   └── bindings/               # UniFFI autogenerated bridge bindings
        │       └── uniffi/
        │           └── compare_core/
        │               └── compare_core.kt
        └── resources/                  # Dynamic packaging platforms directory
            ├── win32-x86-64/
            ├── darwin-aarch64/
            ├── darwin-x86-64/
            └── linux-x86-64/
```
