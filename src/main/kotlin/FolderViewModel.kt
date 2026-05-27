import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import uniffi.compare_core.FolderDiffEngine
import uniffi.compare_core.FolderDiffRow
import uniffi.compare_core.FolderDiffStatus
import uniffi.compare_core.newLocalFsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ==========================================
// 6. Folder Comparison & Virtual File System (VFS)
// ==========================================

enum class FolderFilterMode { SHOW_ALL, SHOW_DIFF, SHOW_SAME }

class FolderViewModel {
    enum class FolderAction {
        COPY_TO_RIGHT,
        COPY_TO_LEFT,
        DELETE
    }

    enum class SelectionSide {
        LEFT, RIGHT, BOTH
    }

    var leftFolderPath by mutableStateOf("")
    var rightFolderPath by mutableStateOf("")

    val leftHistory = androidx.compose.runtime.mutableStateListOf<String>()
    val rightHistory = androidx.compose.runtime.mutableStateListOf<String>()

    init {
        leftHistory.addAll(HistoryManager.getHistory("FOLDER_LEFT"))
        rightHistory.addAll(HistoryManager.getHistory("FOLDER_RIGHT"))
    }

    fun addToLeftHistory(path: String) {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return
        leftHistory.remove(trimmed)
        leftHistory.add(0, trimmed)
        HistoryManager.addToHistory("FOLDER_LEFT", trimmed)
    }

    fun addToRightHistory(path: String) {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return
        rightHistory.remove(trimmed)
        rightHistory.add(0, trimmed)
        HistoryManager.addToHistory("FOLDER_RIGHT", trimmed)
    }

    // Primary display mode (mutually exclusive)
    var filterMode by mutableStateOf(FolderFilterMode.SHOW_ALL)

    // Independent overlay toggles
    // When false -> hides LEFT_ORPHAN and RIGHT_ORPHAN entries
    var showOrphans by mutableStateOf(true)
    // When false -> hides UNIMPORTANT entries (whitespace/comment-only diffs)
    var showLessImportant by mutableStateOf(true)

    var allRows by mutableStateOf<List<FolderDiffRow>>(emptyList())
        private set

    val manualAlignments = mutableStateMapOf<String, String>()

    var dragAlignmentSourcePath by mutableStateOf<String?>(null)
    var dragAlignmentTargetPath by mutableStateOf<String?>(null)

    fun applyManualAlignments(rawRows: List<FolderDiffRow>): List<FolderDiffRow> {
        if (manualAlignments.isEmpty()) return rawRows

        val mergedList = rawRows.toMutableList()
        for ((leftPath, rightPath) in manualAlignments) {
            val leftIdx = mergedList.indexOfFirst { it.leftFile?.relativePath == leftPath }
            val rightIdx = mergedList.indexOfFirst { it.rightFile?.relativePath == rightPath }
            
            if (leftIdx != -1 && rightIdx != -1) {
                val leftRow = mergedList[leftIdx]
                val rightRow = mergedList[rightIdx]
                
                val mergedRow = FolderDiffRow(
                    relativePath = leftRow.relativePath,
                    name = "${leftRow.leftFile?.name ?: leftRow.name} ➔ ${rightRow.rightFile?.name ?: rightRow.name}",
                    level = leftRow.level,
                    isDirectory = leftRow.isDirectory,
                    leftSize = leftRow.leftSize,
                    leftModified = leftRow.leftModified,
                    rightSize = rightRow.rightSize,
                    rightModified = rightRow.rightModified,
                    status = FolderDiffStatus.MODIFIED,
                    leftFile = leftRow.leftFile,
                    rightFile = rightRow.rightFile,
                    isRenamedMatch = true,
                    matchConfidence = 1.0f,
                    isBackupMatch = false
                )
                
                if (leftIdx < rightIdx) {
                    mergedList.removeAt(rightIdx)
                    mergedList.removeAt(leftIdx)
                    mergedList.add(leftIdx, mergedRow)
                } else {
                    mergedList.removeAt(leftIdx)
                    mergedList.removeAt(rightIdx)
                    mergedList.add(rightIdx, mergedRow)
                }
            }
        }
        return mergedList
     }

