use std::sync::Mutex;
use std::collections::HashMap;
use std::thread;

use crate::common::{read_file_to_string, DiffStatus, AlgorithmType, TokenType, TextSpan, DiffLine};

// ==========================================
// Algorithm Strategy Pattern (Trait)
// ==========================================

pub trait DiffStrategy: Send + Sync {
    /// Compares two strings and generates a vector of DiffLine records.
    fn compare(&self, old_text: &str, new_text: &str) -> Vec<DiffLine>;
}

// ==========================================
// Custom High-Performance Lexer
// ==========================================

#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) struct TokenSpan {
    pub(crate) start: u32,
    pub(crate) end: u32,
    pub(crate) token_type: TokenType,
}

pub(crate) fn tokenize_line(text: &str) -> Vec<TokenSpan> {
    let mut spans = Vec::new();
    let chars: Vec<char> = text.chars().collect();
    let n = chars.len();
    
    let keywords = [
        "class", "interface", "fun", "val", "var", "package", "import", 
        "private", "public", "protected", "override", "return", "try", 
        "catch", "fn", "let", "pub", "use", "struct", "enum", "impl", 
        "match", "if", "else", "for", "while", "in", "as", "void", "static"
    ];
    
    let mut i = 0;
    while i < n {
        // 1. Comment check (single line)
        if i + 1 < n && chars[i] == '/' && chars[i+1] == '/' {
            spans.push(TokenSpan {
                start: i as u32,
                end: n as u32,
                token_type: TokenType::Comment,
            });
            break;
        }
        
        // 2. String literal
        if chars[i] == '"' || chars[i] == '\'' {
            let quote = chars[i];
            let start = i;
            i += 1;
            while i < n && chars[i] != quote {
                if chars[i] == '\\' && i + 1 < n {
                    i += 2;
                } else {
                    i += 1;
                }
            }
            if i < n {
                i += 1;
            }
            spans.push(TokenSpan {
                start: start as u32,
                end: i as u32,
                token_type: TokenType::String,
            });
            continue;
        }
        
        // 3. Numeric literal
        if chars[i].is_ascii_digit() {
            let start = i;
            i += 1;
            while i < n && (chars[i].is_ascii_digit() || chars[i] == '.') {
                i += 1;
            }
            spans.push(TokenSpan {
                start: start as u32,
                end: i as u32,
                token_type: TokenType::Number,
            });
            continue;
        }
        
        // 4. Keywords & Identifiers
        if chars[i].is_alphabetic() || chars[i] == '_' {
            let start = i;
            i += 1;
            while i < n && (chars[i].is_alphanumeric() || chars[i] == '_') {
                i += 1;
            }
            let word: String = chars[start..i].iter().collect();
            let is_keyword = keywords.contains(&word.as_str());
            spans.push(TokenSpan {
                start: start as u32,
                end: i as u32,
                token_type: if is_keyword { TokenType::Keyword } else { TokenType::Normal },
            });
            continue;
        }
        
        // 5. Operators / punctuation
        let start = i;
        i += 1;
        spans.push(TokenSpan {
            start: start as u32,
            end: i as u32,
            token_type: TokenType::Normal,
        });
    }
    
    spans
}

// ==========================================
// Linear Sweep Intersection Algorithm
// ==========================================

pub(crate) fn merge_spans(
    diff_spans: &[TextSpan],
    token_spans: &[TokenSpan]
) -> Vec<TextSpan> {
    let mut merged = Vec::new();
    let mut i = 0;
    let mut j = 0;
    
    while i < diff_spans.len() && j < token_spans.len() {
        let ds = &diff_spans[i];
        let ts = &token_spans[j];
        
        let start = std::cmp::max(ds.start, ts.start);
        let end = std::cmp::min(ds.end, ts.end);
        
        if start < end {
            merged.push(TextSpan {
                start,
                end,
                is_changed: ds.is_changed,
                is_right: ds.is_right,
                token_type: ts.token_type,
            });
        }
        
        if ds.end < ts.end {
            i += 1;
        } else if ts.end < ds.end {
            j += 1;
        } else {
            i += 1;
            j += 1;
        }
    }
    merged
}

// ==========================================
// Myers Diff Core Engine Implementation
// ==========================================

