# 虚拟按键模块文档

> 本文档详细说明 ConnectBot 虚拟按键（Virtual Keyboard）的实现，包括 UI 结构、功能逻辑和修改指南。

---

## 1. 模块概述

虚拟按键是 ConnectBot 终端界面底部的一行特殊按键，提供 Esc、Ctrl、方向键、F1-F12 等终端常用键，弥补移动设备物理键盘缺失的问题。

### 功能特性

- **Ctrl 修饰键**：支持三态切换（OFF → 临时 → 锁定 → OFF）
- **方向键**：支持长按自动重复
- **触觉反馈**：方向键可选的震动反馈（Bumpy Arrows）
- **水平滚动**：按键过多时可左右滑动
- **自动隐藏**：闲置一段时间后自动隐藏（由父组件控制）

---

## 2. 核心文件

| 文件路径 | 职责 |
|----------|------|
| `app/src/main/java/org/connectbot/ui/components/TerminalKeyboard.kt` | UI 组件定义 |
| `app/src/main/java/org/connectbot/service/TerminalKeyListener.kt` | 按键逻辑和修饰键状态管理 |
| `app/src/main/java/org/connectbot/ui/screens/console/ConsoleScreen.kt` | 集成虚拟键盘到终端界面 |
| `app/src/main/java/org/connectbot/ui/screens/settings/SettingsScreen.kt` | 键盘相关设置 UI |
| `app/src/main/java/org/connectbot/ui/screens/settings/SettingsViewModel.kt` | 设置逻辑处理 |
| `app/src/main/java/org/connectbot/util/PreferenceConstants.kt` | 偏好设置常量定义 |

---

## 3. 架构分层

```
┌─────────────────────────────────────────────────────────────────────┐
│                         ConsoleScreen                               │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │                    TerminalKeyboard (Compose)                  │  │
│  │                                                               │  │
│  │  ┌────────┐ ┌─────┐ ┌─────┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌────┐ │  │
│  │  │  Ctrl  │ │ Esc │ │ Tab │ │ ↑ │ │ ↓ │ │ ← │ │ → │ │ F1 │ │  │
│  │  └───┬────┘ └──┬──┘ └──┬──┘ └─┬─┘ └─┬─┘ └─┬─┘ └─┬─┘ └──┬─┘ │  │
│  │      │         │       │      │     │     │     │      │    │  │
│  │      └─────────┴───────┴──────┴─────┴─────┴─────┴──────┘    │  │
│  │                              │                               │  │
│  │                   TerminalKeyListener                        │  │
│  │                (按键状态 + 修饰键管理)                         │  │
│  │                              │                               │  │
│  │                   ┌──────────┴──────────┐                    │  │
│  │                   │   TerminalBridge    │                    │  │
│  │                   │  (连接到终端模拟器)   │                    │  │
│  └───────────────────┴─────────────────────┴────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

### 数据流

```
用户点击按键
    ↓
TerminalKeyboard (UI 回调)
    ↓
TerminalKeyListener (处理逻辑)
    ↓
TerminalBridge.keyHandler → TerminalEmulator (发送到终端)
```

---

## 4. UI 实现详解

### 4.1 组件结构

`TerminalKeyboard.kt` 包含以下 Compose 组件：

| 组件名 | 用途 |
|--------|------|
| `TerminalKeyboard` | 有状态的顶层组件，连接 TerminalBridge |
| `TerminalKeyboardContent` | 无状态的 UI 组件，支持 Preview |
| `KeyButton` | 普通按键（单次点击） |
| `ModifierKeyButton` | 修饰键按键（Ctrl，支持状态切换） |
| `RepeatableKeyButton` | 可重复按键（方向键，长按自动重复） |

### 4.2 按键布局

```
┌─────────────────────────────────────────────────────────────────────────┐
│ [Ctrl] [Esc] [Tab] [↑] [↓] [←] [→] [Home] [End] [PgUp] [PgDn] [F1-F12] │ [✎] [⌨] │
└─────────────────────────────────────────────────────────────────────────┘
  ←─────────────────── 可水平滚动区域 ───────────────────→    ←── 固定 ──→
