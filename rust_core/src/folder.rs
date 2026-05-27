use std::sync::Mutex;
use std::collections::HashMap;
use rayon::prelude::*;
use sha2::{Sha256, Digest};

use crate::vfs::{VfsProvider, VfsNode, VfsError};

#[derive(uniffi::Enum, Clone, Copy, Debug, PartialEq)]
pub enum FolderDiffStatus {
    Unchanged,
    Modified,
    LeftOrphan,
    RightOrphan,
}

#[derive(uniffi::Record, Clone, Debug)]
pub struct FolderDiffRow {
    pub relative_path: String,
    pub name: String,
    pub level: u32,
    pub is_directory: bool,
    pub left_size: Option<u64>,
    pub left_modified: Option<u64>,
    pub right_size: Option<u64>,
    pub right_modified: Option<u64>,
    pub status: FolderDiffStatus,
    pub left_file: Option<VfsNode>,
    pub right_file: Option<VfsNode>,
    pub is_renamed_match: bool,
    pub match_confidence: f32,
    pub is_backup_match: bool,
}

pub fn filename_similarity(a: &str, b: &str) -> f32 {
    let a_chars: Vec<char> = a.chars().collect();
    let b_chars: Vec<char> = b.chars().collect();
    if a_chars.is_empty() && b_chars.is_empty() {
        return 1.0;
    }
    if a_chars.is_empty() || b_chars.is_empty() {
        return 0.0;
    }
    
    let get_bigrams = |chars: &[char]| -> std::collections::HashSet<(char, char)> {
        let mut set = std::collections::HashSet::new();
        for i in 0..chars.len() - 1 {
            set.insert((chars[i], chars[i+1]));
        }
        set
    };
    
    let set_a = get_bigrams(&a_chars);
    let set_b = get_bigrams(&b_chars);
    
    if set_a.is_empty() && set_b.is_empty() {
        let mut matches = 0;
        for c in &a_chars {
            if b_chars.contains(c) {
                matches += 1;
            }
        }
        return (2.0 * matches as f32) / (a_chars.len() + b_chars.len()) as f32;
    }
    
    let intersection = set_a.intersection(&set_b).count();
    (2.0 * intersection as f32) / (set_a.len() + set_b.len()) as f32
}

fn compare_pair(
    left: VfsNode,
    right: VfsNode,
    left_provider: &dyn VfsProvider,
    left_rel_path: &str,
    right_provider: &dyn VfsProvider,
    right_rel_path: &str,
    level: u32,
    is_renamed: bool,
    confidence: f32
) -> Vec<FolderDiffRow> {
    let is_dir_l = left.metadata.is_directory;
    let is_dir_r = right.metadata.is_directory;
    
    if is_dir_l && is_dir_r {
        let child_left_path = if left_rel_path.is_empty() { left.name.clone() } else { format!("{}/{}", left_rel_path, left.name) };
        let child_right_path = if right_rel_path.is_empty() { right.name.clone() } else { format!("{}/{}", right_rel_path, right.name) };
        
        let mut row = FolderDiffRow {
            relative_path: left.relative_path.clone(),
            name: if is_renamed { format!("{} ➔ {}", left.name, right.name) } else { left.name.clone() },
            level,
            is_directory: true,
            left_size: None,
            left_modified: None,
            right_size: None,
            right_modified: None,
            status: FolderDiffStatus::Unchanged,
            left_file: Some(left.clone()),
            right_file: Some(right.clone()),
            is_renamed_match: is_renamed,
            match_confidence: confidence,
            is_backup_match: false,
        };
        
        let mut sub_rows = compare_directories_parallel(
            left_provider, &child_left_path,
            right_provider, &child_right_path,
            level + 1
        );
        
        let has_diff = sub_rows.iter().any(|r| r.status != FolderDiffStatus::Unchanged);
        row.status = if has_diff || is_renamed { FolderDiffStatus::Modified } else { FolderDiffStatus::Unchanged };
        
        let mut result = vec![row];
        result.append(&mut sub_rows);
        result
    } else if !is_dir_l && !is_dir_r {
        let status = if left.metadata.size != right.metadata.size {
            FolderDiffStatus::Modified
        } else if left.metadata.modified_time != right.metadata.modified_time {
            let hash_content = |provider: &dyn VfsProvider, rel: &str| -> Option<String> {
                let bytes = provider.read_file_content(rel.to_string()).ok()?;
                let mut hasher = Sha256::new();
                hasher.update(&bytes);
                Some(format!("{:x}", hasher.finalize()))
            };
            let lh = hash_content(left_provider, &left.relative_path);
            let rh = hash_content(right_provider, &right.relative_path);
            match (lh, rh) {
                (Some(l), Some(r)) if l == r => FolderDiffStatus::Unchanged,
                _ => FolderDiffStatus::Modified,
            }
        } else {
            FolderDiffStatus::Unchanged
        };
        
        let final_status = if is_renamed && status == FolderDiffStatus::Unchanged {
            FolderDiffStatus::Modified
        } else {
            status
        };
        
        vec![FolderDiffRow {
            relative_path: left.relative_path.clone(),
            name: if is_renamed { format!("{} ➔ {}", left.name, right.name) } else { left.name.clone() },
            level,
            is_directory: false,
            left_size: Some(left.metadata.size),
            left_modified: Some(left.metadata.modified_time),
            right_size: Some(right.metadata.size),
            right_modified: Some(right.metadata.modified_time),
            status: final_status,
            left_file: Some(left.clone()),
            right_file: Some(right.clone()),
            is_renamed_match: is_renamed,
            match_confidence: confidence,
            is_backup_match: false,
        }]
    } else {
        vec![
            FolderDiffRow {
                relative_path: left.relative_path.clone(),
                name: format!("{} (File)", left.name),
                level,
                is_directory: false,
                left_size: Some(left.metadata.size),
                left_modified: Some(left.metadata.modified_time),
                right_size: None,
                right_modified: None,
                status: FolderDiffStatus::LeftOrphan,
                left_file: Some(left.clone()),
                right_file: None,
                is_renamed_match: false,
                match_confidence: 0.0,
                is_backup_match: false,
            },
            FolderDiffRow {
                relative_path: right.relative_path.clone(),
                name: format!("{} (Directory)", right.name),
                level,
                is_directory: true,
                left_size: None,
                left_modified: None,
                right_size: Some(right.metadata.size),
                right_modified: Some(right.metadata.modified_time),
                status: FolderDiffStatus::RightOrphan,
                left_file: None,
                right_file: Some(right.clone()),
                is_renamed_match: false,
                match_confidence: 0.0,
                is_backup_match: false,
            }
        ]
    }
}

