# 子仓库拆分说明

本仓库（`GlimBrowser`）是**唯一真源（source of truth）**。有些模块（协议层、规则引擎、组件库）适合单独发布、单独引用，所以提供按子目录拆分成独立仓库的能力。

## 一、可拆分的子目录

运行：

```bash
./scripts/split-repos.sh --list
```

清单维护在 [`scripts/repos.conf`](scripts/repos.conf)，格式为 `子目录|仓库名|说明`：

| 子目录 | 子仓库 | 说明 |
|---|---|---|
| `shared/network` | `glim-network` | OpenAI 兼容协议、SSE、错误映射 |
| `shared/agent` | `glim-agent` | Agent 编排、工具 Schema、JSON 兜底 |
| `shared/adfilter` | `glim-adfilter` | 去广告规则引擎 |
| `shared/plugin` | `glim-plugin` | 用户脚本解析、权限模型 |
| `shared/data` | `glim-data` | SQLDelight schema 与仓库 |
| `shared/window` | `glim-window` | 多窗口状态机 |
| `shared/ui-adaptive` | `glim-ui-adaptive` | 自适应布局决策 |
| `app/shared` | `glim-app-shared` | 内核抽象 + Compose UI |
| `app/androidApp` | `glim-android` | Android 壳 |
| `app/desktopApp` | `glim-desktop` | 桌面壳 |
| `app/webApp` | `glim-web` | Web 壳 |
| `app/iosApp` | `glim-ios` | iOS 壳 |
| `tools` | `glim-tools` | 图标生成工具 |

## 二、本地拆分（不推送）

```bash
./scripts/split-repos.sh --branch
# 或只拆一个模块
./scripts/split-repos.sh --branch --only shared/network
```

脚本执行 `git subtree split --prefix=<子目录> -b split/<仓库名>`，在本地生成 `split/<仓库名>` 分支，包含该子目录的完整历史（路径已重写为仓库根）。

## 三、推送到远端

1. 在 GitHub（或其它托管）先**创建空仓库**，例如 `glim-network`；
2. 配置 SSH key 或 PAT；
3. 设置组织名后推送：

```bash
SPLIT_ORG=your-org ./scripts/split-repos.sh --push
# 只看命令不真推：
SPLIT_ORG=your-org SPLIT_DRY_RUN=1 ./scripts/split-repos.sh --push
```

推送使用 `--force`，因为每次 `subtree split` 都会重写历史（子仓库不要直接提交，否则会被覆盖）。

## 四、CI 自动拆分

[`.github/workflows/split-repos.yml`](.github/workflows/split-repos.yml) 在推送到 `main` 时自动拆分并推送。

需要在仓库 Secrets 里配置：

| Secret | 说明 |
|---|---|
| `SPLIT_TOKEN` | 有 `repo` 权限的 PAT（用于推送到各子仓库） |
| `SPLIT_ORG`（变量） | 你的 GitHub 组织或用户名 |

> 若使用 SSH，可改成 deploy key；工作流里默认用 `https://x-access-token:$SPLIT_TOKEN@github.com/<org>/<repo>.git`。

## 五、作为依赖引用

子仓库是**只读镜像**，建议用 git submodule 或直接依赖源码目录：

```bash
# 引用某个子仓库
git submodule add https://github.com/your-org/glim-adfilter.git libs/glim-adfilter
```

但要注意：**子仓库不含根目录的 Gradle 配置**（`settings.gradle.kts`、`gradle/libs.versions.toml`、`build.gradle.kts`）。因此：

- 想直接构建 → 用 monorepo；
- 想作为库引用 → 在 `settings.gradle.kts` 里 `includeBuild`/组合构建，或把源码复制进你的工程；
- 想发布到 Maven → 需要给每个子仓库补一份独立的构建脚本（当前未做）。

## 六、常见问题

**Q：拆出来的子仓库能单独编译吗？**
A：不能直接编译（缺根 Gradle 配置与版本目录）。它们是"代码镜像"，用于阅读、追溯历史、被 submodule 引用。

**Q：子仓库上能直接提 PR 吗？**
A：不建议。子仓库每次由 CI 强制重写，PR 会丢失。改动请提到 monorepo，再同步过去。

**Q：内部文档（PRD、进展记录）会公开吗？**
A：不会。`docs/` 已在 `.gitignore` 中排除，不进入拆分结果。
