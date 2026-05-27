import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// ==========================================
// WorkspaceViewModel
// ==========================================

class WorkspaceViewModel {
    var state by mutableStateOf(WorkspaceState())
        private set

    val diffViewModels = mutableMapOf<String, DiffViewModel>()
    val folderViewModels = mutableMapOf<String, FolderViewModel>()
    val mergeViewModels = mutableMapOf<String, MergeViewModel>()

    fun getDiffViewModel(tabId: String): DiffViewModel {
        return diffViewModels.getOrPut(tabId) { DiffViewModel() }
    }

    fun getFolderViewModel(tabId: String): FolderViewModel {
        return folderViewModels.getOrPut(tabId) { FolderViewModel() }
    }

    fun getMergeViewModel(tabId: String): MergeViewModel {
        return mergeViewModels.getOrPut(tabId) { MergeViewModel() }
    }

    fun addTab(type: TabType, left: String? = null, right: String? = null) {
        val title = when (type) {
            TabType.FOLDER_COMPARE -> {
                val lName = left?.substringAfterLast(java.io.File.separator)?.substringAfterLast("/") ?: "Left"
                val rName = right?.substringAfterLast(java.io.File.separator)?.substringAfterLast("/") ?: "Right"
                "📁 $lName ↔ $rName"
            }
            TabType.TEXT_COMPARE -> {
                val lName = left?.substringAfterLast(java.io.File.separator)?.substringAfterLast("/") ?: "Left"
                val rName = right?.substringAfterLast(java.io.File.separator)?.substringAfterLast("/") ?: "Right"
                "📄 $lName ↔ $rName"
            }
            TabType.MERGE_COMPARE -> "🔀 3-Way Merge"
        }
        val newTab = TabItem(title = title, type = type, leftPath = left, rightPath = right)

        if (type == TabType.TEXT_COMPARE) {
            val vm = getDiffViewModel(newTab.id)
            vm.leftFilePath = left ?: ""
            vm.rightFilePath = right ?: ""
            vm.startCompare()
        } else if (type == TabType.FOLDER_COMPARE && left != null && right != null) {
            val vm = getFolderViewModel(newTab.id)
            vm.startCompare(left, right)
        }

        state = state.copy(
            tabs = state.tabs + newTab,
            activeTabId = newTab.id
        )
    }

    fun updateTabTitleFromPaths(tabId: String, left: String, right: String) {
        state = state.copy(
            tabs = state.tabs.map { tab ->
                if (tab.id == tabId) {
                    val lName = left.substringAfterLast(java.io.File.separator).substringAfterLast("/")
                    val rName = right.substringAfterLast(java.io.File.separator).substringAfterLast("/")
                    tab.copy(
                        leftPath = left,
                        rightPath = right,
                        title = "📁 $lName ↔ $rName"
                    )
                } else tab
            }
        )
    }

    fun closeTab(id: String) {
        val newTabs = state.tabs.filter { it.id != id }
        val newActiveId = if (state.activeTabId == id) {
            newTabs.lastOrNull()?.id
        } else {
            state.activeTabId
        }

        diffViewModels.remove(id)
        folderViewModels.remove(id)
        mergeViewModels.remove(id)

        state = state.copy(tabs = newTabs, activeTabId = newActiveId)
    }

    fun selectTab(id: String) {
        state = state.copy(activeTabId = id)
    }
}