#[derive(Clone, Debug, PartialEq)]
pub enum Edit {
    Keep(usize, usize), // (index in old, index in new)
    Delete(usize),      // index in old
    Add(usize),         // index in new
}

pub fn tokenize_lines(text: &str) -> Vec<&str> {
    if text.is_empty() {
        return Vec::new();
    }
    text.split('\n')
        .map(|line| line.strip_suffix('\r').unwrap_or(line))
        .collect()
}

pub fn lines_to_ids(old_lines: &[&str], new_lines: &[&str]) -> (Vec<u32>, Vec<u32>) {
    let mut map = HashMap::new();
    let mut next_id = 0;

    let mut get_id = |line: &str| {
        *map.entry(line.to_string()).or_insert_with(|| {
            let id = next_id;
            next_id += 1;
            id
        })
    };

    let old_ids = old_lines.iter().map(|&l| get_id(l)).collect();
    let new_ids = new_lines.iter().map(|&l| get_id(l)).collect();
    (old_ids, new_ids)
}

pub fn diff<T: PartialEq>(a: &[T], b: &[T]) -> Vec<Edit> {
    let n = a.len();
    let m = b.len();
    if n == 0 && m == 0 {
        return Vec::new();
    }
    if n == 0 {
        return (0..m).map(Edit::Add).collect();
    }
    if m == 0 {
        return (0..n).map(Edit::Delete).collect();
    }
    
    let max = (n + m) as i32;
    let mut v = vec![0; (2 * max + 1) as usize];
    let mut trace = Vec::new();

    for d in 0..=max {
        trace.push(v.clone());

        let mut k = -d;
        while k <= d {
            let idx = (k + max) as usize;
            let mut x = if k == -d || (k != d && v[idx - 1] < v[idx + 1]) {
                v[idx + 1]
            } else {
                v[idx - 1] + 1
            };
            let mut y = (x as i32 - k) as usize;

            while x < n && y < m && a[x] == b[y] {
                x += 1;
                y += 1;
            }

            v[idx] = x;

            if x >= n && y >= m {
                return backtrack(a, b, &trace);
            }
            k += 2;
        }
    }
    Vec::new()
}

fn backtrack<T>(a: &[T], b: &[T], trace: &[Vec<usize>]) -> Vec<Edit> {
    let mut edits = Vec::new();
    let mut x = a.len() as i32;
    let mut y = b.len() as i32;
    let max = (a.len() + b.len()) as i32;

    for (d, v) in trace.iter().enumerate().rev() {
        let d = d as i32;
        if d == 0 {
            break;
        }
        let k = x - y;
        let idx = (k + max) as usize;
        
        let prev_k = if k == -d || (k != d && v[idx - 1] < v[idx + 1]) {
            k + 1
        } else {
            k - 1
        };

        let prev_idx = (prev_k + max) as usize;
        let prev_x = v[prev_idx] as i32;
        let prev_y = prev_x - prev_k;

        while x > prev_x && y > prev_y {
            x -= 1;
            y -= 1;
            edits.push(Edit::Keep(x as usize, y as usize));
        }

        if x > prev_x {
            x -= 1;
            edits.push(Edit::Delete(x as usize));
        } else if y > prev_y {
            y -= 1;
            edits.push(Edit::Add(y as usize));
        }
    }

    while x > 0 && y > 0 {
        x -= 1;
        y -= 1;
        edits.push(Edit::Keep(x as usize, y as usize));
    }

    edits.reverse();
    edits
}

