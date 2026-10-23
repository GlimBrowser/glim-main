# GlimBrowser · 清舟浏览器

> 仓库组织：**https://github.com/GlimBrowser** ｜ 主仓库：**glim-main** — https://github.com/GlimBrowser/glim-main

> 一个用 Kotlin Multiplatform + Compose Multiplatform 写成的多页浏览器。手机、平板、桌面共用一套内核抽象与界面组件，网页渲染交给各平台原生引擎（Android WebView / iOS WKWebView / 桌面与 Web 走 JVM 与浏览器）。

<p>
<img alt="platforms" src="https://img.shields.io/badge/platforms-Android%20%7C%20iOS%20%7C%20macOS%20%7C%20Windows%20%7C%20Web-1E6B58">
<img alt="kotlin" src="https://img.shields.io/badge/Kotlin-2.4.20-1E6B58">
<img alt="compose" src="https://img.shields.io/badge/Compose%20Multiplatform-1.12.1-1E6B58">
<img alt="license" src="https://img.shields.io/badge/license-Non--Commercial%20(商业需授权)-9E3B2D">
</p>

---

## 目录

- [特性](#特性)
- [平台支持](#平台支持)
- [架构](#架构)
- [快速开始](#快速开始)
- [构建与测试](#构建与测试)
- [项目结构](#项目结构)
- [许可证与商业授权](#许可证与商业授权)
- [子仓库（拆分发布）](#子仓库拆分发布)
- [贡献](#贡献)

## 特性

**浏览**
- 多标签、每标签独立内核与会话，切换不重建、不丢状态
- 前进/后退/刷新/停止、下拉刷新、边缘滑动手势返回
- 地址栏智能解析（URL / 搜索词 / 本地文件）、搜索历史建议
- 隐私模式（无痕标签，独立数据存储，不写入历史与回收站）
- 页内查找（命中计数 + 上下跳转 + 高亮）、桌面版网站、系统浏览器打开、清缓存

**内容与整理**
- 书签：新增/编辑/删除、文件夹管理（新建/改名/删除，删除不丢书签）、Netscape HTML 导入导出
- 历史：按时间分组、搜索、单条删除、清空
- 下载：应用内下载队列（Android DownloadManager / iOS 应用内落盘）、状态与删除
- 标签分组、最近关闭回收站（可恢复）、标签搜索

**去广告**
- 规则引擎（ABP 子集）：网络拦截 + 化妆隐藏，四层净化强度、站点级暂停、误杀上报
- 净化统计与报告，Android 额外清理 Service Worker / CacheStorage

**AI（BYOK，自带密钥）**
- 三步配置引导：端点 / 密钥 / 模型，真实连通性测试、模型列表拉取、可跳过测试直接保存
- 页面摘要（结构化 JSON + 纯文本降级）、多轮页面问答（流式）、划词解释与翻译
- 会话历史（多会话切换）、完整开关集（流式 / 温度 / 最大输出 / 超时 / 语言 / 自定义请求头 / 无痕禁用等）
- 密钥只存平台安全区（Android Keystore / iOS Keychain），数据库只存引用

**插件**
- 用户脚本导入、权限声明与授权、按域注入执行
- 权限白名单桥（剪贴板 / 存储 / 页面文本 / 日志），未授权 API 不挂载

**体验**
- Safari 风格毛玻璃外壳：浮动标签条、统一工具栏、玻璃侧栏与底栏
- 手机 / 平板 / 桌面三套布局（平板按桌面三栏排布）
- 浅色 / 深色 / 护眼 / 跟随系统，网页深色适配（Android 系统级暗化，iOS 覆盖配色方案，可选强制深色）
- 全平台安全区适配、状态栏图标明暗跟随主题

## 平台支持

| 形态 | 目标 | 网页引擎 | 状态 |
|---|---|---|---|
| 手机 / 平板 (Android) | `android` | 系统 WebView | ✅ |
| iPhone / iPad | `iosArm64`, `iosSimulatorArm64` | WKWebView | ✅ |
| macOS (Apple Silicon) | `macosArm64` | WKWebView | ✅ |
| Windows / macOS Intel | `jvm`（Compose Desktop） | 待接入 | 🚧 壳已就绪 |
| Web | `js`, `wasmJs` | 浏览器 iframe / 平台打开 | 🚧 壳已就绪 |

> 说明：桌面 Windows 与 Intel Mac 走 JVM 目标（Compose Desktop 不提供 macosX64/mingwX64 的 UI 变体）；Web 端暂未内置网页内核。

## 架构

```
┌──────────────────────────── UI（Compose Multiplatform）────────────────────────────┐
│  BrowserShell（手机/平板/桌面三套外壳）· SubPageHost · Shelters / 组件库            │
│  BrowserController（事件 + 纯函数 reduce）· BrowserActions · TabManager            │
└───────────────┬───────────────────────────────────────────────┬────────────────────┘
                │ BrowserEngine（expect/actual 接口）           │ Repository（SQLDelight）
   ┌────────────┴───────────┐                    ┌──────────────┴───────────────┐
   │ Android WebView 引擎    │                    │ 书签 / 历史 / 下载 / AI 档案  │
   │ iOS·macOS WKWebView 引擎│                    │ 插件安装 / 净化设置 / 会话    │
   └────────────────────────┘                    └──────────────────────────────┘
                │
      ┌─────────┴──────────┬──────────────┬──────────────┐
      │ 净化引擎（规则匹配） │ AI 运行时     │ 插件运行时     │ 多窗口状态机
      │ shared:adfilter     │ shared:agent │ shared:plugin  │ shared:window
      └─────────────────────┴──────────────┴──────────────┘
```

设计要点：

- **内核抽象**：`BrowserEngine` 定义加载/导航/查找/下载/清缓存/脚本执行与监听回调，平台差异全部收敛在 `actual` 实现里。
- **状态管理**：所有交互都是 `BrowserController.Event`，每个事件用 `reduce(actions, state)` 纯函数迁移状态，便于单测与回放。
- **每标签引擎池**：`EnginePool` 复用 WebView/WKWebView，切标签只换视图不重建内核。
- **文档驱动**：需求来自 PRD 与附件，阶段进展记录在内部文档中（不随本仓库发布）。

## 快速开始

### 获取代码

```bash
git clone https://github.com/GlimBrowser/glim-main.git
cd GlimBrowser
```

### 环境

- JDK 17
- Android Studio（含 Android SDK 36+）或 IntelliJ IDEA
- Xcode 16+（iOS / macOS）
- Xcode Command Line Tools

### 运行

```bash
# Android
./gradlew :app:androidApp:assembleDebug          # 产物 app/androidApp/build/outputs/apk/debug/

# 桌面（macOS / Windows）
./gradlew :app:desktopApp:run

# Web
./gradlew :app:webApp:wasmJsBrowserDevelopmentRun   # 现代浏览器
./gradlew :app:webApp:jsBrowserDevelopmentRun       # 兼容旧浏览器
```

iOS：用 Xcode 打开 `app/iosApp`，选择模拟器或真机运行。

## 构建与测试

```bash
# 全平台编译（不含打包）
./gradlew :app:shared:compileKotlinJvm \
          :app:shared:compileAndroidMain \
          :app:shared:compileKotlinIosSimulatorArm64 \
          :app:shared:compileKotlinMacosArm64 \
          :app:shared:compileKotlinJs \
          :app:shared:compileKotlinWasmJs

# 共享层单测（覆盖状态机、规则引擎、协议解析、AI 配置等）
./gradlew :app:shared:jvmTest \
          :shared:adfilter:jvmTest \
          :shared:network:jvmTest \
          :shared:agent:jvmTest \
          :shared:data:jvmTest \
          :shared:window:jvmTest \
          :shared:plugin:jvmTest \
          :shared:ui-adaptive:jvmTest

# Android 安装包
./gradlew :app:androidApp:assembleDebug

# iOS framework（模拟器）
./gradlew :app:shared:linkDebugFrameworkIosSimulatorArm64
```

最低系统版本：Android `minSdk 26`；iOS 由 Compose Multiplatform 1.12 要求决定。

## 项目结构

```
.
├── app/
│   ├── androidApp/      # Android 壳（Activity、Manifest、图标、主题）
│   ├── iosApp/          # iOS 壳（Xcode 工程、Info.plist、AppIcon）
│   ├── desktopApp/      # 桌面壳（Compose Desktop、打包配置、图标）
│   ├── webApp/          # Web 壳（Wasm/JS 入口）
│   └── shared/          # KMP 共享层：引擎抽象、Compose UI、状态管理
├── shared/
│   ├── adfilter/        # 去广告规则引擎（ABP 子集、净化统计）
│   ├── agent/           # AI Agent 编排、工具 Schema、JSON 兜底
│   ├── data/            # SQLDelight schema 与仓库
│   ├── network/         # OpenAI 兼容协议、SSE、错误映射
│   ├── plugin/          # 用户脚本解析、权限、订阅
│   ├── ui-adaptive/     # 窗口分级与自适应布局决策
│   └── window/          # 多窗口状态机
├── core/                # 平台基础能力（预留）
├── server/              # Ktor 服务端（预留）
├── tools/               # 图标生成等开发工具
└── scripts/             # 子仓库拆分脚本
```

## 许可证与商业授权

本项目采用 **[GlimBrowser Non-Commercial License 1.0](LICENSE)**：

- ✅ 允许：个人学习、研究、教学、非营利组织内部使用；复制、修改、分发（保留声明）
- ❌ 禁止：任何**商业使用**——销售、付费服务、SaaS/托管、营利实体内部生产使用、以本项目功能为价值来源向第三方收费
- 💼 商业授权：请通过 GitHub 组织 **https://github.com/GlimBrowser** 联系维护者（或提交 Commercial License 议题），见 [NOTICE](NOTICE)

这不是 OSI 认可的开源许可证，而是 **source-available（源码可用）**。若你需要真正的开源许可证（如 Apache-2.0 / MIT），可以自行替换 `LICENSE`，但这与"限制商业使用"的目标冲突，请先想清楚。

## 子仓库（拆分展示）

主仓库 `GlimBrowser` 包含**完整项目**（所有模块、壳工程、构建配置）。子仓库是拆出来的**只读镜像**，用于单独浏览、引用与讨论某个模块。

| 子仓库 | 内容 |
|---|---|
| [glim-app-shared](https://github.com/GlimBrowser/glim-app-shared) | KMP 浏览器内核抽象、Compose UI、状态管理 |
| [glim-network](https://github.com/GlimBrowser/glim-network) | OpenAI 兼容协议、SSE 解析、错误映射 |
| [glim-agent](https://github.com/GlimBrowser/glim-agent) | AI Agent 编排、工具 Schema、JSON 兜底链 |
| [glim-adfilter](https://github.com/GlimBrowser/glim-adfilter) | 去广告规则引擎（ABP 子集）与净化统计 |
| [glim-data](https://github.com/GlimBrowser/glim-data) | SQLDelight schema 与仓库实现 |
| [glim-plugin](https://github.com/GlimBrowser/glim-plugin) | 用户脚本解析、权限模型、订阅 |
| [glim-ui-adaptive](https://github.com/GlimBrowser/glim-ui-adaptive) | 窗口宽度分级与自适应布局决策 |
| [glim-window](https://github.com/GlimBrowser/glim-window) | 多窗口状态机与快捷键映射 |
| [glim-android](https://github.com/GlimBrowser/glim-android) | Android 壳（WebView 引擎、主题、图标） |
| [glim-ios](https://github.com/GlimBrowser/glim-ios) | iOS 壳（WKWebView、Xcode 工程） |
| [glim-desktop](https://github.com/GlimBrowser/glim-desktop) | 桌面壳（Compose Desktop、打包） |
| [glim-web](https://github.com/GlimBrowser/glim-web) | Web 壳（Wasm / JS） |
| [glim-tools](https://github.com/GlimBrowser/glim-tools) | 图标生成等开发工具 |

### 发布

```bash
./scripts/create-repos.sh --dry-run    # 先看要在组织下创建哪些仓库
./scripts/create-repos.sh              # 在 GlimBrowser 组织下创建空仓库
./scripts/push-all.sh -m "feat: ..."   # 提交 + 推主仓库 + 拆分推送全部子仓库
./scripts/push-all.sh --main-only      # 只推主仓库
./scripts/push-all.sh --subs-only      # 只推子仓库
./scripts/push-all.sh --dry-run        # 只看会执行什么
```

细分控制用 `scripts/split-repos.sh`（`--list` / `--branch` / `--push` / `--only <子目录>`）。
细节、远端初始化与 CI（`.github/workflows/split-repos.yml`）见 [SUBREPOS.md](SUBREPOS.md)。

## 发布前检查清单

开源前请替换以下占位符（当前是模板值）：

| 文件 | 占位符 | 替换为 |
|---|---|---|
| `README.md`、`NOTICE`、`LICENSE` | 商业授权联系方式 | 目前指向 GitHub 组织主页；如需邮箱请补充 |
| — | 组织地址 | 已设为 `https://github.com/GlimBrowser` |
| `.github/workflows/split-repos.yml` | `vars.SPLIT_ORG` / `secrets.SPLIT_TOKEN` | 组织名与 PAT |
| `scripts/split-repos.sh` | `SPLIT_ORG` 默认值 | 已设为 `GlimBrowser` |

另外确认：

- `docs/`（PRD、进展记录）**不会**被发布（已在 `.gitignore` 排除）
- `local.properties`、`*.jks`、`keystore.properties` 未入库
- 子仓库同样带 `LICENSE`（拆分脚本会自动附加）

## 贡献

- 提交前请跑一遍上面的编译与测试命令
- 代码风格：Kotlin 官方风格；提交信息用中文或英文均可，需说明动机
- 请勿提交密钥、`local.properties`、`docs/` 内部文档与构建产物（已在 `.gitignore` 排除）
- 由于许可证限制商业使用，提交 PR 即表示你同意以同一许可证授权你的贡献

---

<p align="center"><sub>GlimBrowser · 清舟 — 轻、快、自己的浏览器</sub></p>
