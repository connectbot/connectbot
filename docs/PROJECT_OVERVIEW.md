# ConnectBot 项目概览

> 本文档面向开发者和 AI Coding Agent，提供项目级别的全局视图，帮助快速定位"做什么事该去哪找代码"。

---

## 1. 项目概述

ConnectBot 是一个开源的 Android SSH/Telnet 客户端，让用户通过加密安全连接访问远程服务器，同时支持本地终端会话。

---

## 2. 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin（主要）、Java（遗留代码）、C++（本地层） |
| 平台 | Android（minSdk 24，targetSdk 36） |
| UI 框架 | Jetpack Compose + Material3 |
| 架构模式 | MVVM（ViewModel + Repository） |
| 依赖注入 | Hilt（Dagger） |
| 数据库 | Room（SQLite） |
| 导航 | Navigation Compose |
| 构建系统 | Gradle（Kotlin DSL）+ KSP |
| SSH 库 | sshlib（ConnectBot 维护的 Trilead SSH-2 分支） |
| 终端库 | termlib（ConnectBot 维护的终端模拟器） |
| 加密提供者 | Conscrypt（oss 版本）/ Google Play Services（google 版本） |
| 测试框架 | JUnit、Mockito、Robolectric、Espresso、Compose Test |
| 代码质量 | Spotless（ktlint + Google Java Format）、SonarQube、Kover |

---

## 3. 目录结构及职责说明

```
connectbot/
├── app/                              # 主应用模块
│   ├── build.gradle.kts              # 应用级构建配置（flavor、签名、依赖）
│   ├── CMakeLists.txt                # 本地 C++ 代码构建配置
│   ├── lint.xml                      # Android Lint 规则配置
│   ├── proguard.cfg                  # ProGuard 混淆规则
│   ├── schemas/                      # Room 数据库 schema 导出（用于迁移测试）
│   │   └── org.connectbot.data.ConnectBotDatabase/
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml   # 应用清单（权限、组件声明）
│       │   ├── cpp/                  # C++ 本地代码（JNI，用于本地 shell 执行）
│       │   ├── java/org/connectbot/  # Kotlin/Java 源码根目录
│       │   │   ├── ConnectBotApplication.kt  # Application 入口
│       │   │   ├── data/             # 数据层（Room 数据库、Repository）
│       │   │   ├── di/               # Hilt 依赖注入模块
│       │   │   ├── logging/          # 日志初始化（Timber）
│       │   │   ├── service/          # 后台服务（TerminalManager、TerminalBridge）
│       │   │   ├── transport/        # 协议传输层（SSH、Telnet、Local）
│       │   │   ├── ui/               # UI 层（Compose 界面、ViewModel）
│       │   │   └── util/             # 工具类（加密、字体、常量）
│       │   └── res/                  # Android 资源（drawable、values、layout 等）
│       ├── test/                     # 本地单元测试（Robolectric）
│       ├── androidTest/              # 设备/模拟器集成测试
│       └── sharedTest/kotlin/        # 测试共享代码
├── gradle/
│   └── libs.versions.toml            # 版本目录（统一依赖版本管理）
├── translations/                     # 翻译工具（Python 脚本 + 测试）
├── spotless/                         # Spotless 格式化配置
├── icons/                            # 应用图标资源
├── .github/workflows/                # GitHub Actions CI 配置
├── build.gradle.kts                  # 根构建脚本（Spotless、仓库配置）
├── settings.gradle.kts               # 项目设置（模块包含）
├── gradle.properties                 # Gradle 属性（JVM 参数、AndroidX 配置）
├── AGENTS.md                         # AI Agent 开发指南
├── CONTRIBUTING.md                   # 贡献者指南
├── CHANGELOG.md                      # 变更日志
└── README.md                         # 项目说明
```

### 关键目录详解

- **`app/src/main/java/org/connectbot/ui/`**：所有 Compose UI 代码
  - `screens/`：各功能页面（hostlist、console、settings 等），每个子目录包含 Screen 和 ViewModel
  - `components/`：可复用的 UI 组件（对话框、键盘等）
  - `navigation/`：Navigation Compose 路由定义
  - `theme/`：Material3 主题配置