```

### 4.3 关键常量

```kotlin
// TerminalKeyboard.kt
const val TERMINAL_KEYBOARD_HEIGHT_DP = 30      // 按键高度 (dp)
private const val TERMINAL_KEYBOARD_WIDTH_DP = 45       // 按键宽度 (dp)
private const val TERMINAL_KEYBOARD_CONTENT_SIZE_DP = 20 // 图标/文字大小 (dp)
private const val UI_OPACITY = 0.5f                      // 界面透明度
```

### 4.4 按键类型详解

#### 普通按键 (KeyButton)

用于：Esc、Tab、Home、End、PgUp、PgDn、F1-F12

```kotlin
@Composable
private fun KeyButton(
    contentDescription: String?,
    modifier: Modifier = Modifier,
    text: String? = null,           // 文字显示
    icon: ImageVector? = null,      // 图标显示
    onClick: (() -> Unit)? = null,  // 点击回调
    backgroundColor: Color = ...,
    tint: Color = ...,
)
```

**样式特点：**
- 矩形形状 (`RectangleShape`)
- 1dp 边框 (`BorderStroke`)
- 半透明背景

#### 修饰键按键 (ModifierKeyButton)

用于：Ctrl

```kotlin
@Composable
private fun ModifierKeyButton(
    text: String,
    contentDescription: String?,
    modifierLevel: ModifierLevel,  // 当前修饰键状态
    onClick: () -> Unit,
)
```

**状态颜色映射：**

| 状态 | 背景色 | 文字色 |
|------|--------|--------|
| `OFF` | `surface` (半透明) | `onSurface` |
| `TRANSIENT` | `primaryContainer` (70% 不透明) | `onPrimaryContainer` |
| `LOCKED` | `primary` (80% 不透明) | `onPrimary` |

#### 可重复按键 (RepeatableKeyButton)

用于：方向键（↑ ↓ ← →）

```kotlin
@Composable
private fun RepeatableKeyButton(
    icon: ImageVector,
    contentDescription: String?,
    onPress: () -> Unit,
)
```

**重复逻辑：**
1. 按下后等待 `ViewConfiguration.getTapTimeout()` 毫秒（允许滚动手势抢占）
2. 发送第一次按键
3. 等待 500ms - tapTimeout
4. 开始重复，每 50ms 发送一次
5. 松开时停止

### 4.5 固定区域按钮

右侧有两个固定按钮（不随滚动移动）：

| 按钮 | 图标 | 功能 |
|------|------|------|
| 文本输入 | `Icons.Default.Edit` | 打开文本输入对话框 |
| 键盘切换 | `Icons.Default.Keyboard` / `KeyboardHide` | 显示/隐藏系统输入法 |

---

## 5. 功能实现详解

### 5.1 TerminalKeyListener

位置：`app/src/main/java/org/connectbot/service/TerminalKeyListener.kt`

#### 修饰键状态管理

**状态机：**

```
OFF ──点击──→ TRANSIENT ──点击──→ LOCKED ──点击──→ OFF
                 │
                 └──发送按键后──→ OFF (clearTransients)
```

**状态说明：**

| 状态 | 含义 |
|------|------|
| `OFF` | 修饰键未激活 |
| `TRANSIENT` | 临时激活，发送一个按键后自动取消 |
| `LOCKED` | 锁定激活，持续有效直到手动关闭 |

**内部位掩码：**

```kotlin
private const val OUR_CTRL_ON = 0x01    // Ctrl 临时激活
private const val OUR_CTRL_LOCK = 0x02  // Ctrl 锁定
private const val OUR_ALT_ON = 0x04     // Alt 临时激活
private const val OUR_ALT_LOCK = 0x08   // Alt 锁定
private const val OUR_SHIFT_ON = 0x10   // Shift 临时激活
private const val OUR_SHIFT_LOCK = 0x20 // Shift 锁定
```

#### 核心方法

| 方法 | 功能 |
|------|------|
| `metaPress(code, forceSticky)` | 切换修饰键状态 |
| `sendEscape()` | 发送 Esc 键 |
| `sendTab()` | 发送 Tab 键 |
| `sendPressedKey(key)` | 发送普通按键（带当前修饰键） |
| `clearTransients()` | 清除所有临时状态 |
| `isCtrlActive()` | 检查 Ctrl 是否激活 |
| `isAltActive()` | 检查 Alt 是否激活 |
| `isShiftActive()` | 检查 Shift 是否激活 |

### 5.2 按键发送流程

```
用户点击按键
    ↓
