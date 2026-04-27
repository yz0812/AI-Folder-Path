# AI Folder Path

AI Folder Path 是一个面向 Claude、Cursor、Copilot Chat 等 AI 编程工具的 IntelliJ IDEA 插件，用来把**路径、符号、上下文、调用关系、目录结构**直接复制成适合粘贴给 AI 的文本。

它不只是“复制路径”，而是把你当前看到的代码位置整理成更适合 AI 理解的引用格式。

## 适用场景

- 把当前文件、类、方法的位置发给 AI
- 把当前方法的稳定锚点复制给 CLI / Chat
- 把定义和前 N 个调用点一起发给 AI 判断影响面
- 把目录树或多选文件列表发给 AI 理解项目结构
- 在 Maven / Gradle 多模块项目中输出稳定的 `@module/...` 路径

## 功能总览

| 功能 | 场景 | 默认快捷键 | 入口 | 输出说明 |
|---|---|---|---|---|
| Copy AI | 编辑器 / 项目视图 | `Alt + P` | 编辑器右键 + 快捷键 | Alt+P 统一入口：编辑器默认复制 Anchor；项目视图会回退到 Path |
| Copy AI Path | 编辑器 / 项目视图 | 无 | 编辑器右键、Project 视图右键 > Copy AI | 复制文件、目录、方法签名或多选路径列表 |
| Copy AI Anchor | 编辑器 | 无 | 编辑器右键 | 复制 `路径 + 符号 + 行号/行范围` |
| Copy AI Context | 编辑器 | 无 | 编辑器右键 | 复制多行上下文头：`path / class / method / lines` |
| Copy AI Usages | 编辑器 | `Ctrl + Alt + 0` | 编辑器右键 + 快捷键 | 复制定义锚点和前 10 个 usages |
| Copy AI Tree | 项目视图 | 无 | Project 视图右键 > Copy AI | 复制目录树摘要或多选树结构 |

## 输出格式示例

### 1. Copy AI / Copy AI Anchor

适合把当前位置稳定地发给 AI。

```text
@demo/src/main/kotlin/com/example/UserService.kt UserService.login(String, String) L12-L48
```

### 2. Copy AI Path

#### 文件路径

```text
@demo/src/main/kotlin/com/example/UserService.kt
```

#### 方法签名

```text
@demo/src/main/kotlin/com/example/UserService.kt login(String username, String password): Boolean
```

#### 多选文件 / 目录

```text
@demo/src/main/kotlin/com/example/UserService.kt
@demo/src/main/kotlin/com/example/AuthService.kt
@demo/src/main/kotlin/com/example/service\
```

### 3. Copy AI Context

适合给 AI 提供一段简洁但完整的上下文头。

```text
path: @demo/src/main/kotlin/com/example/UserService.kt
class: UserService
method: login(String, String)
lines: 12-48
```

### 4. Copy AI Usages

适合让 AI 同时看到定义和调用点。

```text
definition: @demo/src/main/kotlin/com/example/UserServiceImpl.kt UserServiceImpl.login(String, String) L12-L48
usage[1]: @demo/src/main/kotlin/com/example/AuthFacade.kt AuthFacade.login(String, String) L21
usage[2]: @demo/src/main/kotlin/com/example/LoginController.kt LoginController.submit() L44
... +3 more call sites
```

说明：

- 最多输出前 10 个 usages
- 当目标是接口 / 抽象方法时，会优先尝试解析到一个具体实现
- usages 行号使用**实际调用点所在行**，不是整段方法范围

### 5. Copy AI Tree

#### 单个目录

```text
@demo/src/main/kotlin/com/example/
├─ controller/
├─ service/
└─ UserService.kt
```

#### 多选文件 / 目录

```text
@demo/src/main/kotlin/com/example/
├─ controller/
│  └─ LoginController.kt
└─ service/
   ├─ AuthService.kt
   └─ UserService.kt
```

说明：

- 单目录树默认深度 2、最多 50 个节点
- 多选树会按公共前缀折叠并去重

## 路径规则

插件会优先查找最近的模块根（`pom.xml`、`build.gradle`、`build.gradle.kts`），输出统一的 AI 友好路径：

```text
@module/relative/path
```

例如：

```text
@order-service/src/main/kotlin/com/example/OrderService.kt
```

如果无法识别模块根，则回退到：

```text
@projectName/relative/path
```

## 快捷键与菜单

| 功能 | 默认快捷键 | 菜单位置 |
|---|---|---|
| Copy AI | `Alt + P` | 编辑器右键 |
| Copy AI Usages | `Ctrl + Alt + 0` | 编辑器右键 |
| Copy AI Path | 无 | 编辑器右键 / Project 视图右键 > Copy AI |
| Copy AI Anchor | 无 | 编辑器右键 |
| Copy AI Context | 无 | 编辑器右键 |
| Copy AI Tree | 无 | Project 视图右键 > Copy AI |

复制成功后会弹出气泡通知。

## 设置页

插件内置了独立的快捷键设置页：

`Settings > Tools > AI Folder Path`

当前设置页支持：

- 修改 `Copy AI` 快捷键
- 修改 `Copy AI Usages` 快捷键
- 录制快捷键
- 清空快捷键
- 恢复默认快捷键
- 快捷键冲突校验
- 当前 Keymap 只读时，自动派生可编辑副本并切换

说明：

- 当前设置页只支持**单击组合键**
- 例如 `Alt + P`、`Ctrl + Alt + 0`
- 不支持两段式快捷键

## 环境要求

| 项目 | 版本 |
|---|---|
| IntelliJ IDEA | 2025.1 ~ 2025.2 |
| JDK | 21 |
| Kotlin | 2.1.0 |
| Gradle | 8.14 (Wrapper) |

## 构建

```bash
./gradlew build
```

## 项目结构

```text
src/main/kotlin/com/github/aifolderpath/
    CopyAIOptionsAction.kt        -- Alt+P 统一入口
    CopyAIPathAction.kt           -- 路径复制 / 多选路径复制
    CopyAISymbolAnchorAction.kt   -- 符号锚点复制
    CopyAIContextAction.kt        -- 上下文头复制
    CopyAIRefAction.kt            -- 定义 + usages 复制
    CopyAITreeAction.kt           -- 树结构复制
    PathResolver.kt               -- 模块路径解析
    EditorSymbolContextResolver.kt -- 类/方法/行范围解析
    OutputFormatter.kt            -- Path / Anchor / Context / Usages 格式化
    PathTreeFormatter.kt          -- 目录树 / 多选树格式化
    settings/
        ShortcutSettingsConfigurable.kt -- 设置页入口
        ShortcutSettingsPanel.kt        -- 快捷键设置 UI
        ShortcutKeymapService.kt        -- Keymap 读写与冲突校验
src/main/resources/META-INF/
    plugin.xml                    -- 插件描述、动作注册、设置页注册
```

## License

MIT

# 🎉致谢
本项目在 [LINUX DO](https://linux.do/) 社区推广，感谢 LINUX DO 社区对开源项目的支持与认可。
