import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uniffi.compare_core.MergeEngine
import uniffi.compare_core.MergeRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ==========================================
// 7. 3-Way Merge Engine Integration
// ==========================================

class MergeViewModel {
    var basePath by mutableStateOf("")
    var localPath by mutableStateOf("")
    var remotePath by mutableStateOf("")
    var outputPath by mutableStateOf("")

    var mergeRows by mutableStateOf<List<MergeRow>>(emptyList())
    var isMerging by mutableStateOf(false)
    var errorMsg by mutableStateOf<String?>(null)
    var hasConflicts by mutableStateOf(false)

    val mergeEngine = MergeEngine()

    fun startMerge(local: String, remote: String, base: String, output: String) {
        localPath = local
        remotePath = remote
        basePath = base
        outputPath = output
        isMerging = true
        errorMsg = null

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val localText = File(local).readTextWithFallback()
                val remoteText = File(remote).readTextWithFallback()
                val baseText = File(base).readTextWithFallback()

                val totalRows = mergeEngine.performMerge(baseText, localText, remoteText).toInt()
                val rows = mergeEngine.getRowsPage(0u, totalRows.toUInt())
                val conflicts = mergeEngine.hasConflicts()

                withContext(Dispatchers.Main) {
                    mergeRows = rows
                    hasConflicts = conflicts
                    isMerging = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMsg = e.message ?: "Failed to read files or perform merge"
                    isMerging = false
                }
            }
        }
    }

    private fun File.readTextWithFallback(): String {
        try {
            return this.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            // Fallback to Big5
        }
        try {
            return this.readText(java.nio.charset.Charset.forName("Big5"))
        } catch (e: Exception) {
            // Fallback to UTF-16
        }
        try {
            return this.readText(Charsets.UTF_16)
        } catch (e: Exception) {
            // Fallback to ISO_8859_1
        }
        return this.readText(Charsets.ISO_8859_1)
    }

    fun resolveConflict(rowIndex: Int, choice: String) {
        var start = rowIndex
        while (start > 0 && mergeRows[start - 1].isConflict) {
            start--
        }
        var end = rowIndex
        while (end < mergeRows.size - 1 && mergeRows[end + 1].isConflict) {
            end++
        }

        val localLines = mutableListOf<String>()
        val remoteLines = mutableListOf<String>()
        for (i in start..end) {
            val r = mergeRows[i]
            if (r.localText == "<<<<<<< LOCAL" || r.localText == "=======" || r.localText == ">>>>>>> REMOTE" ||
                r.remoteText == "<<<<<<< LOCAL" || r.remoteText == "=======" || r.remoteText == ">>>>>>> REMOTE" ||
                r.localText.startsWith("<<<<<<< LOCAL") || r.remoteText.startsWith(">>>>>>> REMOTE") ||
                r.localText.startsWith("<<<<<<< LOCAL (MODIFIED)") || r.localText.startsWith("<<<<<<< LOCAL (DELETED)") ||
                r.remoteText.startsWith(">>>>>>> REMOTE (MODIFIED)") || r.remoteText.startsWith(">>>>>>> REMOTE (DELETED)")) {
                continue
            }
            if (r.localLineNum != null && r.localText.isNotEmpty()) {
                localLines.add(r.localText)
            }
            if (r.remoteLineNum != null && r.remoteText.isNotEmpty()) {
                remoteLines.add(r.remoteText)
            }
        }

        val resolvedText = when (choice) {
            "LOCAL" -> localLines.joinToString("\n")
            "REMOTE" -> remoteLines.joinToString("\n")
            else -> (localLines + remoteLines).joinToString("\n")
        }

        mergeEngine.updateMergedLine(start.toUInt(), resolvedText)
        for (i in (start + 1)..end) {
            mergeEngine.updateMergedLine(i.toUInt(), "")
        }

        refreshRows()
    }

    private fun refreshRows() {
        val total = mergeEngine.getRowsPage(0u, 100000u)
        mergeRows = total
        hasConflicts = mergeEngine.hasConflicts()
    }

    fun saveMergedResult(): Boolean {
        if (outputPath.isEmpty()) return false
        return try {
            val content = mergeEngine.getMergedOutput()
            File(outputPath).writeText(content)
            true
        } catch (e: Exception) {
            errorMsg = "Failed to save: ${e.message}"
            false
        }
    }
}