pub(crate) fn compare_directories_parallel(
    left_provider: &dyn VfsProvider,
    left_rel_path: &str,
    right_provider: &dyn VfsProvider,
    right_rel_path: &str,
    level: u32
) -> Vec<FolderDiffRow> {
    let (left_res, right_res) = rayon::join(
        || left_provider.list_directory(left_rel_path.to_string()),
        || right_provider.list_directory(right_rel_path.to_string())
    );
    
    let left_nodes = left_res.unwrap_or_default();
    let right_nodes = right_res.unwrap_or_default();
    
    let mut exact_matches = Vec::new();
    let mut left_orphans = Vec::new();
    let mut right_orphans = Vec::new();
    
    let left_map: HashMap<String, VfsNode> = left_nodes.into_iter().map(|n| (n.name.clone(), n)).collect();
    let mut right_map: HashMap<String, VfsNode> = right_nodes.into_iter().map(|n| (n.name.clone(), n)).collect();
    
    for (name, left_node) in left_map {
        if let Some(right_node) = right_map.remove(&name) {
            exact_matches.push((name, left_node, right_node));
        } else {
            left_orphans.push(left_node);
        }
    }
    for (_, right_node) in right_map {
        right_orphans.push(right_node);
    }
    
    exact_matches.sort_by(|a, b| a.0.cmp(&b.0));
    
    let mut fuzzy_matches = Vec::new();
    let mut remaining_left_orphans = Vec::new();
    
    right_orphans.sort_by(|a, b| a.name.cmp(&b.name));
    let mut right_matched = vec![false; right_orphans.len()];
    
    for left_node in left_orphans {
        let mut best_idx = None;
        let mut best_score = 0.0f32;
        
        for (idx, right_node) in right_orphans.iter().enumerate() {
            if right_matched[idx] { continue; }
            if left_node.metadata.is_directory != right_node.metadata.is_directory { continue; }
            
            let score = filename_similarity(&left_node.name, &right_node.name);
            if score > best_score {
                best_score = score;
                best_idx = Some(idx);
            }
        }
        
        if let Some(idx) = best_idx {
            if best_score >= 0.6 {
                right_matched[idx] = true;
                fuzzy_matches.push((left_node, right_orphans[idx].clone(), best_score));
                continue;
            }
        }
        remaining_left_orphans.push(left_node);
    }
    
    let mut remaining_right_orphans = Vec::new();
    for (idx, right_node) in right_orphans.into_iter().enumerate() {
        if !right_matched[idx] {
            remaining_right_orphans.push(right_node);
        }
    }
    
    let mut results = Vec::new();
    
    // Partition exact matches
    let mut exact_dirs = Vec::new();
    let mut exact_files = Vec::new();
    for (name, left_node, right_node) in exact_matches {
        if left_node.metadata.is_directory {
            exact_dirs.push((name, left_node, right_node));
        } else {
            exact_files.push((name, left_node, right_node));
        }
    }
    
    // Partition fuzzy matches
    let mut fuzzy_dirs = Vec::new();
    let mut fuzzy_files = Vec::new();
    for (left_node, right_node, score) in fuzzy_matches {
        if left_node.metadata.is_directory {
            fuzzy_dirs.push((left_node, right_node, score));
        } else {
            fuzzy_files.push((left_node, right_node, score));
        }
    }
    
    // Partition remaining left orphans
    let mut left_orphan_dirs = Vec::new();
    let mut left_orphan_files = Vec::new();
    for node in remaining_left_orphans {
        if node.metadata.is_directory {
            left_orphan_dirs.push(node);
        } else {
            left_orphan_files.push(node);
        }
    }
    
    // Partition remaining right orphans
    let mut right_orphan_dirs = Vec::new();
    let mut right_orphan_files = Vec::new();
    for node in remaining_right_orphans {
        if node.metadata.is_directory {
            right_orphan_dirs.push(node);
        } else {
            right_orphan_files.push(node);
        }
    }
    
    // ==========================================
    // 1. Process Directories (always first)
    // ==========================================
    for (_, left_node, right_node) in exact_dirs {
        results.append(&mut compare_pair(
            left_node, right_node,
            left_provider, left_rel_path,
            right_provider, right_rel_path,
            level, false, 1.0
        ));
    }
    
    for (left_node, right_node, score) in fuzzy_dirs {
        results.append(&mut compare_pair(
            left_node, right_node,
            left_provider, left_rel_path,
            right_provider, right_rel_path,
            level, true, score
        ));
    }
    
    left_orphan_dirs.sort_by(|a, b| a.name.cmp(&b.name));
    for left_node in left_orphan_dirs {
        results.push(FolderDiffRow {
            relative_path: left_node.relative_path.clone(),
            name: left_node.name.clone(),
            level,
            is_directory: true,
            left_size: None,
            left_modified: None,
            right_size: None,
            right_modified: None,
            status: FolderDiffStatus::LeftOrphan,
            left_file: Some(left_node.clone()),
            right_file: None,
            is_renamed_match: false,
            match_confidence: 0.0,
            is_backup_match: false,
        });
        let child_left_path = if left_rel_path.is_empty() { left_node.name.clone() } else { format!("{}/{}", left_rel_path, left_node.name) };
        results.append(&mut walk_orphan(left_provider, &child_left_path, level + 1, true));
    }
    
    right_orphan_dirs.sort_by(|a, b| a.name.cmp(&b.name));
    for right_node in right_orphan_dirs {
        results.push(FolderDiffRow {
            relative_path: right_node.relative_path.clone(),
            name: right_node.name.clone(),
            level,
            is_directory: true,
            left_size: None,
            left_modified: None,
            right_size: None,
            right_modified: None,
            status: FolderDiffStatus::RightOrphan,
            left_file: None,
            right_file: Some(right_node.clone()),
            is_renamed_match: false,
            match_confidence: 0.0,
            is_backup_match: false,
        });
        let child_right_path = if right_rel_path.is_empty() { right_node.name.clone() } else { format!("{}/{}", right_rel_path, right_node.name) };
        results.append(&mut walk_orphan(right_provider, &child_right_path, level + 1, false));
    }
    
    // ==========================================
    // 2. Process Files (always second)
    // ==========================================
    for (_, left_node, right_node) in exact_files {
        results.append(&mut compare_pair(
            left_node, right_node,
            left_provider, left_rel_path,
            right_provider, right_rel_path,
            level, false, 1.0
        ));
    }
    
    for (left_node, right_node, score) in fuzzy_files {
        results.append(&mut compare_pair(
            left_node, right_node,
            left_provider, left_rel_path,
            right_provider, right_rel_path,
            level, true, score
        ));
    }
    
    left_orphan_files.sort_by(|a, b| a.name.cmp(&b.name));
    for left_node in left_orphan_files {
        results.push(FolderDiffRow {
            relative_path: left_node.relative_path.clone(),
            name: left_node.name.clone(),
            level,
            is_directory: false,
            left_size: Some(left_node.metadata.size),
            left_modified: Some(left_node.metadata.modified_time),
            right_size: None,
            right_modified: None,
            status: FolderDiffStatus::LeftOrphan,
            left_file: Some(left_node.clone()),
            right_file: None,
            is_renamed_match: false,
            match_confidence: 0.0,
            is_backup_match: false,
        });
    }
    
    right_orphan_files.sort_by(|a, b| a.name.cmp(&b.name));
    for right_node in right_orphan_files {
        results.push(FolderDiffRow {
            relative_path: right_node.relative_path.clone(),
            name: right_node.name.clone(),
            level,
            is_directory: false,
            left_size: None,
            left_modified: None,
            right_size: Some(right_node.metadata.size),
            right_modified: Some(right_node.metadata.modified_time),
            status: FolderDiffStatus::RightOrphan,
            left_file: None,
            right_file: Some(right_node.clone()),
            is_renamed_match: false,
            match_confidence: 0.0,
            is_backup_match: false,
        });
    }
    
    results
}