- **`app/src/main/java/org/connectbot/data/`**：数据层
  - `entity/`：Room 实体类（Host、Pubkey、PortForward、KnownHost、ColorScheme、Profile）
  - `dao/`：Room DAO 接口
  - `migration/`：数据库迁移脚本
  - 根目录：Repository 类和数据库定义
- **`app/src/main/java/org/connectbot/transport/`**：传输协议实现
  - `Transport.kt`：密封类，定义协议类型（SSH、Telnet、Local）
  - `AbsTransport.kt`：传输抽象基类
  - `SSH.kt`、`Telnet.kt`、`Local.kt`：具体协议实现
- **`app/src/main/java/org/connectbot/service/`**：后台服务
  - `TerminalManager.kt`：终端会话管理服务（Android Service）
  - `TerminalBridge.kt`：连接桥接（管理单个终端会话的生命周期）

---

## 4. 架构概览

### 分层架构

```
┌─────────────────────────────────────────────────────────────┐
│                        UI Layer                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │   Screens   │  │ Components  │  │    Navigation       │ │
│  │ (Compose)   │  │ (Compose)   │  │ (NavGraph)          │ │
│  └──────┬──────┘  └──────┬──────┘  └──────────┬──────────┘ │
│         │                │                     │             │
│         └────────────────┼─────────────────────┘             │
│                          │                                   │
│                   ┌──────▼──────┐                            │
│                   │  ViewModels │                            │
│                   └──────┬──────┘                            │
├──────────────────────────┼───────────────────────────────────┤
│                   Service Layer                              │
│  ┌───────────────────────▼───────────────────────────────┐  │
│  │              TerminalManager (Service)                 │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌───────────────┐ │  │
│  │  │  Terminal    │  │   Relay     │  │  Connection   │ │  │
│  │  │  Bridge      │  │             │  │  Notifier     │ │  │
│  │  └──────┬──────┘  └─────────────┘  └───────────────┘ │  │
│  └─────────┼─────────────────────────────────────────────┘  │
├────────────┼─────────────────────────────────────────────────┤
│            │          Transport Layer                        │
│  ┌─────────▼─────────────────────────────────────────────┐  │
│  │              Transport (Sealed Class)                  │  │
│  │  ┌─────────┐  ┌──────────┐  ┌─────────┐              │  │
│  │  │   SSH   │  │  Telnet  │  │  Local  │              │  │
│  │  └────┬────┘  └────┬─────┘  └────┬────┘              │  │
│  │       │            │             │                     │  │
│  │       └────────────┼─────────────┘                     │  │
│  │                    │                                   │  │
│  │            ┌───────▼───────┐                           │  │
│  │            │ AbsTransport  │                           │  │
│  │            └───────────────┘                           │  │
│  └───────────────────────────────────────────────────────┘  │
├──────────────────────────────────────────────────────────────┤
│                      Data Layer                              │
│  ┌───────────────┐  ┌───────────────┐  ┌─────────────────┐ │
│  │  Repositories │  │     DAOs      │  │    Entities     │ │
│  │  (Host, Pub,  │  │ (Room)        │  │ (Room)          │ │
│  │   Color...)   │  │               │  │                 │ │
│  └───────┬───────┘  └───────┬───────┘  └────────┬────────┘ │
│          │                  │                    │          │
│          └──────────────────┼────────────────────┘          │
│                             │                               │
│                    ┌────────▼────────┐                      │
│                    │ ConnectBot DB   │                      │
│                    │ (Room/SQLite)   │                      │
│                    └─────────────────┘                      │
├──────────────────────────────────────────────────────────────┤
│                     DI Layer (Hilt)                          │
│  ┌─────────┐ ┌──────────┐ ┌──────────┐ ┌─────────────────┐ │
│  │AppModule│ │DB Module │ │Dispatcher│ │Biometric Module │ │
│  └─────────┘ └──────────┘ └──────────┘ └─────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

### 核心业务流程

**建立 SSH 连接的流程：**

1. 用户在 `HostListScreen` 选择或创建主机配置
2. `HostListViewModel` 调用 `HostRepository` 保存/读取主机数据
3. 导航到 `ConsoleScreen`，触发 `TerminalManager` 创建新会话
4. `TerminalManager` 通过 `TransportFactory` 创建 `SSH` 传输实例
5. `TerminalBridge` 使用 `SSH` 传输建立连接，初始化终端模拟器
6. `ConsoleScreen` 通过 `ConsoleViewModel` 订阅终端状态并渲染输出

---

## 5. 核心模块概要

| 模块 | 职责 | 对应目录 |
|------|------|----------|
| **UI 层** | 所有用户界面，采用 Jetpack Compose 实现，包含各功能页面和可复用组件 | `app/src/main/java/org/connectbot/ui/` |
| **Service 层** | 后台终端管理服务，维护连接生命周期，处理终端 I/O 和通知 | `app/src/main/java/org/connectbot/service/` |
| **Transport 层** | 协议传输抽象，支持 SSH、Telnet、Local 三种连接方式 | `app/src/main/java/org/connectbot/transport/` |
| **Data 层** | Room 数据库、DAO、Repository，管理主机、密钥、配色方案等持久化数据 | `app/src/main/java/org/connectbot/data/` |
| **DI 层** | Hilt 依赖注入配置，提供数据库、调度器、生物识别等模块 | `app/src/main/java/org/connectbot/di/` |
| **Util 层** | 工具类集合，包括加密、字体管理、偏好常量等 | `app/src/main/java/org/connectbot/util/` |
| **翻译系统** | 自动化翻译工具链，管理多语言资源 | `translations/` |

---

## 6. 启动流程与入口点

### 应用入口

- **Application 类**：`app/src/main/java/org/connectbot/ConnectBotApplication.kt`
  - 使用 `@HiltAndroidApp` 注解，触发 Hilt 依赖注入初始化
  - 在 `onCreate()` 中初始化 Timber 日志系统

- **主 Activity**：`app/src/main/java/org/connectbot/ui/MainActivity.kt`
  - 使用 `@AndroidEntryPoint` 注解，支持 Hilt 注入
  - 配置 `setContent` 使用 Compose UI
  - 绑定 `TerminalManager` 服务
  - 处理 Intent（SSH/Telnet URI scheme）

### 启动初始化顺序

1. `ConnectBotApplication.onCreate()` → Hilt 初始化 + Timber 日志
2. `MainActivity.onCreate()` → Compose 内容设置 + 服务绑定
3. Navigation Compose 初始化 → 加载 `NavGraph`
4. 首次启动：显示 EULA → 主机列表
5. 后续启动：直接显示主机列表

### 关键配置文件

- `AndroidManifest.xml`：声明权限、Activity、Service、URI scheme
- `build.gradle.kts`（app）：产品风味（oss/google）、签名、依赖
- `gradle/libs.versions.toml`：统一版本管理

---

## 7. 外部依赖与服务

### 核心依赖库

| 依赖 | 用途 | 版本管理位置 |
|------|------|--------------|
| `org.connectbot:sshlib` | SSH 协议实现 | `gradle/libs.versions.toml` |
| `org.connectbot:termlib` | 终端模拟器 | `gradle/libs.versions.toml` |
| `org.conscrypt:conscrypt-android` | TLS/加密提供者（oss 版本） | `gradle/libs.versions.toml` |
| `com.google.android.gms:play-services-basement` | Google Play Services（google 版本） | `gradle/libs.versions.toml` |

### Android 系统服务

- **网络**：`ACCESS_NETWORK_STATE`、`INTERNET` 权限
- **前台服务**：`FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_REMOTE_MESSAGING`
- **通知**：`POST_NOTIFICATIONS`（Android 13+）
- **生物识别**：`androidx.biometric` 用于密钥保护

### 数据库

- **Room/SQLite**：本地数据库 `connectbot.db`
  - 表：hosts、pubkeys、port_forwards、known_hosts、color_schemes、color_palette、profiles
  - Schema 导出：`app/schemas/`
  - 迁移脚本：`app/src/main/java/org/connectbot/data/migration/`

### 第三方服务

- **Google Play Services**（仅 google 版本）：用于加密提供者更新和可下载字体
- **Google Play Feature Delivery**（仅 google 版本）：动态功能模块

---

## 8. 配置与环境变量

### Gradle 配置

| 配置项 | 位置 | 说明 |
|--------|------|------|
| `org.gradle.caching=true` | `gradle.properties` | 启用构建缓存 |
| `org.gradle.parallel=true` | `gradle.properties` | 并行构建 |
| `org.gradle.jvmargs=-Xmx4g` | `gradle.properties` | JVM 内存配置 |
| `android.useAndroidX=true` | `gradle.properties` | 使用 AndroidX |
| `TRANSLATIONS_ONLY` | 环境变量 | 仅构建翻译模块时设置 |

### 应用配置

| 配置项 | 位置 | 说明 |
|--------|------|------|
| `applicationId` | `app/build.gradle.kts` | `org.connectbot` |
| `minSdk` | `gradle/libs.versions.toml` | 24（Android 7.0） |
| `targetSdk` | `gradle/libs.versions.toml` | 36 |
| `compileSdk` | `gradle/libs.versions.toml` | 37 |
| `keystorePassword` | 系统属性 | 签名密钥密码（CI 环境） |

### 产品风味

| 风味 | 说明 |
|------|------|
| `oss` | 开源版本，使用 Conscrypt 加密提供者，无 Google Play Services |
| `google` | Google Play 版本，使用 Play Services 进行加密提供者更新 |

---

## 9. 开发指南（极简）

### 环境要求

- Android Studio（最新稳定版）
- JDK 17
- Android SDK（API 37）
- Docker（用于 OpenSSH 集成测试）

### 常用命令

```bash
# 构建所有变体
./gradlew assemble

