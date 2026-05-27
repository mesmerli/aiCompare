pub mod common;
pub mod diff;
pub mod vfs;
pub mod folder;
pub mod merge;

// Re-export all submodules at the root namespace level
pub use common::*;
pub use diff::*;
pub use vfs::*;
pub use folder::*;
pub use merge::*;

// Generate FFI bindings boilerplate
uniffi::setup_scaffolding!();

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_local_read_file_content() {
        let left = r"..\test_data\folder_compare\left";
        let right = r"..\test_data\folder_compare\right";

        let lp = LocalFsProvider::new(left.to_string());
        let rp = LocalFsProvider::new(right.to_string());

        let lb = lp.read_file_content("unchanged.txt".to_string()).expect("left read failed");
        let rb = rp.read_file_content("unchanged.txt".to_string()).expect("right read failed");

        println!("Left  {} bytes: {:?}", lb.len(), String::from_utf8_lossy(&lb));
        println!("Right {} bytes: {:?}", rb.len(), String::from_utf8_lossy(&rb));
        assert_eq!(lb, rb, "Contents should be identical");
    }

    #[test]
    fn test_folder_compare_unchanged() {
        let left = r"..\test_data\folder_compare\left";
        let right = r"..\test_data\folder_compare\right";

        let lp: std::sync::Arc<dyn VfsProvider> = std::sync::Arc::new(LocalFsProvider::new(left.to_string()));
        let rp: std::sync::Arc<dyn VfsProvider> = std::sync::Arc::new(LocalFsProvider::new(right.to_string()));

        let rows = compare_directories_parallel(lp.as_ref(), "", rp.as_ref(), "", 0);
        for row in &rows {
            println!("{} -> {:?}", row.name, row.status);
        }
        let unchanged_row = rows.iter().find(|r| r.name == "unchanged.txt");
        assert!(unchanged_row.is_some(), "unchanged.txt should be in results");
        assert_eq!(
            unchanged_row.unwrap().status,
            FolderDiffStatus::Unchanged,
            "unchanged.txt should be UNCHANGED"
        );
    }

    #[test]
    fn test_unimportant_classifier() {
        use crate::diff::UnimportantDiffClassifier;
        
        // Rule A: Whitespace/Indentation differences
        assert!(UnimportantDiffClassifier::is_unimportant("  val x = 1", "val  x  =  1"));
        
        // Rule B: Comments differences
        assert!(UnimportantDiffClassifier::is_unimportant("val x = 1 // comment A", "val x = 1 // comment B"));
        assert!(UnimportantDiffClassifier::is_unimportant("/* block comment A */", "/* block comment B */"));
        assert!(UnimportantDiffClassifier::is_unimportant("val x = 1 /* block comment A */", "val x = 1 /* block comment B */"));
        
        // Negative test: logic change
        assert!(!UnimportantDiffClassifier::is_unimportant("val x = 1", "val y = 1"));
    }

    #[test]
    fn test_unimportant_integration() {
        let left = r"..\test_data\file_compare\unimportant_old.kt";
        let right = r"..\test_data\file_compare\unimportant_new.kt";

        let engine = DiffEngine::new();
        let total_lines = engine.compare_files(left.to_string(), right.to_string()).expect("Compare failed");
        let lines = engine.get_lines_page(0, total_lines);

        // Verify that there is at least one UNIMPORTANT line and one MODIFIED line
        let has_unimportant = lines.iter().any(|l| l.status == DiffStatus::Unimportant);
        let has_modified = lines.iter().any(|l| l.status == DiffStatus::Modified);

        assert!(has_unimportant, "Should have detected unimportant comment/whitespace differences");
        assert!(has_modified, "Should have detected actual logic modifications");
    }

    #[test]
    fn test_fuzzy_folder_alignment() {
        use crate::folder::filename_similarity;
        // Test filename similarity
        assert!(filename_similarity("hello_v1.txt", "hello_v2.txt") > 0.7);
        assert!(filename_similarity("user_viewModel_old.kt", "user_viewModel_new.kt") > 0.6);
        assert!(filename_similarity("diff.rs", "merge.rs") < 0.4);
    }
}
