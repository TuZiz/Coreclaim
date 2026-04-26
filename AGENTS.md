# CoreClaim 项目内 AI Agent 指南（省 token / 高准确率）

> 目标：让 AI 在 **最少上下文** 下也能快速定位文件、准确修改，并避免误改/误读编码。

## 项目速览（先看这段就能开工）

- **平台**：Spigot / Paper / Folia（`api-version: 1.20`），Java 17，Gradle
- **插件入口**：`src/main/resources/plugin.yml` → `main: com.coreclaim.CoreClaimPlugin`
  - 主类：`src/main/java/com/coreclaim/CoreClaimPlugin.java`
- **命令入口**：`/claim`（含别名 `lingdi / res / 领地 / coreclaim / cc`）
  - `plugin.yml` 同时定义了权限节点（改命令/权限时先同步这里）
- **GUI（强配置化）**：
  - 配置：`src/main/resources/gui/*.yml`
  - 布局：`GuiPlain: [ "FFFFFFFFF", ... ]`（字符布局）
  - 物品：`items.<key>.char` 映射到 `GuiPlain` 的字符（尽量不要写 slot 索引硬编码）
- **核心配置**：`src/main/resources/config.yml` / `groups.yml` / `rules.yml`
- **语言资源**：中文 `src/main/resources/lang/zh_cn.yml`；英文 `src/main/resources/lang/en_us.yml`
- **语言选择**：`src/main/resources/config.yml` 的 `language: "zh_cn"` 可切换 `zh_cn` / `en_us`
- **构建产物**：`.\gradlew.bat jar` → `build/libs/CoreClaim-1.0.jar`

## 常见改动 → 先去哪些文件（最短路径）

- **/claim 命令行为 / 子命令路由 / Tab 补全**
  - `src/main/java/com/coreclaim/command/CoreClaimCommand.java`
  - `src/main/java/com/coreclaim/command/ClaimCommandRouter.java`
  - `src/main/java/com/coreclaim/command/ClaimUserCommandHandler.java`
  - `src/main/java/com/coreclaim/command/ClaimAdminCommandHandler.java`
  - `src/main/java/com/coreclaim/command/ClaimTabCompletionService.java`
  - 命令文档同步：`docs/coreclaim-commands.md`
  - 用户文案同步：`src/main/resources/lang/zh_cn.yml` / `src/main/resources/lang/en_us.yml`

- **GUI 打开/跳转/点击逻辑、分页、文本格式、物品工厂**
  - `src/main/java/com/coreclaim/gui/MenuService.java`
  - `src/main/java/com/coreclaim/gui/support/MenuConfigAccessor.java`
  - `src/main/java/com/coreclaim/gui/support/MenuItemFactory.java`
  - `src/main/java/com/coreclaim/gui/support/MenuTextFormatter.java`
  - GUI 配置：`src/main/resources/gui/*.yml`（优先改配置，再补代码支持）

- **聊天输入（重命名/输入确认等）**
  - `src/main/java/com/coreclaim/service/ClaimInputService.java`
  - `src/main/java/com/coreclaim/listener/ClaimInputListener.java`

- **领地交互保护 / flag / 规则**
  - `src/main/java/com/coreclaim/listener/ClaimProtectionListener.java`
  - `src/main/java/com/coreclaim/listener/protection/*`
  - `src/main/java/com/coreclaim/listener/protection/ProtectionRuleSupport.java`

- **数据库/存储/跨服同步**
  - `src/main/java/com/coreclaim/storage/*`
  - `src/main/java/com/coreclaim/service/claim/*`

## 高效检索（先搜最“可见”的东西）

1) **先找用户可见文本**（GUI 标题、Lore、messages 文案、命令用法）
2) 再找 **命令/权限节点**（`plugin.yml` / `docs/coreclaim-commands.md`）
3) 最后才下沉到 service/listener/存储层

推荐命令（任选其一）：

- ripgrep（最快，有就用）：
  - `rg -n "关键词" src/main/java src/main/resources docs`
- PowerShell 纯内置（rg 不可用/报权限时）：
  - `Get-ChildItem -Recurse -File src\\main\\java,src\\main\\resources,docs | Select-String -Pattern "关键词"`

## 修改规则（避免返工）

- **主线程安全优先**：不要在异步线程直接访问世界/区块/实体/背包/GUI 会话等 Bukkit 主线程 API。
- **GUI 以配置为主**：新增/调整界面优先改 `src/main/resources/gui/*.yml`，代码只负责解析与行为。
- **编码/中文排查**：在 Windows PowerShell 读文件建议显式 `-Encoding UTF8`，不要把“显示乱码”误判成“文件损坏”。
- **语言资源归位**：除 `config.yml/groups.yml/rules.yml` 和 `gui/*.yml` 必须保留的配置/GUI 文案外，中文用户提示统一放进 `src/main/resources/lang/zh_cn.yml`，英文同步放进 `src/main/resources/lang/en_us.yml`。
- **不要动生成目录**：`build/**` 为构建输出；除非排查产物问题，不要在这里做源代码改动。
- **改命令一定同步**：`plugin.yml` + `docs/coreclaim-commands.md` +（如有）`lang/zh_cn.yml` / `lang/en_us.yml` / GUI 引导文案。

## MC Plugin Neuron 路由（有 MCP 工具时优先）

- 当前主要知识源：`easygui` / `kotlin-sdk` / `pixelmon-reforged`
- 路由：
  - 箱子 GUI / Screen / GUI DSL / 分页 / 输入桥接 → `easygui`
  - OpenAI / HTTP / Responses / Streaming / MCP 协同 → `kotlin-sdk`
  - Pixelmon 生态 → `pixelmon-reforged`
  - 跨多知识源 → `orchestrate_multi_library_task`
- 默认工具链（能用就按这个跑，不能用就按上面的“高效检索”走本地代码）：
  `solve_plugin_task → preflight_task_validation → orchestrate_multi_library_task → get_topic_kit → search_knowledge → search_code → read_source → verify_api_usage → draft_plugin_codeflow → generate_feature_bundle`