pub(crate) fn walk_orphan(provider: &dyn VfsProvider, path: &str, level: u32, is_left: bool) -> Vec<FolderDiffRow> {
    let mut nodes = provider.list_directory(path.to_string()).unwrap_or_default();
    nodes.sort_by(|a, b| {
        let a_dir = a.metadata.is_directory;
        let b_dir = b.metadata.is_directory;
        if a_dir && !b_dir {
            std::cmp::Ordering::Less
        } else if !a_dir && b_dir {
            std::cmp::Ordering::Greater
        } else {
            a.name.cmp(&b.name)
        }
    });
    nodes.into_par_iter().flat_map(|node| {
        let is_dir = node.metadata.is_directory;
        let mut result = Vec::new();
        result.push(FolderDiffRow {
            relative_path: node.relative_path.clone(),
            name: node.name.clone(),
            level,
            is_directory: is_dir,
            left_size: if !is_dir && is_left { Some(node.metadata.size) } else { None },
            left_modified: if !is_dir && is_left { Some(node.metadata.modified_time) } else { None },
            right_size: if !is_dir && !is_left { Some(node.metadata.size) } else { None },
            right_modified: if !is_dir && !is_left { Some(node.metadata.modified_time) } else { None },
            status: if is_left { FolderDiffStatus::LeftOrphan } else { FolderDiffStatus::RightOrphan },
            left_file: if is_left { Some(node.clone()) } else { None },
            right_file: if !is_left { Some(node.clone()) } else { None },
            is_renamed_match: false,
            match_confidence: 0.0,
            is_backup_match: false,
        });
        if is_dir {
            let child_path = format!("{}/{}", path, node.name);
            result.append(&mut walk_orphan(provider, &child_path, level + 1, is_left));
        }
        result
    }).collect()
}

