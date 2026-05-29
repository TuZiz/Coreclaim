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
- **当前结构基线**：按领域包归类，主源码 Java 文件应尽量保持在 500 行以内；继续拆分时优先抽纯辅助类，避免重排保护判断顺序。

## 常见改动 → 先去哪些文件（最短路径）

- **/claim 命令行为 / 子命令路由 / Tab 补全**
  - `src/main/java/com/coreclaim/command/CoreClaimCommand.java`
  - `src/main/java/com/coreclaim/command/ClaimCommandRouter.java`
  - `src/main/java/com/coreclaim/command/ClaimUserCommandHandler.java`
  - `src/main/java/com/coreclaim/command/ClaimAdminCommandHandler.java`
  - `src/main/java/com/coreclaim/command/ClaimTabCompletionService.java`
  - `src/main/java/com/coreclaim/command/ClaimTabCompletionSupport.java`
  - 命令文档同步：`docs/coreclaim-commands.md`
  - 用户文案同步：`src/main/resources/lang/zh_cn.yml` / `src/main/resources/lang/en_us.yml`

- **GUI 打开/跳转/点击逻辑、分页、文本格式、物品工厂**
  - `src/main/java/com/coreclaim/gui/MenuService.java`
  - `src/main/java/com/coreclaim/gui/support/MenuConfigAccessor.java`
  - `src/main/java/com/coreclaim/gui/support/MenuItemFactory.java`
  - `src/main/java/com/coreclaim/gui/support/MenuTextFormatter.java`
  - GUI 配置：`src/main/resources/gui/*.yml`（优先改配置，再补代码支持）

- **聊天输入（重命名/输入确认等）**
  - `src/main/java/com/coreclaim/input/ClaimInputService.java`
  - `src/main/java/com/coreclaim/input/ClaimInputAccess.java`
  - `src/main/java/com/coreclaim/listener/ClaimInputListener.java`

- **领地交互保护 / 权限 / 规则**
  - `src/main/java/com/coreclaim/protection/listener/*`
  - `src/main/java/com/coreclaim/protection/listener/ProtectionRuleSupport.java`
  - `src/main/java/com/coreclaim/protection/listener/ProtectionMaterialRules.java`
  - 授权入口：`src/main/java/com/coreclaim/claim/auth/ClaimAuthorizationService.java`
  - 成员增删/权限清理：`src/main/java/com/coreclaim/claim/mutation/ClaimRelationMutations.java`

- **数据库/存储/跨服同步**
  - `src/main/java/com/coreclaim/storage/*`
  - 领地持久化：`src/main/java/com/coreclaim/claim/persistence/*`
  - 领地查询：`src/main/java/com/coreclaim/claim/query/*`
  - 领地变更：`src/main/java/com/coreclaim/claim/mutation/*`
  - 跨服同步：`src/main/java/com/coreclaim/sync/*`

- **领域包速查**
  - `src/main/java/com/coreclaim/claim/*`：领地运行时、授权、默认值、变更、持久化、查询。
  - `src/main/java/com/coreclaim/profile/*`：玩家档案；全局信任已废弃，不要重新引入授权效果。
  - `src/main/java/com/coreclaim/cleanup/*`：废弃/闲置领地清理状态与执行。
  - `src/main/java/com/coreclaim/selection/*`：选区、预览、工具支持。
  - `src/main/java/com/coreclaim/input/*`：聊天输入桥接。
  - `src/main/java/com/coreclaim/market/*`：领地市场。
  - `src/main/java/com/coreclaim/transfer/*`：领地转让。
  - `src/main/java/com/coreclaim/teleport/*`：跨服传送。

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
- **保护规则严禁放宽兜底**：遇到破坏、右键蛋糕、斧头去皮等问题，先同时追踪事件分类与 `ClaimAuthorizationService`，不要通过放开 `INTERACT/BREAK` 兜底解决。
- **功能方块独立权限**：附魔台、切石机、砂轮、讲台读书、制图台、锻造台、织布机、铁砧等使用 `UTILITY_INTERACT` / `utility-interact`，不要混回通用 `INTERACT`。
- **全局信任已废弃**：不要新增 `GLOBAL_TRUSTED` 授权来源；`unadd` 和 GUI 移除成员必须同时清理 `claim_members`、`claim_member_permissions` 和旧 `profile_global_members` 残留。
- **旧授权残留优先查诊断**：怀疑旧成员仍有权限时，先用 `/claim admin diagnose <领地> --player <玩家>` 区分 `OWNER / TRUSTED / PUBLIC_PERMISSION / DENIED / BYPASS`，再改代码。
- **编码/中文排查**：在 Windows PowerShell 读文件建议显式 `-Encoding UTF8`，不要把“显示乱码”误判成“文件损坏”。
- **语言资源归位**：除 `config.yml/groups.yml/rules.yml` 和 `gui/*.yml` 必须保留的配置/GUI 文案外，中文用户提示统一放进 `src/main/resources/lang/zh_cn.yml`，英文同步放进 `src/main/resources/lang/en_us.yml`。
- **不要动生成目录**：`build/**` 为构建输出；除非排查产物问题，不要在这里做源代码改动。
- **改命令一定同步**：`plugin.yml` + `docs/coreclaim-commands.md` +（如有）`lang/zh_cn.yml` / `lang/en_us.yml` / GUI 引导文案。
- **提交前验证**：行为改动至少跑 `.\gradlew.bat test --no-daemon`；准备发布或推送前跑 `.\gradlew.bat test jar --no-daemon`。

## MC Plugin Neuron 路由（有 MCP 工具时优先）

- 当前主要知识源：`easygui` / `kotlin-sdk` / `pixelmon-reforged`
- 路由：
  - 箱子 GUI / Screen / GUI DSL / 分页 / 输入桥接 → `easygui`
  - OpenAI / HTTP / Responses / Streaming / MCP 协同 → `kotlin-sdk`
  - Pixelmon 生态 → `pixelmon-reforged`
  - 跨多知识源 → `orchestrate_multi_library_task`
- 默认工具链（能用就按这个跑，不能用就按上面的“高效检索”走本地代码）：
  `solve_plugin_task → preflight_task_validation → orchestrate_multi_library_task → get_topic_kit → search_knowledge → search_code → read_source → verify_api_usage → draft_plugin_codeflow → generate_feature_bundle`
