# Advanced Git Features (IntelliJ IDEA Plugin)

Advanced Git Features is an IntelliJ IDEA plugin that adds a few focused, high–leverage Git actions on top of the built‑in Git integration.

The goal is to streamline frequent branch operations that are otherwise a bit tedious through the standard UI.

---

## Features

### 1. Create Branch from `master` (or main base branch)

**Action ID:** `Git.CreateBranchFromMaster`  
**Location:** Git branch popup / Git menu (depending on your keymap and menus)

Creates a new branch directly from `master` (or your designated base branch), regardless of which branch is currently checked out.

Typical use cases:

- Starting a new feature from `master` while currently working on another branch.
- Quickly resetting a feature branch to match `master` by recreating it.

> The plugin assumes a conventional setup where `master` (or `main`) is your integration branch. Behavior may depend on your repository configuration.

---

### 2. Delete Current Branch and Switch

**Action ID:** `Git.DeleteCurrentBranchAndSwitch.External`  
**Location:** Git branch popup → **Delete current branch and switch**

Deletes the currently checked-out branch and immediately switches to another branch (you’ll be prompted to choose a target branch, typically `master`/`main` or another safe branch).

Typical use cases:

- Cleaning up a short‑lived feature or experiment branch after it has been merged.
- Quickly returning to a stable branch while removing the now‑unneeded local branch.

Safety considerations:

- The plugin delegates actual Git operations to IntelliJ’s Git support, so standard safety checks (like unmerged changes) still apply.
- You may be prompted to confirm the deletion if the branch is not fully merged.

---

### 3. Show All Affected Files (from the Git log)

**Action ID:** `GitHelper.Log.ShowAllAffected`  
**Location:** **Git** tool window → **Log** tab → right-click a commit → **Show All Affected Files**

Opens a standalone changes browser listing every file touched by the selected commit, in the same
"Paths Affected by Commit &lt;hash&gt;" dialog the IDE offers from file history.

The built-in platform actions (`VcsHistory.ShowAllAffected`, `Vcs.Log.ShowAllAffected`) are only
reachable from *file history*, where the dialog is scoped to a single file's revision. This action
makes the same view available for any commit in the main log, with no file selection needed.

Typical use cases:

- Inspecting a whole commit in a large, resizable window instead of the log's cramped changes pane.
- Reviewing several commits side by side, since the dialog is independent of the log's selection.

Notes:

- Enabled for a single selected commit; selecting several commits disables it.
- **No default shortcut** is assigned, deliberately, so it cannot conflict with the platform's
  `Show All Affected Files` binding (`Alt+Shift+A` / `Cmd+Ctrl+A`). Assign one in
  **Settings / Preferences → Keymap** if you want it.
- Merge commits may show no changes, since Git does not always compute a diff against a single
  parent for them.

---

## Installation

### From source (development)

1. Clone the repository:

   ```bash
   git clone https://github.com/your-org-or-user/git-helper.git
   cd git-helper
   ```

2. Build and run in a sandbox IDE:

   ```bash
   ./gradlew runIde
   ```

   This starts a separate IntelliJ instance with the plugin loaded.

3. To build a distributable plugin ZIP:

   ```bash
   ./gradlew buildPlugin
   ```

   The ZIP will be in `build/distributions/`.

---

## Usage

After installing the plugin and restarting IntelliJ IDEA:

1. Open a project with Git enabled.
2. Use the **Git Branches** popup (usually accessible from the bottom toolbar or via `Ctrl+` backtick / `Ctrl+` accent, depending on keymap).
3. Look for:

    - **Create Branch from Master** (or equivalent wording mapped by your localization).
    - **Delete Current Branch and Switch** within the Git branches context.

You can also bind custom keyboard shortcuts:

1. Go to **Settings / Preferences → Keymap**.
2. Search for the action IDs:
    - `Git.CreateBranchFromMaster`
    - `Git.DeleteCurrentBranchAndSwitch.External`
    - `GitHelper.Log.ShowAllAffected`
3. Assign shortcuts as desired.

---

## Requirements

- **IntelliJ Platform:** Compatible with IDEs that include the Git4Idea plugin (e.g., IntelliJ IDEA Community/Ultimate).
- **Git Integration:** Built‑in Git support must be enabled.
- **JDK:** Project is built against Java 21.
- **Kotlin:** Uses Kotlin 2.3 for plugin sources.

Exact IDE version compatibility is controlled by the plugin’s Gradle and `plugin.xml` configuration.

---

## Development

This is a standard Gradle‑based IntelliJ Platform plugin project.

Common Gradle tasks:

- Run plugin in sandbox:

  ```bash
  ./gradlew runIde
  ```

- Run tests:

  ```bash
  ./gradlew test
  ```

- Verify plugin (basic compatibility/structure checks):

  ```bash
  ./gradlew verifyPlugin
  ```

- Build plugin distribution:

  ```bash
  ./gradlew buildPlugin
  ```

You can open the project directly in IntelliJ IDEA; the Gradle configuration will set up the IntelliJ Platform SDK and required dependencies.

---

## Contributing

Contributions that improve usability, add small focused Git helpers, or enhance safety/UX around branch operations are welcome.

1. Fork the repository.
2. Create a feature branch.
3. Implement and add tests where applicable.
4. Open a pull request describing:
    - The new action or change.
    - Where it appears in the UI.
    - Any edge cases or safety behavior.

---

## License

Specify your license here (e.g., MIT, Apache 2.0) and include the license file in the repository.