fn compute_intra_line_diff_raw(left_text: &str, right_text: &str) -> Vec<TextSpan> {
    let left_chars: Vec<char> = left_text.chars().collect();
    let right_chars: Vec<char> = right_text.chars().collect();

    let edits = diff(&left_chars, &right_chars);

    let mut left_spans = Vec::new();
    let mut right_spans = Vec::new();

    let mut left_idx = 0;
    let mut right_idx = 0;

    let mut i = 0;
    while i < edits.len() {
        match edits[i] {
            Edit::Keep(_, _) => {
                let start_l = left_idx;
                let start_r = right_idx;
                while i < edits.len() {
                    if let Edit::Keep(_, _) = edits[i] {
                        left_idx += 1;
                        right_idx += 1;
                        i += 1;
                    } else {
                        break;
                    }
                }
                left_spans.push(TextSpan {
                    start: start_l as u32,
                    end: left_idx as u32,
                    is_changed: false,
                    is_right: false,
                    token_type: TokenType::Normal,
                });
                right_spans.push(TextSpan {
                    start: start_r as u32,
                    end: right_idx as u32,
                    is_changed: false,
                    is_right: true,
                    token_type: TokenType::Normal,
                });
            }
            Edit::Delete(_) => {
                let start_l = left_idx;
                while i < edits.len() {
                    if let Edit::Delete(_) = edits[i] {
                        left_idx += 1;
                        i += 1;
                    } else {
                        break;
                    }
                }
                left_spans.push(TextSpan {
                    start: start_l as u32,
                    end: left_idx as u32,
                    is_changed: true,
                    is_right: false,
                    token_type: TokenType::Normal,
                });
            }
            Edit::Add(_) => {
                let start_r = right_idx;
                while i < edits.len() {
                    if let Edit::Add(_) = edits[i] {
                        right_idx += 1;
                        i += 1;
                    } else {
                        break;
                    }
                }
                right_spans.push(TextSpan {
                    start: start_r as u32,
                    end: right_idx as u32,
                    is_changed: true,
                    is_right: true,
                    token_type: TokenType::Normal,
                });
            }
        }
    }

    let mut all_spans = Vec::new();
    all_spans.extend(left_spans);
    all_spans.extend(right_spans);
    all_spans
}

pub struct UnimportantDiffClassifier;

impl UnimportantDiffClassifier {
    pub fn is_unimportant(left: &str, right: &str) -> bool {
        let left_stripped = Self::strip_comments_and_whitespace(left);
        let right_stripped = Self::strip_comments_and_whitespace(right);
        left_stripped == right_stripped
    }

    fn strip_comments_and_whitespace(text: &str) -> String {
        let chars: Vec<char> = text.chars().collect();
        let mut result = String::new();
        let mut i = 0;
        let mut in_string = None;
        while i < chars.len() {
            if let Some(q) = in_string {
                if chars[i] == '\\' && i + 1 < chars.len() {
                    i += 2;
                } else {
                    if chars[i] == q {
                        in_string = None;
                    }
                    if !chars[i].is_whitespace() {
                        result.push(chars[i]);
                    }
                    i += 1;
                }
            } else {
                if i + 1 < chars.len() && chars[i] == '/' && chars[i+1] == '/' {
                    break;
                } else if i + 1 < chars.len() && chars[i] == '/' && chars[i+1] == '*' {
                    i += 2;
                    while i < chars.len() {
                        if i + 1 < chars.len() && chars[i] == '*' && chars[i+1] == '/' {
                            i += 2;
                            break;
                        }
                        i += 1;
                    }
                } else {
                    if chars[i] == '"' || chars[i] == '\'' {
                        in_string = Some(chars[i]);
                    }
                    if !chars[i].is_whitespace() {
                        result.push(chars[i]);
                    }
                    i += 1;
                }
            }
        }
        result
    }
}