# 构建 oss 调试版本
./gradlew :app:assembleOssDebug

# 运行本地单元测试
./gradlew test

# 运行设备/模拟器测试
./gradlew connectedAndroidTest

# 运行特定风味的设备测试
./gradlew connectedOssDebugAndroidTest
./gradlew connectedGoogleDebugAndroidTest

# 代码检查
./gradlew lint

# 格式化代码
./gradlew spotlessApply

# 验证格式
./gradlew spotlessCheck

# 完整验证（提交前推荐）
./gradlew check test
```

### 开发工作流

1. 从 `main` 分支创建特性分支
2. 进行开发，遵循现有代码风格
3. 运行 `./gradlew spotlessApply` 格式化代码
4. 运行 `./gradlew lint` 检查 lint 问题
5. 运行 `./gradlew check test` 验证所有检查通过
6. 提交 PR，描述变更并关联 issue

### 测试策略

- **本地测试**（`app/src/test/`）：使用 Robolectric 运行，适合 ViewModel、业务逻辑
- **集成测试**（`app/src/androidTest/`）：需要设备/模拟器，用于 UI 交互、Hilt 注入测试
- **共享测试**（`app/src/sharedTest/`）：可在本地和设备测试间共享的代码

### 注意事项

- 不要阻塞主线程，使用协程处理耗时操作
- 在 ViewModel 中使用注入的 `CoroutineDispatchers` 而非硬编码 `Dispatchers.IO`
- 数据库迁移需更新 `app/schemas/` 下的 JSON 文件
- UI 字符串使用 `stringResource()` 从资源读取，便于翻译

---

## 附录：关键文件快速索引

| 文件 | 用途 |
|------|------|
| `app/src/main/AndroidManifest.xml` | 权限、组件声明 |
| `app/build.gradle.kts` | 应用构建配置 |
| `gradle/libs.versions.toml` | 依赖版本管理 |
| `app/src/main/java/org/connectbot/ConnectBotApplication.kt` | Application 入口 |
| `app/src/main/java/org/connectbot/ui/MainActivity.kt` | 主 Activity |
| `app/src/main/java/org/connectbot/ui/navigation/NavGraph.kt` | 导航图定义 |
| `app/src/main/java/org/connectbot/ui/navigation/NavDestinations.kt` | 路由常量 |
| `app/src/main/java/org/connectbot/data/ConnectBotDatabase.kt` | Room 数据库定义 |
| `app/src/main/java/org/connectbot/di/DatabaseModule.kt` | 数据库依赖注入 |
| `app/src/main/java/org/connectbot/service/TerminalManager.kt` | 终端管理服务 |
| `app/src/main/java/org/connectbot/transport/Transport.kt` | 传输协议定义 |
| `AGENTS.md` | AI Agent 开发指南 |
| `CONTRIBUTING.md` | 贡献者指南 |
