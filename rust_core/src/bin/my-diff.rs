use clap::Parser;
use std::process::Command;
use std::path::Path;

#[derive(Parser, Debug)]
#[command(name = "my-diff", version = "1.0", about = "High-performance Side-by-side Diff & 3-Way Merge Tool")]
struct Args {
    /// Run in 3-way merge mode
    #[arg(long)]
    merge: bool,

    /// Files to compare (2 files) or merge (local, remote, base, output)
    #[arg(num_args = 0..=4)]
    files: Vec<String>,
}

fn main() {
    let args = Args::parse();

    // Prepare arguments to pass to the Java UI process
    let mut cmd_args = Vec::new();
    if args.merge {
        cmd_args.push("--merge".to_string());
    }
    for f in &args.files {
        cmd_args.push(f.clone());
    }

    // Determine the working directory and launcher
    let workspace_root = if Path::new("gradlew.bat").exists() {
        Some(".")
    } else if Path::new("../gradlew.bat").exists() {
        Some("..")
    } else if Path::new("../../gradlew.bat").exists() {
        Some("../..")
    } else {
        None
    };

    let status = if let Some(cwd) = workspace_root {
        let args_str = cmd_args.iter()
            .map(|arg| format!("\"{}\"", arg.replace("\"", "\\\"")))
            .collect::<Vec<_>>()
            .join(" ");
        println!("Launching Compose Desktop UI via Gradle (dev mode)...");
        
        Command::new("cmd")
            .args(&["/C", &format!("gradlew.bat run --args=\"{}\"", args_str)])
            .current_dir(cwd)
            .status()
    } else {
        // In a packaged distribution, execute the packaged binary directly
        println!("Launching packaged Compose Desktop UI...");
        Command::new("theCompare.exe")
            .args(&cmd_args)
            .status()
    };

    match status {
        Ok(exit_status) => {
            if exit_status.success() {
                println!("Merge/Diff session closed successfully.");
                std::process::exit(0);
            } else {
                eprintln!("Merge/Diff session closed with conflicts or user cancel.");
                std::process::exit(1);
            }
        }
        Err(e) => {
            eprintln!("Error starting UI process: {}", e);
            std::process::exit(1);
        }
    }
}