    fun findOriginalName(name: String): String? {
        val oldBakRegex = Regex("""^(.+)\.(old|bak|copy)$""", RegexOption.IGNORE_CASE)
        oldBakRegex.find(name)?.let { return it.groupValues[1] }

        val vSuffixWithExt = Regex("""^(.+)[_-]v\d+\.([^.]+)$""", RegexOption.IGNORE_CASE)
        vSuffixWithExt.find(name)?.let { return "${it.groupValues[1]}.${it.groupValues[2]}" }

        val vSuffixNoExt = Regex("""^(.+)[_-]v\d+$""", RegexOption.IGNORE_CASE)
        vSuffixNoExt.find(name)?.let { return it.groupValues[1] }

        val copySuffixWithExt = Regex("""^(.+)[_-]copy\.([^.]+)$""", RegexOption.IGNORE_CASE)
        copySuffixWithExt.find(name)?.let { return "${it.groupValues[1]}.${it.groupValues[2]}" }

        val copySuffixNoExt = Regex("""^(.+)[_-]copy$""", RegexOption.IGNORE_CASE)
        copySuffixNoExt.find(name)?.let { return it.groupValues[1] }
        
        val spaceCopySuffixWithExt = Regex("""^(.+)\s-\sCopy\.([^.]+)$""", RegexOption.IGNORE_CASE)
        spaceCopySuffixWithExt.find(name)?.let { return "${it.groupValues[1]}.${it.groupValues[2]}" }

        return null
    }

    fun applySelfBackupAlignment(rawRows: List<FolderDiffRow>): List<FolderDiffRow> {
        if (leftFolderPath != rightFolderPath) return rawRows

        val fileRows = rawRows.filter { !it.isDirectory }
        val processedMerges = mutableMapOf<String, FolderDiffRow>()

        for (row in fileRows) {
            val originalName = findOriginalName(row.name) ?: continue
            val parentPath = row.relativePath.substringBeforeLast('/', "")
            val originalRelativePath = if (parentPath.isEmpty()) originalName else "$parentPath/$originalName"

            // Find if the original row exists in rawRows and is not already merged
            val originalRow = rawRows.find {
                it.relativePath == originalRelativePath && !it.isDirectory && it.relativePath !in processedMerges
            } ?: continue

            // Create the merged row
            val mergedRow = FolderDiffRow(
                relativePath = originalRow.relativePath,
                name = originalRow.name,
                level = originalRow.level,
                isDirectory = false,
                leftSize = originalRow.leftSize,
                leftModified = originalRow.leftModified,
                rightSize = row.rightSize,
                rightModified = row.rightModified,
                status = FolderDiffStatus.MODIFIED,
                leftFile = originalRow.leftFile,
                rightFile = row.rightFile,
                isRenamedMatch = false,
                matchConfidence = 1.0f,
                isBackupMatch = true
            )

            processedMerges[originalRow.relativePath] = mergedRow
            processedMerges[row.relativePath] = mergedRow
        }

        val finalRows = mutableListOf<FolderDiffRow>()
        for (row in rawRows) {
            if (processedMerges.containsKey(row.relativePath)) {
                val merged = processedMerges[row.relativePath]!!
                if (row.leftFile?.relativePath == row.relativePath && merged.leftFile?.relativePath == row.relativePath) {
                    finalRows.add(merged)
                }
            } else {
                finalRows.add(row)
            }
        }

        return finalRows
    }

    fun performManualAlignment(leftPath: String, rightPath: String) {
        manualAlignments[leftPath] = rightPath
        GlobalScope.launch(Dispatchers.IO) {
            val totalRows = folderEngine.getTotalRows().toInt()
            val rows = folderEngine.getRowsPage(0u, totalRows.toUInt())
            withContext(Dispatchers.Main) {
                allRows = applyManualAlignments(applySelfBackupAlignment(rows))
            }
        }
    }

    val collapsedPaths = mutableStateMapOf<String, Boolean>()

    var isScanning by mutableStateOf(false)
    var scanError by mutableStateOf<String?>(null)

    val selectedRows = mutableStateMapOf<String, SelectionSide>()
    var activeConfirmAction by mutableStateOf<FolderAction?>(null)

