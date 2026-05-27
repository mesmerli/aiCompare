import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.ui.unit.DpOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import uniffi.compare_core.TextSpan
import uniffi.compare_core.TokenType

// ==========================================
// 3. UI Component (The Viewer)
// ==========================================

@Composable
fun SideBySideDiffViewer(viewModel: DiffViewModel, modifier: Modifier = Modifier) {
    // Both columns share this single LazyListState to ensure perfect scroll synchronization.
    // In Compose Desktop, virtualized scroll rendering performs extremely well when drawing a single LazyColumn
    // containing both panels row-by-row, keeping left and right lines completely aligned and responsive.
    val listState = rememberLazyListState()
    val sharedScrollOffset = remember { mutableStateOf(0f) }
    val sharedMaxScrollOffset = remember { mutableStateOf(0f) }

    // Reset scroll position to top when diff data changes (switching algorithms or files)
    LaunchedEffect(viewModel.diffLines) {
        if (viewModel.diffLines.isNotEmpty()) {
            listState.scrollToItem(0)
            sharedScrollOffset.value = 0f
            sharedMaxScrollOffset.value = 0f
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // Top Toolbar
        TopToolbar(viewModel)

        // File Headers
        FileHeaders(viewModel)

        // Main Diff Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .background(Color(0xFF121214), RoundedCornerShape(16.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // Overview Map
                    OverviewMap(
                        viewModel = viewModel,
                        listState = listState,
                        modifier = Modifier
                            .width(14.dp)
                            .fillMaxHeight()
                    )

                    // Box containing LazyColumn and Overlay
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        // Shared LazyColumn
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            items(viewModel.diffLines.size) { index ->
                                val rowData = viewModel.diffLines[index]
                                if (rowData == null) {
                                    viewModel.requestLine(index)
                                    DiffRowPlaceholder()
                                } else {
                                    DiffRowView(
                                        rowData = rowData,
                                        viewModel = viewModel,
                                        scrollOffset = sharedScrollOffset.value,
                                        onMaxScrollChanged = { maxScroll ->
                                            if (maxScroll > sharedMaxScrollOffset.value) {
                                                sharedMaxScrollOffset.value = maxScroll
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // Move Alignment Overlay
                        MoveAlignmentOverlay(
                            viewModel = viewModel,
                            listState = listState,
                            onMergeClicked = { clickedBlock, direction ->
                                viewModel.mergeBlock(clickedBlock, direction)
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Left Side Missing Overlay Card
                        if (viewModel.leftFilePath.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .fillMaxHeight()
                                    .fillMaxWidth(0.5f)
                                    .background(Color(0xFF141416).copy(alpha = 0.9f))
                                    .clickable(enabled = true, onClick = {}),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                    Text("👈 Left File Missing", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("This file does not exist on the left side.", color = Color.Gray, fontSize = 12.sp, textAlign = TextAlign.Center)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    SpringButton(
                                        onClick = {
                                            pickFile(PickerSide.LEFT) { path ->
                                                viewModel.leftFilePath = path
                                                viewModel.addToLeftHistory(path)
                                                viewModel.startCompare()
                                            }
                                        },
                                        backgroundColor = Color(0xFF007ACC)
                                    ) {
                                        Text("Select Left File...", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Right Side Missing Overlay Card
                        if (viewModel.rightFilePath.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .fillMaxWidth(0.5f)
                                    .background(Color(0xFF141416).copy(alpha = 0.9f))
                                    .clickable(enabled = true, onClick = {}),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                    Text("👉 Right File Missing", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("This file does not exist on the right side.", color = Color.Gray, fontSize = 12.sp, textAlign = TextAlign.Center)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    SpringButton(
                                        onClick = {
                                            pickFile(PickerSide.RIGHT) { path ->
                                                viewModel.rightFilePath = path
                                                viewModel.addToRightHistory(path)
                                                viewModel.startCompare()
                                            }
                                        },
                                        backgroundColor = Color(0xFF007ACC)
                                    ) {
                                        Text("Select Right File...", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Horizontal Scrollbar (Synchronized perfectly via custom adapter matching Compose version 1.6 API)
                val horizontalScrollbarAdapter = remember(sharedScrollOffset, sharedMaxScrollOffset) {
                    object : ScrollbarAdapter {
                        override val scrollOffset: Float
                            get() = sharedScrollOffset.value

                        override fun maxScrollOffset(containerSize: Int): Float {
                            return sharedMaxScrollOffset.value
                        }

                        override suspend fun scrollTo(containerSize: Int, scrollOffset: Float) {
                            sharedScrollOffset.value = scrollOffset.coerceIn(0f, sharedMaxScrollOffset.value)
                        }
                    }
                }

                HorizontalScrollbar(
                    adapter = horizontalScrollbarAdapter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .background(Color(0xFF121214))
                        .drawBehind {
                            drawLine(
                                color = Color(0xFF2D2D2D),
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = 1.dp.toPx()
                            )
                        },
                    style = ScrollbarStyle(
                        minimalHeight = 16.dp,
                        thickness = 8.dp,
                        shape = RoundedCornerShape(4.dp),
                        hoverDurationMillis = 100,
                        unhoverColor = Color(0xFF4F4F4F),
                        hoverColor = Color(0xFF7F7F7F)
                    )
                )
            }
        }

        // Statistics Summary Bar (Diff Summary)
        StatsSummaryBar(viewModel)
    }
}

@Composable
fun TopToolbar(viewModel: DiffViewModel) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF252526))
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                val y = size.height - strokeWidth / 2
                drawLine(
                    color = Color(0xFF3C3C3C),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = strokeWidth
                )
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // App Title / Branding
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "⚡",
                fontSize = 18.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = "BeyondDiff Prototype",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = " (Compose Multiplatform & Rust UniFFI Ready)",
                color = Color(0xFF858585),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        // Undo, Redo and Dropdown Menu Selector
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            SpringButton(
                onClick = { viewModel.undo() },
                backgroundColor = if (viewModel.canUndo) Color(0xFF2C2C32) else Color(0x332C2C32),
                borderColor = Color.Transparent,
                modifier = Modifier.size(28.dp),
                shape = RoundedCornerShape(6.dp),
                enabled = viewModel.canUndo,
                padding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Undo,
                    contentDescription = "Undo",
                    tint = if (viewModel.canUndo) Color.White else Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            SpringButton(
                onClick = { viewModel.redo() },
                backgroundColor = if (viewModel.canRedo) Color(0xFF2C2C32) else Color(0x332C2C32),
                borderColor = Color.Transparent,
                modifier = Modifier.size(28.dp),
                shape = RoundedCornerShape(6.dp),
                enabled = viewModel.canRedo,
                padding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Redo,
                    contentDescription = "Redo",
                    tint = if (viewModel.canRedo) Color.White else Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))

            Box {
                SpringButton(
                    onClick = { expanded = true },
                    backgroundColor = Color(0xFF2C2C32),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = viewModel.currentAlgorithmDisplayName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select Algorithm",
                        tint = Color.White,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .background(Color(0xFF1E1E22))
                        .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                ) {
                    DropdownMenuItem(
                        onClick = {
                            viewModel.changeAlgorithm(AlgorithmType.AUTO)
                            expanded = false
                        }
                    ) {
                        Text(
                            text = AlgorithmType.AUTO.displayName,
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }
                    DropdownMenuItem(
                        onClick = {
                            viewModel.changeAlgorithm(AlgorithmType.MYERS)
                            expanded = false
                        }
                    ) {
                        Text(
                            text = AlgorithmType.MYERS.displayName,
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }
                    DropdownMenuItem(
                        onClick = {
                            viewModel.changeAlgorithm(AlgorithmType.PATIENCE)
                            expanded = false
                        }
                    ) {
                        Text(
                            text = AlgorithmType.PATIENCE.displayName,
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatsSummaryBar(viewModel: DiffViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            .background(Color(0xFF141416), RoundedCornerShape(8.dp))
            .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Diff Summary:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text("Unchanged: ${viewModel.unchangedCount}", color = Color(0xFF8E8E93), fontSize = 11.sp)
        Text("Modified: ${viewModel.modificationsCount}", color = Color(0xFFFFC107), fontSize = 11.sp)
        Text("Added: ${viewModel.additionsCount}", color = Color(0xFF4CAF50), fontSize = 11.sp)
        Text("Deleted: ${viewModel.deletionsCount}", color = Color(0xFF9C27B0), fontSize = 11.sp)
        Text("Unimportant: ${viewModel.unimportantCount}", color = Color(0xFF569CD6), fontSize = 11.sp)
    }
}

@Composable
fun PathComboBox(
    path: String,
    history: List<String>,
    onPathSelected: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onPathChanged: ((String) -> Unit)? = null
) {
    var text by remember(path) { mutableStateOf(path) }
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .background(Color(0xFF1E1E1E), RoundedCornerShape(4.dp))
                .border(1.dp, Color(0xFF3F3F46), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp)
        ) {
            BasicTextField(
                value = text,
                onValueChange = {
                    text = it
                    onPathChanged?.invoke(it)
                },
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                ),
                singleLine = true,
                cursorBrush = SolidColor(Color.White),
                decorationBox = { innerTextField ->
                    if (text.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = Color(0xFF71717A),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    innerTextField()
                },
                modifier = Modifier
                    .weight(1f)
                    .pointerHoverIcon(PointerIcon(java.awt.Cursor(java.awt.Cursor.TEXT_CURSOR)))
                    .onKeyEvent {
                        if (it.key == Key.Enter && it.type == KeyEventType.KeyDown) {
                            onPathSelected(text)
                            true
                        } else {
                            false
                        }
                    }
            )

            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Show history",
                tint = Color(0xFFA1A1AA),
                modifier = Modifier
                    .size(20.dp)
                    .clickable { expanded = !expanded }
                    .pointerHoverIcon(PointerIcon(java.awt.Cursor(java.awt.Cursor.HAND_CURSOR)))
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(Color(0xFF1E1E1E))
                .border(1.dp, Color(0xFF3F3F46), RoundedCornerShape(4.dp))
                .fillMaxWidth()
        ) {
            if (history.isEmpty()) {
                DropdownMenuItem(enabled = false, onClick = {}) {
                    Text("No history available", color = Color(0xFF71717A), fontSize = 12.sp)
                }
            } else {
                history.forEach { histPath ->
                    DropdownMenuItem(
                        onClick = {
                            onPathSelected(histPath)
                            expanded = false
                        }
                    ) {
                        Text(
                            text = histPath,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FileHeaders(viewModel: DiffViewModel) {
    val isLeftMissing = viewModel.leftFilePath.isEmpty()
    val isRightMissing = viewModel.rightFilePath.isEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF252526))
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                val y = size.height - strokeWidth / 2
                drawLine(
                    color = Color(0xFF2D2D2D),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = strokeWidth
                )
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Alignment spacer for OverviewMap
        Spacer(modifier = Modifier.width(14.dp))

        // Left File Header
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PathComboBox(
                path = viewModel.leftFilePath,
                history = viewModel.leftHistory,
                onPathSelected = { path ->
                    viewModel.leftFilePath = path
                    viewModel.addToLeftHistory(path)
                    viewModel.startCompare()
                },
                placeholder = if (isLeftMissing) "⚠️ Left File Missing (Type path or browse)..." else "Enter left file path...",
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpringButton(
                    onClick = {
                        pickFile(PickerSide.LEFT) { path ->
                            viewModel.leftFilePath = path
                            viewModel.addToLeftHistory(path)
                            viewModel.startCompare()
                        }
                    },
                    backgroundColor = Color(0xFF2C2C32),
                    borderColor = Color.Transparent,
                    modifier = Modifier.size(28.dp),
                    shape = RoundedCornerShape(6.dp),
                    padding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Browse Left",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                SpringButton(
                    onClick = { viewModel.saveLeftChanges() },
                    backgroundColor = if (viewModel.hasLeftUnsaved) Color(0xFF2E7D32) else Color(0x662E7D32),
                    borderColor = Color.Transparent,
                    modifier = Modifier.size(28.dp),
                    shape = RoundedCornerShape(6.dp),
                    padding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save Left",
                        tint = if (viewModel.hasLeftUnsaved) Color.White else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Separation Spacer corresponding to vertical divider (Center Track)
        Spacer(modifier = Modifier.width(24.dp))

        // Right File Header
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PathComboBox(
                path = viewModel.rightFilePath,
                history = viewModel.rightHistory,
                onPathSelected = { path ->
                    viewModel.rightFilePath = path
                    viewModel.addToRightHistory(path)
                    viewModel.startCompare()
                },
                placeholder = if (isRightMissing) "⚠️ Right File Missing (Type path or browse)..." else "Enter right file path...",
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpringButton(
                    onClick = {
                        pickFile(PickerSide.RIGHT) { path ->
                            viewModel.rightFilePath = path
                            viewModel.addToRightHistory(path)
                            viewModel.startCompare()
                        }
                    },
                    backgroundColor = Color(0xFF2C2C32),
                    borderColor = Color.Transparent,
                    modifier = Modifier.size(28.dp),
                    shape = RoundedCornerShape(6.dp),
                    padding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Browse Right",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                SpringButton(
                    onClick = { viewModel.saveRightChanges() },
                    backgroundColor = if (viewModel.hasRightUnsaved) Color(0xFF2E7D32) else Color(0x662E7D32),
                    borderColor = Color.Transparent,
                    modifier = Modifier.size(28.dp),
                    shape = RoundedCornerShape(6.dp),
                    padding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save Right",
                        tint = if (viewModel.hasRightUnsaved) Color.White else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// 4. Visual Highlighting (配色與單行元件)
// ==========================================

fun Modifier.customHorizontalScroll(
    scrollOffset: Float,
    onMaxScrollChanged: (Float) -> Unit
): Modifier = this
    .clipToBounds()
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(maxWidth = androidx.compose.ui.unit.Constraints.Infinity))
        // Ignore dummy measurements during intrinsic measurement passes (where width equals Short.MAX_VALUE)
        if (placeable.width < 32767) {
            val maxScroll = maxOf(0, placeable.width - constraints.maxWidth)
            onMaxScrollChanged(maxScroll.toFloat())
        }
        layout(constraints.maxWidth, placeable.height) {
            placeable.placeRelative(-scrollOffset.toInt(), 0)
        }
    }

fun Modifier.hatchBackground(): Modifier = this.drawBehind {
    val stridePx = 8.dp.toPx()
    val strokeWidthPx = 1.dp.toPx()
    val hatchColor = Color(0xFFFFFFFF).copy(alpha = 0.04f)

    var c = 0f
    val limit = size.width + size.height
    while (c <= limit) {
        val xStart = maxOf(0f, c - size.height)
        val xEnd = minOf(size.width, c)
        if (xStart < xEnd) {
            drawLine(
                color = hatchColor,
                start = Offset(xStart, c - xStart),
                end = Offset(xEnd, c - xEnd),
                strokeWidth = strokeWidthPx
            )
        }
        c += stridePx
    }
}

fun buildAnnotatedText(
    text: String,
    spans: List<TextSpan>?,
    isRight: Boolean,
    status: LineStatus,
    isUnsaved: Boolean = false
): AnnotatedString {
    val defaultColor = if (isUnsaved) {
        Color(0xFF10B981) // Emerald Green
    } else {
        when (status) {
            LineStatus.UNCHANGED -> Color(0xFF909095)
            LineStatus.UNIMPORTANT -> Color(0xFF569CD6)
            LineStatus.ADDED, LineStatus.DELETED, LineStatus.MODIFIED -> Color(0xFFFF8F8F)
        }
    }

    if (spans == null || spans.isEmpty()) {
        return AnnotatedString(
            text = text,
            spanStyle = SpanStyle(color = defaultColor, fontWeight = FontWeight.Normal)
        )
    }

    val sideSpans = spans.filter { it.isRight == isRight }
    if (sideSpans.isEmpty()) {
        return AnnotatedString(
            text = text,
            spanStyle = SpanStyle(color = defaultColor, fontWeight = FontWeight.Normal)
        )
    }

    return buildAnnotatedString {
        append(text)
        addStyle(
            style = SpanStyle(color = defaultColor, fontWeight = FontWeight.Normal),
            start = 0,
            end = text.length
        )

        for (span in sideSpans) {
            val start = span.start.toInt().coerceIn(0, text.length)
            val end = span.end.toInt().coerceIn(0, text.length)
            if (start >= end) continue

            // Apply syntax highlighting based on token type
            val tokenStyle = when (span.tokenType) {
                TokenType.KEYWORD -> SpanStyle(color = Color(0xFF569CD6), fontWeight = FontWeight.Bold)
                TokenType.COMMENT -> SpanStyle(color = Color(0xFF6A9955))
                TokenType.STRING -> SpanStyle(color = Color(0xFFCE9178))
                TokenType.NUMBER -> SpanStyle(color = Color(0xFFB5CEA8))
                TokenType.NORMAL -> null
            }
            if (tokenStyle != null) {
                addStyle(tokenStyle, start, end)
            }

            // Overlay change markers for modified lines
            if (status == LineStatus.MODIFIED && span.isChanged) {
                addStyle(
                    style = SpanStyle(
                        color = Color(0xFFFF3B30), // Bright red
                        fontWeight = FontWeight.Bold
                    ),
                    start = start,
                    end = end
                )
            }
        }
    }
}

@Composable
fun DiffRowView(
    rowData: DiffRowData,
    viewModel: DiffViewModel,
    scrollOffset: Float,
    onMaxScrollChanged: (Float) -> Unit
) {
    val hoveredLeftBlock = viewModel.hoveredMoveBlockIndexLeft?.let { viewModel.moveBlocks.getOrNull(it) }
    val isLeftHovered = hoveredLeftBlock != null && rowData.leftLineNum != null && rowData.leftLineNum in hoveredLeftBlock.leftRange

    val hoveredRightBlock = viewModel.hoveredMoveBlockIndexRight?.let { viewModel.moveBlocks.getOrNull(it) }
    val isRightHovered = hoveredRightBlock != null && rowData.rightLineNum != null && rowData.rightLineNum in hoveredRightBlock.rightRange

    val isLeftUnsaved = rowData.leftLineNum?.let { lineNum ->
        val originalText = viewModel.leftOriginalLines.getOrNull(lineNum - 1)
        originalText == null || rowData.leftText != originalText
    } ?: false

    val isRightUnsaved = rowData.rightLineNum?.let { lineNum ->
        val originalText = viewModel.rightOriginalLines.getOrNull(lineNum - 1)
        originalText == null || rowData.rightText != originalText
    } ?: false

    val leftBgColor: Color
    var leftTextColor: Color
    val rightBgColor: Color
    var rightTextColor: Color

    var leftText by remember(rowData.leftText) { mutableStateOf(rowData.leftText) }
    var rightText by remember(rowData.rightText) { mutableStateOf(rowData.rightText) }

    when (rowData.status) {
        LineStatus.UNCHANGED -> {
            leftBgColor = if (isLeftHovered) Color(0x10FFFFFF) else Color.Transparent
            leftTextColor = if (isLeftUnsaved) Color(0xFF10B981) else Color(0xFF909095)
            rightBgColor = if (isRightHovered) Color(0x10FFFFFF) else Color.Transparent
            rightTextColor = if (isRightUnsaved) Color(0xFF10B981) else Color(0xFF909095)
        }
        LineStatus.MODIFIED -> {
            leftBgColor = if (isLeftHovered) Color(0x30FF4545) else Color(0x15FF4545) // Low-saturation translucent red (~8%)
            leftTextColor = if (isLeftUnsaved) Color(0xFF10B981) else Color(0xFFFF8F8F)
            rightBgColor = if (isRightHovered) Color(0x30FF4545) else Color(0x15FF4545)
            rightTextColor = if (isRightUnsaved) Color(0xFF10B981) else Color(0xFFFF8F8F)
        }
        LineStatus.DELETED -> {
            leftBgColor = if (isLeftHovered) Color(0x25888888) else Color(0x12888888) // Low-saturation translucent dark grey (~7%)
            leftTextColor = if (isLeftUnsaved) Color(0xFF10B981) else Color(0xFFFF8F8F)
            rightBgColor = if (isRightHovered) Color(0x10FFFFFF) else Color.Transparent
            rightTextColor = Color(0xFF444448) // Dim placeholder
        }
        LineStatus.ADDED -> {
            leftBgColor = if (isLeftHovered) Color(0x10FFFFFF) else Color.Transparent
            leftTextColor = Color(0xFF444448) // Dim placeholder
            rightBgColor = if (isRightHovered) Color(0x25888888) else Color(0x12888888) // Low-saturation translucent dark grey (~7%)
            rightTextColor = if (isRightUnsaved) Color(0xFF10B981) else Color(0xFFFF8F8F)
        }
        LineStatus.UNIMPORTANT -> {
            leftBgColor = if (isLeftHovered) Color(0x10FFFFFF) else Color.Transparent
            leftTextColor = if (isLeftUnsaved) Color(0xFF10B981) else Color(0xFF569CD6) // Soft tech blue
            rightBgColor = if (isRightHovered) Color(0x10FFFFFF) else Color.Transparent
            rightTextColor = if (isRightUnsaved) Color(0xFF10B981) else Color(0xFF569CD6)
        }
    }

    // Row setup using IntrinsicSize.Min so left and right columns remain vertically aligned to the taller element
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Left Column (Original View)
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Line Number (Fixed width, right aligned, no decorator lines, space separated)
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight()
                    .padding(end = 12.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = rowData.leftLineNum?.toString() ?: "",
                    color = Color(0xFF45454A), // Very low contrast dark grey
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End
                )
            }

            // Code Line Cell (no borders or grid lines, with optional Hatch background)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (rowData.leftLineNum == null) {
                            Modifier.hatchBackground()
                        } else {
                            Modifier.background(leftBgColor)
                        }
                    )
                    .customHorizontalScroll(scrollOffset, onMaxScrollChanged)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                if (rowData.leftLineNum != null) {
                    BasicTextField(
                        value = leftText,
                        onValueChange = { leftText = it },
                        textStyle = TextStyle(
                            color = leftTextColor,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(leftTextColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (!focusState.isFocused) {
                                    viewModel.updateLineText(rowData.leftLineNum, false, leftText)
                                }
                            }
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyDown) {
                                    viewModel.updateLineText(rowData.leftLineNum, false, leftText)
                                    true
                                } else {
                                    false
                                }
                            }
                    )
                }
            }
        }

        // Space/negative space separator (No physical line)
        Box(
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight()
                .background(Color(0xFF0A0A0C))
        )

        // Right Column (Modified View)
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Line Number (Fixed width, right aligned, no decorator lines, space separated)
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight()
                    .padding(end = 12.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = rowData.rightLineNum?.toString() ?: "",
                    color = Color(0xFF45454A), // Very low contrast dark grey
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End
                )
            }

            // Code Line Cell (no borders or grid lines, with optional Hatch background)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (rowData.rightLineNum == null) {
                            Modifier.hatchBackground()
                        } else {
                            Modifier.background(rightBgColor)
                        }
                    )
                    .customHorizontalScroll(scrollOffset, onMaxScrollChanged)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                if (rowData.rightLineNum != null) {
                    BasicTextField(
                        value = rightText,
                        onValueChange = { rightText = it },
                        textStyle = TextStyle(
                            color = rightTextColor,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(rightTextColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (!focusState.isFocused) {
                                    viewModel.updateLineText(rowData.rightLineNum, true, rightText)
                                }
                            }
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyDown) {
                                    viewModel.updateLineText(rowData.rightLineNum, true, rightText)
                                    true
                                } else {
                                    false
                                }
                            }
                    )
                }
            }
        }
    }
}

@Composable
fun OverviewMap(
    viewModel: DiffViewModel,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var containerHeight by remember { mutableStateOf(1f) }
    val diffLines = viewModel.diffLines
    val totalCount = diffLines.size

    Canvas(
        modifier = modifier
            .fillMaxHeight()
            .width(14.dp)
            .background(Color(0xFF121214))
            .onGloballyPositioned { coordinates ->
                if (coordinates.size.height > 0) {
                    containerHeight = coordinates.size.height.toFloat()
                }
            }
            .pointerInput(totalCount) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (change != null && (event.type == PointerEventType.Press || event.type == PointerEventType.Move)) {
                            if (change.pressed && totalCount > 0 && containerHeight > 0f) {
                                change.consume()
                                val pct = (change.position.y / containerHeight).coerceIn(0f, 1f)
                                val targetIndex = (pct * totalCount).toInt().coerceIn(0, totalCount - 1)
                                coroutineScope.launch {
                                    listState.scrollToItem(targetIndex)
                                }
                            }
                        }
                    }
                }
            }
    ) {
        if (totalCount > 0) {
            val strokeWidthPx = 1.dp.toPx()
            for (i in 0 until totalCount) {
                val status = viewModel.lineStatuses.getOrNull(i) ?: continue
                val color = when (status) {
                    LineStatus.MODIFIED -> Color(0xFFFF3B30)
                    LineStatus.ADDED, LineStatus.DELETED -> Color(0xFF55555A)
                    else -> Color.Transparent
                }
                if (color != Color.Transparent) {
                    val y = (i.toFloat() / totalCount) * size.height
                    drawLine(
                        color = color,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = strokeWidthPx
                    )
                }
            }

            // Draw current viewport overlay
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isNotEmpty()) {
                val firstIndex = visibleItems.first().index
                val lastIndex = visibleItems.last().index

                val startY = (firstIndex.toFloat() / totalCount) * size.height
                val endY = ((lastIndex + 1).toFloat() / totalCount) * size.height
                val viewHeight = maxOf(8.dp.toPx(), endY - startY)

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.12f),
                    topLeft = Offset(0f, startY),
                    size = Size(size.width, viewHeight),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.25f),
                    topLeft = Offset(0.5.dp.toPx(), startY + 0.5.dp.toPx()),
                    size = Size(size.width - 1.dp.toPx(), viewHeight - 1.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                    style = Stroke(width = 0.5.dp.toPx())
                )
            }
        }
    }
}

fun getRowYTop(index: Int, listState: LazyListState): Float? {
    val layoutInfo = listState.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return null

    val firstVisible = visibleItems.first()
    val lastVisible = visibleItems.last()

    val visibleItem = visibleItems.find { it.index == index }
    if (visibleItem != null) {
        return visibleItem.offset.toFloat()
    }

    val avgSize = visibleItems.map { it.size }.average().toFloat()
    if (index < firstVisible.index) {
        return firstVisible.offset.toFloat() - (firstVisible.index - index) * avgSize
    } else {
        return lastVisible.offset.toFloat() + lastVisible.size + (index - lastVisible.index - 1) * avgSize
    }
}

fun getRowYBottom(index: Int, listState: LazyListState): Float? {
    val layoutInfo = listState.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return null

    val firstVisible = visibleItems.first()
    val lastVisible = visibleItems.last()

    val visibleItem = visibleItems.find { it.index == index }
    if (visibleItem != null) {
        return visibleItem.offset.toFloat() + visibleItem.size.toFloat()
    }

    val avgSize = visibleItems.map { it.size }.average().toFloat()
    if (index < firstVisible.index) {
        return firstVisible.offset.toFloat() - (firstVisible.index - index - 1) * avgSize
    } else {
        return lastVisible.offset.toFloat() + lastVisible.size + (index - lastVisible.index) * avgSize
    }
}

@Composable
fun MergeArrowButton(
    direction: MoveDirection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (isHovered) 1.25f else 1.0f)

    Box(
        modifier = modifier
            .size(24.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val arrowLen = 9.dp.toPx()
            if (isHovered) {
                drawCircle(
                    color = Color(0xFFFFD700).copy(alpha = 0.4f),
                    radius = arrowLen + 3.dp.toPx(),
                    center = Offset(size.width / 2f, size.height / 2f)
                )
            }
            val path = Path().apply {
                if (direction == MoveDirection.LEFT_TO_RIGHT) {
                    moveTo(size.width / 2f + arrowLen * 0.5f, size.height / 2f)
                    lineTo(size.width / 2f - arrowLen * 0.5f, size.height / 2f - arrowLen * 0.6f)
                    lineTo(size.width / 2f - arrowLen * 0.5f, size.height / 2f + arrowLen * 0.6f)
                } else {
                    moveTo(size.width / 2f - arrowLen * 0.5f, size.height / 2f)
                    lineTo(size.width / 2f + arrowLen * 0.5f, size.height / 2f - arrowLen * 0.6f)
                    lineTo(size.width / 2f + arrowLen * 0.5f, size.height / 2f + arrowLen * 0.6f)
                }
                close()
            }
            drawPath(path, color = Color(0xFFFFC107))
            drawPath(path, color = Color(0xFF121214), style = Stroke(width = 1.dp.toPx()))
        }
    }
}

@Composable
fun MoveAlignmentOverlay(
    viewModel: DiffViewModel,
    listState: LazyListState,
    onMergeClicked: (MoveBlock, MoveDirection) -> Unit,
    modifier: Modifier = Modifier
) {
    val diffLines = viewModel.diffLines
    val moveBlocks = viewModel.moveBlocks
    if (diffLines.isEmpty() || moveBlocks.isEmpty()) return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight

        val overviewMapWidth = 0.dp
        val gutterWidth = 24.dp
        val leftWidth = (w - overviewMapWidth - gutterWidth) / 2f
        val xGutterStart = overviewMapWidth + leftWidth

        // 1. Click-through Canvas for borders (no pointerInput!)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val wPx = size.width
            val hPx = size.height

            val overviewMapWidthPx = 0.dp.toPx()
            val separatorWidthPx = 24.dp.toPx()
            val lazyColumnWidth = wPx - overviewMapWidthPx
            val leftColumnWidth = (lazyColumnWidth - separatorWidthPx) / 2f
            val xRightEdge = overviewMapWidthPx + leftColumnWidth + separatorWidthPx

            val visibleItems = listState.layoutInfo.visibleItemsInfo
            if (visibleItems.isNotEmpty()) {
                val firstVisibleIdx = visibleItems.first().index

                for (i in moveBlocks.indices) {
                    val move = moveBlocks[i]
                    val leftStartIdx = if (!move.leftRange.isEmpty()) diffLines.indexOfFirst { it?.leftLineNum == move.leftRange.first } else -1
                    val leftEndIdx = if (!move.leftRange.isEmpty()) diffLines.indexOfLast { it?.leftLineNum == move.leftRange.last } else -1
                    val rightStartIdx = if (!move.rightRange.isEmpty()) diffLines.indexOfFirst { it?.rightLineNum == move.rightRange.first } else -1
                    val rightEndIdx = if (!move.rightRange.isEmpty()) diffLines.indexOfLast { it?.rightLineNum == move.rightRange.last } else -1

                    val startIdx = listOf(leftStartIdx, rightStartIdx).filter { it != -1 }.minOrNull() ?: -1
                    val endIdx = listOf(leftEndIdx, rightEndIdx).filter { it != -1 }.maxOrNull() ?: -1

                    if (startIdx != -1 && endIdx != -1) {
                        val startItem = visibleItems.find { it.index == startIdx }
                        val endItem = visibleItems.find { it.index == endIdx }

                        if (startItem != null || endItem != null) {
                            val yTop = startItem?.offset?.toFloat() ?: if (startIdx < firstVisibleIdx) -100f else hPx + 100f
                            val yBottom = endItem?.let { (it.offset + it.size).toFloat() } ?: if (endIdx < firstVisibleIdx) -100f else hPx + 100f

                            val isVisible = !(yBottom < 0f || yTop > hPx)
                            if (!isVisible) continue

                            val borderAlpha = 0.25f
                            val leftBorderColor = Color(0xFFFF3B30).copy(alpha = borderAlpha)
                            val rightBorderColor = Color(0xFFFF3B30).copy(alpha = borderAlpha)

                            if (!move.leftRange.isEmpty()) {
                                drawRect(
                                    color = leftBorderColor,
                                    topLeft = Offset(overviewMapWidthPx, yTop),
                                    size = Size(leftColumnWidth, yBottom - yTop),
                                    style = Stroke(width = 0.5.dp.toPx())
                                )
                            }

                            if (!move.rightRange.isEmpty()) {
                                drawRect(
                                    color = rightBorderColor,
                                    topLeft = Offset(xRightEdge, yTop),
                                    size = Size(wPx - xRightEdge, yBottom - yTop),
                                    style = Stroke(width = 0.5.dp.toPx())
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Interactive merge buttons placed on top
        val density = LocalDensity.current
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        if (visibleItems.isNotEmpty()) {
            for (i in moveBlocks.indices) {
                val move = moveBlocks[i]
                val leftStartIdx = if (!move.leftRange.isEmpty()) diffLines.indexOfFirst { it?.leftLineNum == move.leftRange.first } else -1
                val rightStartIdx = if (!move.rightRange.isEmpty()) diffLines.indexOfFirst { it?.rightLineNum == move.rightRange.first } else -1
                val startIdx = listOf(leftStartIdx, rightStartIdx).filter { it != -1 }.minOrNull() ?: -1

                if (startIdx != -1) {
                    val startItem = visibleItems.find { it.index == startIdx }
                    if (startItem != null) {
                        val yTopDp = with(density) { startItem.offset.toDp() }

                        // Render Left Arrow Merge Button
                        if (!move.leftRange.isEmpty()) {
                            MergeArrowButton(
                                direction = MoveDirection.LEFT_TO_RIGHT,
                                onClick = { onMergeClicked(move, MoveDirection.LEFT_TO_RIGHT) },
                                modifier = Modifier.offset(x = 12.dp - 12.dp, y = yTopDp - 12.dp)
                            )
                        }

                        // Render Right Arrow Merge Button
                        if (!move.rightRange.isEmpty()) {
                            MergeArrowButton(
                                direction = MoveDirection.RIGHT_TO_LEFT,
                                onClick = { onMergeClicked(move, MoveDirection.RIGHT_TO_LEFT) },
                                modifier = Modifier.offset(x = leftWidth + 24.dp - 12.dp, y = yTopDp - 12.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. Scrollbar inside the central gutter
        Box(
            modifier = Modifier
                .offset(x = xGutterStart, y = 0.dp)
                .width(gutterWidth)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier
                    .fillMaxHeight()
                    .width(12.dp),
                style = ScrollbarStyle(
                    minimalHeight = 16.dp,
                    thickness = 8.dp,
                    shape = RoundedCornerShape(4.dp),
                    hoverDurationMillis = 100,
                    unhoverColor = Color(0xFF4F4F4F),
                    hoverColor = Color(0xFF7F7F7F)
                )
            )
        }
    }
}

@Composable
fun DiffRowPlaceholder() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(Color(0xFF1E1E1E)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .fillMaxHeight()
                .background(Color(0xFF1E1E1E))
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(Color(0xFF2D2D2D))
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "...",
                color = Color(0xFF555555),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(Color(0xFF3C3C3C))
        )
        Box(
            modifier = Modifier
                .width(42.dp)
                .fillMaxHeight()
                .background(Color(0xFF1E1E1E))
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(Color(0xFF2D2D2D))
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "...",
                color = Color(0xFF555555),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
