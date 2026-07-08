**CloudFlare Assistant v7.6.1 更新日志**
========================================

## **1. 📱 重构：设备管理详情展示**

全面重构设备列表与设备详情页，显示每个设备的所有详情字段，并新增设备删除能力。

**功能特性**

| 功能 | 说明 |
|------|------|
| **设备删除** | 替换原更新设备策略接口为删除设备接口，支持从组织中移除设备 |
| **详情字段扩展** | 设备详情弹窗大幅扩展，展示设备完整信息 |
| **活跃状态标识** | 新增「最后活跃用户」Chip 标识（`activeRegChip`） |
| **删除状态提示** | 已删除设备以红色文本显示删除时间 |
| **配置文件信息** | 设备详情展示当前应用的设备配置文件（名称、是否默认、是否已删除、更新时间） |

**设备详情新增字段**

- 系统版本附加（`osVersionExtra`）
- 硬件 ID（`hardwareId`）
- 客户端版本（`clientVersion`）
- 设备 ID（`deviceId`）
- 最后活跃时间（`lastSeenAt`）
- 删除时间（`deletedAt`，已删除设备显示）
- 当前设备配置文件信息（名称、默认标识、删除标识、更新时间）

**调用 API**
- `DELETE /accounts/{account_id}/devices/{device_id}` — 删除设备

***

## **2. 🗂️ 新增：设备配置文件（自定义 + 默认）**

将原「设备策略」全局重构为「设备配置文件」，完整实现默认配置文件与自定义配置文件的创建、编辑、删除和分配功能。

### 默认配置文件
- 应用于所有未匹配其他配置文件的设备
- 使用专用接口：`GET/PATCH /accounts/{account_id}/devices/policy`（无 `policy_id`）
- 不包含 name / match / precedence / description 字段
- 列表置顶显示「默认配置文件」标识，不可删除
- 编辑弹窗自动隐藏 name / match / precedence / enabled 字段

### 自定义配置文件
- 使用专用接口：`GET/PATCH /accounts/{account_id}/devices/policy/{policy_id}`
- 必填 name 和 match 字段
- 支持创建、编辑、删除完整生命周期

### 配置文件完整字段

| 配置项 | 字段 |
|--------|------|
| 强制网络门户检测 | `captivePortal` |
| 模式切换 | `allowModeSwitch` |
| 设备隧道协议 | `tunnelProtocol` |
| 锁定设备客户端开关 | `switchLocked` |
| 允许设备离开组织 | `allowedToLeave` |
| 允许更新 | `allowUpdates` |
| 自动连接 | `autoConnect` |
| 支持 URL | `supportUrl` |
| 服务模式 | `serviceModeV2` |
| 拆分隧道（排除） | `exclude` |
| 拆分隧道（包含） | `include` |
| 直接路由 Microsoft 365 流量 | `excludeOfficeIps` |
| 允许用户以启用本地网络排除 | `allowLocalNetworkExclude` |
| 设备客户端接口 IP DNS 注册 | `registerInterfaceIpWithDns` |
| SCCM VPN 边界支持 | `sccmVpnBoundarySupport` |
| 开启 NETBT | `enableNetbt` |
| 网关 ID | `gatewayUniqueId` |
| 启用配置文件 | `enabled` |

**调用 API**
- `GET /accounts/{account_id}/devices/policy` — 默认配置文件
- `PATCH /accounts/{account_id}/devices/policy` — 更新默认配置文件
- `GET /accounts/{account_id}/devices/policy/list` — 列出自定义配置文件
- `POST /accounts/{account_id}/devices/policy` — 创建自定义配置文件
- `PATCH /accounts/{account_id}/devices/policy/{policy_id}` — 更新自定义配置文件
- `DELETE /accounts/{account_id}/devices/policy/{policy_id}` — 删除自定义配置文件
- `PUT /accounts/{account_id}/devices/policy/{policy_id}/split_tunnel` — 拆分隧道配置

***

## **3. 🚇 新增：拆分隧道（Split Tunnel）**

