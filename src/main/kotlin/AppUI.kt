import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

// ==========================================
// 5. Main Application Entry Point & UI Shell
// ==========================================

@Composable
fun DashboardCard(title: String, description: String, icon: String, onClick: () -> Unit) {
    SpringButton(
        onClick = onClick,
        backgroundColor = Color(0xFF25252A),
        shape = RoundedCornerShape(16.dp),
        borderColor = Color.White.copy(alpha = 0.05f),
        padding = PaddingValues(0.dp),
        modifier = Modifier.size(width = 240.dp, height = 160.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Text(icon, fontSize = 24.sp)
            Column {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(description, color = Color.Gray, fontSize = 11.sp, maxLines = 3)
            }
        }
    }
}

@Composable
fun DashboardView(workspaceViewModel: WorkspaceViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1E1E22), Color(0xFF121214)))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "BeyondDiff Workspace",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Select a session type to get started",
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Folder Compare Card
                DashboardCard(
                    title = "Folder Compare",
                    description = "Compare directories, sync files, and show directory structures.",
                    icon = "📁",
                    onClick = { workspaceViewModel.addTab(TabType.FOLDER_COMPARE) }
                )

                // File Compare Card
                DashboardCard(
                    title = "File Compare",
                    description = "Compare two text files side by side with Patience or Myers diff.",
                    icon = "📄",
                    onClick = { workspaceViewModel.addTab(TabType.TEXT_COMPARE) }
                )

                // 3-Way Merge Card
                DashboardCard(
                    title = "3-Way Merge",
                    description = "Resolve conflicts between local, base, and remote file revisions.",
                    icon = "🔀",
                    onClick = { workspaceViewModel.addTab(TabType.MERGE_COMPARE) }
                )
            }
        }
    }
}

@Composable
fun WorkspaceTabBar(workspaceViewModel: WorkspaceViewModel) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    val state = workspaceViewModel.state

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0E0E10))
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "BeyondDiff",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(end = 16.dp)
        )

        // Scrollable Row for tabs
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            state.tabs.forEach { tab ->
                val isSelected = state.activeTabId == tab.id
                val tabBgColor by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFF1E1E22) else Color(0x331E1E22),
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else Color(0xFF8E8E93),
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                )

                Row(
                    modifier = Modifier
                        .background(tabBgColor, RoundedCornerShape(8.dp))
                        .border(
                            width = 0.5.dp,
                            color = if (isSelected) Color.White.copy(alpha = 0.08f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { workspaceViewModel.selectTab(tab.id) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = tab.title,
                        color = textColor,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(min = 40.dp, max = 160.dp)
                    )

                    // Close button icon
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Tab",
                        tint = textColor.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(14.dp)
                            .clickable { workspaceViewModel.closeTab(tab.id) }
                    )
                }
            }

            // Add (+) tab button
            Box {
                SpringButton(
                    onClick = { dropdownExpanded = true },
                    backgroundColor = Color(0xFF1E1E22),
                    shape = RoundedCornerShape(8.dp),
                    padding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier
                        .background(Color(0xFF1E1E22))
                        .border(1.dp, Color(0xFF3F3F46), RoundedCornerShape(8.dp))
                ) {
                    DropdownMenuItem(onClick = {
                        workspaceViewModel.addTab(TabType.FOLDER_COMPARE)
                        dropdownExpanded = false
                    }) {
                        Text("📁 New Folder Compare", color = Color.White, fontSize = 12.sp)
                    }
                    DropdownMenuItem(onClick = {
                        workspaceViewModel.addTab(TabType.TEXT_COMPARE)
                        dropdownExpanded = false
                    }) {
                        Text("📄 New File Compare", color = Color.White, fontSize = 12.sp)
                    }
                    DropdownMenuItem(onClick = {
                        workspaceViewModel.addTab(TabType.MERGE_COMPARE)
                        dropdownExpanded = false
                    }) {
                        Text("🔀 New 3-Way Merge", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

fun main(args: Array<String>) = application {
    val state = rememberWindowState(
        width = 1280.dp,
        height = 800.dp
    )

    val workspaceViewModel = remember { WorkspaceViewModel() }

    LaunchedEffect(args) {
        val isMergeMode = args.contains("--merge")
        val paths = args.filter { it != "--merge" }
        if (isMergeMode && paths.size >= 4) {
            workspaceViewModel.addTab(TabType.MERGE_COMPARE, paths[0], paths[1])
            val tabId = workspaceViewModel.state.activeTabId
            if (tabId != null) {
                workspaceViewModel.getMergeViewModel(tabId).startMerge(
                    local = paths[0],
                    remote = paths[1],
                    base = paths[2],
                    output = paths[3]
                )
            }
        } else if (paths.size >= 2) {
            workspaceViewModel.addTab(TabType.TEXT_COMPARE, paths[0], paths[1])
        } else {
            // Default clean start with a default compare option
            workspaceViewModel.addTab(TabType.FOLDER_COMPARE)
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "aiCompare",
        state = state,
        onKeyEvent = { keyEvent ->
            val isUndo = (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && keyEvent.key == Key.Z && keyEvent.type == KeyEventType.KeyDown
            val isRedo = ((keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && keyEvent.key == Key.Y && keyEvent.type == KeyEventType.KeyDown) ||
                         ((keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && keyEvent.isShiftPressed && keyEvent.key == Key.Z && keyEvent.type == KeyEventType.KeyDown)

            if (isUndo || isRedo) {
                val activeTabId = workspaceViewModel.state.activeTabId
                val activeTab = workspaceViewModel.state.tabs.find { it.id == activeTabId }
                if (activeTab != null && activeTab.type == TabType.TEXT_COMPARE) {
                    val vm = workspaceViewModel.getDiffViewModel(activeTab.id)
                    if (isUndo && vm.canUndo) {
                        vm.undo()
                        true
                    } else if (isRedo && vm.canRedo) {
                        vm.redo()
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            } else {
                false
            }
        }
    ) {
        MaterialTheme(
            colors = darkColors(
                primary = Color(0xFF007ACC),
                background = Color(0xFF1E1E1E),
                surface = Color(0xFF252526),
                onBackground = Color.White,
                onSurface = Color.White
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF121214), Color(0xFF0A0A0C))))
            ) {
                WorkspaceTabBar(workspaceViewModel)

                Box(modifier = Modifier.weight(1f)) {
                    val activeTabId = workspaceViewModel.state.activeTabId
                    val activeTab = workspaceViewModel.state.tabs.find { it.id == activeTabId }

                    if (activeTab == null) {
                        DashboardView(workspaceViewModel)
                    } else {
                        when (activeTab.type) {
                            TabType.FOLDER_COMPARE -> {
                                val vm = workspaceViewModel.getFolderViewModel(activeTab.id)
                                FolderCompareViewer(vm, workspaceViewModel, activeTab.id)
                            }
                            TabType.TEXT_COMPARE -> {
                                val vm = workspaceViewModel.getDiffViewModel(activeTab.id)
                                SideBySideDiffViewer(vm)
                            }
                            TabType.MERGE_COMPARE -> {
                                val vm = workspaceViewModel.getMergeViewModel(activeTab.id)
                                MergeCompareViewer(vm)
                            }
                        }
                    }
                }
            }
        }
    }
}
