use std::sync::Mutex;
use crate::diff::{tokenize_lines, lines_to_ids, diff, Edit};

#[derive(Clone, Debug, uniffi::Record)]
pub struct MergeRow {
    pub base_line_num: Option<u32>,
    pub base_text: String,
    
    pub local_line_num: Option<u32>,
    pub local_text: String,
    
    pub remote_line_num: Option<u32>,
    pub remote_text: String,
    
    pub merged_line_num: Option<u32>,
    pub merged_text: String,
    
    pub is_conflict: bool,
}

#[derive(Clone, Debug, PartialEq)]
enum BaseStatus {
    Kept(usize),
    Deleted,
}

#[derive(Clone, Debug)]
struct BaseLineInfo {
    local_insertions: Vec<usize>,
    remote_insertions: Vec<usize>,
    local_status: BaseStatus,
    remote_status: BaseStatus,
}

impl Default for BaseLineInfo {
    fn default() -> Self {
        Self {
            local_insertions: Vec::new(),
            remote_insertions: Vec::new(),
            local_status: BaseStatus::Deleted,
            remote_status: BaseStatus::Deleted,
        }
    }
}

fn run_3way_merge(base: &str, local: &str, remote: &str) -> Vec<MergeRow> {
    let base_lines = tokenize_lines(base);
    let local_lines = tokenize_lines(local);
    let remote_lines = tokenize_lines(remote);

    let (base_ids_l, local_ids) = lines_to_ids(&base_lines, &local_lines);
    let (base_ids_r, remote_ids) = lines_to_ids(&base_lines, &remote_lines);

    let diff_local = diff(&base_ids_l, &local_ids);
    let diff_remote = diff(&base_ids_r, &remote_ids);

    let mut local_info = vec![BaseLineInfo::default(); base_lines.len() + 1];
    let mut remote_info = vec![BaseLineInfo::default(); base_lines.len() + 1];

    let mut current_local_inserts = Vec::new();
    for edit in diff_local {
        match edit {
            Edit::Add(l_idx) => {
                current_local_inserts.push(l_idx);
            }
            Edit::Keep(b_idx, l_idx) => {
                local_info[b_idx].local_status = BaseStatus::Kept(l_idx);
                local_info[b_idx].local_insertions = std::mem::take(&mut current_local_inserts);
            }
            Edit::Delete(b_idx) => {
                local_info[b_idx].local_status = BaseStatus::Deleted;
                local_info[b_idx].local_insertions = std::mem::take(&mut current_local_inserts);
            }
        }
    }
    if !current_local_inserts.is_empty() {
        local_info[base_lines.len()].local_insertions = current_local_inserts;
    }

    let mut current_remote_inserts = Vec::new();
    for edit in diff_remote {
        match edit {
            Edit::Add(r_idx) => {
                current_remote_inserts.push(r_idx);
            }
            Edit::Keep(b_idx, r_idx) => {
                remote_info[b_idx].remote_status = BaseStatus::Kept(r_idx);
                remote_info[b_idx].remote_insertions = std::mem::take(&mut current_remote_inserts);
            }
            Edit::Delete(b_idx) => {
                remote_info[b_idx].remote_status = BaseStatus::Deleted;
                remote_info[b_idx].remote_insertions = std::mem::take(&mut current_remote_inserts);
            }
        }
    }
    if !current_remote_inserts.is_empty() {
        remote_info[base_lines.len()].remote_insertions = current_remote_inserts;
    }

    let mut rows = Vec::new();

    for b in 0..=base_lines.len() {
        // 1. Process insertions before line b
        let local_insert = &local_info[b].local_insertions;
        let remote_insert = &remote_info[b].remote_insertions;

        if !local_insert.is_empty() || !remote_insert.is_empty() {
            if local_insert.is_empty() {
                for &r_idx in remote_insert {
                    rows.push(MergeRow {
                        base_line_num: None,
                        base_text: "".to_string(),
                        local_line_num: None,
                        local_text: "".to_string(),
                        remote_line_num: Some((r_idx + 1) as u32),
                        remote_text: remote_lines[r_idx].to_string(),
                        merged_line_num: None,
                        merged_text: remote_lines[r_idx].to_string(),
                        is_conflict: false,
                    });
                }
            } else if remote_insert.is_empty() {
                for &l_idx in local_insert {
                    rows.push(MergeRow {
                        base_line_num: None,
                        base_text: "".to_string(),
                        local_line_num: Some((l_idx + 1) as u32),
                        local_text: local_lines[l_idx].to_string(),
                        remote_line_num: None,
                        remote_text: "".to_string(),
                        merged_line_num: None,
                        merged_text: local_lines[l_idx].to_string(),
                        is_conflict: false,
                    });
                }
            } else {
                // Both inserted at the same place
                let identical = local_insert.len() == remote_insert.len() &&
                    local_insert.iter().zip(remote_insert.iter())
                        .all(|(&l, &r)| local_lines[l] == remote_lines[r]);

                if identical {
                    for i in 0..local_insert.len() {
                        let l_idx = local_insert[i];
                        let r_idx = remote_insert[i];
                        rows.push(MergeRow {
                            base_line_num: None,
                            base_text: "".to_string(),
                            local_line_num: Some((l_idx + 1) as u32),
                            local_text: local_lines[l_idx].to_string(),
                            remote_line_num: Some((r_idx + 1) as u32),
                            remote_text: remote_lines[r_idx].to_string(),
                            merged_line_num: None,
                            merged_text: local_lines[l_idx].to_string(),
                            is_conflict: false,
                        });
                    }
                } else {
                    // Conflict
                    rows.push(MergeRow {
                        base_line_num: None,
                        base_text: "".to_string(),
                        local_line_num: None,
                        local_text: "<<<<<<< LOCAL".to_string(),
                        remote_line_num: None,
                        remote_text: "".to_string(),
                        merged_line_num: None,
                        merged_text: "<<<<<<< LOCAL".to_string(),
                        is_conflict: true,
                    });
                    for &l_idx in local_insert {
                        rows.push(MergeRow {
                            base_line_num: None,
                            base_text: "".to_string(),
                            local_line_num: Some((l_idx + 1) as u32),
                            local_text: local_lines[l_idx].to_string(),
                            remote_line_num: None,
                            remote_text: "".to_string(),
                            merged_line_num: None,
                            merged_text: local_lines[l_idx].to_string(),
                            is_conflict: true,
                        });
                    }
                    rows.push(MergeRow {
                        base_line_num: None,
                        base_text: "".to_string(),
                        local_line_num: None,
                        local_text: "=======".to_string(),
                        remote_line_num: None,
                        remote_text: "=======".to_string(),
                        merged_line_num: None,
                        merged_text: "=======".to_string(),
                        is_conflict: true,
                    });
                    for &r_idx in remote_insert {
                        rows.push(MergeRow {
                            base_line_num: None,
                            base_text: "".to_string(),
                            local_line_num: None,
                            local_text: "".to_string(),
                            remote_line_num: Some((r_idx + 1) as u32),
                            remote_text: remote_lines[r_idx].to_string(),
                            merged_line_num: None,
                            merged_text: remote_lines[r_idx].to_string(),
                            is_conflict: true,
                        });
                    }
                    rows.push(MergeRow {
                        base_line_num: None,
                        base_text: "".to_string(),
                        local_line_num: None,
                        local_text: "".to_string(),
                        remote_line_num: None,
                        remote_text: ">>>>>>> REMOTE".to_string(),
                        merged_line_num: None,
                        merged_text: ">>>>>>> REMOTE".to_string(),
                        is_conflict: true,
                    });
                }
            }
        }

        // 2. Process base line b itself
        if b < base_lines.len() {
            let l_status = &local_info[b].local_status;
            let r_status = &remote_info[b].remote_status;

            match (l_status, r_status) {
                (BaseStatus::Kept(l_idx), BaseStatus::Kept(r_idx)) => {
                    if local_lines[*l_idx] == remote_lines[*r_idx] {
                        rows.push(MergeRow {
                            base_line_num: Some((b + 1) as u32),
                            base_text: base_lines[b].to_string(),
                            local_line_num: Some((*l_idx + 1) as u32),
                            local_text: local_lines[*l_idx].to_string(),
                            remote_line_num: Some((*r_idx + 1) as u32),
                            remote_text: remote_lines[*r_idx].to_string(),
                            merged_line_num: None,
                            merged_text: local_lines[*l_idx].to_string(),
                            is_conflict: false,
                        });
                    } else {
                        // Conflict: both modified differently
                        rows.push(MergeRow {
                            base_line_num: Some((b + 1) as u32),
                            base_text: base_lines[b].to_string(),
                            local_line_num: None,
                            local_text: "<<<<<<< LOCAL".to_string(),
                            remote_line_num: None,
                            remote_text: "".to_string(),
                            merged_line_num: None,
                            merged_text: "<<<<<<< LOCAL".to_string(),
                            is_conflict: true,
                        });
                        rows.push(MergeRow {
                            base_line_num: Some((b + 1) as u32),
                            base_text: base_lines[b].to_string(),
                            local_line_num: Some((*l_idx + 1) as u32),
                            local_text: local_lines[*l_idx].to_string(),
                            remote_line_num: None,
                            remote_text: "".to_string(),
                            merged_line_num: None,
                            merged_text: local_lines[*l_idx].to_string(),
                            is_conflict: true,
                        });
                        rows.push(MergeRow {
                            base_line_num: None,
                            base_text: "".to_string(),
                            local_line_num: None,
                            local_text: "=======".to_string(),
                            remote_line_num: None,
                            remote_text: "=======".to_string(),
                            merged_line_num: None,
                            merged_text: "=======".to_string(),
                            is_conflict: true,
                        });
                        rows.push(MergeRow {
                            base_line_num: Some((b + 1) as u32),
                            base_text: base_lines[b].to_string(),
                            local_line_num: None,
                            local_text: "".to_string(),
                            remote_line_num: Some((*r_idx + 1) as u32),
                            remote_text: remote_lines[*r_idx].to_string(),
                            merged_line_num: None,
                            merged_text: remote_lines[*r_idx].to_string(),
                            is_conflict: true,
                        });
                        rows.push(MergeRow {
                            base_line_num: Some((b + 1) as u32),
                            base_text: base_lines[b].to_string(),
                            local_line_num: None,
                            local_text: "".to_string(),
                            remote_line_num: None,
                            remote_text: ">>>>>>> REMOTE".to_string(),
                            merged_line_num: None,
                            merged_text: ">>>>>>> REMOTE".to_string(),
                            is_conflict: true,
                        });
                    }
                }
                (BaseStatus::Kept(l_idx), BaseStatus::Deleted) => {
                    if local_lines[*l_idx] == base_lines[b] {
                        // Local kept, Remote deleted
                        rows.push(MergeRow {
                            base_line_num: Some((b + 1) as u32),
                            base_text: base_lines[b].to_string(),
                            local_line_num: Some((*l_idx + 1) as u32),
                            local_text: local_lines[*l_idx].to_string(),
                            remote_line_num: None,
                            remote_text: "".to_string(),
                            merged_line_num: None,
                            merged_text: "".to_string(),
                            is_conflict: false,
                        });
                    } else {
                        // Conflict: Local modified, Remote deleted
                        rows.push(MergeRow {
                            base_line_num: Some((b + 1) as u32),
                            base_text: base_lines[b].to_string(),
                            local_line_num: None,
                            local_text: "<<<<<<< LOCAL (MODIFIED)".to_string(),
                            remote_line_num: None,
                            remote_text: "".to_string(),
                            merged_line_num: None,
                            merged_text: "<<<<<<< LOCAL (MODIFIED)".to_string(),
                            is_conflict: true,
                        });
                        rows.push(MergeRow {
                            base_line_num: Some((b + 1) as u32),
                            base_text: base_lines[b].to_string(),
                            local_line_num: Some((*l_idx + 1) as u32),
                            local_text: local_lines[*l_idx].to_string(),
                            remote_line_num: None,
                            remote_text: "".to_string(),
                            merged_line_num: None,
                            merged_text: local_lines[*l_idx].to_string(),
                            is_conflict: true,
                        });
                        rows.push(MergeRow {
                            base_line_num: None,
                            base_text: "".to_string(),
                            local_line_num: None,
                            local_text: "=======".to_string(),
                            remote_line_num: None,
                            remote_text: "=======".to_string(),
                            merged_line_num: None,
                            merged_text: "=======".to_string(),
                            is_conflict: true,
                        });
                        rows.push(MergeRow {
                            base_line_num: Some((b + 1) as u32),
                            base_text: base_lines[b].to_string(),
                            local_line_num: None,
                            local_text: "".to_string(),
                            remote_line_num: None,
                            remote_text: ">>>>>>> REMOTE (DELETED)".to_string(),
                            merged_line_num: None,
                            merged_text: ">>>>>>> REMOTE (DELETED)".to_string(),
                            is_conflict: true,
                        });
                    }
                }
                (BaseStatus::Deleted, BaseStatus::Kept(r_idx)) => {
                    if remote_lines[*r_idx] == base_lines[b] {
                        // Remote kept, Local deleted
                        rows.push(MergeRow {
                            base_line_num: Some((b + 1) as u32),
                            base_text: base_lines[b].to_string(),
                            local_line_num: None,
                            local_text: "".to_string(),
                            remote_line_num: Some((*r_idx + 1) as u32),
                            remote_text: remote_lines[*r_idx].to_string(),
                            merged_line_num: None,
                            merged_text: "".to_string(),
                            is_conflict: false,
                        });
                    } else {
                        // Conflict: Local deleted, Remote modified
                        rows.push(MergeRow {
                            base_line_num: Some((b + 1) as u32),
                            base_text: base_lines[b].to_string(),
                            local_line_num: None,
                            local_text: "<<<<<<< LOCAL (DELETED)".to_string(),
                            remote_line_num: None,
                            remote_text: "".to_string(),
                            merged_line_num: None,
                            merged_text: "<<<<<<< LOCAL (DELETED)".to_string(),
                            is_conflict: true,
                        });
                        rows.push(MergeRow {
                            base_line_num: None,
                            base_text: "".to_string(),
                            local_line_num: None,
                            local_text: "=======".to_string(),
                            remote_line_num: None,
                            remote_text: "=======".to_string(),
                            merged_line_num: None,
                            merged_text: "=======".to_string(),
                            is_conflict: true,
                        });
                        rows.push(MergeRow {
                            base_line_num: Some((b + 1) as u32),
                            base_text: base_lines[b].to_string(),
                            local_line_num: None,
                            local_text: "".to_string(),
                            remote_line_num: Some((*r_idx + 1) as u32),
                            remote_text: remote_lines[*r_idx].to_string(),
                            merged_line_num: None,
                            merged_text: remote_lines[*r_idx].to_string(),
                            is_conflict: true,
                        });
                        rows.push(MergeRow {
                            base_line_num: Some((b + 1) as u32),
                            base_text: base_lines[b].to_string(),
                            local_line_num: None,
                            local_text: "".to_string(),
                            remote_line_num: None,
                            remote_text: ">>>>>>> REMOTE (MODIFIED)".to_string(),
                            merged_line_num: None,
                            merged_text: ">>>>>>> REMOTE (MODIFIED)".to_string(),
                            is_conflict: true,
                        });
                    }
                }
                (BaseStatus::Deleted, BaseStatus::Deleted) => {
                    // Both deleted
                    rows.push(MergeRow {
                        base_line_num: Some((b + 1) as u32),
                        base_text: base_lines[b].to_string(),
                        local_line_num: None,
                        local_text: "".to_string(),
                        remote_line_num: None,
                        remote_text: "".to_string(),
                        merged_line_num: None,
                        merged_text: "".to_string(),
                        is_conflict: false,
                    });
                }
            }
        }
    }

    // Assign sequential merged line numbers
    let mut merged_line_num = 1;
    for row in &mut rows {
        if !row.merged_text.is_empty() {
            row.merged_line_num = Some(merged_line_num);
            merged_line_num += 1;
        }
    }

    rows
}