为设备配置文件实现完整的拆分隧道功能，支持排除和包含两种模式。

**功能特性**

| 功能 | 说明 |
|------|------|
| **双模式** | 支持排除（exclude）和包含（include）两种模式，互斥选择 |
| **多行输入** | 每行一个地址，支持 IP CIDR 和域名格式 |
| **IP CIDR 支持** | 自动识别 IPv4 / IPv6 CIDR 格式（如 `10.0.0.0/8`、`fe80::/10`） |
| **域名支持** | 自动识别域名格式（如 `example.com`） |
| **地址分离序列化** | IP CIDR 写入 `address` 字段，域名写入 `host` 字段 |
| **默认配置文件兼容** | 默认配置文件和自定义配置文件均支持拆分隧道编辑 |
| **回填展示** | 编辑时自动从 API 拉取并回填已有地址到对应输入框 |

**调用 API**：`PUT /accounts/{account_id}/devices/policy/{policy_id}/split_tunnel`

***

## **4. 🎯 新增：可视化匹配表达式构建器**

重构设备配置文件创建/编辑弹窗的匹配规则输入模块，新增可视化匹配表达式构建器。

**功能特性**

- 通过下拉选择 + 输入框快速构建匹配表达式
- 支持邮箱地址简写：直接输入邮箱自动转换为 `identity.email == "email"` 格式
- 保留高级编辑模式，支持直接编辑原始表达式
- 新增 Spinner 边框样式（`bg_spinner_border.xml`）
- 新增左对齐 Spinner 选项布局（`spinner_item_left.xml`）
- 芯片背景属性优化

***

## **5. 🛠️ 重构：配置文件序列化与数据模型**

### 新增 GSON 适配器
- `DeviceSettingsPolicyRequestAdapter` — 设备配置文件请求序列化
- `DevicePolicyUpdateAdapter` — 配置文件更新数据序列化
- 仅序列化非空字段，避免 Cloudflare API 因 null 字段返回错误

### 数据模型完善
- `DevicePolicy` 新增 `default` 标记和 `lanAllowMinutes` 字段
- `DevicePolicyUpdate` 包含全部支持字段
- 排除 API 不支持字段：`gateway_unique_id`、`m365_direct_routing`

### 接口修复
- 修复接口地址错误
- 修复 GSON 解析问题
- 修复 Split Tunnel 默认配置问题
- 修复 IPv6 地址作为域名导致的 2049 错误
- 修复请求包含无效字段导致的 2004 错误

***

## **6. 📦 新增：Pages 项目创建**

Pages 页面新增创建项目功能。

**功能特性**
- 新增创建项目弹窗（`dialog_pages_create_project.xml`）
- 扩展项目创建接口参数，新增构建配置相关字段
- 添加创建项目按钮
- 「路由/域名」文本改为「路由/自定义域」
- 优化部署列表加载逻辑

**调用 API**：`POST /accounts/{account_id}/pages/projects`

***

## **7. 🔧 修复：Pages 部署 _worker.js 识别**

修复 zip 包内根目录外的 `_worker.js` 被误识别为 worker 部署的问题，避免普通静态资产被错误处理。

***

## **8. 🔄 新增：应用内更新检查**

关于对话框新增应用内更新检查功能。

**功能特性**
- 新增「检查更新」按钮与加载状态
- 动态显示版权年份
- 实现应用内更新检查逻辑

***

## **9. 🎨 优化：UI 文字按钮替换**

将多个列表项中的图标按钮替换为文字按钮，统一视觉风格并优化布局结构。

**涉及列表**
- Access 组列表（`item_access_group.xml`）
- Access 策略列表（`item_access_policy.xml`）
- 设备配置文件列表（`item_device_policy.xml`）
- 隧道列表（`item_tunnel.xml`）

**优化内容**
- 图标按钮改为文字按钮（如「编辑」「删除」）
- 优化布局间距与对齐
- AccessDetailFragment 适配新交互

***

**完整提交范围**：`89cd032` ~ `97db1a3`
