# CoreClaim 命令与权限说明

适用于当前工作区中的 `CoreClaim 1.0`。

## 主命令

- 主命令：`/claim`
- 别名：`/lingdi`、`/coreclaim`、`/cc`

## 玩家命令

当前玩家侧按“当前领地优先”设计，公开入口如下：

| 命令 | 作用 | 权限 |
| --- | --- | --- |
| `/claim` | 查看玩家帮助 | `coreclaim.use` |
| `/claim help` | 查看玩家帮助 | `coreclaim.use` |
| `/claim menu` | 打开领地菜单 | `coreclaim.use` |
| `/claim info` | 查看脚下当前领地详情 | `coreclaim.use` |
| `/claim list` | 查看自己拥有的领地列表 | `coreclaim.use` |
| `/claim create <领地名>` | 用当前选区创建领地 | `coreclaim.use` |
| `/claim remove <领地名>` | 删除自己拥有的领地 | `coreclaim.use` |
| `/claim confirm` | 确认删除领地 | `coreclaim.use` |
| `/claim tp <领地名>` | 传送到有权限进入的领地 | `coreclaim.use` |
| `/claim tpset` | 把脚下位置设为当前领地传送点 | `coreclaim.manage.tpset` |
| `/claim add <玩家>` | 在当前领地添加成员 | `coreclaim.use` |
| `/claim unadd <玩家>` | 在当前领地移除成员 | `coreclaim.use` |
| `/claim deny <玩家>` | 将玩家加入当前领地 deny 列表 | `coreclaim.manage.deny` |
| `/claim deny *` | 开启当前领地封闭模式 | `coreclaim.manage.deny` |
| `/claim undeny <玩家>` | 将玩家从当前领地 deny 列表移除 | `coreclaim.manage.deny` |
| `/claim undeny *` | 关闭当前领地封闭模式 | `coreclaim.manage.deny` |
| `/claim flag` | 查看当前领地交互细则 | `coreclaim.manage.flags` |
| `/claim flag <flag> <allow\|deny\|unset>` | 修改当前领地单个交互细则 | `coreclaim.manage.flags` |
| `/claim transfer ...` | 转让自己的领地 | `coreclaim.transfer` |

说明：

- `remove` 现在只负责删除领地；成员移除统一使用 `unadd`。
- `deny` 现在是纯命令管理项，不再提供 GUI 页面。
- 成员通过 `/claim add <玩家>` 添加后，默认拥有该领地全部成员权限。

## 新手引导

- 玩家首次进入服务器时，会提示“累计在线满 30 分钟可获得第一块领地核心”。
- 在线提醒只在关键剩余时间触发：`20 / 10 / 5 / 1` 分钟。
- 满 30 分钟后会自动发放 1 个新人核心。
- 新人核心创建的是 **默认全格保护领地**。
- 新人核心 **不能丢弃**，并且每个玩家 **只能成功使用一次**。
- 玩家也可以直接拿普通金锄头选区，再使用 `/claim create <领地名>` 创建第一块普通领地。
- 第一块普通领地创建成功后，会提示一次：以后继续用普通金锄头选区创建即可。

## 选区工具

- 普通金锄头就是正式选区工具：
  - 左键方块：设置点 1
  - 右键方块：设置点 2
- 新合成出来的金锄头会自动套用圈地工具外观。
- 玩家背包里原本已有的普通金锄头，不会再被插件强行改名或改 lore。

## 当前 GUI

当前实际启用的 GUI 共 6 个：

| GUI | 作用 |
| --- | --- |
| `core` | 当前领地主菜单 |
| `claim-list` | 我的领地列表 |
| `claim-manage` | 领地扩建与删除 |
| `trust` | 成员列表 |
| `claim-permissions` | 基础权限 + 交互细则合并页 |
| `selection-create` | 选区创建确认页 |

说明：

- `claim-deny.yml`、`claim-deny-online.yml` 已下线。
- `trust-online.yml`、`trust-member-permissions.yml` 已下线。
- `claim-flags.yml` 已下线，功能已经并入 `claim-permissions.yml`。

## 管理员命令

管理员日常修改也以“当前领地优先”为主，远程命令只保留排障和治理用途。

