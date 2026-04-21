# CoreClaim

CoreClaim 是一个面向 `Spigot / Paper / Folia 1.20+` 的领地插件，核心目标是把“新人上手、GUI 管理、跨服兼容、多人环境稳定性”放在第一位。

当前版本已经切到 `/claim` 主命令体系，并额外提供 `lingdi`、`res`、`领地`、`coreclaim`、`cc` 别名。

## 当前特性

- 支持两种创建方式：
  - 使用领地核心创建核心领地
  - 使用普通金锄头选区，再执行 `/claim create <名字>` 创建
- `/claim` 无参数时，玩家默认直接打开领地 GUI
- 领地列表会同时显示：
  - 自己拥有的领地
  - 通过 `/claim add` 直接授权给自己的领地
- 被授权领地在 GUI 中只开放只读查看和传送，不开放配置修改
- 成员 GUI 支持直接添加当前在线玩家
- 容器访问统一由 `flag container` 控制，trusted 成员默认始终可开容器
- 领地内木头支持正常使用斧头去皮
- 支持 `deny`、交互旗标、传送点、转让、系统领地、跨服传送与跨服同步
- 支持长期未上线空地清理机制：
  - 默认关闭
  - 仅清理“无建筑痕迹”或“从未产生有效交互”的旧地
  - 旧领地默认走 `legacy-unknown` 安全模式，不会直接被自动删除
- 支持 PlaceholderAPI
- 支持 Paper RGB 颜色格式

## 当前规则摘要

- 核心创建领地默认最小间距为 `50`
- 金锄头选区创建默认边界间距为 `10`
- 新人核心在线奖励默认时间为 `30` 分钟
- `/claim add`、`/claim unadd`、`/claim deny`、`/claim undeny`、`/claim flag`、`/claim tpset` 现在都支持“站在同一 X/Z 的单一水平命中领地内”直接识别当前领地

## 常用命令

### 玩家命令

- `/claim` 打开领地菜单
- `/claim help` 查看帮助
- `/claim info` 查看脚下领地信息
- `/claim list` 查看自己和被直接授权的领地列表
- `/claim create <领地名>` 用当前选区创建领地
- `/claim tp <领地名>`
- `/claim tpset`
- `/claim add <玩家>`
- `/claim unadd <玩家>`
- `/claim deny <玩家|*>`
- `/claim undeny <玩家|*>`
- `/claim flag [list]`
- `/claim flag <flag> <allow|deny|unset>`
- `/claim transfer <玩家>`
- `/claim remove <领地名>`
- `/claim confirm`

### 管理命令

- `/claim admin create system <领地名>`
- `/claim admin info <领地名|#claimId>`
- `/claim admin diagnose <领地名|#claimId>`
- `/claim admin playerclaims <玩家>`
- `/claim admin add <玩家>`
- `/claim admin unadd <玩家>`
- `/claim admin deny <玩家|*>`
- `/claim admin undeny <玩家|*>`
- `/claim admin permission <permission> <allow|deny>`
- `/claim admin flag <flag> <allow|deny|unset>`
- `/claim admin setserver <claimId> <serverId>`
- `/claim admin cleanup list`
- `/claim admin cleanup run`
- `/claim admin cleanup skip <领地名|#claimId>`
- `/claim admin cleanup baseline <领地名|#claimId> <empty|used|skip>`
- `/claim activity <get|set|add|take> <玩家> [值]`
- `/claim reload`
- `/claim givecore <玩家> [数量]`

更完整的命令与权限说明见 [docs/coreclaim-commands.md](docs/coreclaim-commands.md)。

## 配置重点

主要配置文件：

- [config.yml](src/main/resources/config.yml)
- [groups.yml](src/main/resources/groups.yml)
- [rules.yml](src/main/resources/rules.yml)
- [messages.yml](src/main/resources/messages.yml)

建议关注这些配置项：

- `minimum-gap`: 核心创建领地最小间距，当前默认 `50`
- `selection-minimum-gap`: 选区创建边界间距，当前默认 `10`
- `starter-reward-minutes`: 新人核心发放时间
- `claim-name-max-length`: 领地名长度限制
- `inactive-claim-cleanup.*`: 长期未上线空地清理
- `cross-server-teleport.*`: 跨服传送
- `claim-sync.*`: 多服同步

## Paper RGB 颜色写法

当前所有走 `plugin.color(...)` 的文本都支持十六进制颜色，包括：

- `messages.yml`
- GUI 标题和 Lore
- 物品名
- 进出领地提示
- 全息字文本

支持两种写法：

```yml
prefix: '&#55FFAA[CoreClaim] &f'
claim-created: '&#55FFAA创建成功：&f{name}'
claim-removed: '<#FF6B6B>你的领地已删除'
```

也就是说可以混用：

- 传统颜色：`&6 &f &c &l`
- RGB 颜色：`&#55FFAA`
- MiniMessage 风格十六进制：`<#55FFAA>`

修改 `messages.yml` 后执行：

```text
/claim reload
```

## 构建

```powershell
.\gradlew.bat compileJava
.\gradlew.bat jar
```

输出 jar：

- `build/libs/CoreClaim-1.0.jar`

## 依赖

- `Vault`：经济相关功能
- `PlaceholderAPI`：占位符

插件已在 `plugin.yml` 中声明：

- `folia-supported: true`
