# NEI-RecipeTree

[简体中文](#中文) | [English](#english)

NEI-RecipeTree is a recipe tree / bill of materials viewer for `NotEnoughItems` on Minecraft `1.7.10`, adapted from the recipe tree idea in EMI and rebuilt for the NEI workflow.

---

## 中文

### 简介

`NEI-RecipeTree` 是一个面向 Minecraft `1.7.10` 与 `NotEnoughItems` 的合成树模组，用来把 NEI 的单个配方页扩展成可递归浏览的整棵材料树。

它的目标不是替代 NEI，而是在 NEI 原有配方查看流程上补一层“材料展开 / 配方选择 / 总成本统计 / 导出”的能力。

### 兼容性

- Minecraft：`1.7.10`
- Forge：`10.13.4.1614`
- MCP Mapping：`stable_12`
- 依赖：`NotEnoughItems`
- 语言级别：通过 `Jabel` 使用现代 Java 语法，产物仍为 Java 8 字节码

### 主要功能

- 在 NEI 配方界面直接打开当前物品的合成树
- 背包界面按钮会记住上次关闭时的界面，直接打开当前合成树或合成树列表，可切换 / 新建 / 删除多棵合成树；已打开的树会保存到磁盘，重启游戏后自动恢复
- 对同一产物在多种候选配方之间切换，并将选择回写到整棵树
- 同一配方槽位存在多个可替换物品时，自动选用可分解的那个，并支持手动切换；手动选择会被记住，同一槽位在其它配方与下次启动时自动沿用
- 递归展开材料需求，汇总总材料成本
- 总材料成本与副产物优先按物品 ID、损伤值排序，流体始终排在最后，列表不会随哈希顺序跳动
- 液体按体积（`L` / mB）统计，同一液体的多处需求合并为一行
- 支持折叠 / 展开节点，保留用户手动选择的分解路径
- 悬停材料按 `Del` 可将其从树和总材料成本中删除，`Ctrl + Z` 逐项恢复，删除和撤销顺序随该树保存
- 右上角快捷操作：
  - 返回合成树列表
  - 导出到 NEI 收藏夹
  - 导出为 PNG 图片
- 批次面板支持快速调整数量并自动推荐合适批次
- 悬停物品框时高亮当前节点、直连连线和下一级子节点
- 悬停左上角配方角标时显示该节点的配方预览

### 本分支的修复

- **字体宽度渲染修复**：总成本行与副产物行原先按固定 `24px` 槽位排布，数量文字也用固定偏移绘制；遇到字宽更大的字体（例如 Angelica 的自定义字体）时，数量会被压到相邻物品图标上。现在每一项先用 `FontRenderer#getStringWidth` 实测宽度再顺序排布，导出图片的边界计算与悬停判定也复用同一宽度，不再重叠。
- **可替换物品（多候选槽位）**：NEI 在 `PositionedStack.items` 中给出槽位的全部候选物品，旧实现只取第一项。当第一项本身没有配方时，节点会误判为原材料；现在会自动回退到可分解的候选，并支持手动切换（见上方“主要功能”）。
- **液体需求量合并**：GT 把液体体积写在展示物的 NBT 里（`mFluidDisplayAmount`），物品堆叠数恒为 1，所以同一液体在不同配方中的需求会被当成不同物品、在总材料成本里各占一行。现在按 `StackInfo#getFluid` 读出体积并以 mB 参与计算，统计时用“去掉体积后的展示物”作为聚合键，同一液体只保留一行、体积累加（显示为 `3000L`）。
- **液体数量标签不再重叠**：GT 的流体展示物会在图标内再绘制一遍 NBT 里的体积，而树本身也会绘制节点数量和总成本数量，两串数字会叠在同一个图标上。现在树内绘制带数量标签的物品时会给展示物加上 `mHideStackSize`，隐藏 GT 的内置体积文字，只显示按当前批次算出的数量。
- **重启后保留合成树**：合成树列表原先只存在内存里，重启后列表为空，背包按钮也随之隐藏。现在每棵树的产物、批次和每个节点的配方选择都会写入 `config/neirecipetree_trees.dat`；打开树时先套用已保存的选择，其余节点再向 NEI 解析。背包按钮也改为常驻，列表为空时仍可从光标或背包里最后悬停的物品新建一棵树。

### 使用方式

#### 1. 打开合成树

在 NEI 的配方界面中点击 `Recipe Tree` 按钮，即可从当前配方进入合成树界面。

如果某个物品存在多个可选配方，可以：

- 在 NEI 配方页点击 `选择此配方`
- 或者进入合成树后，对节点使用左键浏览并切换配方

合成树一旦建立，打开背包界面时左下角（快捷栏左侧）会出现一个按钮（图标是当前选中树的产物）。左键点击会按照上次关闭时的界面，直接回到当前合成树，或打开**合成树列表**：

- 左键点击条目：切换并打开该树
- 鼠标前进键：打开当前合成树
- 鼠标后退键：关闭列表
- 右侧 `X`：删除该树
- `新建`：用光标上拿着的物品、背包里悬停的物品或当前手持物品新建一棵树；即使相同产物已有树也会新建，而不是切换到旧树（没有可用物品时按钮置灰）
- 滚动滚轮：条目超过 7 个时翻页

列表为空时按钮仍然显示，方便直接新建；已打开的树和上次关闭的界面状态保存在 `config/neirecipetree_trees.dat`，重启游戏后按钮会继续打开当时的合成树或列表。NEI 配方页的 `T` 按钮总是新建一棵树，即使相同配方已经有一棵树也只追加新树。

另外，在游戏世界内按**配置的快捷键**（默认鼠标中键）可以直接打开当前合成树。快捷键可以在 `选项 → 控制 → 合成树` 中修改；容器界面内不注册该快捷键，避免与中键整理冲突。没有合成树时不会拦截原版中键功能。

#### 2. 界面操作

| 操作 | 说明 |
| --- | --- |
| `左键` | 浏览并选择当前节点的配方 |
| `Shift + 左键` | 自动选择第一个匹配配方 |
| `Ctrl + 左键` | 在当前槽位的可替换物品之间切换（`Ctrl + Shift + 左键` 反向）；选择会被记住并写入 `config/neirecipetree_alternatives.dat`，切回该槽位的首个物品即清除对应记忆 |
| `右键` | 折叠 / 展开当前节点 |
| `Shift + 右键` | 清除当前节点的配方选择 |
| `Del` | 删除当前悬停的材料及其分支，并从总材料成本中排除；删除状态随该树保存 |
| `Ctrl + Z` | 恢复最近一次删除的材料，可连续按下逐项恢复；撤销顺序随树保存，重启后仍可用 |
| `打开合成树快捷键（默认鼠标中键，仅世界内）` | 直接打开当前合成树；可在 `选项 → 控制 → 合成树` 中修改 |
| `Esc` | 直接关闭配方树界面，返回游戏 |
| `E` | 直接关闭配方树界面，返回游戏 |
| `右上角列表按钮` | 返回合成树列表 |
| `鼠标后退键` | 返回合成树列表 |
| `滚轮（批次面板）` | 数量 `±1` |
| `Shift + 滚轮（批次面板）` | 数量 `±16` |
| `Ctrl + 滚轮（批次面板）` | 数量 `×2 / ÷2` |
| `左键（批次面板）` | 设为推荐批次 |

#### 3. 导出

- **导出到收藏夹**：将当前树中的配方链写入 NEI Bookmark
- **导出为图片**：将当前树保存为 PNG 文件到：

```text
minecraft/screenshots/recipe_trees/
```

图片导出使用离屏 FBO 渲染，不依赖当前窗口大小，也不会污染当前 GUI 的缩放与偏移状态。

导出完成后会直接用系统默认图片查看器打开该 PNG（调不起来时回退为打开所在文件夹并选中该文件），不再往聊天栏里输出图片路径。

### 开发与构建

在项目目录中执行：

```bash
cd NEI-RecipeTree
./gradlew compileJava --console=plain
./gradlew build
./gradlew runClient
```

### 代码结构

- `src/main/java/moe/takochan/neirecipetree/bom`：材料树模型、配方解析、分解状态管理
- `src/main/java/moe/takochan/neirecipetree/gui`：树布局、渲染、交互、高亮、导出图片
- `src/main/java/moe/takochan/neirecipetree/nei`：NEI 按钮注入、NEI 配方页联动
- `src/main/java/moe/takochan/neirecipetree/recipe`：NEI 配方引用与适配层
- `src/main/java/moe/takochan/neirecipetree/util`：系统级辅助，例如导出后调起系统图片查看器
- `src/main/resources/assets/neirecipetree`：语言文件、GUI 纹理等资源

### 致谢

- EMI 的配方树 / BoM 思路提供了最初的交互参考
- 感谢 `NotEnoughItems` 提供配方浏览与收藏夹基础能力

---

## English

### Overview

`NEI-RecipeTree` is a recipe tree mod for Minecraft `1.7.10` and `NotEnoughItems`. It extends NEI's single-recipe view into a recursively browsable material tree.

The goal is not to replace NEI, but to add one more layer on top of the existing NEI workflow: material expansion, recipe selection, total cost aggregation, and export.

### Compatibility

- Minecraft: `1.7.10`
- Forge: `10.13.4.1614`
- MCP mappings: `stable_12`
- Dependency: `NotEnoughItems`
- Language level: modern Java syntax via `Jabel`, still compiled to Java 8 bytecode

### Features

- Open a full recipe tree directly from an NEI recipe page
- The inventory button reopens whatever screen the tree was closed from — the active tree or the tree list — and the list still supports switching / creating / deleting trees; opened trees are saved to disk and restored after a restart
- Switch between multiple valid recipes for the same output and apply the selection back into the tree
- Recipe slots that accept several substitutable items fall back to a decomposable one and can be switched manually; picks are remembered and reused by other recipes and later sessions
- Recursively expand ingredient requirements and calculate total material cost
- Total cost and remainder rows are ordered by item id and damage, with fluids always placed last, so they no longer follow the hash order of the cost maps
- Fluids are counted by volume (`L` / mB) and every request for the same fluid is merged into one row
- Collapse / expand nodes while preserving manual recipe choices
- Hover a material and press `Del` to remove it from the tree and total cost; `Ctrl + Z` restores removals one by one, and both the removals and their undo order are saved with that tree
- Quick actions in the top-right corner:
  - return to the tree list
  - export to NEI bookmarks
  - export to PNG image
- Batch panel for fast quantity changes and ideal-batch selection
- Hovering an item frame highlights the node itself, its connected lines, and its direct children
- Hovering the small recipe badge in the top-left shows the recipe preview for that node

### Fixes in this fork

- **Font width rendering**: the total cost and remainder rows used to be laid out in fixed `24px` slots with fixed text offsets, so wider fonts (for example Angelica's custom font) made the amounts overlap the neighbouring item icons. Every entry is now measured with `FontRenderer#getStringWidth` and spaced sequentially, and both the exported image bounds and the hover hitboxes reuse that measured width.
- **Substitutable items (multi-candidate slots)**: NEI reports every candidate of a slot in `PositionedStack.items`, but the tree used to keep only the first entry, so a slot whose first candidate has no recipe was mistaken for a raw material. The tree now falls back to a decomposable candidate and lets you switch between them (see *Features*).
- **Fluid requirement merging**: GT stores a fluid's volume in the display item's NBT (`mFluidDisplayAmount`) while the stack size stays `1`, so the same fluid requested by different recipes looked like different items and took one row each in the total cost. The volume is now read through `StackInfo#getFluid` and counted in mB, and costs aggregate on the display item with the volume removed, so a fluid keeps a single row and its volumes add up (rendered as `3000L`).
- **Fluid amount labels no longer overlap**: GT's fluid display item paints the NBT volume inside its icon as well, so the tree's own node and total-cost amounts ended up stacked on top of it. Items drawn with an amount now get `mHideStackSize` on the display copy, which suppresses GT's built-in volume text and leaves only the amount calculated for the current batch.
- **Recipe trees survive a restart**: the tree list used to live only in memory, so after a restart the list was empty and the inventory button hid itself. Each tree's goal, batch size, and per-node recipe choices are now written to `config/neirecipetree_trees.dat`; opening a tree applies those choices first and only resolves the remaining nodes through NEI. The inventory button is always visible as well, and an empty list can still start a tree from the held item or the last item hovered in the inventory.

### Usage

#### 1. Open the recipe tree

From an NEI recipe screen, click the `Recipe Tree` button to open the tree for the current recipe.

If an item has multiple valid recipes, you can either:

- click `Select this recipe` in the NEI recipe page
- or left-click the node inside the tree to browse and switch recipes

Once a tree exists, opening your inventory shows a button at the bottom-left (left of the hotbar, its icon is the selected tree's goal). Left-clicking returns to the screen the tree was last closed from: the active tree, or the **tree list**:

- left-click a row to switch to and open that tree
- mouse forward button opens the active tree
- mouse back button closes the list
- the `X` on the right deletes that tree
- `New` creates a tree from the item on the cursor, an item hovered in the inventory, or the held hotbar item. It always creates a new tree, even when the same output already has one (greyed out when no item is available)
- the mouse wheel pages through the list when there are more than 7 trees

The button stays visible even when the list is empty, so a tree can always be started; opened trees and the last closed view are stored in `config/neirecipetree_trees.dat`, so after a restart the button still reopens that tree or the list. The `T` button on an NEI recipe page always creates a new tree, even when an identical recipe is already open.

In addition, a configurable shortcut (middle mouse by default) opens the active tree from the world. Change it under `Options → Controls → Recipe Tree`; it is not registered inside container screens, so it cannot steal a middle-click sort action. When no tree exists, the shortcut leaves vanilla middle-click behaviour alone.

#### 2. Controls

| Action | Description |
| --- | --- |
| `LMB` | Browse and choose a recipe for the current node |
| `Shift + LMB` | Auto-select the first matching recipe |
| `Ctrl + LMB` | Switch the selected alternative of the slot (`Ctrl + Shift + LMB` for the previous one); the pick is remembered in `config/neirecipetree_alternatives.dat` and cleared by switching back to the slot's first item |
| `RMB` | Collapse / expand the current node |
| `Shift + RMB` | Clear the current node's recipe selection |
| `Del` | Remove the hovered material and its branch from the tree and the total cost; removals are saved with the tree |
| `Ctrl + Z` | Restore the most recently removed material; press repeatedly to undo more. The undo order is saved with the tree and survives a restart |
| `Open-tree shortcut (middle mouse by default, world only)` | Open the active recipe tree directly; rebind under `Options → Controls → Recipe Tree` |
| `Esc` | Close the recipe tree screen and return to the game |
| `E` | Close the recipe tree screen and return to the game |
| `List button (top right)` | Return to the tree list |
| `Mouse back` | Return to the tree list |
| `Mouse wheel (batch panel)` | Change amount by `±1` |
| `Shift + mouse wheel (batch panel)` | Change amount by `±16` |
| `Ctrl + mouse wheel (batch panel)` | Change amount by `×2 / ÷2` |
| `LMB (batch panel)` | Set the ideal batch size |

#### 3. Export

- **Export to Bookmarks**: writes the current recipe chain into NEI bookmarks
- **Export as Image**: saves the current tree as a PNG file under:

```text
minecraft/screenshots/recipe_trees/
```

Image export uses off-screen FBO rendering, so it does not depend on the current window size and does not mutate the active GUI zoom or pan state.

Once the PNG is written it is opened with the system's default image viewer (falling back to opening the containing folder with the file selected), so the path is no longer printed into chat.

### Development and Build

Run from the project directory:

```bash
cd NEI-RecipeTree
./gradlew compileJava --console=plain
./gradlew build
./gradlew runClient
```

### Project Structure

- `src/main/java/moe/takochan/neirecipetree/bom`: material tree model, recipe resolution, decomposition state
- `src/main/java/moe/takochan/neirecipetree/gui`: tree layout, rendering, interaction, highlighting, image export
- `src/main/java/moe/takochan/neirecipetree/nei`: NEI button injection and recipe-screen integration
- `src/main/java/moe/takochan/neirecipetree/recipe`: NEI recipe references and adapters
- `src/main/java/moe/takochan/neirecipetree/util`: system helpers, e.g. handing the exported image to the OS
- `src/main/resources/assets/neirecipetree`: language files, GUI textures, and other resources

### Credits

- EMI for the original recipe tree / BoM interaction idea
- `NotEnoughItems` for the recipe browsing and bookmark foundation