| 命令 | 作用 | 权限 |
| --- | --- | --- |
| `/claim admin create system <领地名>` | 用当前选区创建 `[SYSTEM]` 系统领地 | `coreclaim.admin.create.system` / `coreclaim.admin.force` / `coreclaim.admin` |
| `/claim admin info <领地名\|#claimId>` | 查看指定领地完整详情 | `coreclaim.admin.view` / `coreclaim.admin` |
| `/claim admin diagnose <领地名\|#claimId>` | 查看跨服、TP 路由和规则摘要 | `coreclaim.admin.view` / `coreclaim.admin` |
| `/claim admin playerclaims <玩家>` | 查看玩家名下全部领地 | `coreclaim.admin.view` / `coreclaim.admin` |
| `/claim admin add <玩家>` | 在当前领地强制添加成员 | `coreclaim.admin.member.manage` / `coreclaim.admin.force` / `coreclaim.admin` |
| `/claim admin unadd <玩家>` | 在当前领地强制移除成员 | `coreclaim.admin.member.manage` / `coreclaim.admin.force` / `coreclaim.admin` |
| `/claim admin deny <玩家>` | 在当前领地强制加入 deny | `coreclaim.admin.member.manage` / `coreclaim.admin.force` / `coreclaim.admin` |
| `/claim admin deny *` | 在当前领地强制开启 `deny *` | `coreclaim.admin.member.manage` / `coreclaim.admin.force` / `coreclaim.admin` |
| `/claim admin undeny <玩家>` | 在当前领地强制移除 deny | `coreclaim.admin.member.manage` / `coreclaim.admin.force` / `coreclaim.admin` |
| `/claim admin undeny *` | 在当前领地强制关闭 `deny *` | `coreclaim.admin.member.manage` / `coreclaim.admin.force` / `coreclaim.admin` |
| `/claim admin permission <permission> <allow\|deny>` | 在当前领地强制修改基础权限 | `coreclaim.admin.permission.manage` / `coreclaim.admin.force` / `coreclaim.admin` |
| `/claim admin flag <flag> <allow\|deny\|unset>` | 在当前领地强制修改交互细则 | `coreclaim.admin.flag.manage` / `coreclaim.admin.force` / `coreclaim.admin` |
| `/claim admin setserver <claimId> <serverId>` | 修复或指定领地 `server_id` | `coreclaim.admin.claim.manage` / `coreclaim.admin.force` / `coreclaim.admin` |
| `/claim activity <get\|set\|add\|take> <玩家> [值]` | 管理玩家活跃值 | `coreclaim.admin.activity.manage` / `coreclaim.admin.force` / `coreclaim.admin` |
| `/claim reload` | 重载配置、GUI、缓存与同步服务 | `coreclaim.admin.ops` / `coreclaim.admin` |
| `/claim givecore <玩家> [数量]` | 手动发放领地核心 | `coreclaim.admin.reward.givecore` / `coreclaim.admin.force` / `coreclaim.admin` |

## 权限节点

| 权限 | 说明 | 默认 |
| --- | --- | --- |
| `coreclaim.use` | 使用基础领地命令 | `true` |
| `coreclaim.manage.deny` | 管理当前领地 deny | `true` |
| `coreclaim.manage.tpset` | 设置当前领地传送点 | `true` |
| `coreclaim.manage.flags` | 管理当前领地交互细则 | `true` |
| `coreclaim.transfer` | 转让自己的领地 | `true` |
| `coreclaim.admin.view` | 查看管理员级领地详情 | `op` |
| `coreclaim.admin.force` | 旧兼容：管理员强制修改总权限 | `op` |
| `coreclaim.admin.ops` | 旧兼容：重载等运维总权限 | `op` |
| `coreclaim.admin.create.system` | 创建系统领地 | `op` |
| `coreclaim.admin.member.manage` | 强制管理成员与 deny | `op` |
| `coreclaim.admin.permission.manage` | 强制管理基础权限 | `op` |
| `coreclaim.admin.flag.manage` | 强制管理交互细则 | `op` |
| `coreclaim.admin.claim.manage` | 强制管理 `server_id` 等领地治理操作 | `op` |
| `coreclaim.admin.activity.manage` | 管理活跃值 | `op` |
| `coreclaim.admin.reward.givecore` | 发放领地核心 | `op` |
| `coreclaim.admin` | 管理员全权限 | `op` |

## 说明

- 系统领地不会计入普通配额，也不会出现在普通 `/claim list` 中。
- 新普通领地与新系统领地都会分别套用 `rules.yml` 中对应的默认规则。
- `claim-worlds` 为空时表示所有世界都可以圈地。
- 活跃值系统仍保留展示、占位符和管理员调整能力，但不再作为圈地门槛。