// ------------------------------------------
// Folder Comparison Engine Object
// ------------------------------------------

#[derive(uniffi::Object)]
pub struct FolderDiffEngine {
    left_provider: Mutex<Option<std::sync::Arc<dyn VfsProvider>>>,
    right_provider: Mutex<Option<std::sync::Arc<dyn VfsProvider>>>,
    cached_rows: Mutex<Option<Vec<FolderDiffRow>>>,
}

#[uniffi::export]
impl FolderDiffEngine {
    #[uniffi::constructor]
    pub fn new() -> Self {
        Self {
            left_provider: Mutex::new(None),
            right_provider: Mutex::new(None),
            cached_rows: Mutex::new(None),
        }
    }

    pub fn set_providers(&self, left: std::sync::Arc<dyn VfsProvider>, right: std::sync::Arc<dyn VfsProvider>) {
        let mut left_p = self.left_provider.lock().unwrap();
        let mut right_p = self.right_provider.lock().unwrap();
        *left_p = Some(left);
        *right_p = Some(right);
    }

    pub fn compare_folders(&self) -> Result<u32, VfsError> {
        let left_p = self.left_provider.lock().unwrap();
        let right_p = self.right_provider.lock().unwrap();
        
        if let (Some(ref left), Some(ref right)) = (&*left_p, &*right_p) {
            let rows = compare_directories_parallel(left.as_ref(), "", right.as_ref(), "", 0);
            let total = rows.len() as u32;
            let mut cache = self.cached_rows.lock().unwrap();
            *cache = Some(rows);
            Ok(total)
        } else {
            Err(VfsError::IoError { reason: "Providers not set".to_string() })
        }
    }

    pub fn get_total_rows(&self) -> u32 {
        let cache = self.cached_rows.lock().unwrap();
        cache.as_ref().map(|r| r.len() as u32).unwrap_or(0)
    }

    pub fn get_rows_page(&self, start_index: u32, count: u32) -> Vec<FolderDiffRow> {
        let cache = self.cached_rows.lock().unwrap();
        if let Some(ref rows) = *cache {
            let start = start_index as usize;
            if start >= rows.len() {
                return Vec::new();
            }
            let end = std::cmp::min(start + count as usize, rows.len());
            rows[start..end].to_vec()
        } else {
            Vec::new()
        }
    }
}
