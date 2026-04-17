# ywzj_vehicle 鼠标飞控调研报告（面向 MCH-Reforged 方案借鉴）

## 1. 调研目标与边界
- 目标：调研 `D:\MCHR\ywzj_vehicle` 的固定翼鼠标飞控实现方法，提炼可迁移思路。
- 重点入口：`FixedWingVehicle.tickMove()`（原作者给定）。
- 边界：仅借鉴方法论，不复制任何源码实现。
- 对比对象：当前项目 `MCH-Reforged` 既有飞机鼠标飞控链路。

## 2. 结论先行
- `ywzj_vehicle` 不是“鼠标直接改舵面”，而是“鼠标视线目标 -> 姿态误差投影 -> 三轴杆量自动生成 -> 气动模型驱动旋转”。
- 其飞控特点是“目标导向 + 气动约束 + 自动协调（含滚转倾向与回正）”，不是纯 PID 双环。
- 对 `MCH-Reforged` 的最大借鉴价值不在公式细节，而在“控制架构分层”：
  - 输入层（视线/目标）与操纵层（pitch/yaw/roll）解耦；
  - 操纵层与动力学层解耦；
  - UI 准星基于真实视线和弹道落点分离渲染。

## 3. 关键实现链路（ywzj_vehicle）

### 3.1 输入采集与控制状态
- 客户端每 tick 组装 `ControlUnit`，把键盘状态与视角角度送入 `xRot/yRot`。
- 固定翼模式下 `xRot` 会做一个上抬偏置：`playerXRot - CAMERA_UPWARD_ANGLE`（10 度）。
- 控制包 `ClientVehicleMoveControl` 发到服务端，服务端写入实体 `controlUnit`。

对应代码：
- `InputHandler.checkKey()`：`src/main/java/org/ywzj/vehicle/vehicle/control/InputHandler.java`
- `ControlUnit`：`src/main/java/org/ywzj/vehicle/vehicle/control/ControlUnit.java`
- `ClientVehicleMoveControl`：`src/main/java/org/ywzj/vehicle/network/message/ClientVehicleMoveControl.java`

### 3.2 tickMove 的飞控核心（FixedWingVehicle）
- 先取机体三轴：前/上/左方向向量（由 OBB 姿态得到）。
- 读取当前三轴输入杆量：`pitchInput/yawInput/rollInput`，并叠加键盘输入。
- 计算“鼠标瞄准方向向量” `aimVec`（由 `controlUnit.xRot/yRot` 转向量）。
- 自动控制逻辑（在无对应手动输入时）：
  - 俯仰：`aimVec` 与机体上轴投影误差 -> `xRotInput`
  - 偏航：`aimVec` 与机体左轴投影误差 -> `yRotInput`
  - 滚转：
    - 大偏差时向目标侧倾（bank toward target）
    - 偏差小时保持当前滚转
    - 接近对正时自动回正
- 后续进入空气动力学计算：
  - 推力、阻力、升力、尾舵力、操控面附加阻力；
  - 随速度缩放转向效率；
  - 再按三轴输入更新四元数姿态。

对应代码：
- `src/main/java/org/ywzj/vehicle/entity/vehicle/FixedWingVehicle.java` 的 `tickMove()`

### 3.3 相机与准星（绿圈）链路
- `LocalVehiclePlayer` 维护本地相机姿态、自由视角、第三人称/操作员/瞄具视图切换。
- `VehicleCrossHairOverlay` 每 tick 计算两类屏幕点：
  - `screenAim`：视线命中点（用于“瞄准圈”）
  - `screenHit`：武器真实瞄准落点（用于“命中/火控圈”）
- 两者分离渲染，且都有插值，避免跳变。

对应代码：
- `src/main/java/org/ywzj/vehicle/vehicle/LocalVehiclePlayer.java`
- `src/main/java/org/ywzj/vehicle/client/gui/VehicleCrossHairOverlay.java`

## 4. 与 MCH-Reforged 的主要差异

### 4.1 控制结构差异
- `ywzj_vehicle`：目标向量驱动三轴输入，再进入气动模型。
- `MCH-Reforged` 当前：更偏“姿态增量控制 + 参数限幅 + 状态阻尼”，气动抽象较简化。

### 4.2 数据流差异
- `ywzj_vehicle`：控制量通过 `ControlUnit` 统一同步，实体 tick 内统一处理。
- `MCH-Reforged`：鼠标增量与杆量在客户端 tick 中计算，再进入 `setAngles` 链路。

### 4.3 UI 差异
- `ywzj_vehicle`：瞄准圈和命中圈是“真实世界点投影”的两条独立链路。
- `MCH-Reforged`：现阶段双圈已有，但部分状态下仍偏“控制状态派生”，不完全等价于真实命中点。

## 5. 可直接借鉴的方法（不复用代码）
- 借鉴 1：把“目标方向误差”映射为三轴输入，而不是直接映射舵面。
- 借鉴 2：滚转策略采用三态机：
  - 对正前：回正；
  - 中误差：保持；
  - 大误差：朝目标方向快速倾侧。
- 借鉴 3：引入“速度影响转向效率”因子，避免低速/高速同手感。
- 借鉴 4：把 UI 分成“视线圈”和“落点圈”，并给两者独立插值。
- 借鉴 5：将视角模式（第三人称/自由视角/瞄具）显式纳入控制判定，避免状态冲突。

## 6. 不建议照搬的部分
- 不建议照搬其气动参数和力学公式，项目版本与物理抽象层不同。
- 不建议照搬其控制包结构，网络协议与实体系统差异大。
- 不建议照搬其相机系统（第三人称碰撞修正、scope 链路）实现细节。

## 7. 面向 MCH-Reforged 的落地建议

### 7.1 最小改造版（1~2 天）
- 在现有 WT 控制里，把“横向误差 -> roll 目标”做成显式三态逻辑（回正/保持/倾侧）。
- 将 yaw 权重与 bank 角绑定，bank 大时自动减小 yaw 占比。
- 绿圈改为“目标点圈”，白圈保留“机头圈”，两者都做可视插值。

### 7.2 中期版（3~5 天）
- 引入“速度影响控制效率”参数层（而非直接固定倍率）。
- 增加“视线命中点圈”和“机头圈”双链路并可切换显示。
- debug 输出增加：
  - 目标向量误差；
  - bank 状态机状态；
  - yaw/pitch/roll 实际贡献占比。

### 7.3 长期版（>1 周）
- 若要接近高版本项目手感，建议建立轻量气动模块（升力/阻力/迎角曲线）并与输入控制解耦。

## 8. 风险与注意事项
- 仅强化滚转不一定提升体感，若 yaw 仍过强会继续“平偏航”。
- 目标点回中策略必须“快响应、慢回归”，否则会出现“圈钉中心/卡手”。
- 视角链路与控制链路要同条件门控，避免“显示已激活但控制未激活”的错觉。

## 9. 结论
- `ywzj_vehicle` 的核心价值是“目标向量驱动 + 自动滚转策略 + 气动约束”这套架构思想。
- 对 `MCH-Reforged` 最实用的迁移路径是：先迁移控制分层与滚转状态机，再逐步补速度因子和双圈语义分离。
- 在不互通代码前提下，上述方法可独立实现且风险可控。
