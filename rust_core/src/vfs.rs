use std::sync::Arc;

#[derive(uniffi::Record, Clone, Debug)]
pub struct FileMetadata {
    pub size: u64,
    pub modified_time: u64,
    pub is_directory: bool,
}

#[derive(uniffi::Record, Clone, Debug)]
pub struct VfsNode {
    pub relative_path: String,
    pub name: String,
    pub metadata: FileMetadata,
}

#[derive(Debug, Clone, uniffi::Error)]
pub enum VfsError {
    IoError { reason: String },
    PathNotFound { reason: String },
}

impl std::fmt::Display for VfsError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            VfsError::IoError { reason } => write!(f, "IO Error: {}", reason),
            VfsError::PathNotFound { reason } => write!(f, "Path not found: {}", reason),
        }
    }
}

impl std::error::Error for VfsError {}

#[uniffi::export(with_foreign)]
pub trait VfsProvider: Send + Sync {
    fn list_directory(&self, path: String) -> Result<Vec<VfsNode>, VfsError>;
    fn read_file_content(&self, path: String) -> Result<Vec<u8>, VfsError>;
}

// ------------------------------------------
// Local Filesystem Provider
// ------------------------------------------

#[derive(uniffi::Object)]
pub struct LocalFsProvider {
    root_path: String,
}

#[uniffi::export]
impl LocalFsProvider {
    #[uniffi::constructor]
    pub fn new(root_path: String) -> Self {
        Self { root_path }
    }
}

impl VfsProvider for LocalFsProvider {
    fn list_directory(&self, path: String) -> Result<Vec<VfsNode>, VfsError> {
        let full_path = if path.is_empty() {
            std::path::PathBuf::from(&self.root_path)
        } else {
            std::path::PathBuf::from(&self.root_path).join(&path)
        };
        
        let entries = std::fs::read_dir(&full_path)
            .map_err(|e| VfsError::IoError { reason: e.to_string() })?;
            
        let mut nodes = Vec::new();
        for entry in entries {
            let entry = entry.map_err(|e| VfsError::IoError { reason: e.to_string() })?;
            let metadata = entry.metadata()
                .map_err(|e| VfsError::IoError { reason: e.to_string() })?;
            let name = entry.file_name().to_string_lossy().to_string();
            let relative_path = if path.is_empty() {
                name.clone()
            } else {
                format!("{}/{}", path, name)
            };
            
            let modified_time = metadata.modified()
                .unwrap_or(std::time::SystemTime::UNIX_EPOCH)
                .duration_since(std::time::SystemTime::UNIX_EPOCH)
                .unwrap_or_default()
                .as_secs();
                
            nodes.push(VfsNode {
                relative_path,
                name,
                metadata: FileMetadata {
                    size: metadata.len(),
                    modified_time,
                    is_directory: metadata.is_dir(),
                },
            });
        }
        Ok(nodes)
    }

    fn read_file_content(&self, path: String) -> Result<Vec<u8>, VfsError> {
        let full_path = std::path::PathBuf::from(&self.root_path).join(path);
        std::fs::read(full_path).map_err(|e| VfsError::IoError { reason: e.to_string() })
    }
}

// ------------------------------------------
// Zip Filesystem Provider
// ------------------------------------------

#[derive(uniffi::Object)]
pub struct ZipFsProvider {
    archive_bytes: Vec<u8>,
}

#[uniffi::export]
impl ZipFsProvider {
    #[uniffi::constructor]
    pub fn new(archive_bytes: Vec<u8>) -> Self {
        Self { archive_bytes }
    }
}

impl VfsProvider for ZipFsProvider {
    fn list_directory(&self, path: String) -> Result<Vec<VfsNode>, VfsError> {
        let reader = std::io::Cursor::new(&self.archive_bytes);
        let mut archive = zip::ZipArchive::new(reader)
            .map_err(|e| VfsError::IoError { reason: e.to_string() })?;
            
        let mut nodes = Vec::new();
        let normalized_path = if path.is_empty() {
            "".to_string()
        } else if path.ends_with('/') {
            path.clone()
        } else {
            format!("{}/", path)
        };
        
        for i in 0..archive.len() {
            let file = archive.by_index(i)
                .map_err(|e| VfsError::IoError { reason: e.to_string() })?;
            let file_name = file.name();
            
            if file_name.starts_with(&normalized_path) && file_name != normalized_path {
                let remainder = &file_name[normalized_path.len()..];
                let parts: Vec<&str> = remainder.split('/').collect();
                if parts.is_empty() || parts[0].is_empty() {
                    continue;
                }
                
                let child_name = parts[0];
                let is_dir = parts.len() > 1 || file.is_dir();
                let child_relative = if path.is_empty() {
                    child_name.to_string()
                } else {
                    format!("{}{}", normalized_path, child_name)
                };
                
                if nodes.iter().any(|n: &VfsNode| n.name == child_name) {
                    continue;
                }
                
                nodes.push(VfsNode {
                    relative_path: child_relative,
                    name: child_name.to_string(),
                    metadata: FileMetadata {
                        size: if is_dir { 0 } else { file.size() },
                        modified_time: 0,
                        is_directory: is_dir,
                    },
                });
            }
        }
        
        Ok(nodes)
    }

    fn read_file_content(&self, path: String) -> Result<Vec<u8>, VfsError> {
        let reader = std::io::Cursor::new(&self.archive_bytes);
        let mut archive = zip::ZipArchive::new(reader)
            .map_err(|e| VfsError::IoError { reason: e.to_string() })?;
        let mut file = archive.by_name(&path)
            .map_err(|_e| VfsError::PathNotFound { reason: format!("Path '{}' not found in zip", path) })?;
        let mut content = Vec::new();
        std::io::Read::read_to_end(&mut file, &mut content)
            .map_err(|e| VfsError::IoError { reason: e.to_string() })?;
        Ok(content)
    }
}

#[uniffi::export]
pub fn new_local_fs_provider(root_path: String) -> Arc<dyn VfsProvider> {
    Arc::new(LocalFsProvider::new(root_path))
}

#[uniffi::export]
pub fn new_zip_fs_provider(archive_bytes: Vec<u8>) -> Arc<dyn VfsProvider> {
    Arc::new(ZipFsProvider::new(archive_bytes))
}