TerminalKeyboard.onKeyPress(key) 或 onCtrlPress() 等
    ↓
TerminalKeyListener.sendPressedKey(key)
    ↓
keyDispatcher.dispatchKey(modifiers, key)
    ↓
TerminalEmulator.dispatchKey(modifiers, key)
    ↓
发送转义序列到远程终端
```

### 5.3 VTermKey 常量

按键常量定义在 `termlib` 库的 `org.connectbot.terminal.VTermKey` 类中。常用常量：

| 常量 | 用途 |
|------|------|
| `VTermKey.ESCAPE` | Esc 键 |
| `VTermKey.TAB` | Tab 键 |
| `VTermKey.UP` / `DOWN` / `LEFT` / `RIGHT` | 方向键 |
| `VTermKey.HOME` / `END` | Home/End 键 |
| `VTermKey.PAGEUP` / `PAGEDOWN` | 翻页键 |
| `VTermKey.FUNCTION_1` ~ `VTermKey.FUNCTION_12` | F1-F12 |

---

## 6. 配置与设置

### 6.1 相关偏好设置

位置：`app/src/main/java/org/connectbot/util/PreferenceConstants.kt`

| 常量 | 键名 | 类型 | 默认值 | 说明 |
|------|------|------|--------|------|
| `STICKY_MODIFIERS` | `"stickymodifiers"` | String | `"no"` | 粘性修饰键模式 |
| `BUMPY_ARROWS` | `"bumpyarrows"` | Boolean | `true` | 方向键触觉反馈 |

### 6.2 粘性修饰键模式

| 值 | 行为 |
|----|------|
| `"no"` | 禁用粘性，硬件键盘不进入临时状态 |
| `"alt"` | 仅 Alt 键支持粘性 |
| `"yes"` | Ctrl、Alt、Shift 都支持粘性 |

**注意：** 软键盘（屏幕上的虚拟按键）始终使用 `forceSticky = true`，确保至少进入临时状态。

### 6.3 设置界面

位置：`app/src/main/java/org/connectbot/ui/screens/settings/SettingsScreen.kt`

设置项位于 "Keyboard" 分类下：
- **Sticky Modifiers**：粘性修饰键模式选择
- **Bumpy Arrows**：方向键触觉反馈开关

---

## 7. 集成到 ConsoleScreen

位置：`app/src/main/java/org/connectbot/ui/screens/console/ConsoleScreen.kt`

### 7.1 显示逻辑

```kotlin
// 状态变量
var showExtraKeyboard by remember { mutableStateOf(true) }

