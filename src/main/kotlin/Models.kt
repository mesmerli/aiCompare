import uniffi.compare_core.TextSpan

// ==========================================
// 1. Data Structure (Core-UI Contract)
// ==========================================

enum class LineStatus {
    UNCHANGED,
    ADDED,
    DELETED,
    MODIFIED,
    UNIMPORTANT
}

enum class PickerSide {
    LEFT,
    RIGHT
}

enum class TabType {
    FOLDER_COMPARE,
    TEXT_COMPARE,
    MERGE_COMPARE
}

data class TabItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val type: TabType,
    val leftPath: String? = null,
    val rightPath: String? = null,
    val isLeftOrphan: Boolean = false,
    val isRightOrphan: Boolean = false
)

data class WorkspaceState(
    val tabs: List<TabItem> = emptyList(),
    val activeTabId: String? = null
)

data class DiffRowData(
    val leftLineNum: Int?,
    val leftText: String,
    val rightLineNum: Int?,
    val rightText: String,
    val status: LineStatus,
    val inlineChanges: List<TextSpan>? = null
)

enum class MoveDirection {
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT
}

data class MoveBlock(
    val leftRange: IntRange,
    val rightRange: IntRange,
    val direction: MoveDirection
)

data class HistoryState(
    val leftContent: List<String>,
    val rightContent: List<String>,
    val fallbackDiffLines: List<DiffRowData?>? = null
)

object CharsetDetector {
    fun detectCharsetAndRead(file: java.io.File): Pair<java.nio.charset.Charset, String> {
        val bytes = file.readBytes()
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            // UTF-8 with BOM
            val text = String(bytes, 3, bytes.size - 3, java.nio.charset.StandardCharsets.UTF_8)
            return Pair(java.nio.charset.StandardCharsets.UTF_8, text)
        }
        if (bytes.size >= 2) {
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                // UTF-16 LE with BOM
                val text = String(bytes, 2, bytes.size - 2, java.nio.charset.StandardCharsets.UTF_16LE)
                return Pair(java.nio.charset.StandardCharsets.UTF_16LE, text)
            }
            if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                // UTF-16 BE with BOM
                val text = String(bytes, 2, bytes.size - 2, java.nio.charset.StandardCharsets.UTF_16BE)
                return Pair(java.nio.charset.StandardCharsets.UTF_16BE, text)
            }
        }

        // If no BOM, try to decode as UTF-8 strictly
        try {
            val decoder = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
            decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            decoder.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            val charBuffer = decoder.decode(java.nio.ByteBuffer.wrap(bytes))
            return Pair(java.nio.charset.StandardCharsets.UTF_8, charBuffer.toString())
        } catch (e: Exception) {
            // Try Big5
            try {
                val big5 = java.nio.charset.Charset.forName("Big5")
                val decoder = big5.newDecoder()
                decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                decoder.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                val charBuffer = decoder.decode(java.nio.ByteBuffer.wrap(bytes))
                return Pair(big5, charBuffer.toString())
            } catch (e2: Exception) {
                // Try GBK
                try {
                    val gbk = java.nio.charset.Charset.forName("GBK")
                    val decoder = gbk.newDecoder()
                    decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    decoder.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    val charBuffer = decoder.decode(java.nio.ByteBuffer.wrap(bytes))
                    return Pair(gbk, charBuffer.toString())
                } catch (e3: Exception) {
                    val systemDefault = java.nio.charset.Charset.defaultCharset()
                    return Pair(systemDefault, String(bytes, systemDefault))
                }
            }
        }
    }
}

enum class AlgorithmType(val displayName: String) {
    AUTO("Auto Detect (Recommended)"),
    MYERS("Myers Diff Algorithm"),
    PATIENCE("Patience Diff Algorithm")
}
