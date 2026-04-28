# CoreClaim

CoreClaim 是一个面向 `Spigot / Paper / Folia 1.20+` 的 Java 17 领地插件，目标是把新人创建领地、GUI 管理、权限边界、跨服数据和长期维护都做成一套可配置、可诊断的系统。

主命令为 `/claim`，并提供 `lingdi`、`res`、`领地`、`coreclaim`、`cc` 别名。

## 功能概览

- **双创建流程**：支持新人核心右键命名创建，也支持普通金锄头两点选区后 `/claim create <领地名>` 创建。
- **完整 GUI 管理**：内置领地列表、详情、核心管理、成员管理、在线玩家添加、权限管理、选区创建确认、扩建数量与确认菜单。
- **权限与特殊规则**：基础权限覆盖放置、破坏、交互、红石、爆炸、桶、传送、飞行；交互统一控制容器、门、活板门、栅栏门、床和普通右键，特殊规则保留液体流入与领地时间。
- **成员与禁足**：支持 `/claim add`、`/claim unadd`、`/claim deny <玩家|*|全部>`、`/claim undeny <玩家|*|全部>`，被授权领地在列表中以只读/传送入口展示。
- **扩建与经济**：按方向扩建，组别可配置初始半径、最大半径、数量上限、创建单价、扩建单价；Vault 存在时启用扣费。
- **传送与转让**：支持领地传送点设置、领地传送、限时转让请求、接收/拒绝转让。
- **跨服能力**：支持 `server-id`、跨服传送占位流程、MySQL 共享存储、SQLite 到 MySQL 迁移、Redis 领地同步通知。
- **空地清理**：支持长期未上线领地扫描、候选/宽限/旧地基线状态、手动跳过和基线修正，默认关闭以保证安全。
- **资源自修复**：语言和 GUI 默认值会补全，过期或损坏的 GUI 资源会按 `layout-version` 自动替换。
- **现代文本**：语言文件和菜单资源支持 `&#RRGGBB` 与 `<#RRGGBB>` 十六进制颜色。
- **PlaceholderAPI**：检测到 PlaceholderAPI 时注册占位符扩展。

## GUI 文件

所有菜单资源都放在 `src/main/resources/gui/`，并使用 `Shape` 布局：

| 文件 | 用途 |
| --- | --- |
| `core.yml` | 领地核心管理主菜单 |
| `claim-list.yml` | 我的领地 / 已授权领地列表 |
| `claim-view.yml` | 被授权领地只读详情 |
| `claim-manage.yml` | 领地名称、传送点、核心显示、成员和权限入口 |
| `trust.yml` | 成员管理 |
| `trust-online-add.yml` | 在线玩家快速添加 |
| `claim-permissions.yml` | 基础权限与特殊规则管理 |
| `selection-create.yml` | 选区创建确认 |
| `claim-expand-amount.yml` | 扩建数量选择 |
| `claim-expand-confirm.yml` | 扩建最终确认 |

## 命令

### 玩家命令

| 命令 | 说明 |
| --- | --- |
| `/claim` | 玩家打开领地菜单，控制台显示帮助 |
| `/claim help` | 查看帮助 |
| `/claim menu` | 打开领地菜单 |
| `/claim info` | 查看脚下领地详情 |
| `/claim list` | 查看自己拥有或被授权的领地 |
| `/claim show [领地名]` | 显示当前或指定领地边界 |
| `/claim show auto [on|off]` | 切换进入领地自动显示边界 |
| `/claim create <领地名>` | 使用当前金锄头选区创建领地 |
| `/claim tp <领地名>` | 传送到领地传送点 |
| `/claim tpset` | 将当前位置设置为领地传送点 |
| `/claim expand <east|south|west|north>` | 向指定方向扩建 |
| `/claim add <玩家>` | 添加成员 |
| `/claim unadd <玩家>` | 移除成员 |
| `/claim deny <玩家|*|全部>` | 禁足指定玩家，或开启全员禁足 |
| `/claim undeny <玩家|*|全部>` | 解除指定玩家禁足，或关闭全员禁足 |
| `/claim flag [list]` | 查看特殊规则 |
| `/claim flag <flag> <allow|deny|unset>` | 修改特殊规则，目前包含 `liquid-flow` 和 `time-cycle` |
| `/claim transfer <玩家>` | 转让当前领地 |
| `/claim transfer <领地名> <玩家>` | 转让指定领地 |
| `/claim transfer accept` | 接受转让 |
| `/claim transfer deny` | 拒绝转让 |
| `/claim remove <领地名>` | 删除领地 |
| `/claim confirm` | 确认待处理删除 |

### 管理命令

| 命令 | 说明 |
| --- | --- |
| `/claim admin create system <领地名>` | 按当前选区创建系统领地 |
| `/claim admin info <领地名|#claimId>` | 查看完整领地详情 |
| `/claim admin playerclaims <玩家>` | 查看玩家名下全部领地 |
| `/claim admin diagnose <领地名|#claimId>` | 查看跨服和传送诊断 |
| `/claim admin add <玩家>` | 强制添加当前领地成员 |
| `/claim admin unadd <玩家>` | 强制移除当前领地成员 |
| `/claim admin deny <玩家|*|全部>` | 强制修改禁足 |
| `/claim admin undeny <玩家|*|全部>` | 强制取消禁足 |
| `/claim admin permission <permission> <allow|deny>` | 修改默认权限 |
| `/claim admin flag <flag> <allow|deny|unset>` | 修改特殊规则 |
| `/claim admin remove [领地名|#claimId]` | 删除脚下或指定领地 |
| `/claim admin cleanup list` | 查看空地清理候选 |
| `/claim admin cleanup run` | 立即运行清理扫描 |
| `/claim admin cleanup skip <领地名|#claimId>` | 永久跳过自动清理 |
| `/claim admin cleanup baseline <领地名|#claimId> <empty|used|skip>` | 修正旧地清理基线 |
| `/claim admin setserver <claimId> <serverId>` | 修复旧领地所属服务器 |
| `/claim activity <get|set|add|take> <玩家> [值]` | 管理玩家活跃值 |
| `/claim reload` | 重载配置、语言、菜单和数据 |
| `/claim givecore <玩家> [数量]` | 发放领地核心 |

