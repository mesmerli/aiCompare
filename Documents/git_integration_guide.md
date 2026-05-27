# Git Integration & CLI Usage Guide

This guide explains how to integrate `theCompare` (using the `my-diff` CLI wrapper) as your default Git `difftool` and `mergetool`.

---

## 1. CLI Commands & Options

The `my-diff` command-line utility supports two main operating modes:

### 1.1 Side-by-Side Diff Mode
Launches the side-by-side file comparison GUI.
```bash
my-diff <file_a> <file_b>
```
* **Behavior**: Opens the "File Compare" tab directly, loading the contents of `file_a` and `file_b`.

### 1.2 3-Way Merge Mode
Launches the interactive 4-pane merge viewer.
```bash
my-diff --merge <local> <remote> <base> <output>
```
* **Behavior**: Opens the "3-Way Merge" tab, loading the files and allowing line-by-line conflict resolution.
* **Arguments**:
  - `local`: The file containing your local modifications.
  - `remote`: The file containing the remote modifications (from the other branch).
  - `base`: The common ancestor file before modifications were made.
  - `output`: The destination path where the resolved result will be written.

---

## 2. CLI Exit Codes & Git Automation

To fit seamlessly into Git's automation workflow, the `my-diff` process returns standard shell exit codes when the GUI is closed:

| Exit Code | Meaning / Scenario | Git Outcome |
| :---: | :--- | :--- |
| **`0`** | The merge completed successfully, the file was saved, and **no unresolved conflicts** remain. | Git marks the conflict as resolved. |
| **`1`** | The merge session was closed/cancelled, or **unresolved conflicts still remain** in the output. | Git aborts the merge or marks it as failed. |

---

## 3. Git Configuration (`.gitconfig`)

To set up `theCompare` as your default comparison tool in Git, add the following configuration to your global Git config file (`~/.gitconfig` or `%USERPROFILE%\.gitconfig` on Windows).

Replace `/path/to/my-diff.exe` with the absolute path to your compiled `my-diff.exe`.

```ini
[diff]
    tool = mydiff
[difftool]
    prompt = false
[difftool "mydiff"]
    # Launch in side-by-side diff mode
    cmd = \"C:/path/to/my-diff.exe\" \"$LOCAL\" \"$REMOTE\"

[merge]
    tool = mymerge
[mergetool]
    prompt = false
    keepBackup = false
[mergetool "mymerge"]
    # Launch in 3-way merge mode
    cmd = \"C:/path/to/my-diff.exe\" --merge \"$LOCAL\" \"$REMOTE\" \"$BASE\" \"$MERGED\"
    trustExitCode = true
```

---

## 4. Usage Example

### Running Diff Tool
```bash
git difftool main feature-branch -- path/to/file.txt
```

### Running Merge Tool (when conflicts occur)
```bash
git mergetool
```
This will automatically launch the GUI showing:
1. **LOCAL** on the left.
2. **BASE** in the middle.
3. **REMOTE** on the right.
4. **MERGED OUTPUT** on the bottom, with clear conflict markers and action buttons (`[Local]`, `[Remote]`, `[Both]`) to resolve conflicts.
5. Click **[Save Output]** once all conflicts are resolved, and close the window to complete the merge.
