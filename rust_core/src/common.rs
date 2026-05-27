use std::fs::File;
use std::io::Read;

pub fn read_file_to_string(path: &str) -> std::io::Result<String> {
    let mut file = File::open(path)?;
    let mut bytes = Vec::new();
    file.read_to_end(&mut bytes)?;

    // 1. Detect UTF-16 BOM
    let decoded = if bytes.len() >= 2 && bytes[0] == 0xFF && bytes[1] == 0xFE {
        // UTF-16 LE
        let u16_chars: Vec<u16> = bytes[2..]
            .chunks_exact(2)
            .map(|chunk| u16::from_le_bytes([chunk[0], chunk[1]]))
            .collect();
        String::from_utf16(&u16_chars).unwrap_or_else(|_| String::from_utf8_lossy(&bytes).into_owned())
    } else if bytes.len() >= 2 && bytes[0] == 0xFE && bytes[1] == 0xFF {
        // UTF-16 BE
        let u16_chars: Vec<u16> = bytes[2..]
            .chunks_exact(2)
            .map(|chunk| u16::from_be_bytes([chunk[0], chunk[1]]))
            .collect();
        String::from_utf16(&u16_chars).unwrap_or_else(|_| String::from_utf8_lossy(&bytes).into_owned())
    } else if let Ok(s) = String::from_utf8(bytes.clone()) {
        s
    } else {
        // Try Big5 (Taiwan Traditional Chinese)
        let (decoded_big5, _, had_errors_big5) = encoding_rs::BIG5.decode(&bytes);
        if !had_errors_big5 {
            decoded_big5.into_owned()
        } else {
            // Try GBK/GB18030 (Simplified Chinese)
            let (decoded_gbk, _, had_errors_gbk) = encoding_rs::GB18030.decode(&bytes);
            if !had_errors_gbk {
                decoded_gbk.into_owned()
            } else {
                String::from_utf8_lossy(&bytes).into_owned()
            }
        }
    };

    Ok(decoded.replace("\r\n", "\n"))
}

#[derive(uniffi::Enum, Clone, Copy, Debug, PartialEq)]
pub enum DiffStatus {
    Unchanged,
    Added,
    Deleted,
    Modified,
    Unimportant,
}

#[derive(uniffi::Enum, Clone, Copy, Debug, PartialEq)]
pub enum AlgorithmType {
    Myers,
    SimpleLinear,
}

#[derive(uniffi::Enum, Clone, Copy, Debug, PartialEq)]
pub enum TokenType {
    Normal,
    Keyword,
    Comment,
    String,
    Number,
}

#[derive(uniffi::Record, Clone, Debug)]
pub struct TextSpan {
    pub start: u32,
    pub end: u32,
    pub is_changed: bool,
    pub is_right: bool,
    pub token_type: TokenType,
}

#[derive(uniffi::Record, Clone, Debug)]
pub struct DiffLine {
    pub left_line_num: Option<u32>,
    pub left_text: String,
    pub right_line_num: Option<u32>,
    pub right_text: String,
    pub status: DiffStatus,
    pub inline_changes: Option<Vec<TextSpan>>,
}