    // For drag-select coordination
    var isDraggingSelection by mutableStateOf(false)
    var dragStartPath by mutableStateOf<String?>(null)
    var dragStartSide by mutableStateOf<SelectionSide?>(null)

    private val folderEngine = FolderDiffEngine()

    fun selectRange(fromPath: String?, toPath: String?, side: SelectionSide) {
        if (fromPath == null || toPath == null) return
        val fromIndex = visibleRows.indexOfFirst { it.relativePath == fromPath }
        val toIndex = visibleRows.indexOfFirst { it.relativePath == toPath }
        if (fromIndex != -1 && toIndex != -1) {
            val min = minOf(fromIndex, toIndex)
            val max = maxOf(fromIndex, toIndex)
            selectedRows.clear()
            for (i in min..max) {
                selectedRows[visibleRows[i].relativePath] = side
            }
        }
    }

    fun startCompare(left: String, right: String) {
        leftFolderPath = left
        rightFolderPath = right
        addToLeftHistory(left)
        addToRightHistory(right)
        collapsedPaths.clear()
        selectedRows.clear()
        manualAlignments.clear()
        isScanning = true
        scanError = null

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val leftProvider = newLocalFsProvider(left)
                val rightProvider = newLocalFsProvider(right)

                folderEngine.setProviders(leftProvider, rightProvider)
                val totalRows = folderEngine.compareFolders().toInt()

                val rows = folderEngine.getRowsPage(0u, totalRows.toUInt())
                withContext(Dispatchers.Main) {
                    val alignedRows = applySelfBackupAlignment(rows)
                    allRows = applyManualAlignments(alignedRows)
                    collapsedPaths.clear()
                    alignedRows.filter { it.isDirectory }.forEach {
                        collapsedPaths[it.relativePath] = true
                    }
                    isScanning = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    scanError = e.message ?: "Unknown error"
                    isScanning = false
                }
            }
        }
    }

    fun executeFolderAction(action: FolderAction) {
        if (selectedRows.isEmpty()) return
        when (action) {
            FolderAction.COPY_TO_RIGHT -> {
                var operated = false
                selectedRows.forEach { (path, side) ->
                    if (side == SelectionSide.LEFT || side == SelectionSide.BOTH) {
                        val src = java.io.File(leftFolderPath).resolve(path)
                        val dest = java.io.File(rightFolderPath).resolve(path)
                        try {
                            src.copyRecursively(dest, overwrite = true)
                            operated = true
                        } catch (e: Exception) {
                            scanError = "Failed to copy to right: ${e.message}"
                        }
                    }
                }
                if (operated) {
                    startCompare(leftFolderPath, rightFolderPath)
                }
            }
            FolderAction.COPY_TO_LEFT -> {
                var operated = false
                selectedRows.forEach { (path, side) ->
                    if (side == SelectionSide.RIGHT || side == SelectionSide.BOTH) {
                        val src = java.io.File(rightFolderPath).resolve(path)
                        val dest = java.io.File(leftFolderPath).resolve(path)
                        try {
                            src.copyRecursively(dest, overwrite = true)
                            operated = true
                        } catch (e: Exception) {
                            scanError = "Failed to copy to left: ${e.message}"
                        }
                    }
                }
                if (operated) {
                    startCompare(leftFolderPath, rightFolderPath)
                }
            }
            FolderAction.DELETE -> {
                try {
                    var deleted = false
                    selectedRows.forEach { (path, side) ->
                        val leftFile = java.io.File(leftFolderPath).resolve(path)
                        val rightFile = java.io.File(rightFolderPath).resolve(path)
                        if (side == SelectionSide.LEFT || side == SelectionSide.BOTH) {
                            if (leftFile.exists()) {
                                leftFile.deleteRecursively()
                                deleted = true
                            }
                        }
                        if (side == SelectionSide.RIGHT || side == SelectionSide.BOTH) {
                            if (rightFile.exists()) {
                                rightFile.deleteRecursively()
                                deleted = true
                            }
                        }
                    }
                    if (deleted) {
                        startCompare(leftFolderPath, rightFolderPath)
                    }
                } catch (e: Exception) {
                    scanError = "Failed to delete: ${e.message}"
                }
            }
        }
        activeConfirmAction = null
        selectedRows.clear()
    }

    fun executeFolderActionDirectly(action: FolderAction) {
        if (selectedRows.isEmpty()) return
        var operated = false
        try {
            selectedRows.forEach { (path, side) ->
                when (action) {
                    FolderAction.COPY_TO_RIGHT -> {
                        if (side == SelectionSide.LEFT || side == SelectionSide.BOTH) {
                            val src = java.io.File(leftFolderPath).resolve(path)
                            val dest = java.io.File(rightFolderPath).resolve(path)
                            src.copyRecursively(dest, overwrite = true)
                            operated = true
                        }
                    }
                    FolderAction.COPY_TO_LEFT -> {
                        if (side == SelectionSide.RIGHT || side == SelectionSide.BOTH) {
                            val src = java.io.File(rightFolderPath).resolve(path)
                            val dest = java.io.File(leftFolderPath).resolve(path)
                            src.copyRecursively(dest, overwrite = true)
                            operated = true
                        }
                    }
                    FolderAction.DELETE -> {}
                }
            }
            if (operated) {
                startCompare(leftFolderPath, rightFolderPath)
            }
        } catch (e: Exception) {
            scanError = "Failed to execute copy sync: ${e.message}"
        }
        selectedRows.clear()
    }

    fun toggleExpand(path: String) {
        if (collapsedPaths.containsKey(path)) {
            collapsedPaths.remove(path)
        } else {
            collapsedPaths[path] = true
        }
    }

    fun isExpanded(path: String): Boolean {
        return !collapsedPaths.containsKey(path)
    }

    fun expandAll() {
        collapsedPaths.clear()
    }

    fun collapseAll() {
        collapsedPaths.clear()
        allRows.filter { it.isDirectory }.forEach {
            collapsedPaths[it.relativePath] = true
        }
    }

    val canGoUp: Boolean
        get() {
            val leftFile = java.io.File(leftFolderPath)
            val rightFile = java.io.File(rightFolderPath)
            val hasLeftParent = leftFolderPath.isNotEmpty() && leftFile.parentFile != null && leftFile.parentFile.exists()
            val hasRightParent = rightFolderPath.isNotEmpty() && rightFile.parentFile != null && rightFile.parentFile.exists()
            return hasLeftParent || hasRightParent
        }

    fun goUpOneLevel() {
        if (!canGoUp) return
        val leftFile = java.io.File(leftFolderPath)
        val rightFile = java.io.File(rightFolderPath)
        val newLeft = if (leftFolderPath.isNotEmpty() && leftFile.parentFile != null && leftFile.parentFile.exists()) leftFile.parent else leftFolderPath
        val newRight = if (rightFolderPath.isNotEmpty() && rightFile.parentFile != null && rightFile.parentFile.exists()) rightFile.parent else rightFolderPath
        startCompare(newLeft, newRight)
    }

    val visibleRows: List<FolderDiffRow>
        get() {
            return allRows.filter { row ->
                // 1. Respect tree collapse state
                isPathVisible(row.relativePath, collapsedPaths.keys) &&
                // 2. Primary mode filter (Show All / Show Diff / Show Same)
                when (filterMode) {
                    FolderFilterMode.SHOW_ALL  -> true
                    FolderFilterMode.SHOW_DIFF -> row.status != FolderDiffStatus.UNCHANGED
                    FolderFilterMode.SHOW_SAME -> row.status == FolderDiffStatus.UNCHANGED
                } &&
                // 3. Orphan toggle: when OFF, hide left-only and right-only entries
                (showOrphans || (row.status != FolderDiffStatus.LEFT_ORPHAN &&
                                 row.status != FolderDiffStatus.RIGHT_ORPHAN))
                // Note: showLessImportant is wired up for future UNIMPORTANT status
                // (Rust backend does not yet emit this status; toggle is ready for when it does)
            }
        }

    private fun isPathVisible(path: String, collapsed: Set<String>): Boolean {
        val parts = path.split("/")
        var current = ""
        for (i in 0 until parts.size - 1) {
            current = if (current.isEmpty()) parts[i] else "$current/${parts[i]}"
            if (collapsed.contains(current)) {
                return false
            }
        }
        return true
    }
}
