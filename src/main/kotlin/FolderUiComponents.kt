import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LowPriority
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uniffi.compare_core.FolderDiffRow
import uniffi.compare_core.FolderDiffStatus

// ==========================================
// 5. Folder Compare UI Components
// ==========================================

@Composable
fun SpringButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF1E1E22),
    contentColor: Color = Color.White,
    borderColor: Color = Color.White.copy(alpha = 0.08f),
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    enabled: Boolean = true,
    padding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            !enabled -> 1f
            isPressed -> 0.95f
            isHovered -> 1.04f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )

    val animatedBgColor by animateColorAsState(
        targetValue = when {
            !enabled -> backgroundColor.copy(alpha = 0.3f)
            isPressed -> backgroundColor.copy(alpha = 0.85f)
            isHovered -> backgroundColor.copy(alpha = 0.95f)
            else -> backgroundColor
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    )

    Box(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .background(animatedBgColor, shape)
            .border(0.5.dp, if (enabled) borderColor else borderColor.copy(alpha = 0.02f), shape)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CompositionLocalProvider(
                LocalContentColor provides contentColor
            ) {
                content()
            }
        }
    }
}

private var lastOpenedDirectoryLeft: java.io.File? = null
private var lastOpenedDirectoryRight: java.io.File? = null

fun pickFolder(side: PickerSide = PickerSide.LEFT, onSelected: (String) -> Unit) {
    javax.swing.SwingUtilities.invokeLater {
        val chooser = javax.swing.JFileChooser().apply {
            fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Select Folder"
            val lastDir = if (side == PickerSide.LEFT) lastOpenedDirectoryLeft else lastOpenedDirectoryRight
            if (lastDir != null) {
                currentDirectory = lastDir
            }
        }
        val result = chooser.showOpenDialog(null)
        if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
            val selected = chooser.selectedFile
            val dir = if (selected.isDirectory) selected else selected.parentFile
            if (side == PickerSide.LEFT) {
                lastOpenedDirectoryLeft = dir
            } else {
                lastOpenedDirectoryRight = dir
            }
            onSelected(selected.absolutePath)
        }
    }
}

fun formatSize(size: ULong?): String {
    if (size == null) return ""
    val s = size.toLong()
    if (s < 1024) return "$s B"
    val kb = s / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format("%.1f MB", mb)
}

fun formatTime(timestamp: ULong?): String {
    if (timestamp == null || timestamp == 0UL) return ""
    val t = timestamp.toLong()
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(t * 1000))
}

fun pickFile(side: PickerSide = PickerSide.LEFT, onSelected: (String) -> Unit) {
    javax.swing.SwingUtilities.invokeLater {
        val chooser = javax.swing.JFileChooser().apply {
            fileSelectionMode = javax.swing.JFileChooser.FILES_AND_DIRECTORIES
            dialogTitle = "Select File or Folder"
            val lastDir = if (side == PickerSide.LEFT) lastOpenedDirectoryLeft else lastOpenedDirectoryRight
            if (lastDir != null) {
                currentDirectory = lastDir
            }
        }
        val result = chooser.showOpenDialog(null)
        if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
            val selected = chooser.selectedFile
            val dir = if (selected.isDirectory) selected else selected.parentFile
            if (side == PickerSide.LEFT) {
                lastOpenedDirectoryLeft = dir
            } else {
                lastOpenedDirectoryRight = dir
            }
            onSelected(selected.absolutePath)
        }
    }
}