pub(crate) fn align_edits(
    old_lines: &[&str],
    new_lines: &[&str],
    edits: &[Edit],
    old_line_tokens: &[Vec<TokenSpan>],
    new_line_tokens: &[Vec<TokenSpan>]
) -> Vec<DiffLine> {
    let mut rows = Vec::new();
    let mut i = 0;

    let token_spans_to_text_spans = |tokens: &[TokenSpan], is_right: bool, is_changed: bool| -> Vec<TextSpan> {
        tokens.iter().map(|t| TextSpan {
            start: t.start,
            end: t.end,
            is_changed,
            is_right,
            token_type: t.token_type,
        }).collect()
    };

    while i < edits.len() {
        let mut delete_indices = Vec::new();
        let mut add_indices = Vec::new();

        let mut j = i;
        while j < edits.len() {
            match edits[j] {
                Edit::Delete(idx) => {
                    if !add_indices.is_empty() {
                        break;
                    }
                    delete_indices.push(idx);
                }
                Edit::Add(idx) => {
                    add_indices.push(idx);
                }
                Edit::Keep(_, _) => {
                    break;
                }
            }
            j += 1;
        }

        if !delete_indices.is_empty() || !add_indices.is_empty() {
            let num_deletes = delete_indices.len();
            let num_adds = add_indices.len();
            let num_modified = std::cmp::min(num_deletes, num_adds);

            for k in 0..num_modified {
                let left_idx = delete_indices[k];
                let right_idx = add_indices[k];
                let left_text = old_lines[left_idx].to_string();
                let right_text = new_lines[right_idx].to_string();

                let raw_diff_spans = compute_intra_line_diff_raw(&left_text, &right_text);
                
                let left_diff: Vec<TextSpan> = raw_diff_spans.iter().filter(|s| !s.is_right).cloned().collect();
                let right_diff: Vec<TextSpan> = raw_diff_spans.iter().filter(|s| s.is_right).cloned().collect();

                let left_merged = merge_spans(&left_diff, &old_line_tokens[left_idx]);
                let right_merged = merge_spans(&right_diff, &new_line_tokens[right_idx]);

                let mut inline_changes = Vec::new();
                inline_changes.extend(left_merged);
                inline_changes.extend(right_merged);

                let is_unimportant = UnimportantDiffClassifier::is_unimportant(&left_text, &right_text);
                let status = if is_unimportant {
                    DiffStatus::Unimportant
                } else {
                    DiffStatus::Modified
                };

                rows.push(DiffLine {
                    left_line_num: Some((left_idx + 1) as u32),
                    left_text,
                    right_line_num: Some((right_idx + 1) as u32),
                    right_text,
                    status,
                    inline_changes: Some(inline_changes),
                });
            }

            for k in num_modified..num_deletes {
                let left_idx = delete_indices[k];
                let inline_changes = token_spans_to_text_spans(&old_line_tokens[left_idx], false, false);
                rows.push(DiffLine {
                    left_line_num: Some((left_idx + 1) as u32),
                    left_text: old_lines[left_idx].to_string(),
                    right_line_num: None,
                    right_text: "".to_string(),
                    status: DiffStatus::Deleted,
                    inline_changes: Some(inline_changes),
                });
            }

            for k in num_modified..num_adds {
                let right_idx = add_indices[k];
                let inline_changes = token_spans_to_text_spans(&new_line_tokens[right_idx], true, false);
                rows.push(DiffLine {
                    left_line_num: None,
                    left_text: "".to_string(),
                    right_line_num: Some((right_idx + 1) as u32),
                    right_text: new_lines[right_idx].to_string(),
                    status: DiffStatus::Added,
                    inline_changes: Some(inline_changes),
                });
            }

            i = j;
        } else {
            if let Edit::Keep(idx_a, idx_b) = edits[i] {
                let mut inline_changes = Vec::new();
                inline_changes.extend(token_spans_to_text_spans(&old_line_tokens[idx_a], false, false));
                inline_changes.extend(token_spans_to_text_spans(&new_line_tokens[idx_b], true, false));

                rows.push(DiffLine {
                    left_line_num: Some((idx_a + 1) as u32),
                    left_text: old_lines[idx_a].to_string(),
                    right_line_num: Some((idx_b + 1) as u32),
                    right_text: new_lines[idx_b].to_string(),
                    status: DiffStatus::Unchanged,
                    inline_changes: Some(inline_changes),
                });
            }
            i += 1;
        }
    }

    rows
}

// ==========================================
// Algorithm Strategy Implementations
// ==========================================

pub struct MyersAlgorithm;

impl DiffStrategy for MyersAlgorithm {
    fn compare(&self, old_text: &str, new_text: &str) -> Vec<DiffLine> {
        let old_text_clone = old_text.to_string();
        let new_text_clone = new_text.to_string();

        // Multi-threaded syntax highlight parsing (Rayon/std::thread)
        let handle_old_lexer = thread::spawn(move || {
            let lines = tokenize_lines(&old_text_clone);
            lines.into_iter().map(|line| tokenize_line(line)).collect::<Vec<_>>()
        });

        let handle_new_lexer = thread::spawn(move || {
            let lines = tokenize_lines(&new_text_clone);
            lines.into_iter().map(|line| tokenize_line(line)).collect::<Vec<_>>()
        });

        let old_lines = tokenize_lines(old_text);
        let new_lines = tokenize_lines(new_text);
        
        let (old_ids, new_ids) = lines_to_ids(&old_lines, &new_lines);
        let edits = diff(&old_ids, &new_ids);
        
        let old_line_tokens = handle_old_lexer.join().unwrap();
        let new_line_tokens = handle_new_lexer.join().unwrap();

        align_edits(&old_lines, &new_lines, &edits, &old_line_tokens, &new_line_tokens)
    }
}

