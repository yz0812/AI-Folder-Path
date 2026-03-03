# AI Folder Path

IntelliJ IDEA 插件，提供两个核心功能：

1. **Copy AI Path** — 将选中的类名、方法名或代码片段以 AI IDE 友好的格式复制到剪贴板
2. **Copy AI Ref Path** — 解析光标处标识符到具体实现（穿透接口/抽象类），复制实现处路径

## 输出格式

### Copy AI Path

| 场景 | 输出示例 |
|------|----------|
| 选中类名 / 光标在类名上 | `@module/src/main/java/com/example/UserService.java` |
| 选中方法名 / 光标在方法名上 | `@module/src/main/java/com/example/UserService.java login(String username, String password): boolean` |
| 选中代码片段 | `@module/.../UserService.java login`<br>`42line    if (user == null) {`<br>`43line        throw new RuntimeException();`<br>`44line    }` |
| Project 视图选中文件 | `@module/src/main/java/com/example/UserService.java` |

### Copy AI Ref Path

| 场景 | 输出示例 |
|------|----------|
| 光标在接口方法调用上 | `@module/src/main/java/com/example/UserServiceImpl.java login`（跳转到实现类） |
| 光标在具体方法名上 | `@module/src/main/java/com/example/UserService.java login` |
| 光标在类名上 | `@module/src/main/java/com/example/UserService.java` |

多模块项目自动识别最近的 `pom.xml` / `build.gradle(.kts)` 确定模块根目录。

## 使用方式

| 功能 | 快捷键 | 右键菜单 |
|------|--------|----------|
| Copy AI Path | `Alt + P` | 编辑器 / Project 视图右键 |
| Copy AI Ref Path | `Ctrl + Alt + P` | 编辑器右键 |

快捷键可在 Settings > Keymap 中搜索对应名称自定义。

复制成功后会弹出气泡通知。

## 环境要求

| 项目 | 版本 |
|------|------|
| IntelliJ IDEA | 2025.1+ |
| JDK | 21 |
| Kotlin | 2.1.0 |
| Gradle | 8.14 (Wrapper) |

## 构建

```bash
./gradlew build
```

## 项目结构

```
src/main/kotlin/com/github/aifolderpath/
    CopyAIPathAction.kt    -- 主 Action，处理选中内容判断与格式化输出
    CopyAIRefAction.kt     -- Ref Action，解析标识符到实现处并复制路径
    PathResolver.kt        -- 路径解析，生成 @模块名/相对路径 格式
src/main/resources/META-INF/
    plugin.xml             -- 插件描述符，注册 Action 与通知组
```

## License

MIT