// 显示条件
AnimatedVisibility(
    visible = showExtraKeyboard,
    enter = fadeIn(animationSpec = tween(durationMillis = 100)),
    exit = fadeOut(animationSpec = tween(durationMillis = 100)),
) {
    TerminalKeyboard(
        bridge = bridge,
        onInteraction = { handleTerminalInteraction() },
        onHideIme = { showSoftwareKeyboard = false },
        onShowIme = { showSoftwareKeyboard = true },
        onOpenTextInput = { showTextInputDialog = true },
        // ...
    )
}
```

### 7.2 自动隐藏机制

虚拟键盘的自动隐藏由 ConsoleScreen 控制，不是 TerminalKeyboard 自身的逻辑。当用户闲置一段时间后，`showExtraKeyboard` 会被设为 `false`。

---

## 8. 修改指南

### 8.1 添加新按键

**步骤：**

1. 打开 `app/src/main/java/org/connectbot/ui/components/TerminalKeyboard.kt`
2. 在 `TerminalKeyboardContent` 函数的 `Row` 中找到合适位置
3. 添加新的 `KeyButton`

**示例：添加 Insert 键**

```kotlin
// 在 Row 中添加（约第 232 行的 Row 内）
KeyButton(
    text = stringResource(R.string.button_key_insert),  // 需要在 strings.xml 添加
    contentDescription = null,
    onClick = { onKeyPress(VTermKey.INSERT) },  // 需确认 termlib 是否支持
)
```

**需要修改的文件：**

| 文件 | 修改内容 |
|------|----------|
| `TerminalKeyboard.kt` | 添加按键 UI |
| `app/src/main/res/values/strings.xml` | 添加按键文字字符串 |
| `app/src/main/res/values-xx/strings.xml` | 添加翻译（可选） |

### 8.2 修改按键样式

**修改尺寸：**

```kotlin
// TerminalKeyboard.kt 顶部常量
const val TERMINAL_KEYBOARD_HEIGHT_DP = 35  // 增大高度
private const val TERMINAL_KEYBOARD_WIDTH_DP = 50   // 增大宽度
```

**修改颜色：**

在 `KeyButton` 函数中修改 `backgroundColor` 参数的默认值：

```kotlin
backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,  // 使用不同颜色
```

**修改透明度：**

```kotlin
private const val UI_OPACITY = 0.7f  // 从 0.5 改为 0.7，更不透明
```

### 8.3 修改方向键重复速度

在 `RepeatableKeyButton` 的 `pointerInput` 块中：

```kotlin
// TerminalKeyboard.kt 约第 577 行
delay(50) // 重复间隔，改为更大的值会更慢
```

### 8.4 添加新的修饰键

如果需要添加 Alt 或 Shift 按钮：

```kotlin
// 在 Row 中添加
ModifierKeyButton(
    text = stringResource(R.string.button_key_alt),
    contentDescription = "Alt key",
    modifierLevel = modifierState.altState,
    onClick = { keyHandler.metaPress(TerminalKeyListener.ALT_ON, true) },
)
```

**注意：** `TerminalKeyListener` 已经支持 Alt 和 Shift，只需要在 UI 中添加按钮。

### 8.5 修改按键排列顺序

直接调整 `TerminalKeyboardContent` 中 `Row` 内各按键的顺序即可。

---

## 9. 测试

### 9.1 预览

`TerminalKeyboard.kt` 包含多个 Preview 函数：

| Preview 名称 | 测试场景 |
|--------------|----------|
| `TerminalKeyboardPreview` | 默认状态 |
| `TerminalKeyboardCtrlPressedPreview` | Ctrl 临时激活 |
| `TerminalKeyboardCtrlLockedPreview` | Ctrl 锁定 |
| `TerminalKeyboardImeVisiblePreview` | 系统输入法可见 |

### 9.2 单元测试

位置：`app/src/test/kotlin/org/connectbot/ui/components/`

相关测试文件：
- `TerminalKeyboardContentTest.kt` - 键盘 UI 测试

### 9.3 运行测试

```bash
# 运行键盘相关的单元测试
./gradlew test --tests "org.connectbot.ui.components.*"

# 运行所有测试
./gradlew test
```

---

## 10. 常见问题

### Q: 为什么虚拟按键没有响应？

检查以下几点：
1. `TerminalBridge` 是否正确初始化
2. `keyHandler` 是否正确绑定
3. 按键回调是否正确传递到 `TerminalKeyListener`

### Q: 如何添加一个新的 VTermKey？

VTermKey 常量定义在 `termlib` 库中（外部依赖）。如果需要新的按键：
1. 检查 `termlib` 是否已定义该常量
2. 如果没有，需要先修改 `termlib` 库
3. 然后在 ConnectBot 中使用新的常量

### Q: 如何让虚拟键盘始终显示？

在 `ConsoleScreen.kt` 中，`showExtraKeyboard` 的初始值和控制逻辑决定了显示状态。设置相关的偏好项 `conn_persist` 可能影响行为。

---

## 附录：关键代码位置索引

| 功能 | 文件 | 大约行号 |
|------|------|----------|
| 按键布局定义 | `TerminalKeyboard.kt` | 228-398 |
| Ctrl 按键逻辑 | `TerminalKeyListener.kt` | 107-123 |
| 方向键重复逻辑 | `TerminalKeyboard.kt` | 558-596 |
| 键盘显示控制 | `ConsoleScreen.kt` | 640-669 |
| 粘性修饰键设置 | `SettingsScreen.kt` | 583-596 |
| 触觉反馈设置 | `SettingsScreen.kt` | 647-650 |
