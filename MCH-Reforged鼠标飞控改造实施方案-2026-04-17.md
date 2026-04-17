# MCH-Reforged 鼠标飞控改造实施方案（含 Debug 指令）

## 1. 目标与约束
- 目标：在当前 `MCH-Reforged` 架构下实现稳定、可调、可验证的 WT 风格鼠标飞控。
- 约束：
  - 仅在 `thirdPersonView = 1` 且驾驶位启用。
  - 不复用 `ywzj_vehicle` 代码，只借鉴方法。
  - 优先兼容现有网络与实体更新链路，避免大规模协议改造。

## 2. 总体技术路线
- 路线：`目标点驱动 -> 三轴控制分配 -> Bank-To-Turn 优先 -> 轻量速度因子 -> 双圈可视化`。
- 核心原则：
  - 鼠标输入不直接控制舵面，先更新“目标点”。
  - 横向机动优先转化为滚转与俯仰，不走纯偏航。
  - 显示链路与控制链路同条件门控，避免“看起来启用但实际未启用”。

## 3. 代码实施方案

### 阶段 0：链路校正（先消除现有 Bug）
- 当前状态：已完成（2026-04-17）
- 目标：
  - 统一 WT 激活条件与双圈渲染条件。
  - 保证 debug 开关开启后一定有可见日志。
- 改动点：
  - `src/main/java/mcheli/plane/MCP_GuiPlane.java`
    - `drawMouseAimCircles()` 仅在 `plane.isWTMouseAimActive()` 时绘制。
  - `src/main/java/mcheli/MCH_MouseAimDebug.java`
    - `getLogPath()` 返回绝对路径。
  - `src/main/java/mcheli/command/MCH_Command.java`
    - `/mcheli debug mouseaim true` 时立即写一条启动日志。
- 验收：
  - WT 未激活时不画绿圈。
  - 开启 debug 后可立即在日志文件看到“开启记录”。

### 阶段 1：控制律重构（主手感改造）
- 当前状态：进行中（核心控制律已落地，剩余联调与参数收敛）
- 目标：
  - 形成“快启动、慢回归”的目标点控制。
  - 实现 Bank-To-Turn（滚转优先）机动。
- 改动点：
  - `src/main/java/mcheli/plane/MCP_EntityPlane.java`
    - `updateWTMouseAimControl(...)`
      - 鼠标增量先更新 `wtAimTargetX/Y`（快启动）。
      - `wtAimTargetX/Y` 低速回中（慢回归）。
      - 由目标点生成 `desiredRoll`（70~90 动态区间）。
      - 根据当前 bank 角动态衰减 yaw 权重。
      - bank 成形后增加 pitch assist（减少平偏航）。
      - 新增手感档位：`normal/aggressive`（通过服务端设置同步到客户端）。
  - `src/main/java/mcheli/aircraft/MCH_EntityAircraft.java`
    - 保持 `setAngles()` 主流程不变，仅复用现有限幅与姿态更新。
- 验收：
  - 左右大机动时优先滚转，再由俯仰完成转向。
  - 绿圈可快速拉出，不会立即回到中心。

### 阶段 2：速度因子与保护（稳定性改造）
- 当前状态：进行中（速度因子与低速保护第一版已接入）
- 目标：
  - 降低低速失控与高速过敏。
- 改动点：
  - `src/main/java/mcheli/plane/MCP_EntityPlane.java`
    - 增加速度因子：`gainBySpeed` 作用于 yaw/pitch/roll 权重。
    - 增加低速保护：低速时俯仰/滚转限幅收缩。
    - 保留 VTOL 状态下的额外抑制。
    - debug 增加保护量观测：`speedNorm/inputDamp/lowSpeedProtect/rollLimitBySpeed`。
- 验收：
  - 低速不易过拉失速；高速不抖动、不抢杆。

### 阶段 3：双圈语义分离（可视化改造）
- 当前状态：进行中（绿圈/白圈语义分离已落地，双圈独立插值已接入）
- 目标：
  - 绿圈表达“控制目标”，白圈表达“机头当前指向”。
- 改动点：
  - `src/main/java/mcheli/plane/MCP_GuiPlane.java`
    - 绿圈：使用 `wtAimTargetX/Y` 投影并插值。
    - 白圈：机头前向投影点，独立插值。
    - 可选：增加“视线/命中圈”扩展位（已埋占位开关，默认关闭）。
- 验收：
  - 玩家能明确区分“我想去哪”和“机头现在在哪”。

## 4. 实施顺序与回滚点
- 顺序建议：`阶段0 -> 阶段1 -> 阶段2 -> 阶段3`。
- 每阶段结束即打一个可回滚节点（本地 commit/tag）。
- 如果阶段 1 手感不达标，先不进入阶段 2，避免叠加变量干扰调参。

## 5. Debug 指令（现有可用）
- 开启鼠标飞控调试日志：
  - `/mcheli debug mouseaim true`
- 关闭鼠标飞控调试日志：
  - `/mcheli debug mouseaim false`
- 查询鼠标飞控激活状态：
  - `/mcheli debug mouseaim status`
- 查询当前飞机快照（姿态/速度/mobility）：
  - `/mcheli debug mouseaim snapshot`
- 设置鼠标飞控手感档位：
  - `/mcheli debug mouseaimprofile normal`
  - `/mcheli debug mouseaimprofile aggressive`
- 设置扩展圈占位开关（默认关闭）：
  - `/mcheli debug mouseaimext true`
  - `/mcheli debug mouseaimext false`
- 开启自由视角调试（辅助排查输入链路）：
  - `/mcheli debug freelook true`
- 开启航点导航调试（可排除 AI 行为干扰）：
  - `/mcheli debug waypointnav true`

## 6. Debug 日志定位
- 鼠标飞控日志文件：
  - `logs/mcheli_mouseaim_debug.log`
- 建议通过命令回显中的 `log:` 绝对路径直接打开。

## 7. Debug 指令说明
- `/mcheli debug mouseaim status`
  - 用途：快速确认“为什么没激活”。
  - 当前输出：debug 开关、总开关、profile、`thirdPersonView`、是否在飞机上、是否驾驶位。
- `/mcheli debug mouseaim snapshot`
  - 用途：查看当前飞行状态，辅助联调。
  - 当前输出：`isWTMouseAimActive()`、`yaw/pitch/roll`、速度、油门、mobility。
- `/mcheli debug mouseaimprofile <normal|aggressive>`
  - 用途：实时切换手感档位，不改配置即可对比。

## 8. 联调验收清单
- 场景 A：第一人称
  - 不启用鼠标飞控，不显示双圈。
- 场景 B：第三人称后视（`thirdPersonView = 1`）
  - 启用鼠标飞控，双圈显示正常，绿圈可移动且慢回中。
- 场景 C：第三人称前视（`thirdPersonView = 2`）
  - 不启用鼠标飞控，不显示双圈。
- 场景 D：关闭总开关
  - 任意视角都不启用、不显示。
- 场景 E：左右大机动
  - 体感应表现为“先滚转再转向”，而非平偏航主导。

## 9. 最终说明
- 本方案是“当前架构可落地”的工程化版本，优先稳定与可验证。
- 若后续追求更高真实度，可在此基础上增加轻量气动模块，但不建议与当前阶段并行推进。