fn patience_diff(old_lines: &[&str], new_lines: &[&str]) -> Vec<Edit> {
    let mut edits = Vec::new();
    patience_diff_slice(old_lines, 0, new_lines, 0, &mut edits);
    edits
}

fn patience_diff_slice(
    old_lines: &[&str],
    old_offset: usize,
    new_lines: &[&str],
    new_offset: usize,
    edits: &mut Vec<Edit>,
) {
    if old_lines.is_empty() && new_lines.is_empty() {
        return;
    }
    if old_lines.is_empty() {
        for i in 0..new_lines.len() {
            edits.push(Edit::Add(new_offset + i));
        }
        return;
    }
    if new_lines.is_empty() {
        for i in 0..old_lines.len() {
            edits.push(Edit::Delete(old_offset + i));
        }
        return;
    }

    // 1. Find unique common lines in the current slices
    let mut old_counts = HashMap::new();
    for &line in old_lines {
        *old_counts.entry(line).or_insert(0) += 1;
    }

    let mut new_counts = HashMap::new();
    for &line in new_lines {
        *new_counts.entry(line).or_insert(0) += 1;
    }

    let mut unique_common = Vec::new();
    for (i, &line) in old_lines.iter().enumerate() {
        if old_counts.get(line) == Some(&1) && new_counts.get(line) == Some(&1) {
            if let Some(j) = new_lines.iter().position(|&x| x == line) {
                unique_common.push((i, j));
            }
        }
    }

    if unique_common.is_empty() {
        // Fallback to Myers Diff
        let (old_ids, new_ids) = lines_to_ids(old_lines, new_lines);
        let myers_edits = diff(&old_ids, &new_ids);
        for edit in myers_edits {
            match edit {
                Edit::Keep(o, n) => edits.push(Edit::Keep(old_offset + o, new_offset + n)),
                Edit::Delete(o) => edits.push(Edit::Delete(old_offset + o)),
                Edit::Add(n) => edits.push(Edit::Add(new_offset + n)),
            }
        }
        return;
    }

    // 2. Find Longest Increasing Subsequence (LIS) on the second indices
    let seq: Vec<usize> = unique_common.iter().map(|&(_, j)| j).collect();
    let lis_indices = longest_increasing_subsequence(&seq);

    // 3. Match and recurse around the LIS elements (anchors)
    let mut last_old = 0;
    let mut last_new = 0;

    for idx in lis_indices {
        let (o_idx, n_idx) = unique_common[idx];
        
        patience_diff_slice(
            &old_lines[last_old..o_idx],
            old_offset + last_old,
            &new_lines[last_new..n_idx],
            new_offset + last_new,
            edits,
        );

        edits.push(Edit::Keep(old_offset + o_idx, new_offset + n_idx));

        last_old = o_idx + 1;
        last_new = n_idx + 1;
    }

    patience_diff_slice(
        &old_lines[last_old..],
        old_offset + last_old,
        &new_lines[last_new..],
        new_offset + last_new,
        edits,
    );
}

fn longest_increasing_subsequence(seq: &[usize]) -> Vec<usize> {
    if seq.is_empty() {
        return Vec::new();
    }

    let n = seq.len();
    let mut tails = Vec::new();
    let mut tail_indices = Vec::new();
    let mut parent = vec![0; n];

    for i in 0..n {
        let x = seq[i];
        let pos = tails.binary_search(&x).unwrap_or_else(|e| e);

        if pos == tails.len() {
            tails.push(x);
            tail_indices.push(i);
        } else {
            tails[pos] = x;
            tail_indices[pos] = i;
        }

        if pos > 0 {
            parent[i] = tail_indices[pos - 1];
        }
    }

    let mut lis = Vec::new();
    if !tail_indices.is_empty() {
        let mut curr = tail_indices[tails.len() - 1];
        for _ in 0..tails.len() {
            lis.push(curr);
            curr = parent[curr];
        }
        lis.reverse();
    }
    lis
}

