import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uniffi.compare_core.DiffEngine
import uniffi.compare_core.AlgorithmType as RustAlgoType
import uniffi.compare_core.DiffStatus as RustDiffStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ==========================================
// 2. State Management (ViewModel)
// ==========================================

object HistoryManager {
    private val historyFile = java.io.File(System.getProperty("user.home"), ".thecompare_history.txt")

    private val fileLeft = mutableListOf<String>()
    private val fileRight = mutableListOf<String>()
    private val folderLeft = mutableListOf<String>()
    private val folderRight = mutableListOf<String>()

    init {
        load()
    }

    private fun load() {
        if (!historyFile.exists()) return
        try {
            var currentSection: String? = null
            historyFile.readLines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    currentSection = trimmed.substring(1, trimmed.length - 1)
                } else if (trimmed.isNotEmpty()) {
                    when (currentSection) {
                        "FILE_LEFT" -> fileLeft.add(trimmed)
                        "FILE_RIGHT" -> fileRight.add(trimmed)
                        "FOLDER_LEFT" -> folderLeft.add(trimmed)
                        "FOLDER_RIGHT" -> folderRight.add(trimmed)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun save() {
        try {
            val content = buildString {
                append("[FILE_LEFT]\n")
                fileLeft.forEach { append(it).append("\n") }
                append("[FILE_RIGHT]\n")
                fileRight.forEach { append(it).append("\n") }
                append("[FOLDER_LEFT]\n")
                folderLeft.forEach { append(it).append("\n") }
                append("[FOLDER_RIGHT]\n")
                folderRight.forEach { append(it).append("\n") }
            }
            historyFile.writeText(content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getHistory(section: String): List<String> {
        return when (section) {
            "FILE_LEFT" -> fileLeft
            "FILE_RIGHT" -> fileRight
            "FOLDER_LEFT" -> folderLeft
            "FOLDER_RIGHT" -> folderRight
            else -> emptyList()
        }
    }

    fun addToHistory(section: String, path: String) {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return
        val list = when (section) {
            "FILE_LEFT" -> fileLeft
            "FILE_RIGHT" -> fileRight
            "FOLDER_LEFT" -> folderLeft
            "FOLDER_RIGHT" -> folderRight
            else -> return
        }
        list.remove(trimmed)
        list.add(0, trimmed)
        if (list.size > 50) {
            list.removeAt(list.size - 1)
        }
        save()
    }
}

class DiffViewModel {
    var leftFilePath by mutableStateOf("")
    var rightFilePath by mutableStateOf("")

    val leftHistory = androidx.compose.runtime.mutableStateListOf<String>()
    val rightHistory = androidx.compose.runtime.mutableStateListOf<String>()

    fun addToLeftHistory(path: String) {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return
        leftHistory.remove(trimmed)
        leftHistory.add(0, trimmed)
        HistoryManager.addToHistory("FILE_LEFT", trimmed)
    }

    fun addToRightHistory(path: String) {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return
        rightHistory.remove(trimmed)
        rightHistory.add(0, trimmed)
        HistoryManager.addToHistory("FILE_RIGHT", trimmed)
    }

    var leftTempPath = ""
    var rightTempPath = ""
    var lastLoadedLeft = ""
    var lastLoadedRight = ""
    var hasUnsavedChanges by mutableStateOf(false)
    var hasLeftUnsaved by mutableStateOf(false)
    var hasRightUnsaved by mutableStateOf(false)
    var leftOriginalLines by mutableStateOf<List<String>>(emptyList())
    var rightOriginalLines by mutableStateOf<List<String>>(emptyList())
    var leftCharset: java.nio.charset.Charset = java.nio.charset.StandardCharsets.UTF_8
    var rightCharset: java.nio.charset.Charset = java.nio.charset.StandardCharsets.UTF_8

    private val undoStack = androidx.compose.runtime.mutableStateListOf<HistoryState>()
    private val redoStack = androidx.compose.runtime.mutableStateListOf<HistoryState>()

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    // Current diff data to display in the side-by-side view (can contain nulls representing placeholders)
    var diffLines by mutableStateOf<List<DiffRowData?>>(emptyList())
        private set

    var lineStatuses by mutableStateOf<List<LineStatus>>(emptyList())
        private set

    // Current selected algorithm
    var currentAlgorithm by mutableStateOf(AlgorithmType.AUTO)
        private set

    var autoDetectedAlgorithm by mutableStateOf<AlgorithmType?>(null)

    val currentAlgorithmDisplayName: String
        get() {
            if (currentAlgorithm == AlgorithmType.AUTO) {
                val detected = autoDetectedAlgorithm
                return if (detected != null) {
                    "Auto (${detected.displayName.substringBefore(" ")})"
                } else {
                    "Auto Detect"
                }
            }
            return currentAlgorithm.displayName
        }

    fun detectOptimalAlgorithm(leftPath: String, rightPath: String, leftLines: List<String>): AlgorithmType {
        val extension = leftPath.substringAfterLast('.', "").lowercase()
        val structuredExtensions = setOf("kt", "java", "rs", "js", "ts", "json", "cpp", "c", "h", "cs", "xml", "html", "css", "py")

        if (structuredExtensions.contains(extension)) {
            return AlgorithmType.PATIENCE
        }

        // Fallback to analyzing content: count curly braces '{' or '}'
        if (leftLines.isNotEmpty()) {
            var braceCount = 0
            val sampleSize = leftLines.size.coerceAtMost(1000)
            for (i in 0 until sampleSize) {
                val line = leftLines[i]
                if (line.contains('{') || line.contains('}')) {
                    braceCount++
                }
            }
            if (braceCount.toFloat() / sampleSize > 0.02f) {
                return AlgorithmType.PATIENCE
            }
        }

        return AlgorithmType.MYERS
    }

    // Statistics counts
    var additionsCount by mutableStateOf(0)
    var deletionsCount by mutableStateOf(0)
    var modificationsCount by mutableStateOf(0)
    var unchangedCount by mutableStateOf(0)
    var unimportantCount by mutableStateOf(0)
    val moveBlocks: List<MoveBlock>
        get() {
            val list = mutableListOf<MoveBlock>()
            var currentStatus: LineStatus? = null
            var blockStartIdx = -1
            val lines = diffLines

            fun commitBlock(endIdx: Int) {
                if (blockStartIdx != -1) {
                    val blockLines = lines.subList(blockStartIdx, endIdx)
                    val leftNums = blockLines.mapNotNull { it?.leftLineNum }
                    val rightNums = blockLines.mapNotNull { it?.rightLineNum }
                    if (leftNums.isNotEmpty() || rightNums.isNotEmpty()) {
                        val leftRange = if (leftNums.isNotEmpty()) leftNums.minOrNull()!!..leftNums.maxOrNull()!! else IntRange.EMPTY
                        val rightRange = if (rightNums.isNotEmpty()) rightNums.minOrNull()!!..rightNums.maxOrNull()!! else IntRange.EMPTY
                        list.add(MoveBlock(leftRange, rightRange, MoveDirection.LEFT_TO_RIGHT))
                    }
                }
            }

            for (i in lines.indices) {
                val row = lines[i]
                if (row == null) {
                    if (currentStatus != null) {
                        commitBlock(i)
                        currentStatus = null
                        blockStartIdx = -1
                    }
                    continue
                }

                val isChange = row.status == LineStatus.MODIFIED || row.status == LineStatus.ADDED || row.status == LineStatus.DELETED
                if (isChange) {
                    if (currentStatus == null) {
                        currentStatus = row.status
                        blockStartIdx = i
                    } else if (currentStatus != row.status) {
                        commitBlock(i)
                        currentStatus = row.status
                        blockStartIdx = i
                    }
                } else {
                    if (currentStatus != null) {
                        commitBlock(i)
                        currentStatus = null
                        blockStartIdx = -1
                    }
                }
            }
            if (currentStatus != null) {
                commitBlock(lines.size)
            }
            return list
        }

    var hoveredMoveBlockIndexLeft by mutableStateOf<Int?>(null)
    var hoveredMoveBlockIndexRight by mutableStateOf<Int?>(null)

    private var rustEngine = DiffEngine()
    private val pageSize = 100
    private val loadedPages = mutableSetOf<Int>()
    private val loadingQueue = mutableSetOf<Int>()

    init {
        leftHistory.addAll(HistoryManager.getHistory("FILE_LEFT"))
        rightHistory.addAll(HistoryManager.getHistory("FILE_RIGHT"))

        // Load default data
        changeAlgorithm(AlgorithmType.AUTO)
    }

    fun changeAlgorithm(algo: AlgorithmType) {
        currentAlgorithm = algo
        loadedPages.clear()
        loadingQueue.clear()
        lineStatuses = emptyList()

        try {

            val isLeftEmpty = leftFilePath.isEmpty()
            val isRightEmpty = rightFilePath.isEmpty()

            // Handle Left Side
            if (isLeftEmpty) {
                leftFilePath = ""
                lastLoadedLeft = ""
                leftOriginalLines = emptyList()
                val tempFile = java.io.File.createTempFile("temp_left_empty_", ".txt").apply {
                    writeText("")
                    deleteOnExit()
                }
                leftTempPath = tempFile.absolutePath
            } else {
                val leftOriginalFile = java.io.File(leftFilePath)
                if (leftFilePath != lastLoadedLeft || leftTempPath.isEmpty() || !java.io.File(leftTempPath).exists()) {
                    if (leftOriginalFile.exists()) {
                        val (charset, originalText) = CharsetDetector.detectCharsetAndRead(leftOriginalFile)
                        leftCharset = charset
                        val tempFile = java.io.File.createTempFile("temp_left_", ".txt").apply {
                            writeText(originalText)
                            deleteOnExit()
                        }
                        leftTempPath = tempFile.absolutePath
                        lastLoadedLeft = leftFilePath
                        leftOriginalLines = originalText.lines()
                        hasUnsavedChanges = false
                    } else {
                        leftTempPath = leftFilePath
                    }
                }
            }

            // Handle Right Side
            if (isRightEmpty) {
                rightFilePath = ""
                lastLoadedRight = ""
                rightOriginalLines = emptyList()
                val tempFile = java.io.File.createTempFile("temp_right_empty_", ".txt").apply {
                    writeText("")
                    deleteOnExit()
                }
                rightTempPath = tempFile.absolutePath
            } else {
                val rightOriginalFile = java.io.File(rightFilePath)
                if (rightFilePath != lastLoadedRight || rightTempPath.isEmpty() || !java.io.File(rightTempPath).exists()) {
                    if (rightOriginalFile.exists()) {
                        val (charset, originalText) = CharsetDetector.detectCharsetAndRead(rightOriginalFile)
                        rightCharset = charset
                        val tempFile = java.io.File.createTempFile("temp_right_", ".txt").apply {
                            writeText(originalText)
                            deleteOnExit()
                        }
                        rightTempPath = tempFile.absolutePath
                        lastLoadedRight = rightFilePath
                        rightOriginalLines = originalText.lines()
                        hasUnsavedChanges = false
                    } else {
                        rightTempPath = rightFilePath
                    }
                }
            }

            // Resolve AUTO algorithm
            val resolvedAlgo = if (algo == AlgorithmType.AUTO) {
                val detected = detectOptimalAlgorithm(leftFilePath, rightFilePath, leftOriginalLines)
                autoDetectedAlgorithm = detected
                detected
            } else {
                autoDetectedAlgorithm = null
                algo
            }

            val rustAlgo = when (resolvedAlgo) {
                AlgorithmType.MYERS -> RustAlgoType.MYERS
                AlgorithmType.PATIENCE -> RustAlgoType.SIMPLE_LINEAR
                else -> RustAlgoType.MYERS
            }
            rustEngine.setAlgorithm(rustAlgo)

            // Perform diffing and retrieve total lines count
            val totalLines = rustEngine.compareFiles(leftTempPath, rightTempPath).toInt()

            // Initialize placeholder list
            val placeholders = ArrayList<DiffRowData?>(totalLines)
            for (i in 0 until totalLines) {
                placeholders.add(null)
            }
            diffLines = placeholders

             // Prefetch lines summary to calculate stats
            val allLines = rustEngine.getLinesPage(0u, totalLines.toUInt())
            additionsCount = allLines.count { it.status == RustDiffStatus.ADDED }
            deletionsCount = allLines.count { it.status == RustDiffStatus.DELETED }
            modificationsCount = allLines.count { it.status == RustDiffStatus.MODIFIED }
            unchangedCount = allLines.count { it.status == RustDiffStatus.UNCHANGED }
            unimportantCount = allLines.count { it.status == RustDiffStatus.UNIMPORTANT }

            lineStatuses = allLines.map {
                when (it.status) {
                    RustDiffStatus.UNCHANGED -> LineStatus.UNCHANGED
                    RustDiffStatus.ADDED -> LineStatus.ADDED
                    RustDiffStatus.DELETED -> LineStatus.DELETED
                    RustDiffStatus.MODIFIED -> LineStatus.MODIFIED
                    RustDiffStatus.UNIMPORTANT -> LineStatus.UNIMPORTANT
                }
            }

            // Preload first page
            loadPage(0)

        } catch (e: Exception) {
            println("FFI Error: ${e.message}")
            e.printStackTrace()
            // Fallback to local mock data if Rust DLL is not found or fails
            val resolvedAlgoForFallback = if (algo == AlgorithmType.AUTO) {
                detectOptimalAlgorithm(leftFilePath, rightFilePath, leftOriginalLines)
            } else {
                algo
            }
            val localLines = when (resolvedAlgoForFallback) {
                AlgorithmType.MYERS -> {
                    generateMyersMockData()
                }
                AlgorithmType.PATIENCE -> {
                    generatePatienceMockData()
                }
                else -> {
                    generateMyersMockData()
                }
            }
            diffLines = localLines
            lineStatuses = localLines.map { it.status }
            additionsCount = localLines.count { it.status == LineStatus.ADDED }
            deletionsCount = localLines.count { it.status == LineStatus.DELETED }
            modificationsCount = localLines.count { it.status == LineStatus.MODIFIED }
            unchangedCount = localLines.count { it.status == LineStatus.UNCHANGED }
            unimportantCount = localLines.count { it.status == LineStatus.UNIMPORTANT }

            val leftLinesList = MutableList<String>(localLines.mapNotNull { it.leftLineNum }.maxOrNull() ?: 0) { "" }
            val rightLinesList = MutableList<String>(localLines.mapNotNull { it.rightLineNum }.maxOrNull() ?: 0) { "" }
            for (line in localLines) {
                if (line.leftLineNum != null) {
                    leftLinesList[line.leftLineNum - 1] = line.leftText
                }
                if (line.rightLineNum != null) {
                    rightLinesList[line.rightLineNum - 1] = line.rightText
                }
            }
            leftOriginalLines = leftLinesList
            rightOriginalLines = rightLinesList
        }
    }

    fun startCompare() {
        changeAlgorithm(currentAlgorithm)
    }

    fun mergeBlock(block: MoveBlock, direction: MoveDirection) {
        val currentState = captureCurrentState()
        undoStack.add(currentState)
        redoStack.clear()

        if (leftTempPath.isNotEmpty() && rightTempPath.isNotEmpty() &&
            java.io.File(leftTempPath).exists() && java.io.File(rightTempPath).exists()) {
            try {
                val leftFile = java.io.File(leftTempPath)
                val rightFile = java.io.File(rightTempPath)
                val leftLines = leftFile.readLines().toMutableList()
                val rightLines = rightFile.readLines().toMutableList()

                if (direction == MoveDirection.LEFT_TO_RIGHT) {
                    val linesToCopy = if (block.leftRange.isEmpty()) emptyList() else leftLines.subList(block.leftRange.first - 1, block.leftRange.last)
                    if (!block.rightRange.isEmpty()) {
                        // Replace range
                        val start = block.rightRange.first - 1
                        val end = block.rightRange.last
                        for (k in end - 1 downTo start) {
                            if (k in rightLines.indices) rightLines.removeAt(k)
                        }
                        rightLines.addAll(start, linesToCopy)
                    } else {
                        // Insert at calculated position
                        var insertIdx = 0
                        val leftStartIdx = diffLines.indexOfFirst { it?.leftLineNum == block.leftRange.first }
                        if (leftStartIdx != -1) {
                            for (idx in (leftStartIdx - 1) downTo 0) {
                                val rNum = diffLines.getOrNull(idx)?.rightLineNum
                                if (rNum != null) {
                                    insertIdx = rNum
                                    break
                                }
                            }
                        }
                        rightLines.addAll(insertIdx.coerceIn(0, rightLines.size), linesToCopy)
                    }
                } else {
                    // RIGHT_TO_LEFT
                    val linesToCopy = if (block.rightRange.isEmpty()) emptyList() else rightLines.subList(block.rightRange.first - 1, block.rightRange.last)
                    if (!block.leftRange.isEmpty()) {
                        val start = block.leftRange.first - 1
                        val end = block.leftRange.last
                        for (k in end - 1 downTo start) {
                            if (k in leftLines.indices) leftLines.removeAt(k)
                        }
                        leftLines.addAll(start, linesToCopy)
                    } else {
                        var insertIdx = 0
                        val rightStartIdx = diffLines.indexOfFirst { it?.rightLineNum == block.rightRange.first }
                        if (rightStartIdx != -1) {
                            for (idx in (rightStartIdx - 1) downTo 0) {
                                val lNum = diffLines.getOrNull(idx)?.leftLineNum
                                if (lNum != null) {
                                    insertIdx = lNum
                                    break
                                }
                            }
                        }
                        leftLines.addAll(insertIdx.coerceIn(0, leftLines.size), linesToCopy)
                    }
                }

                // Write temp files
                leftFile.writeText(leftLines.joinToString("\n"))
                rightFile.writeText(rightLines.joinToString("\n"))

                // Re-compare to refresh
                changeAlgorithm(currentAlgorithm)
            } catch (e: Exception) {
                println("Error merging files: ${e.message}")
                e.printStackTrace()
            }
        } else {
            // Fallback: merge in-memory list
            val leftStartIdx = if (!block.leftRange.isEmpty()) diffLines.indexOfFirst { it?.leftLineNum == block.leftRange.first } else -1
            val rightStartIdx = if (!block.rightRange.isEmpty()) diffLines.indexOfFirst { it?.rightLineNum == block.rightRange.first } else -1
            val startIdx = listOf(leftStartIdx, rightStartIdx).filter { it != -1 }.minOrNull() ?: -1

            val leftEndIdx = if (!block.leftRange.isEmpty()) diffLines.indexOfLast { it?.leftLineNum == block.leftRange.last } else -1
            val rightEndIdx = if (!block.rightRange.isEmpty()) diffLines.indexOfLast { it?.rightLineNum == block.rightRange.last } else -1
            val endIdx = listOf(leftEndIdx, rightEndIdx).filter { it != -1 }.maxOrNull() ?: -1

            if (startIdx != -1 && endIdx != -1) {
                val newList = diffLines.toMutableList()
                if (direction == MoveDirection.LEFT_TO_RIGHT) {
                    val blockRows = newList.subList(startIdx, endIdx + 1)
                    val leftRows = blockRows.filter { it?.leftLineNum != null }
                    val mergedRows = leftRows.map { row ->
                        DiffRowData(
                            leftLineNum = row?.leftLineNum,
                            leftText = row?.leftText ?: "",
                            rightLineNum = row?.leftLineNum,
                            rightText = row?.leftText ?: "",
                            status = LineStatus.UNCHANGED
                        )
                    }
                    for (k in endIdx downTo startIdx) {
                        newList.removeAt(k)
                    }
                    newList.addAll(startIdx, mergedRows)
                } else {
                    val blockRows = newList.subList(startIdx, endIdx + 1)
                    val rightRows = blockRows.filter { it?.rightLineNum != null }
                    val mergedRows = rightRows.map { row ->
                        DiffRowData(
                            leftLineNum = row?.rightLineNum,
                            leftText = row?.rightText ?: "",
                            rightLineNum = row?.rightLineNum,
                            rightText = row?.rightText ?: "",
                            status = LineStatus.UNCHANGED
                        )
                    }
                    for (k in endIdx downTo startIdx) {
                        newList.removeAt(k)
                    }
                    newList.addAll(startIdx, mergedRows)
                }
                diffLines = newList
                additionsCount = newList.count { it?.status == LineStatus.ADDED }
                deletionsCount = newList.count { it?.status == LineStatus.DELETED }
                modificationsCount = newList.count { it?.status == LineStatus.MODIFIED }
                unchangedCount = newList.count { it?.status == LineStatus.UNCHANGED }
                unimportantCount = newList.count { it?.status == LineStatus.UNIMPORTANT }
            }
        }
        updateUnsavedChangesStatus()
    }

    fun saveLeftChanges() {
        try {
            if (leftFilePath.isNotEmpty() && leftTempPath.isNotEmpty()) {
                val origFile = java.io.File(leftFilePath)
                val tempFile = java.io.File(leftTempPath)
                if (tempFile.exists() && origFile.exists()) {
                    val savedText = tempFile.readText()
                    val bytes = savedText.toByteArray(leftCharset)
                    origFile.writeBytes(bytes)
                    leftOriginalLines = savedText.lines()
                }
            } else {
                // For in-memory fallback
                val leftLinesList = MutableList<String>(diffLines.mapNotNull { it?.leftLineNum }.maxOrNull() ?: 0) { "" }
                for (line in diffLines) {
                    if (line != null && line.leftLineNum != null) {
                        leftLinesList[line.leftLineNum - 1] = line.leftText
                    }
                }
                leftOriginalLines = leftLinesList
            }
            updateUnsavedChangesStatus()
        } catch (e: Exception) {
            println("Error saving left changes: ${e.message}")
            e.printStackTrace()
        }
    }

    fun saveRightChanges() {
        try {
            if (rightFilePath.isNotEmpty() && rightTempPath.isNotEmpty()) {
                val origFile = java.io.File(rightFilePath)
                val tempFile = java.io.File(rightTempPath)
                if (tempFile.exists() && origFile.exists()) {
                    val savedText = tempFile.readText()
                    val bytes = savedText.toByteArray(rightCharset)
                    origFile.writeBytes(bytes)
                    rightOriginalLines = savedText.lines()
                }
            } else {
                // For in-memory fallback
                val rightLinesList = MutableList<String>(diffLines.mapNotNull { it?.rightLineNum }.maxOrNull() ?: 0) { "" }
                for (line in diffLines) {
                    if (line != null && line.rightLineNum != null) {
                        rightLinesList[line.rightLineNum - 1] = line.rightText
                    }
                }
                rightOriginalLines = rightLinesList
            }
            updateUnsavedChangesStatus()
        } catch (e: Exception) {
            println("Error saving right changes: ${e.message}")
            e.printStackTrace()
        }
    }

    fun saveChanges() {
        saveLeftChanges()
        saveRightChanges()
    }

    fun updateLineText(lineNum: Int, isRight: Boolean, newText: String) {
        val path = if (isRight) rightTempPath else leftTempPath
        if (path.isNotEmpty() && java.io.File(path).exists()) {
            try {
                val file = java.io.File(path)
                val lines = file.readLines().toMutableList()
                if (lineNum - 1 in lines.indices) {
                    if (lines[lineNum - 1] != newText) {
                        lines[lineNum - 1] = newText
                        file.writeText(lines.joinToString("\n"))
                        changeAlgorithm(currentAlgorithm)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // Memory fallback mode
            val newList = diffLines.toMutableList()
            val index = newList.indexOfFirst {
                if (isRight) it?.rightLineNum == lineNum else it?.leftLineNum == lineNum
            }
            if (index != -1) {
                val oldRow = newList[index]!!
                val isChanged = if (isRight) oldRow.rightText != newText else oldRow.leftText != newText
                if (isChanged) {
                    newList[index] = if (isRight) {
                        oldRow.copy(rightText = newText)
                    } else {
                        oldRow.copy(leftText = newText)
                    }
                    diffLines = newList
                    updateUnsavedChangesStatus()
                }
            }
        }
    }

    private fun captureCurrentState(): HistoryState {
        return HistoryState(
            leftContent = if (leftTempPath.isNotEmpty() && java.io.File(leftTempPath).exists()) java.io.File(leftTempPath).readLines() else emptyList(),
            rightContent = if (rightTempPath.isNotEmpty() && java.io.File(rightTempPath).exists()) java.io.File(rightTempPath).readLines() else emptyList(),
            fallbackDiffLines = if (leftTempPath.isEmpty() || !java.io.File(leftTempPath).exists()) diffLines.toList() else null
        )
    }

    private fun applyHistoryState(state: HistoryState) {
        if (leftTempPath.isNotEmpty() && rightTempPath.isNotEmpty() &&
            java.io.File(leftTempPath).exists() && java.io.File(rightTempPath).exists()) {
            try {
                java.io.File(leftTempPath).writeText(state.leftContent.joinToString("\n"))
                java.io.File(rightTempPath).writeText(state.rightContent.joinToString("\n"))
                changeAlgorithm(currentAlgorithm)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // Fallback mode
            if (state.fallbackDiffLines != null) {
                diffLines = state.fallbackDiffLines
                additionsCount = diffLines.count { it?.status == LineStatus.ADDED }
                deletionsCount = diffLines.count { it?.status == LineStatus.DELETED }
                modificationsCount = diffLines.count { it?.status == LineStatus.MODIFIED }
                unchangedCount = diffLines.count { it?.status == LineStatus.UNCHANGED }
                unimportantCount = diffLines.count { it?.status == LineStatus.UNIMPORTANT }
            }
        }
        updateUnsavedChangesStatus()
    }

    private fun updateUnsavedChangesStatus() {
        if (leftTempPath.isNotEmpty() && rightTempPath.isNotEmpty() &&
            java.io.File(leftTempPath).exists() && java.io.File(rightTempPath).exists()) {
            val leftCurrent = java.io.File(leftTempPath).readLines()
            val rightCurrent = java.io.File(rightTempPath).readLines()
            hasLeftUnsaved = (leftCurrent != leftOriginalLines)
            hasRightUnsaved = (rightCurrent != rightOriginalLines)
        } else {
            // Fallback mode
            val leftLinesList = diffLines.mapNotNull { if (it?.leftLineNum != null) it.leftText else null }
            val rightLinesList = diffLines.mapNotNull { if (it?.rightLineNum != null) it.rightText else null }
            hasLeftUnsaved = (leftLinesList != leftOriginalLines)
            hasRightUnsaved = (rightLinesList != rightOriginalLines)
        }
        hasUnsavedChanges = hasLeftUnsaved || hasRightUnsaved
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val currentState = captureCurrentState()
            redoStack.add(currentState)
            val prevState = undoStack.removeAt(undoStack.size - 1)
            applyHistoryState(prevState)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val currentState = captureCurrentState()
            undoStack.add(currentState)
            val nextState = redoStack.removeAt(redoStack.size - 1)
            applyHistoryState(nextState)
        }
    }

    fun requestLine(index: Int) {
        if (index < 0 || index >= diffLines.size) return
        if (diffLines[index] != null) return

        val page = index / pageSize
        if (page in loadedPages || page in loadingQueue) return

        loadingQueue.add(page)
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val start = page * pageSize
                val count = pageSize
                val rustLines = rustEngine.getLinesPage(start.toUInt(), count.toUInt())

                val mapped = rustLines.map { line ->
                    DiffRowData(
                        leftLineNum = line.leftLineNum?.toInt(),
                        leftText = line.leftText,
                        rightLineNum = line.rightLineNum?.toInt(),
                        rightText = line.rightText,
                        status = when (line.status) {
                            RustDiffStatus.UNCHANGED -> LineStatus.UNCHANGED
                            RustDiffStatus.ADDED -> LineStatus.ADDED
                            RustDiffStatus.DELETED -> LineStatus.DELETED
                            RustDiffStatus.MODIFIED -> LineStatus.MODIFIED
                            RustDiffStatus.UNIMPORTANT -> LineStatus.UNIMPORTANT
                        },
                        inlineChanges = line.inlineChanges
                    )
                }

                withContext(Dispatchers.Main) {
                    val newList = ArrayList(diffLines)
                    for (offset in 0 until mapped.size) {
                        val idx = start + offset
                        if (idx < newList.size) {
                            newList[idx] = mapped[offset]
                        }
                    }
                    diffLines = newList
                    loadedPages.add(page)
                    loadingQueue.remove(page)
                }
            } catch (e: Exception) {
                loadingQueue.remove(page)
                println("Failed to load page $page: ${e.message}")
            }
        }
    }

    private fun loadPage(page: Int) {
        val start = page * pageSize
        val count = pageSize
        try {
            val rustLines = rustEngine.getLinesPage(start.toUInt(), count.toUInt())
            val mapped = rustLines.map { line ->
                DiffRowData(
                    leftLineNum = line.leftLineNum?.toInt(),
                    leftText = line.leftText,
                    rightLineNum = line.rightLineNum?.toInt(),
                    rightText = line.rightText,
                    status = when (line.status) {
                        RustDiffStatus.UNCHANGED -> LineStatus.UNCHANGED
                        RustDiffStatus.ADDED -> LineStatus.ADDED
                        RustDiffStatus.DELETED -> LineStatus.DELETED
                        RustDiffStatus.MODIFIED -> LineStatus.MODIFIED
                        RustDiffStatus.UNIMPORTANT -> LineStatus.UNIMPORTANT
                    },
                    inlineChanges = line.inlineChanges
                )
            }
            val newList = ArrayList(diffLines)
            for (offset in 0 until mapped.size) {
                val idx = start + offset
                if (idx < newList.size) {
                    newList[idx] = mapped[offset]
                }
            }
            diffLines = newList
            loadedPages.add(page)
        } catch (e: Exception) {
            println("Failed to preload page $page: ${e.message}")
        }
    }

    // Mock Data Generator for Myers (representing a focused class modification comparison)
    private fun generateMyersMockData(): List<DiffRowData> {
        val list = mutableListOf<DiffRowData>()

        // Let's build a realistic diff row by row
        list.add(DiffRowData(1, "package com.example.compare", 1, "package com.example.compare", LineStatus.UNCHANGED))
        list.add(DiffRowData(2, "", 2, "", LineStatus.UNCHANGED))
        list.add(DiffRowData(3, "import kotlinx.coroutines.flow.MutableStateFlow", 3, "import androidx.compose.runtime.mutableStateOf", LineStatus.MODIFIED))
        list.add(DiffRowData(4, "import kotlinx.coroutines.flow.StateFlow", 4, "import androidx.compose.runtime.State", LineStatus.MODIFIED))
        list.add(DiffRowData(null, "", 5, "import kotlinx.coroutines.flow.asStateFlow", LineStatus.ADDED))
        list.add(DiffRowData(5, "", 6, "", LineStatus.UNCHANGED))
        list.add(DiffRowData(6, "/**", 7, "/**", LineStatus.UNCHANGED))
        list.add(DiffRowData(7, " * Simple ViewModel for tracking comparison results.", 8, " * Simple ViewModel for tracking comparison results.", LineStatus.UNCHANGED))
        list.add(DiffRowData(8, " */", 9, " */", LineStatus.UNCHANGED))
        list.add(DiffRowData(9, "class DiffViewModel {", 10, "class DiffViewModel {", LineStatus.UNCHANGED))
        list.add(DiffRowData(10, "    private val _diffLines = MutableStateFlow<List<DiffRowData>>(emptyList())", 11, "    private val _diffLines = mutableStateOf<List<DiffRowData>>(emptyList())", LineStatus.MODIFIED))
        list.add(DiffRowData(11, "    val diffLines: StateFlow<List<DiffRowData>> = _diffLines", null, "", LineStatus.DELETED))
        list.add(DiffRowData(null, "", 12, "    val diffLines: State<List<DiffRowData>> = _diffLines", LineStatus.ADDED))
        list.add(DiffRowData(12, "", 13, "", LineStatus.UNCHANGED))
        list.add(DiffRowData(13, "    fun changeAlgorithm(algo: AlgorithmType) {", 14, "    fun changeAlgorithm(algo: AlgorithmType) {", LineStatus.UNCHANGED))
        list.add(DiffRowData(14, "        // Perform standard Myers diff calculation", 15, "        // Perform standard Myers diff calculation", LineStatus.UNCHANGED))
        list.add(DiffRowData(15, "        val result = runMyers(algo)", 16, "        val result = runMyers(algo)", LineStatus.UNCHANGED))
        list.add(DiffRowData(16, "        _diffLines.value = result", 17, "        _diffLines.value = result", LineStatus.UNCHANGED))
        list.add(DiffRowData(17, "    }", 18, "    }", LineStatus.UNCHANGED))
        list.add(DiffRowData(18, "", 19, "", LineStatus.UNCHANGED))
        list.add(DiffRowData(19, "@Deprecated(\"Use new logger API instead\")", null, "", LineStatus.DELETED))
        list.add(DiffRowData(20, "fun debugLog() {", null, "", LineStatus.DELETED))
        list.add(DiffRowData(21, "        println(\"Current algorithm changed\")", null, "", LineStatus.DELETED))
        list.add(DiffRowData(22, "    }", null, "", LineStatus.DELETED))
        list.add(DiffRowData(23, "", 20, "", LineStatus.UNCHANGED))
        list.add(DiffRowData(null, "", 21, "    // Added premium performance optimization tracer", LineStatus.ADDED))
        list.add(DiffRowData(null, "", 22, "    fun tracePerf() {", LineStatus.ADDED))
        list.add(DiffRowData(null, "", 23, "        println(\"Tracing UI render pipeline performance...\")", LineStatus.ADDED))
        list.add(DiffRowData(null, "", 24, "    }", LineStatus.ADDED))

        // Complex modified row demonstrating intra-line red-centric bold focus
        val complexSpans = listOf(
            uniffi.compare_core.TextSpan(18u, 22u, true, false, uniffi.compare_core.TokenType.NORMAL), // "true"
            uniffi.compare_core.TextSpan(18u, 23u, true, true, uniffi.compare_core.TokenType.NORMAL)   // "false"
        )
        list.add(DiffRowData(24, "    val isReady = true", 25, "    val isReady = false", LineStatus.MODIFIED, complexSpans))

        // Unimportant row (whitespace / comment differences mapped to blue)
        list.add(DiffRowData(25, "        // Perform standard Myers diff calculation", 26, "        // Perform standard Myers diff calculation (optimized)", LineStatus.UNIMPORTANT))

        list.add(DiffRowData(26, "}", 27, "}", LineStatus.UNCHANGED))

        return list
    }

    // Mock Data Generator for Patience (representing a huge file with 2000+ lines for testing scroll performance)
    private fun generatePatienceMockData(): List<DiffRowData> {
        val list = mutableListOf<DiffRowData>()

        list.add(DiffRowData(1, "package com.example.performance", 1, "package com.example.performance", LineStatus.UNCHANGED))
        list.add(DiffRowData(2, "", 2, "", LineStatus.UNCHANGED))
        list.add(DiffRowData(3, "import androidx.compose.runtime.Composable", 3, "import androidx.compose.runtime.Composable", LineStatus.UNCHANGED))
        list.add(DiffRowData(4, "import androidx.compose.foundation.lazy.LazyColumn", 4, "import androidx.compose.foundation.lazy.LazyColumn", LineStatus.UNCHANGED))
        list.add(DiffRowData(5, "", 5, "", LineStatus.UNCHANGED))

        var leftLine = 6
        var rightLine = 6

        // Generate ~2000 lines of mock code to show off virtualized list scroll performance
        for (i in 1..250) {
            // Unchanged chunk (usually class definition, imports, common structural elements)
            list.add(DiffRowData(leftLine++, "class LargeComponentDemo$i {", rightLine++, "class LargeComponentDemo$i {", LineStatus.UNCHANGED))
            list.add(DiffRowData(leftLine++, "    fun renderUIElement$i() {", rightLine++, "    fun renderUIElement$i() {", LineStatus.UNCHANGED))
            list.add(DiffRowData(leftLine++, "        println(\"Element $i initialized\")", rightLine++, "        println(\"Element $i initialized\")", LineStatus.UNCHANGED))

            // Periodically insert ADDED chunks
            if (i % 3 == 0) {
                list.add(DiffRowData(null, "", rightLine++, "        // ADDED: Additional logging for diagnostics", LineStatus.ADDED))
                list.add(DiffRowData(null, "", rightLine++, "        if (System.currentTimeMillis() % 2 == 0L) {", LineStatus.ADDED))
                list.add(DiffRowData(null, "", rightLine++, "            println(\"Diagnostic trace $i active\")", LineStatus.ADDED))
                list.add(DiffRowData(null, "", rightLine++, "        }", LineStatus.ADDED))
            }

            // Periodically insert DELETED chunks
            if (i % 4 == 0) {
                list.add(DiffRowData(leftLine++, "        // DELETED: Outdated legacy performance metrics code", null, "", LineStatus.DELETED))
                list.add(DiffRowData(leftLine++, "        val elapsed = System.nanoTime()", null, "", LineStatus.DELETED))
                list.add(DiffRowData(leftLine++, "        println(\"Time taken: \$elapsed ns\")", null, "", LineStatus.DELETED))
            }

            // Periodically insert MODIFIED chunks
            if (i % 5 == 0) {
                list.add(DiffRowData(leftLine++, "        val configValue = \"legacy-config-v1\"", rightLine++, "        val configValue = \"optimized-config-v2\"", LineStatus.MODIFIED))
                list.add(DiffRowData(leftLine++, "        val threads = 4 // standard parallel threads", rightLine++, "        val threads = 8 // doubled threads for performance boost", LineStatus.MODIFIED))
            } else {
                list.add(DiffRowData(leftLine++, "        val configValue = \"legacy-config-v1\"", rightLine++, "        val configValue = \"legacy-config-v1\"", LineStatus.UNCHANGED))
            }

            list.add(DiffRowData(leftLine++, "    }", rightLine++, "    }", LineStatus.UNCHANGED))
            list.add(DiffRowData(leftLine++, "}", rightLine++, "}", LineStatus.UNCHANGED))
            list.add(DiffRowData(leftLine++, "", rightLine++, "", LineStatus.UNCHANGED))
        }

        return list
    }
}