#[derive(uniffi::Object)]
pub struct MergeEngine {
    rows: Mutex<Vec<MergeRow>>,
}

#[uniffi::export]
impl MergeEngine {
    #[uniffi::constructor]
    pub fn new() -> Self {
        Self {
            rows: Mutex::new(Vec::new()),
        }
    }

    pub fn perform_merge(&self, base_text: &str, local_text: &str, remote_text: &str) -> u32 {
        let rows = run_3way_merge(base_text, local_text, remote_text);
        let len = rows.len() as u32;
        let mut cache = self.rows.lock().unwrap();
        *cache = rows;
        len
    }

    pub fn get_rows_page(&self, start_index: u32, count: u32) -> Vec<MergeRow> {
        let cache = self.rows.lock().unwrap();
        let start = start_index as usize;
        if start >= cache.len() {
            return Vec::new();
        }
        let end = std::cmp::min(start + count as usize, cache.len());
        cache[start..end].to_vec()
    }

    pub fn get_all_rows(&self) -> Vec<MergeRow> {
        self.rows.lock().unwrap().clone()
    }

    pub fn update_merged_line(&self, index: u32, new_text: String) {
        let mut cache = self.rows.lock().unwrap();
        if let Some(row) = cache.get_mut(index as usize) {
            row.merged_text = new_text;
            row.is_conflict = false;
        }
        let mut merged_line_num = 1;
        for row in cache.iter_mut() {
            if !row.merged_text.is_empty() {
                row.merged_line_num = Some(merged_line_num);
                merged_line_num += 1;
            } else {
                row.merged_line_num = None;
            }
        }
    }

    pub fn has_conflicts(&self) -> bool {
        let cache = self.rows.lock().unwrap();
        cache.iter().any(|r| r.is_conflict)
    }

    pub fn get_merged_output(&self) -> String {
        let cache = self.rows.lock().unwrap();
        let lines: Vec<String> = cache.iter()
            .filter(|r| r.merged_line_num.is_some())
            .map(|r| r.merged_text.clone())
            .collect();
        lines.join("\n")
    }
}