pub struct SimpleLinearAlgorithm;

impl DiffStrategy for SimpleLinearAlgorithm {
    fn compare(&self, old_text: &str, new_text: &str) -> Vec<DiffLine> {
        let old_text_clone = old_text.to_string();
        let new_text_clone = new_text.to_string();

        let handle_old_lexer = thread::spawn(move || {
            let lines = tokenize_lines(&old_text_clone);
            lines.into_iter().map(|line| tokenize_line(line)).collect::<Vec<_>>()
        });

        let handle_new_lexer = thread::spawn(move || {
            let lines = tokenize_lines(&new_text_clone);
            lines.into_iter().map(|line| tokenize_line(line)).collect::<Vec<_>>()
        });

        let old_lines = tokenize_lines(old_text);
        let new_lines = tokenize_lines(new_text);
        
        let edits = patience_diff(&old_lines, &new_lines);
        
        let old_line_tokens = handle_old_lexer.join().unwrap();
        let new_line_tokens = handle_new_lexer.join().unwrap();

        align_edits(&old_lines, &new_lines, &edits, &old_line_tokens, &new_line_tokens)
    }
}

// ==========================================
// Error Definition (UniFFI Friendly)
// ==========================================

#[derive(Debug, Clone, uniffi::Error)]
pub enum DiffError {
    FileError { reason: String },
}

impl std::fmt::Display for DiffError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            DiffError::FileError { reason } => write!(f, "File error: {}", reason),
        }
    }
}

impl std::error::Error for DiffError {}

// ==========================================
// Core Controller (Engine) - Thread-Safe & Cached
// ==========================================

#[derive(uniffi::Object)]
pub struct DiffEngine {
    strategy: Mutex<Box<dyn DiffStrategy>>,
    cached_lines: Mutex<Option<Vec<DiffLine>>>,
}

#[uniffi::export]
impl DiffEngine {
    #[uniffi::constructor]
    pub fn new() -> Self {
        Self {
            strategy: Mutex::new(Box::new(MyersAlgorithm)),
            cached_lines: Mutex::new(None),
        }
    }

    pub fn set_algorithm(&self, algo_type: AlgorithmType) {
        let mut strategy = self.strategy.lock().unwrap();
        *strategy = match algo_type {
            AlgorithmType::Myers => Box::new(MyersAlgorithm),
            AlgorithmType::SimpleLinear => Box::new(SimpleLinearAlgorithm),
        };
    }

    /// Read files, compare, cache in Rust memory, and return total lines count
    pub fn compare_files(&self, path_a: String, path_b: String) -> Result<u32, DiffError> {
        let old_text = read_file_to_string(&path_a)
            .map_err(|e| DiffError::FileError { reason: format!("Failed to read source file '{}': {}", path_a, e) })?;
        let new_text = read_file_to_string(&path_b)
            .map_err(|e| DiffError::FileError { reason: format!("Failed to read modified file '{}': {}", path_b, e) })?;

        let strategy = self.strategy.lock().unwrap();
        let result = strategy.compare(&old_text, &new_text);
        let total_lines = result.len() as u32;

        let mut cache = self.cached_lines.lock().unwrap();
        *cache = Some(result);

        Ok(total_lines)
    }

    /// Expose total calculated lines count
    pub fn get_total_lines(&self) -> u32 {
        let cache = self.cached_lines.lock().unwrap();
        cache.as_ref().map(|lines| lines.len() as u32).unwrap_or(0)
    }

    /// Expose subset (page) of computed diff lines
    pub fn get_lines_page(&self, start_index: u32, count: u32) -> Vec<DiffLine> {
        let cache = self.cached_lines.lock().unwrap();
        if let Some(ref lines) = *cache {
            let start = start_index as usize;
            if start >= lines.len() {
                return Vec::new();
            }
            let end = std::cmp::min(start + count as usize, lines.len());
            lines[start..end].to_vec()
        } else {
            Vec::new()
        }
    }
}
