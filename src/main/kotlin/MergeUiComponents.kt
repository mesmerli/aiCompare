import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uniffi.compare_core.MergeRow

// ==========================================
// 8. 3-Way Merge UI Components
// ==========================================

@Composable
fun MergeCompareViewer(viewModel: MergeViewModel) {
    var localPathInput by remember { mutableStateOf(viewModel.localPath) }
    var remotePathInput by remember { mutableStateOf(viewModel.remotePath) }
    var basePathInput by remember { mutableStateOf(viewModel.basePath) }
    var outputPathInput by remember { mutableStateOf(viewModel.outputPath) }

    LaunchedEffect(viewModel.localPath, viewModel.remotePath, viewModel.basePath, viewModel.outputPath) {
        localPathInput = viewModel.localPath
        remotePathInput = viewModel.remotePath
        basePathInput = viewModel.basePath
        outputPathInput = viewModel.outputPath
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Transparent).padding(16.dp)) {
        // Paths Selection Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E22), RoundedCornerShape(16.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Row 1: Local & Base
                    Row(modifier = Modifier.fillMaxWidth()) {
                        // Local
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Local Path", color = Color(0xFF8E8E93), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BasicTextField(
                                    value = localPathInput,
                                    onValueChange = { localPathInput = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp)
                                        .background(Color(0xFF0E0E10), RoundedCornerShape(8.dp))
                                        .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                                    singleLine = true,
                                    cursorBrush = SolidColor(Color.White)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                SpringButton(
                                    onClick = { pickFile(PickerSide.LEFT) { path -> localPathInput = path } },
                                    backgroundColor = Color(0xFF2C2C32),
                                    borderColor = Color.Transparent,
                                    modifier = Modifier.size(32.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    padding = PaddingValues(0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = "Browse Local",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Base
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Base Path", color = Color(0xFF8E8E93), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BasicTextField(
                                    value = basePathInput,
                                    onValueChange = { basePathInput = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp)
                                        .background(Color(0xFF0E0E10), RoundedCornerShape(8.dp))
                                        .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                                    singleLine = true,
                                    cursorBrush = SolidColor(Color.White)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                SpringButton(
                                    onClick = { pickFile(PickerSide.LEFT) { path -> basePathInput = path } },
                                    backgroundColor = Color(0xFF2C2C32),
                                    borderColor = Color.Transparent,
                                    modifier = Modifier.size(32.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    padding = PaddingValues(0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = "Browse Base",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Row 2: Remote & Output
                    Row(modifier = Modifier.fillMaxWidth()) {
                        // Remote
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Remote Path", color = Color(0xFF8E8E93), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BasicTextField(
                                    value = remotePathInput,
                                    onValueChange = { remotePathInput = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp)
                                        .background(Color(0xFF0E0E10), RoundedCornerShape(8.dp))
                                        .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                                    singleLine = true,
                                    cursorBrush = SolidColor(Color.White)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                SpringButton(
                                    onClick = { pickFile(PickerSide.RIGHT) { path -> remotePathInput = path } },
                                    backgroundColor = Color(0xFF2C2C32),
                                    borderColor = Color.Transparent,
                                    modifier = Modifier.size(32.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    padding = PaddingValues(0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = "Browse Remote",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Output
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Output Path", color = Color(0xFF8E8E93), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BasicTextField(
                                    value = outputPathInput,
                                    onValueChange = { outputPathInput = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp)
                                        .background(Color(0xFF0E0E10), RoundedCornerShape(8.dp))
                                        .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                                    singleLine = true,
                                    cursorBrush = SolidColor(Color.White)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                SpringButton(
                                    onClick = { pickFile(PickerSide.LEFT) { path -> outputPathInput = path } },
                                    backgroundColor = Color(0xFF2C2C32),
                                    borderColor = Color.Transparent,
                                    modifier = Modifier.size(32.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    padding = PaddingValues(0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = "Browse Output",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SpringButton(
                        onClick = {
                            viewModel.startMerge(localPathInput, remotePathInput, basePathInput, outputPathInput)
                        },
                        backgroundColor = Color(0xFF007ACC),
                        borderColor = Color.Transparent,
                        modifier = Modifier.padding(top = 16.dp).height(32.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Merge", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    SpringButton(
                        onClick = {
                            val ok = viewModel.saveMergedResult()
                            if (ok) {
                                println("Merge saved successfully.")
                            }
                        },
                        backgroundColor = Color(0xFF22C55E),
                        borderColor = Color.Transparent,
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save Output", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (viewModel.isMerging) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF007ACC))
            }
        } else if (viewModel.errorMsg != null) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(viewModel.errorMsg!!, color = Color.Red, fontSize = 16.sp)
            }
        } else if (viewModel.mergeRows.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Select files and click 'Merge' to begin", color = Color.Gray, fontSize = 16.sp)
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF1E1E22), RoundedCornerShape(16.dp))
                            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .padding(8.dp)
                    ) {
                        Text("LOCAL (Left)", color = Color(0xFF4ADE80), fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                        MergeSidePanel(viewModel.mergeRows, panelType = "LOCAL")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF1E1E22), RoundedCornerShape(16.dp))
                            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .padding(8.dp)
                    ) {
                        Text("BASE (Middle)", color = Color(0xFF8E8E93), fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                        MergeSidePanel(viewModel.mergeRows, panelType = "BASE")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF1E1E22), RoundedCornerShape(16.dp))
                            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .padding(8.dp)
                    ) {
                        Text("REMOTE (Right)", color = Color(0xFFC084FC), fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                        MergeSidePanel(viewModel.mergeRows, panelType = "REMOTE")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF1E1E22), RoundedCornerShape(16.dp))
                        .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF141416), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("MERGED OUTPUT (Bottom)", color = Color(0xFF60A5FA), fontWeight = FontWeight.Black, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        if (viewModel.hasConflicts) {
                            Text("⚠️ Remaining Conflicts Exist", color = Color(0xFFFFC107), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text("✅ No Conflicts", color = Color(0xFF4ADE80), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    MergeOutputPanel(viewModel)
                }
            }
        }
    }
}

@Composable
fun MergeSidePanel(rows: List<MergeRow>, panelType: String) {
    val scrollState = rememberLazyListState()
    LazyColumn(modifier = Modifier.fillMaxSize(), state = scrollState) {
        items(rows.size) { index ->
            val row = rows[index]
            val (lineNum, text) = when (panelType) {
                "LOCAL" -> Pair(row.localLineNum, row.localText)
                "BASE" -> Pair(row.baseLineNum, row.baseText)
                else -> Pair(row.remoteLineNum, row.remoteText)
            }

            val isMarker = text == "<<<<<<< LOCAL" || text == "=======" || text == ">>>>>>> REMOTE" || text.startsWith("<<<<<<< LOCAL") || text.startsWith(">>>>>>> REMOTE")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(if (row.isConflict) Color(0x33FF5555) else Color.Transparent),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = lineNum?.toString() ?: "",
                    color = Color(0xFF858585),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Right,
                    modifier = Modifier.width(36.dp).padding(end = 6.dp)
                )

                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color(0xFF444444)))

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = if (isMarker && panelType != "LOCAL" && panelType != "REMOTE") "" else text,
                    color = if (row.isConflict) Color(0xFFFF8585) else Color(0xFFD4D4D4),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun MergeOutputPanel(viewModel: MergeViewModel) {
    val scrollState = rememberLazyListState()
    LazyColumn(modifier = Modifier.fillMaxSize(), state = scrollState) {
        items(viewModel.mergeRows.size) { index ->
            val row = viewModel.mergeRows[index]

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(if (row.isConflict) Color(0x22FFC107) else Color.Transparent),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = row.mergedLineNum?.toString() ?: "",
                    color = Color(0xFF858585),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Right,
                    modifier = Modifier.width(36.dp).padding(end = 6.dp)
                )

                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color(0xFF444444)))

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = row.mergedText,
                    color = if (row.isConflict) Color(0xFFFFC107) else Color(0xFFD4D4D4),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                    softWrap = false
                )

                if (row.isConflict && (row.mergedText.startsWith("<<<<<<< LOCAL") || row.mergedText.startsWith(">>>>>>> REMOTE") || row.mergedText == "=======")) {
                    Row(
                        modifier = Modifier.padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { viewModel.resolveConflict(index, "LOCAL") },
                            modifier = Modifier.height(24.dp).padding(horizontal = 2.dp)
                        ) {
                            Text("Local", color = Color(0xFF4CAF50), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = { viewModel.resolveConflict(index, "REMOTE") },
                            modifier = Modifier.height(24.dp).padding(horizontal = 2.dp)
                        ) {
                            Text("Remote", color = Color(0xFF9C27B0), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = { viewModel.resolveConflict(index, "BOTH") },
                            modifier = Modifier.height(24.dp).padding(horizontal = 2.dp)
                        ) {
                            Text("Both", color = Color(0xFFFFC107), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
