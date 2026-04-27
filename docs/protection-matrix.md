# CoreClaim 保护规则矩阵

本矩阵用于审计保护监听的权限边界。后续修改监听器时，先更新这里，再对照源码和测试补齐行为。

| 场景 | 主要事件/入口 | 需要权限或旗标 | 拦截方式 | 活跃值记录 |
| --- | --- | --- | --- | --- |
| 破坏普通方块 | `BlockBreakEvent` | `BREAK`；领地核心只能主人或强制管理员处理 | `setCancelled(true)` | 成功时记录建造活跃 |
| 放置普通方块 | `BlockPlaceEvent` | `PLACE`；领地核心物品走创建流程豁免 | `setCancelled(true)` | 成功时记录建造活跃 |
| 桶倒出/取水 | `PlayerBucketEmptyEvent` / `PlayerBucketFillEvent` | `BUCKET` | `setCancelled(true)` | 成功时记录建造活跃 |
| 原木去皮 | `PlayerInteractEvent` 右键方块 | `INTERACT` | 无权限时取消事件 | 成功时记录交互活跃 |
| 铜块除锈/去蜡 | `PlayerInteractEvent` 右键方块 | `BREAK` | 无权限时取消事件；铜类容器额外区分工具使用和打开容器 | 成功时记录建造活跃 |
| 铲子路径化 | `PlayerInteractEvent` 右键方块 | `BREAK` | 无权限时取消事件 | 成功时记录建造活跃 |
| 蜂巢、蛋糕、蜡烛、堆肥桶、炼药锅等右键状态变化 | `PlayerInteractEvent` 右键方块 | `INTERACT` | 无权限时取消事件 | 成功时记录交互活跃 |
| 容器打开 | `PlayerInteractEvent` / 实体交互 | `CONTAINER` 旗标权限 | `setCancelled(true)`；工具动作可仅拒绝物品或方块使用 | 成功时记录交互活跃 |
| 红石/特殊交互 | `PlayerInteractEvent` / `ProjectileHitEvent` | `REDSTONE` 或 `INTERACT`，取决于 `strict-redstone-interact` 与 `always-protected-interact` | `setCancelled(true)`，投掷物会移除实体 | 成功时记录交互活跃 |
| 床、重生锚等特殊爆炸触发 | `PlayerInteractEvent` | `EXPLOSION` | 无权限时取消事件；有权限时登记爆炸授权位置 | 成功时记录交互活跃 |
| 盔甲架、命名牌、实体容器 | `PlayerInteractEntityEvent` / `PlayerArmorStandManipulateEvent` | 盔甲架和命名牌为 `BREAK`，实体容器为 `CONTAINER`，其他为 `INTERACT` | `setCancelled(true)` | `BREAK` 记录建造活跃，其他记录交互活跃 |
| 栓绳、钓鱼竿实体交互 | `PlayerLeashEntityEvent` / `PlayerUnleashEntityEvent` / `PlayerFishEvent` | `INTERACT` 或目标实体推导权限 | `setCancelled(true)`，钓鱼钩可移除 | 成功时记录交互活跃 |
| 投掷物触发方块或实体 | `ProjectileHitEvent` / 药水/滞留药水/区域效果云 | 爆炸型投掷物为 `EXPLOSION`，其他危险投掷物为 `BREAK`，敏感红石块为 `INTERACT` | 取消事件、移除投掷物、或清空药水影响 | 不记录活跃值 |
| 爆炸破坏方块 | `ExplosionPrimeEvent` / `EntityExplodeEvent` | 来源玩家需要 `EXPLOSION`；无来源默认不破坏领地方块 | 取消爆炸或移除 blockList 中的领地方块 | 不记录活跃值 |
| 村民收割/补种作物 | `EntityChangeBlockEvent` | 不需要玩家权限；仅限村民对小麦、胡萝卜、土豆、甜菜的收割和补种变化 | 直接放行；其他实体改方块仍按环境保护取消 | 不记录活跃值 |
| 玩家坐骑跨界、珍珠/紫颂果/传送门进入 | `PlayerMoveEvent` / `PlayerTeleportEvent` / `PlayerPortalEvent` | `TELEPORT`，可由配置项放行对应入口 | 回退位置或取消传送 | 不记录活跃值 |
| 载具跨界 | `VehicleMoveEvent` | 车上至少一名可解析玩家拥有 `TELEPORT` 或强制绕过 | 传回原位置并清速度 | 不记录活跃值 |

通用规则：

- `coreclaim.admin.force` 和 `coreclaim.admin` 可绕过保护判断。
- `coreclaim.admin.view` 只允许查看，不允许写入领地数据。
- GUI、命令和聊天输入的写入入口必须在提交时再次检查对应写权限。
- 容器打开和工具右键要分开处理，避免为了允许铜块除锈或铲子路径化而误放开容器权限。