## 权限节点

| 权限 | 默认 | 说明 |
| --- | --- | --- |
| `coreclaim.use` | `true` | 使用玩家命令 |
| `coreclaim.manage.deny` | `true` | 管理当前领地禁足 |
| `coreclaim.manage.tpset` | `true` | 设置当前领地传送点 |
| `coreclaim.manage.flags` | `true` | 管理当前领地特殊规则 |
| `coreclaim.transfer` | `true` | 转让自己的领地 |
| `coreclaim.admin` | `op` | 完整管理员权限 |
| `coreclaim.admin.view` | `op` | 查看管理员级详情 |
| `coreclaim.admin.force` | `op` | 强制编辑和绕过限制 |
| `coreclaim.admin.ops` | `op` | reload 等运维命令 |
| `coreclaim.admin.create.system` | `op` | 创建系统领地 |
| `coreclaim.admin.member.manage` | `op` | 强制管理成员和禁足 |
| `coreclaim.admin.permission.manage` | `op` | 强制管理默认权限 |
| `coreclaim.admin.flag.manage` | `op` | 强制管理特殊规则 |
| `coreclaim.admin.claim.manage` | `op` | 强制管理领地删除、归属、server_id、系统领地 |
| `coreclaim.admin.activity.manage` | `op` | 管理活跃值 |
| `coreclaim.admin.reward.givecore` | `op` | 手动发放领地核心 |
| `coreclaim.group.vip` | 无 | 示例 VIP 组别权限 |

## 配置文件

| 文件 | 说明 |
| --- | --- |
| `config.yml` | 主配置：世界、语言、数据库、跨服、同步、清理、核心物品、选区工具等 |
| `groups.yml` | 组别：初始半径、最大半径、领地数量、创建价格、扩建价格 |
| `rules.yml` | 新建普通领地和系统领地的基础权限 / 特殊规则 |
| `lang/zh_cn.yml` | 中文语言 |
| `lang/en_us.yml` | 英文语言 |
| `gui/*.yml` | 全部箱子菜单资源 |

旧版 `messages.yml` 会在启动时迁移到 `lang/zh_cn.yml`。旧版 `flags.yml` 与 `config.yml` 内的旧权限/旗标节点仍会兼容读取，但建议迁移到 `rules.yml`。

## 默认规则摘要

- 普通领地默认拒绝放置、破坏、交互、红石、爆炸、桶、传送、飞行。
- 系统领地默认允许传送，默认拒绝放置、破坏、交互、红石、爆炸、桶、飞行。
- `interact` 统一控制容器、门、活板门、栅栏门、床和普通右键；`redstone` 控制按钮、拉杆、压力板和红石类交互。
- `liquid-flow` 默认拒绝，且不再跟随 `bucket`；时间规则默认 `unset`，即跟随世界时间。
- 新人核心在线奖励默认 `30` 分钟。
- 核心创建领地默认要求核心间隔 `50`，选区创建默认边界间隔 `10`。
- 默认组可拥有 `3` 块领地，VIP 示例组可拥有 `6` 块领地。

## 数据库与跨服

CoreClaim 默认使用 SQLite：`plugins/CoreClaim/coreclaim.db`。

可在 `config.yml` 中将 `database.type` 改为 `mysql`，并配置 MySQL / MariaDB 连接信息。启用 `database.migration.enabled` 时，可把旧 SQLite 数据迁移到空的 MySQL 目标库。

跨服相关配置：

- `server-id`：当前子服标识。
- `cross-server-teleport.enabled`：启用跨服传送流程。
- `claim-sync.enabled`：启用 Redis 领地变更同步。
- `claim-sync.redis.*`：Redis 地址、库、频道和重连时间。

## 文本颜色

所有走 `plugin.color(...)` 的文本都支持：

- 传统颜色：`&6`、`&f`、`&c`、`&l`
- RGB 颜色：`&#55FFAA`
- MiniMessage 风格十六进制：`<#55FFAA>`

示例：

```yml
prefix: '&#64748B[&#A7F3D0领地&#64748B] &#CBD5E1'
claim-created: '&#55FFAA创建成功：&#F8FAFC{name}'
claim-removed: '<#FF6B6B>你的领地已删除'
```

修改语言或菜单后执行：

```text
/claim reload
```

## 构建

需要 Java 17：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat build --no-daemon --console=plain
```

输出：

```text
build/libs/CoreClaim-1.0.jar
```

## 依赖

- `Vault`：创建扣费、扩建扣费等经济相关能力。
- `PlaceholderAPI`：占位符扩展。
- `Redis`：仅在启用 `claim-sync` 时需要。
- `MySQL / MariaDB`：仅在启用 MySQL 存储时需要。

`plugin.yml` 已声明 `folia-supported: true`。