@Composable
fun FolderCompareViewer(viewModel: FolderViewModel, workspaceViewModel: WorkspaceViewModel? = null, tabId: String? = null) {
    fun triggerCompare(left: String, right: String) {
        if (left.isNotEmpty() || right.isNotEmpty()) {
            viewModel.startCompare(left, right)
            if (workspaceViewModel != null && tabId != null) {
                workspaceViewModel.updateTabTitleFromPaths(tabId, left, right)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(16.dp)
        ) {
            // Toolbar above path inputs
            FolderCompareToolbar(viewModel, workspaceViewModel, tabId)

            Spacer(modifier = Modifier.height(8.dp))

            // Selection Card
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
                    // Left Folder Input
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PathComboBox(
                                path = viewModel.leftFolderPath,
                                history = viewModel.leftHistory,
                                onPathSelected = { path ->
                                    viewModel.leftFolderPath = path
                                    triggerCompare(path, viewModel.rightFolderPath)
                                },
                                onPathChanged = { path ->
                                    viewModel.leftFolderPath = path
                                },
                                placeholder = "Enter left folder path...",
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val leftHasParent = viewModel.leftFolderPath.isNotEmpty() && java.io.File(viewModel.leftFolderPath).parentFile != null
                            SpringButton(
                                onClick = {
                                    val currentFile = java.io.File(viewModel.leftFolderPath)
                                    val parent = currentFile.parentFile
                                    if (parent != null) {
                                        viewModel.leftFolderPath = parent.absolutePath
                                        triggerCompare(viewModel.leftFolderPath, viewModel.rightFolderPath)
                                    }
                                },
                                backgroundColor = Color(0xFF2C2C32),
                                borderColor = Color.Transparent,
                                modifier = Modifier.size(32.dp),
                                shape = RoundedCornerShape(8.dp),
                                enabled = leftHasParent,
                                padding = PaddingValues(0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Go to Parent Left",
                                    tint = if (leftHasParent) Color.White else Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            SpringButton(
                                onClick = {
                                    pickFolder(PickerSide.LEFT) { path ->
                                        viewModel.leftFolderPath = path
                                        triggerCompare(viewModel.leftFolderPath, viewModel.rightFolderPath)
                                    }
                                },
                                backgroundColor = Color(0xFF2C2C32),
                                borderColor = Color.Transparent,
                                modifier = Modifier.size(32.dp),
                                shape = RoundedCornerShape(8.dp),
                                padding = PaddingValues(0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = "Browse Left",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Right Folder Input
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PathComboBox(
                                path = viewModel.rightFolderPath,
                                history = viewModel.rightHistory,
                                onPathSelected = { path ->
                                    viewModel.rightFolderPath = path
                                    triggerCompare(viewModel.leftFolderPath, path)
                                },
                                onPathChanged = { path ->
                                    viewModel.rightFolderPath = path
                                },
                                placeholder = "Enter right folder path...",
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val rightHasParent = viewModel.rightFolderPath.isNotEmpty() && java.io.File(viewModel.rightFolderPath).parentFile != null
                            SpringButton(
                                onClick = {
                                    val currentFile = java.io.File(viewModel.rightFolderPath)
                                    val parent = currentFile.parentFile
                                    if (parent != null) {
                                        viewModel.rightFolderPath = parent.absolutePath
                                        triggerCompare(viewModel.leftFolderPath, viewModel.rightFolderPath)
                                    }
                                },
                                backgroundColor = Color(0xFF2C2C32),
                                borderColor = Color.Transparent,
                                modifier = Modifier.size(32.dp),
                                shape = RoundedCornerShape(8.dp),
                                enabled = rightHasParent,
                                padding = PaddingValues(0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Go to Parent Right",
                                    tint = if (rightHasParent) Color.White else Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            SpringButton(
                                onClick = {
                                    pickFolder(PickerSide.RIGHT) { path ->
                                        viewModel.rightFolderPath = path
                                        triggerCompare(viewModel.leftFolderPath, viewModel.rightFolderPath)
                                    }
                                },
                                backgroundColor = Color(0xFF2C2C32),
                                borderColor = Color.Transparent,
                                modifier = Modifier.size(32.dp),
                                shape = RoundedCornerShape(8.dp),
                                padding = PaddingValues(0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = "Browse Right",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            viewModel.scanError?.let { err ->
                Text(
                    text = "Error: $err",
                    color = Color.Red,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            FolderHeaderGrid()

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E22), RoundedCornerShape(16.dp))
                    .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
            ) {
                if (viewModel.isScanning) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF007ACC))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Scanning directories in parallel using Rust + Rayon...", color = Color.Gray)
                        }
                    }
                } else if (viewModel.visibleRows.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Select left and right folders to start comparison.", color = Color.Gray)
                    }
                } else {
                    val state = rememberLazyListState()
                    LazyColumn(
                        state = state,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(viewModel.visibleRows) { index, row ->
                            FolderDiffRowView(row, index, viewModel, workspaceViewModel, tabId)
                        }
                    }

                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(state)
                    )
                }
            }

            if (viewModel.allRows.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                FolderStatsBar(viewModel)
            }
        }

        // Overlay dialog
        val activeConfirmAction = viewModel.activeConfirmAction
        val hasSelection = viewModel.selectedRows.isNotEmpty()
        AnimatedVisibility(
            visible = activeConfirmAction != null && hasSelection,
            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                    scaleIn(initialScale = 0.9f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)),
            exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                    scaleOut(targetScale = 0.9f, animationSpec = spring(stiffness = Spring.StiffnessMedium))
        ) {
            if (activeConfirmAction != null && hasSelection) {
                ConfirmationDialog(
                    action = activeConfirmAction,
                    selectedRows = viewModel.selectedRows,
                    onConfirm = {
                        viewModel.executeFolderAction(activeConfirmAction)
                    },
                    onDismiss = {
                        viewModel.activeConfirmAction = null
                    }
                )
            }
        }
    }
}

@Composable
fun ConfirmationDialog(
    action: FolderViewModel.FolderAction,
    selectedRows: Map<String, FolderViewModel.SelectionSide>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val titleText = when (action) {
        FolderViewModel.FolderAction.COPY_TO_RIGHT -> "Copy to Right"
        FolderViewModel.FolderAction.COPY_TO_LEFT -> "Copy to Left"
        FolderViewModel.FolderAction.DELETE -> "Delete File/Folder"
    }

    val count = selectedRows.size
    val firstItemName = selectedRows.keys.firstOrNull()?.substringAfterLast("/") ?: ""
    val itemsText = if (count == 1) "'$firstItemName'" else "$count selected items"

    val warningText = when (action) {
        FolderViewModel.FolderAction.COPY_TO_RIGHT ->
            "Are you sure you want to copy $itemsText to the right folder? This will modify/overwrite files on the right side."
        FolderViewModel.FolderAction.COPY_TO_LEFT ->
            "Are you sure you want to copy $itemsText to the left folder? This will modify/overwrite files on the left side."
        FolderViewModel.FolderAction.DELETE -> {
            val sideText = when {
                selectedRows.values.all { it == FolderViewModel.SelectionSide.LEFT } -> "from the LEFT folder only"
                selectedRows.values.all { it == FolderViewModel.SelectionSide.RIGHT } -> "from the RIGHT folder only"
                else -> "from BOTH left and right folders"
            }
            "WARNING: This action is permanent! Are you sure you want to delete $itemsText $sideText?"
        }
    }

    val confirmButtonColor = when (action) {
        FolderViewModel.FolderAction.DELETE -> Color(0xCCFF3B30)
        else -> Color(0xCC007ACC)
    }

    val confirmButtonText = when (action) {
        FolderViewModel.FolderAction.COPY_TO_RIGHT -> "Copy ➔"
        FolderViewModel.FolderAction.COPY_TO_LEFT -> "⬅ Copy"
        FolderViewModel.FolderAction.DELETE -> "Delete 🗑"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF26262B),
                            Color(0xFF1E1E22)
                        )
                    )
                )
                .border(
                    width = 0.5.dp,
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(28.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = titleText,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = warningText,
                    color = Color(0xFFE2E2E6),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SpringButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        backgroundColor = Color(0xFF2C2C32),
                        borderColor = Color.White.copy(alpha = 0.06f),
                        shape = RoundedCornerShape(14.dp),
                        padding = PaddingValues(vertical = 10.dp)
                    ) {
                        Text(
                            text = "Cancel",
                            color = Color(0xFF8E8E93),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }

                    SpringButton(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        backgroundColor = confirmButtonColor,
                        borderColor = Color.Transparent,
                        shape = RoundedCornerShape(14.dp),
                        padding = PaddingValues(vertical = 10.dp)
                    ) {
                        Text(
                            text = confirmButtonText,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FilterSegmentButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val animatedBgColor by animateColorAsState(
        targetValue = if (selected) Color(0xFF2C2C30) else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    )
    val animatedTextColor by animateColorAsState(
        targetValue = if (selected) Color.White else Color(0xFF8E8E93),
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(28.dp))
            .background(animatedBgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = animatedTextColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun FolderCompareToolbar(
    viewModel: FolderViewModel,
    workspaceViewModel: WorkspaceViewModel? = null,
    tabId: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color(0xFF1E1E22), RoundedCornerShape(8.dp))
            .border(
                0.5.dp,
                Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(8.dp)
            )
            .drawBehind {
                drawLine(
                    color = Color(0xFFFFFFFF).copy(alpha = 0.08f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 0.5.dp.toPx()
                )
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // ── Group 1: Primary display mode (mutually exclusive) ──
        FilterIconButton(
            icon = Icons.AutoMirrored.Filled.FormatListBulleted,
            tooltip = "Show All",
            selected = viewModel.filterMode == FolderFilterMode.SHOW_ALL,
            onClick = { viewModel.filterMode = FolderFilterMode.SHOW_ALL }
        )
        FilterIconButton(
            icon = Icons.Default.Compare,
            tooltip = "Show Differences",
            selected = viewModel.filterMode == FolderFilterMode.SHOW_DIFF,
            onClick = { viewModel.filterMode = FolderFilterMode.SHOW_DIFF }
        )
        FilterIconButton(
            icon = Icons.Default.DragHandle,
            tooltip = "Show Same",
            selected = viewModel.filterMode == FolderFilterMode.SHOW_SAME,
            onClick = { viewModel.filterMode = FolderFilterMode.SHOW_SAME }
        )

        // ── Vertical divider ──
        Box(
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .width(0.5.dp)
                .height(24.dp)
                .background(Color.White.copy(alpha = 0.12f))
        )

        // ── Group 2: Independent overlay toggles ──
        FilterToggleButton(
            icon = Icons.AutoMirrored.Filled.CallSplit,
            tooltip = if (viewModel.showOrphans) "Hide Orphans" else "Show Orphans",
            active = viewModel.showOrphans,
            activeColor = Color(0xFF4CAF50),   // green — orphans use green/purple already
            onClick = { viewModel.showOrphans = !viewModel.showOrphans }
        )
        FilterToggleButton(
            icon = Icons.Default.LowPriority,
            tooltip = if (viewModel.showLessImportant) "Hide Less Important" else "Show Less Important",
            active = viewModel.showLessImportant,
            activeColor = Color(0xFF569CD6),   // soft tech blue — matches unimportant color
            onClick = { viewModel.showLessImportant = !viewModel.showLessImportant }
        )

        // ── Vertical divider ──
        Box(
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .width(0.5.dp)
                .height(24.dp)
                .background(Color.White.copy(alpha = 0.12f))
        )

        // ── Group 3: View & Navigation ──
        val infiniteTransition = rememberInfiniteTransition()
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )

        ActionIconButton(
            icon = Icons.Default.Refresh,
            tooltip = "Refresh",
            enabled = viewModel.leftFolderPath.isNotEmpty() && viewModel.rightFolderPath.isNotEmpty(),
            activeColor = Color(0xFF82AAFF),
            iconModifier = if (viewModel.isScanning) Modifier.graphicsLayer(rotationZ = rotation) else Modifier,
            onClick = {
                viewModel.startCompare(viewModel.leftFolderPath, viewModel.rightFolderPath)
            }
        )

        ActionIconButton(
            icon = Icons.Default.UnfoldMore,
            tooltip = "Expand All",
            enabled = viewModel.allRows.isNotEmpty(),
            activeColor = Color(0xFF82AAFF),
            onClick = { viewModel.expandAll() }
        )

        ActionIconButton(
            icon = Icons.Default.UnfoldLess,
            tooltip = "Collapse All",
            enabled = viewModel.allRows.isNotEmpty(),
            activeColor = Color(0xFF82AAFF),
            onClick = { viewModel.collapseAll() }
        )

        ActionIconButton(
            icon = Icons.Default.ArrowUpward,
            tooltip = "Up One Level",
            enabled = viewModel.canGoUp,
            activeColor = Color(0xFF82AAFF),
            onClick = {
                viewModel.goUpOneLevel()
                if (workspaceViewModel != null && tabId != null) {
                    workspaceViewModel.updateTabTitleFromPaths(tabId, viewModel.leftFolderPath, viewModel.rightFolderPath)
                }
            }
        )

        // Spacer to push action buttons to the right
        Spacer(modifier = Modifier.weight(1f))

        val isAnySelected = viewModel.selectedRows.isNotEmpty()

        ContextAwareCopyButton(viewModel)

        Spacer(modifier = Modifier.width(4.dp))

        ActionIconButton(
            icon = Icons.Default.Delete,
            tooltip = "Delete",
            enabled = isAnySelected,
            activeColor = Color(0xFFFF5252), // soft red
            onClick = {
                viewModel.activeConfirmAction = FolderViewModel.FolderAction.DELETE
            }
        )
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
fun ContextAwareCopyButton(viewModel: FolderViewModel, modifier: Modifier = Modifier) {
    val selectedRows = viewModel.selectedRows
    val hasLeftSelection = selectedRows.any { it.value == FolderViewModel.SelectionSide.LEFT || it.value == FolderViewModel.SelectionSide.BOTH }
    val hasRightSelection = selectedRows.any { it.value == FolderViewModel.SelectionSide.RIGHT || it.value == FolderViewModel.SelectionSide.BOTH }
    val isAnySelected = selectedRows.isNotEmpty()

    val buttonState = when {
        !isAnySelected -> ButtonState.DISABLED
        hasLeftSelection && !hasRightSelection -> ButtonState.COPY_TO_RIGHT
        !hasLeftSelection && hasRightSelection -> ButtonState.COPY_TO_LEFT
        else -> ButtonState.COPY_TO_SIDE
    }

    val isEnabled = buttonState != ButtonState.DISABLED

    val tooltipText = when (buttonState) {
        ButtonState.DISABLED -> "Copy"
        ButtonState.COPY_TO_RIGHT -> "Copy to Right ➔"
        ButtonState.COPY_TO_LEFT -> "⬅ Copy to Left"
        ButtonState.COPY_TO_SIDE -> "Copy to Side... ⇄"
    }

    val icon = when (buttonState) {
        ButtonState.COPY_TO_LEFT -> Icons.AutoMirrored.Filled.ArrowBack
        ButtonState.COPY_TO_SIDE -> Icons.Default.SwapHoriz
        else -> Icons.AutoMirrored.Filled.ArrowForward
    }

    val activeColor = when (buttonState) {
        ButtonState.COPY_TO_RIGHT -> Color(0xFF4CAF50) // Green
        ButtonState.COPY_TO_LEFT -> Color(0xFF9C27B0)  // Purple
        ButtonState.COPY_TO_SIDE -> Color(0xFF00B0FF)  // Blue/Cyan
        else -> Color.White.copy(alpha = 0.25f)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val animatedBgColor by animateColorAsState(
        targetValue = when {
            !isEnabled -> Color.Transparent
            isPressed -> activeColor.copy(alpha = 0.2f)
            isHovered -> activeColor.copy(alpha = 0.12f)
            else -> activeColor.copy(alpha = 0.08f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = when {
            !isEnabled -> Color.White.copy(alpha = 0.08f)
            isHovered -> activeColor.copy(alpha = 0.4f)
            else -> activeColor.copy(alpha = 0.2f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    )

    val animatedIconTint by animateColorAsState(
        targetValue = when {
            !isEnabled -> Color.White.copy(alpha = 0.25f)
            isHovered -> activeColor
            else -> Color.White.copy(alpha = 0.85f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    )

    val scale by animateFloatAsState(
        targetValue = when {
            !isEnabled -> 1f
            isPressed -> 0.92f
            isHovered -> 1.08f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
    )

    var showDropdown by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        TooltipArea(
            tooltip = {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF2C2C32),
                    elevation = 4.dp
                ) {
                    Text(
                        text = tooltipText,
                        color = Color(0xFFCCCCCC),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            },
            delayMillis = 400,
            tooltipPlacement = TooltipPlacement.CursorPoint(
                offset = DpOffset(0.dp, 20.dp)
            )
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale)
                    .clip(RoundedCornerShape(8.dp))
                    .background(animatedBgColor)
                    .border(0.5.dp, animatedBorderColor, RoundedCornerShape(8.dp))
                    .then(
                        if (isEnabled) {
                            Modifier
                                .hoverable(interactionSource)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = {
                                        if (buttonState == ButtonState.COPY_TO_SIDE) {
                                            showDropdown = true
                                        } else {
                                            val action = if (buttonState == ButtonState.COPY_TO_RIGHT) {
                                                FolderViewModel.FolderAction.COPY_TO_RIGHT
                                            } else {
                                                FolderViewModel.FolderAction.COPY_TO_LEFT
                                            }
                                            viewModel.executeFolderActionDirectly(action)
                                        }
                                    }
                                )
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = buttonState,
                    transitionSpec = {
                        (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                                scaleIn(initialScale = 0.92f, animationSpec = spring(stiffness = Spring.StiffnessMedium)))
                            .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)))
                    }
                ) { targetState ->
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = animatedIconTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (showDropdown && isEnabled) {
            MaterialTheme(
                colors = MaterialTheme.colors.copy(
                    surface = Color(0xFF1E1E22),
                    onSurface = Color.White
                )
            ) {
                DropdownMenu(
                    expanded = showDropdown,
                    onDismissRequest = { showDropdown = false },
                    modifier = Modifier
                        .background(Color(0xFF1E1E22))
                        .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                ) {
                    DropdownMenuItem(
                        onClick = {
                            showDropdown = false
                            viewModel.executeFolderActionDirectly(FolderViewModel.FolderAction.COPY_TO_RIGHT)
                        }
                    ) {
                        Text(
                            text = "Copy Left to Right ➔",
                            color = Color(0xFF4CAF50),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    DropdownMenuItem(
                        onClick = {
                            showDropdown = false
                            viewModel.executeFolderActionDirectly(FolderViewModel.FolderAction.COPY_TO_LEFT)
                        }
                    ) {
                        Text(
                            text = "⬅ Copy Right to Left",
                            color = Color(0xFF9C27B0),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

enum class ButtonState {
    DISABLED,
    COPY_TO_RIGHT,
    COPY_TO_LEFT,
    COPY_TO_SIDE
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tooltip: String,
    enabled: Boolean,
    activeColor: Color = Color(0xFF82AAFF),
    iconModifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            !enabled -> 1f
            isPressed -> 0.92f
            isHovered -> 1.08f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )

    val animatedBgColor by animateColorAsState(
        targetValue = when {
            !enabled -> Color.Transparent
            isPressed -> activeColor.copy(alpha = 0.2f)
            isHovered -> activeColor.copy(alpha = 0.12f)
            else -> Color.Transparent
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    )

    val animatedIconTint by animateColorAsState(
        targetValue = when {
            !enabled -> Color.White.copy(alpha = 0.25f)
            isHovered -> activeColor
            else -> Color.White.copy(alpha = 0.85f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    )

    TooltipArea(
        tooltip = {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF2C2C32),
                elevation = 4.dp
            ) {
                Text(
                    text = tooltip,
                    color = Color(0xFFCCCCCC),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        },
        delayMillis = 400,
        tooltipPlacement = TooltipPlacement.CursorPoint(
            offset = DpOffset(0.dp, 20.dp)
        )
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .clip(RoundedCornerShape(8.dp))
                .background(animatedBgColor)
                .then(
                    if (enabled) {
                        Modifier
                            .hoverable(interactionSource)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = onClick
                            )
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = tooltip,
                tint = animatedIconTint,
                modifier = Modifier.size(18.dp).then(iconModifier)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilterIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tooltip: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val animatedBgColor by animateColorAsState(
        targetValue = when {
            selected -> Color(0xFF2C2C38)
            isHovered -> Color(0xFF222228)
            else -> Color.Transparent
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    )
    val animatedIconTint by animateColorAsState(
        targetValue = if (selected) Color(0xFF82AAFF) else Color(0xFF8E8E93),
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    )

    TooltipArea(
        tooltip = {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF2C2C32),
                elevation = 4.dp
            ) {
                Text(
                    text = tooltip,
                    color = Color(0xFFCCCCCC),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        },
        delayMillis = 400,
        tooltipPlacement = TooltipPlacement.CursorPoint(
            offset = DpOffset(0.dp, 20.dp)
        )
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(animatedBgColor)
                .hoverable(interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = tooltip,
                tint = animatedIconTint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Independent toggle button for overlay filters (Show Orphans, Show Less Important).
 * Unlike [FilterIconButton], these are not mutually exclusive — each controls its own boolean.
 * When [active] is true the icon glows with [activeColor]; when false it dims.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilterToggleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tooltip: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    // Background: faint tinted wash when active, subtle on hover, transparent when inactive+not hovered
    val animatedBgColor by animateColorAsState(
        targetValue = when {
            active && isHovered -> activeColor.copy(alpha = 0.22f)
            active             -> activeColor.copy(alpha = 0.14f)
            isHovered          -> Color(0xFF222228)
            else               -> Color.Transparent
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    )
    // Icon tint: glows with activeColor when ON, dims when OFF
    val animatedIconTint by animateColorAsState(
        targetValue = if (active) activeColor else Color(0xFF555558),
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    )
    // Border: accent glow ring when active
    val animatedBorderColor by animateColorAsState(
        targetValue = if (active) activeColor.copy(alpha = 0.35f) else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    )

    TooltipArea(
        tooltip = {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF2C2C32),
                elevation = 4.dp
            ) {
                Text(
                    text = tooltip,
                    color = Color(0xFFCCCCCC),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        },
        delayMillis = 400,
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 20.dp))
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(animatedBgColor)
                .border(0.5.dp, animatedBorderColor, RoundedCornerShape(8.dp))
                .hoverable(interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = tooltip,
                tint = animatedIconTint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun FolderStatsBar(viewModel: FolderViewModel) {
    val total = viewModel.allRows.size
    val unchanged = viewModel.allRows.count { it.status == FolderDiffStatus.UNCHANGED }
    val modified = viewModel.allRows.count { it.status == FolderDiffStatus.MODIFIED }
    val leftOrphan = viewModel.allRows.count { it.status == FolderDiffStatus.LEFT_ORPHAN }
    val rightOrphan = viewModel.allRows.count { it.status == FolderDiffStatus.RIGHT_ORPHAN }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF141416), RoundedCornerShape(8.dp))
            .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Total Scanned: $total", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text("Unchanged: $unchanged", color = Color(0xFF8E8E93), fontSize = 11.sp)
        Text("Modified: $modified", color = Color(0xFFFFC107), fontSize = 11.sp)
        Text("Left Orphan: $leftOrphan", color = Color(0xFF4CAF50), fontSize = 11.sp)
        Text("Right Orphan: $rightOrphan", color = Color(0xFF9C27B0), fontSize = 11.sp)
    }
}

@Composable
fun FolderHeaderGrid() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF252526))
            .padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text("Left File / Folder Name", color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), fontSize = 12.sp)
            Text("Size", color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp), textAlign = TextAlign.End, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Modified Time", color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.width(160.dp), textAlign = TextAlign.Center, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.width(40.dp))

        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text("Right File / Folder Name", color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), fontSize = 12.sp)
            Text("Size", color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp), textAlign = TextAlign.End, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Modified Time", color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.width(160.dp), textAlign = TextAlign.Center, fontSize = 12.sp)
        }
    }
}

fun shouldDrawVerticalLineBelow(visibleRows: List<FolderDiffRow>, currentIndex: Int, col: Int): Boolean {
    for (i in (currentIndex + 1) until visibleRows.size) {
        val nextLevel = visibleRows[i].level.toInt()
        if (nextLevel == col + 1) {
            return true
        }
        if (nextLevel <= col) {
            return false
        }
    }
    return false
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FolderTreeLines(row: FolderDiffRow, rowIndex: Int, viewModel: FolderViewModel) {
    val level = row.level.toInt()
    if (level <= 0) return

    val visibleRows = viewModel.visibleRows
    if (rowIndex < 0 || rowIndex >= visibleRows.size) {
        Spacer(modifier = Modifier.width((level * 16).dp))
        return
    }

    Row(modifier = Modifier.fillMaxHeight()) {
        for (col in 0 until level) {
            val drawFullVertical = remember(rowIndex, col, visibleRows) {
                shouldDrawVerticalLineBelow(visibleRows, rowIndex, col)
            }
            val drawLowerVertical = remember(rowIndex, col, visibleRows) {
                shouldDrawVerticalLineBelow(visibleRows, rowIndex, col)
            }
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .fillMaxHeight()
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val colWidth = size.width
                    val colHeight = size.height
                    val centerX = colWidth / 2f
                    val centerY = colHeight / 2f
                    val lineColor = Color.White.copy(alpha = 0.15f)
                    val strokeWidth = 1.dp.toPx()

                    if (col < level - 1) {
                        if (drawFullVertical) {
                            drawLine(
                                color = lineColor,
                                start = Offset(centerX, 0f),
                                end = Offset(centerX, colHeight),
                                strokeWidth = strokeWidth
                            )
                        }
                    } else if (col == level - 1) {
                        // Upper vertical line
                        drawLine(
                            color = lineColor,
                            start = Offset(centerX, 0f),
                            end = Offset(centerX, centerY),
                            strokeWidth = strokeWidth
                        )
                        // Horizontal connector
                        drawLine(
                            color = lineColor,
                            start = Offset(centerX, centerY),
                            end = Offset(colWidth, centerY),
                            strokeWidth = strokeWidth
                        )
                        // Lower vertical line
                        if (drawLowerVertical) {
                            drawLine(
                                color = lineColor,
                                start = Offset(centerX, centerY),
                                end = Offset(centerX, colHeight),
                                strokeWidth = strokeWidth
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FolderDiffRowView(
    row: FolderDiffRow,
    rowIndex: Int,
    viewModel: FolderViewModel,
    workspaceViewModel: WorkspaceViewModel? = null,
    tabId: String? = null
) {
    val statusColor = if (row.isBackupMatch) {
        Color(0xFF569CD6)
    } else {
        when (row.status) {
            FolderDiffStatus.UNCHANGED -> Color(0xFFD4D4D4)
            FolderDiffStatus.MODIFIED -> Color(0xFFFFC107)
            FolderDiffStatus.LEFT_ORPHAN -> Color(0xFF4CAF50)
            FolderDiffStatus.RIGHT_ORPHAN -> Color(0xFF9C27B0)
        }
    }

    val backgroundColor = if (row.isBackupMatch) {
        Color(0x15569CD6)
    } else {
        when (row.status) {
            FolderDiffStatus.UNCHANGED -> Color.Transparent
            FolderDiffStatus.MODIFIED -> Color(0x15FFC107)
            FolderDiffStatus.LEFT_ORPHAN -> Color(0x154CAF50)
            FolderDiffStatus.RIGHT_ORPHAN -> Color(0x159C27B0)
        }
    }

    val selectionSideForThisRow = viewModel.selectedRows[row.relativePath]
    val isLeftSelected = selectionSideForThisRow == FolderViewModel.SelectionSide.LEFT || selectionSideForThisRow == FolderViewModel.SelectionSide.BOTH
    val isRightSelected = selectionSideForThisRow == FolderViewModel.SelectionSide.RIGHT || selectionSideForThisRow == FolderViewModel.SelectionSide.BOTH

    val leftBgColor = when {
        viewModel.dragAlignmentSourcePath == row.relativePath -> Color(0x304CAF50)
        isLeftSelected -> Color(0xFF2C2C35)
        else -> backgroundColor
    }

    val rightBgColor = when {
        viewModel.dragAlignmentTargetPath == row.relativePath -> Color(0x309C27B0)
        isRightSelected -> Color(0xFF2C2C35)
        else -> backgroundColor
    }

    val leftBorderColor = when {
        viewModel.dragAlignmentSourcePath == row.relativePath -> Color(0xFF4CAF50)
        isLeftSelected -> Color(0xFF82AAFF).copy(alpha = 0.3f)
        else -> Color.Transparent
    }

    val rightBorderColor = when {
        viewModel.dragAlignmentTargetPath == row.relativePath -> Color(0xFF9C27B0)
        isRightSelected -> Color(0xFF82AAFF).copy(alpha = 0.3f)
        else -> Color.Transparent
    }

    var totalWidth by remember { mutableStateOf(0f) }
    var rowHeight by remember { mutableStateOf(0f) }
    val rowAlpha = if (viewModel.dragAlignmentSourcePath == row.relativePath) 0.5f else 1.0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .graphicsLayer(alpha = rowAlpha)
            .onGloballyPositioned { coordinates ->
                totalWidth = coordinates.size.width.toFloat()
                rowHeight = coordinates.size.height.toFloat()
            }
            .pointerInput(row) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (row.status == FolderDiffStatus.LEFT_ORPHAN) {
                            viewModel.dragAlignmentSourcePath = row.relativePath
                            viewModel.dragAlignmentTargetPath = null
                        } else {
                            val leftBoundary = totalWidth / 2f - 20.dp.toPx()
                            val rightBoundary = totalWidth / 2f + 20.dp.toPx()
                            val side = when {
                                offset.x < leftBoundary -> FolderViewModel.SelectionSide.LEFT
                                offset.x > rightBoundary -> FolderViewModel.SelectionSide.RIGHT
                                else -> FolderViewModel.SelectionSide.BOTH
                            }
                            viewModel.isDraggingSelection = true
                            viewModel.dragStartPath = row.relativePath
                            viewModel.dragStartSide = side
                            viewModel.selectedRows.clear()
                            viewModel.selectedRows[row.relativePath] = side
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        if (viewModel.dragAlignmentSourcePath != null) {
                            val startIndex = rowIndex
                            if (startIndex != -1 && rowHeight > 0f) {
                                val offsetRow = Math.floor(change.position.y.toDouble() / rowHeight.toDouble()).toInt()
                                val targetIndex = (startIndex + offsetRow).coerceIn(0, viewModel.visibleRows.lastIndex)
                                val targetRow = viewModel.visibleRows[targetIndex]
                                if (targetRow.status == FolderDiffStatus.RIGHT_ORPHAN && targetRow.isDirectory == row.isDirectory) {
                                    viewModel.dragAlignmentTargetPath = targetRow.relativePath
                                } else {
                                    viewModel.dragAlignmentTargetPath = null
                                }
                            }
                        } else {
                            val leftBoundary = totalWidth / 2f - 20.dp.toPx()
                            val rightBoundary = totalWidth / 2f + 20.dp.toPx()
                            val currentX = change.position.x
                            val currentSide = when {
                                currentX < leftBoundary -> FolderViewModel.SelectionSide.LEFT
                                currentX > rightBoundary -> FolderViewModel.SelectionSide.RIGHT
                                else -> FolderViewModel.SelectionSide.BOTH
                            }

                            val startSide = viewModel.dragStartSide ?: FolderViewModel.SelectionSide.BOTH
                            val activeSide = if (startSide == FolderViewModel.SelectionSide.BOTH || startSide != currentSide) {
                                FolderViewModel.SelectionSide.BOTH
                            } else {
                                startSide
                            }

                            val startIndex = rowIndex
                            if (startIndex != -1 && rowHeight > 0f) {
                                val offsetRow = Math.floor(change.position.y.toDouble() / rowHeight.toDouble()).toInt()
                                val targetIndex = (startIndex + offsetRow).coerceIn(0, viewModel.visibleRows.lastIndex)
                                val targetRow = viewModel.visibleRows[targetIndex]
                                viewModel.selectRange(row.relativePath, targetRow.relativePath, activeSide)
                            }
                        }
                    },
                    onDragEnd = {
                        val source = viewModel.dragAlignmentSourcePath
                        val target = viewModel.dragAlignmentTargetPath
                        if (source != null && target != null) {
                            viewModel.performManualAlignment(source, target)
                        }
                        viewModel.dragAlignmentSourcePath = null
                        viewModel.dragAlignmentTargetPath = null

                        viewModel.isDraggingSelection = false
                        viewModel.dragStartPath = null
                        viewModel.dragStartSide = null
                    },
                    onDragCancel = {
                        viewModel.dragAlignmentSourcePath = null
                        viewModel.dragAlignmentTargetPath = null

                        viewModel.isDraggingSelection = false
                        viewModel.dragStartPath = null
                        viewModel.dragStartSide = null
                    }
                )
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(leftBgColor, RoundedCornerShape(4.dp))
                .border(0.5.dp, leftBorderColor, RoundedCornerShape(4.dp))
                .combinedClickable(
                    onDoubleClick = {
                        if (row.status != FolderDiffStatus.RIGHT_ORPHAN) {
                            if (row.isDirectory) {
                                val newLeft = java.io.File(viewModel.leftFolderPath).resolve(row.leftFile?.relativePath ?: row.relativePath).absolutePath
                                viewModel.leftFolderPath = newLeft
                                if (viewModel.leftFolderPath.isNotEmpty() && viewModel.rightFolderPath.isNotEmpty()) {
                                    viewModel.startCompare(viewModel.leftFolderPath, viewModel.rightFolderPath)
                                    if (workspaceViewModel != null && tabId != null) {
                                        workspaceViewModel.updateTabTitleFromPaths(tabId, viewModel.leftFolderPath, viewModel.rightFolderPath)
                                    }
                                }
                            } else if (workspaceViewModel != null) {
                                val leftAbsPath = if (viewModel.leftFolderPath.isNotEmpty()) {
                                    java.io.File(viewModel.leftFolderPath).resolve(row.leftFile?.relativePath ?: row.relativePath).absolutePath
                                } else null
                                val rightAbsPath = if (row.status != FolderDiffStatus.LEFT_ORPHAN && viewModel.rightFolderPath.isNotEmpty()) {
                                    java.io.File(viewModel.rightFolderPath).resolve(row.rightFile?.relativePath ?: row.relativePath).absolutePath
                                } else null
                                workspaceViewModel.addTab(TabType.TEXT_COMPARE, leftAbsPath, rightAbsPath)
                            }
                        }
                    },
                    onClick = {
                        viewModel.selectedRows.clear()
                        viewModel.selectedRows[row.relativePath] = FolderViewModel.SelectionSide.LEFT
                        if (row.status != FolderDiffStatus.RIGHT_ORPHAN && row.isDirectory) {
                            viewModel.toggleExpand(row.relativePath)
                        }
                    }
                )
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (row.status != FolderDiffStatus.RIGHT_ORPHAN) {
                FolderTreeLines(row, rowIndex, viewModel)
                FolderIconIndicator(row, viewModel)
                Text(
                    text = row.leftFile?.name ?: row.name,
                    color = statusColor,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatSize(row.leftSize),
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(80.dp),
                    textAlign = TextAlign.End
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatTime(row.leftModified),
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(160.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        Box(
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight()
                .combinedClickable(
                    onDoubleClick = {
                        if (!row.isDirectory && workspaceViewModel != null) {
                            val leftAbsPath = if (row.status != FolderDiffStatus.RIGHT_ORPHAN && viewModel.leftFolderPath.isNotEmpty()) {
                                java.io.File(viewModel.leftFolderPath).resolve(row.leftFile?.relativePath ?: row.relativePath).absolutePath
                            } else null
                            val rightAbsPath = if (row.status != FolderDiffStatus.LEFT_ORPHAN && viewModel.rightFolderPath.isNotEmpty()) {
                                java.io.File(viewModel.rightFolderPath).resolve(row.rightFile?.relativePath ?: row.relativePath).absolutePath
                            } else null
                            workspaceViewModel.addTab(TabType.TEXT_COMPARE, leftAbsPath, rightAbsPath)
                        }
                    },
                    onClick = {
                        viewModel.selectedRows.clear()
                        viewModel.selectedRows[row.relativePath] = FolderViewModel.SelectionSide.BOTH
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            val statusSymbol = if (row.isBackupMatch) "↩" else when (row.status) {
                FolderDiffStatus.UNCHANGED -> "="
                FolderDiffStatus.MODIFIED -> "≠"
                FolderDiffStatus.LEFT_ORPHAN -> "→"
                FolderDiffStatus.RIGHT_ORPHAN -> "←"
            }
            val symbolColor = if (row.isBackupMatch) Color(0xFFFFC107) else statusColor
            Text(
                text = statusSymbol,
                color = symbolColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(rightBgColor, RoundedCornerShape(4.dp))
                .border(0.5.dp, rightBorderColor, RoundedCornerShape(4.dp))
                .combinedClickable(
                    onDoubleClick = {
                        if (row.status != FolderDiffStatus.LEFT_ORPHAN) {
                            if (row.isDirectory) {
                                val newRight = java.io.File(viewModel.rightFolderPath).resolve(row.rightFile?.relativePath ?: row.relativePath).absolutePath
                                viewModel.rightFolderPath = newRight
                                if (viewModel.leftFolderPath.isNotEmpty() && viewModel.rightFolderPath.isNotEmpty()) {
                                    viewModel.startCompare(viewModel.leftFolderPath, viewModel.rightFolderPath)
                                    if (workspaceViewModel != null && tabId != null) {
                                        workspaceViewModel.updateTabTitleFromPaths(tabId, viewModel.leftFolderPath, viewModel.rightFolderPath)
                                    }
                                }
                            } else if (workspaceViewModel != null) {
                                val leftAbsPath = if (row.status != FolderDiffStatus.RIGHT_ORPHAN && viewModel.leftFolderPath.isNotEmpty()) {
                                    java.io.File(viewModel.leftFolderPath).resolve(row.leftFile?.relativePath ?: row.relativePath).absolutePath
                                } else null
                                val rightAbsPath = if (viewModel.rightFolderPath.isNotEmpty()) {
                                    java.io.File(viewModel.rightFolderPath).resolve(row.rightFile?.relativePath ?: row.relativePath).absolutePath
                                } else null
                                workspaceViewModel.addTab(TabType.TEXT_COMPARE, leftAbsPath, rightAbsPath)
                            }
                        }
                    },
                    onClick = {
                        viewModel.selectedRows.clear()
                        viewModel.selectedRows[row.relativePath] = FolderViewModel.SelectionSide.RIGHT
                        if (row.status != FolderDiffStatus.LEFT_ORPHAN && row.isDirectory) {
                            viewModel.toggleExpand(row.relativePath)
                        }
                    }
                )
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (row.status != FolderDiffStatus.LEFT_ORPHAN) {
                FolderTreeLines(row, rowIndex, viewModel)
                FolderIconIndicator(row, viewModel)
                Text(
                    text = row.rightFile?.name ?: row.name,
                    color = statusColor,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatSize(row.rightSize),
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(80.dp),
                    textAlign = TextAlign.End
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatTime(row.rightModified),
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(160.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun FolderIconIndicator(row: FolderDiffRow, viewModel: FolderViewModel) {
    if (row.isDirectory) {
        val isExpanded = viewModel.isExpanded(row.relativePath)
        val arrowText = if (isExpanded) "▼" else "▶"
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(end = 6.dp)
        ) {
            Text(
                text = arrowText,
                color = Color(0xFFE5C07B),
                fontSize = 9.sp,
                modifier = Modifier.padding(end = 4.dp)
            )
            val folderStatus = getFolderContentStatus(row, viewModel.allRows)
            AiCompareFolderIcon(status = folderStatus)
        }
    } else {
        Text(
            text = "📄",
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 12.dp, end = 6.dp)
        )
    }
}

enum class FolderContentStatus {
    ALL_SAME,
    ALL_DIFF,
    MIXED
}

fun getFolderContentStatus(row: FolderDiffRow, allRows: List<FolderDiffRow>): FolderContentStatus {
    if (row.status == FolderDiffStatus.UNCHANGED) {
        return FolderContentStatus.ALL_SAME
    }
    if (row.status == FolderDiffStatus.LEFT_ORPHAN || row.status == FolderDiffStatus.RIGHT_ORPHAN) {
        return FolderContentStatus.ALL_DIFF
    }
    
    val prefix = row.relativePath + "/"
    val descendants = allRows.filter { it.relativePath.startsWith(prefix) }
    
    if (descendants.isEmpty()) {
        return FolderContentStatus.MIXED
    }
    
    val hasChanges = descendants.any { it.status != FolderDiffStatus.UNCHANGED }
    val hasSame = descendants.any { it.status == FolderDiffStatus.UNCHANGED }
    
    return when {
        hasChanges && hasSame -> FolderContentStatus.MIXED
        hasChanges -> FolderContentStatus.ALL_DIFF
        else -> FolderContentStatus.ALL_SAME
    }
}

@Composable
fun AiCompareFolderIcon(
    status: FolderContentStatus,
    modifier: Modifier = Modifier
) {
    val baseStrokeColor by animateColorAsState(
        targetValue = when (status) {
            FolderContentStatus.ALL_SAME -> Color(0xFF909095)
            FolderContentStatus.MIXED -> Color(0xFF909095)
            FolderContentStatus.ALL_DIFF -> Color(0xFFFF8F8F)
        },
        animationSpec = tween(150)
    )

    val baseFillColor by animateColorAsState(
        targetValue = when (status) {
            FolderContentStatus.ALL_SAME -> Color(0x25909095)
            FolderContentStatus.MIXED -> Color(0x25909095)
            FolderContentStatus.ALL_DIFF -> Color(0x25FF8F8F)
        },
        animationSpec = tween(150)
    )

    val overlayStrokeColor by animateColorAsState(
        targetValue = when (status) {
            FolderContentStatus.ALL_SAME -> Color(0xFF909095)
            FolderContentStatus.MIXED -> Color(0xFFFF8F8F)
            FolderContentStatus.ALL_DIFF -> Color(0xFFFF8F8F)
        },
        animationSpec = tween(150)
    )

    val overlayFillColor by animateColorAsState(
        targetValue = when (status) {
            FolderContentStatus.ALL_SAME -> Color(0x25909095)
            FolderContentStatus.MIXED -> Color(0x25FF8F8F)
            FolderContentStatus.ALL_DIFF -> Color(0x25FF8F8F)
        },
        animationSpec = tween(150)
    )

    Canvas(
        modifier = modifier
            .size(16.dp)
            .padding(1.dp)
    ) {
        val w = size.width
        val h = size.height

        val folderPath = Path().apply {
            moveTo(0.1f * w, 0.85f * h)
            lineTo(0.1f * w, 0.3f * h)
            lineTo(0.15f * w, 0.3f * h)
            lineTo(0.15f * w, 0.15f * h)
            lineTo(0.45f * w, 0.15f * h)
            lineTo(0.55f * w, 0.3f * h)
            lineTo(0.9f * w, 0.3f * h)
            lineTo(0.9f * w, 0.85f * h)
            close()
        }

        // 1. Draw base (un-clipped)
        drawPath(
            path = folderPath,
            color = baseFillColor,
            style = androidx.compose.ui.graphics.drawscope.Fill
        )
        drawPath(
            path = folderPath,
            color = baseStrokeColor,
            style = Stroke(width = 1.dp.toPx())
        )

        // 2. Draw overlay (clipped to bottom-left half)
        val clipPath = Path().apply {
            moveTo(0f, 0f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }

        clipPath(clipPath) {
            drawPath(
                path = folderPath,
                color = overlayFillColor,
                style = androidx.compose.ui.graphics.drawscope.Fill
            )
            drawPath(
                path = folderPath,
                color = overlayStrokeColor,
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}
