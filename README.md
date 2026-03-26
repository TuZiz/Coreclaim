# CoreClaim

一个同时兼容 Spigot 和 Folia 的轻量级领地插件，玩法核心如下：

- 首个领地核心在玩家累计在线 `30` 分钟后自动发放
- 玩家把 `领地核心` 放到地面后，会先出现悬浮字并提示在聊天栏输入领地名字
- 输入领地名字后才会正式创建领地，领地默认保护整列高度
- 领地数量上限由活跃度决定，活跃度由管理员手动发放
- 额外领地核心可通过金币购买
- 领地扩张按新增面积收取金币
- `100x100` 范围内只允许存在一个领地核心
- 领地主人可以把其他玩家加入信任列表
- 领地核心默认材质为 `紫水晶簇`
- 提供两个 GUI：
  - `/coreclaim menu` 打开的总览菜单
  - 右键地面上的领地核心打开的核心信息/设置菜单
- 核心菜单里的“移除核心展示”只会删除地面核心和全息字，不会删除领地
- 玩家进出领地时会收到 `actionbar` 提示
- 提供 PlaceholderAPI 变量
- 数据持久化使用内置 SQLite 数据库
- 非领地主人无法在领地内放置、破坏、交互、倒水倒岩浆或破坏生物/展示实体

## 命令

- `/coreclaim info` 查看当前活跃度、在线时长和脚下领地
- `/coreclaim list` 查看自己的所有领地
- `/coreclaim menu` 打开图形菜单
- `/coreclaim buycore` 购买额外领地核心
- `/coreclaim expand <格数>` 扩大脚下领地
- `/coreclaim unclaim` 删除脚下领地
- `/coreclaim trust <玩家>` 信任脚下领地中的玩家
- `/coreclaim untrust <玩家>` 移除脚下领地中的信任玩家
- `/coreclaim activity <get|set|add|take> <玩家> [值]` 管理员调整活跃度
- `/coreclaim givecore <玩家> [数量]` 管理员直接发放领地核心

## 配置

主要配置位于 [config.yml](/D:/codex/Coreclaim/src/main/resources/config.yml)：

- `starter-reward-minutes` 首个领地核心发放所需在线分钟
- `starter-claim-radius` 初始领地半径
- `minimum-core-spacing` 领地核心最小间距
- `max-claim-radius` 最大领地半径
- `claim-name-max-length` 领地名字最大长度
- `database.file` SQLite 数据库文件名
- `economy.additional-core-price` 额外领地核心价格
- `economy.expand-price-per-block` 扩张新增面积的单格价格
- `activity-slots` 活跃度与可拥有领地数量的映射
- `show-enter-leave-messages` 是否启用进入/离开领地提示

## PlaceholderAPI

已提供以下变量：

- `%coreclaim_activity%`
- `%coreclaim_online_minutes%`
- `%coreclaim_claim_count%`
- `%coreclaim_claim_limit%`
- `%coreclaim_starter_core_granted%`
- `%coreclaim_next_reward_minutes%`
- `%coreclaim_current_claim_id%`
- `%coreclaim_current_claim_name%`
- `%coreclaim_current_claim_owner%`
- `%coreclaim_current_claim_radius%`
- `%coreclaim_current_claim_trusted_count%`

## 依赖

- 经济系统通过 Vault 对接，因此扩地和购买核心需要服务器安装 `Vault + 一个经济插件`
- PlaceholderAPI 变量需要服务器安装 `PlaceholderAPI`
- 插件已在 `plugin.yml` 中声明 `folia-supported: true`
