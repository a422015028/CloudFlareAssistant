**CloudFlare Assistant v7.6 更新日志**
========================================

发布日期：2026-07-07

***

## **1. 🚀 新增：Pages 自定义域名管理**

在 Pages 项目列表中新增域名管理功能，支持查看、添加、删除自定义域名，并自动配置 DNS 验证记录。

**功能特性**

| 功能 | 说明 |
|------|------|
| **查看域名** | 每个项目可查看完整域名列表，含预览域名（`*.pages.dev`）和自定义域名 |
| **添加域名** | 一键添加自定义域名，添加后自动配置 DNS 记录，无需手动操作 |
| **删除域名** | 自定义域名右侧显示删除按钮，支持快速删除 |
| **状态展示** | 域名状态彩色标识：active（绿）、pending（橙）、error（红）、deactivated（灰） |
| **一键复制** | 点击域名自动复制完整 URL（含 `https://` 前缀） |
| **DNS 自动配置** | 根据验证方式（TXT / CNAME）自动创建对应的 DNS 解析记录 |

**调用 API**
- `GET /accounts/{account_id}/pages/projects/{project_name}/domains` — 列出域名
- `POST /accounts/{account_id}/pages/projects/{project_name}/domains` — 添加域名
- `DELETE /accounts/{account_id}/pages/projects/{project_name}/domains/{domain_name}` — 删除域名
- `POST /zones/{zone_id}/dns_records` — 自动添加 DNS 验证记录

***

## **2. 📋 新增：Pages 部署日志查看**

Pages 项目列表新增「日志」按钮，可查看指定部署的完整构建日志。

**功能特性**
- 支持按部署 ID 选择查看对应日志
- 日志内容可滚动浏览，支持长文本显示
- 构建失败时可快速定位错误原因

**调用 API**：`GET /accounts/{account_id}/pages/projects/{project_name}/deployments/{deployment_id}/history/logs`

***

## **3. 🔄 新增：Pages 部署重试（重新部署）**

部署记录列表的删除按钮改为「重新部署」按钮，支持基于已有部署配置重新发起一次构建。

- 仅对源码构建（source-built）的部署显示，Direct Upload 等无源码部署不显示
- 调用 API：`POST /accounts/{account_id}/pages/projects/{project_name}/deployments/{deployment_id}/retry`

***

## **4. 📊 增强：Pages 部署详情**

部署详情弹窗大幅扩展，新增以下字段展示：

- **环境变量**（`env_vars`）
- **部署阶段信息**（`stages` / `latest_stage`）— 含阶段名称、状态、开始/结束时间
- **构建配置**（`build_config`）— 构建命令、输出目录、根目录等
- **源码配置**（`source.config`）— 源码来源、分支信息等

***

## **5. 📦 增强：Pages 部署能力扩展**

### 分段资产上传
将部署流程拆分为「获取上传 Token → 批量上传资产 → 提交 Manifest 清单」三步，降低大文件请求超时风险。

### _worker.js 高级部署
支持上传包含 `_worker.js` 的高级部署模式，自动构建符合 Cloudflare 规范的 worker 部署包。

### 单文件部署
新增 `.js` 和 `.html` 单文件部署支持：
- 单 JS 文件 → 以 `_worker.js` 方式部署
- 单 HTML 文件 → 静态资产部署

### 其他优化
- 部署完成后自动清理临时生成的 zip 文件
- 文件哈希计算替换为 CF Hash（Base64 + 扩展名 → SHA256 截断）

***

## **6. 📜 新增：Worker 实时日志（Tail Log）**

Worker 脚本列表新增「日志」入口，支持通过 WebSocket 实时查看 Worker 运行日志。

**功能特性**

| 功能 | 说明 |
|------|------|
| **实时流式** | WebSocket 连接，日志实时推送 |
| **暂停/继续** | 可暂停日志滚动，方便排查 |
| **清空日志** | 一键清空当前日志缓冲区 |
| **全选复制** | 支持全选并复制全部日志内容 |
| **自动重连** | 连接断开后 5 秒自动尝试重连 |
| **连接状态** | 彩色状态点标识：🟡连接中 / 🟢已连接 / 🔴断开 |

**调用 API**
- `POST /accounts/{account_id}/workers/scripts/{script_name}/tail` — 创建 tail 会话
- `DELETE /accounts/{account_id}/workers/scripts/{script_name}/tail` — 删除 tail 会话
- WebSocket：`wss://tail.deployments.workers.cloudflare.com/...`（`trace-v1` 协议）

***

## **7. ⏰ 新增：Worker 触发器管理**

Worker 脚本列表新增「触发器」入口，支持管理 Cron 触发器。

- 查看当前脚本所有 Cron 触发器
- 添加新的 Cron 触发器（定时表达式 + 说明）
- 删除已有触发器

**调用 API**
- `GET /accounts/{account_id}/workers/scripts/{script_name}/schedules` — 列出触发器
- `PUT /accounts/{account_id}/workers/scripts/{script_name}/schedules` — 更新触发器
- `DELETE /accounts/{account_id}/workers/scripts/{script_name}/schedules/{schedule_id}` — 删除触发器

***

## **8. 📝 新增：Worker / Pages 部署版本历史**

### Worker 版本历史
- 查看脚本所有版本记录（版本号、创建时间、修改者等）
- 版本详情弹窗展示元数据和部署信息
- 支持回滚到指定版本

### Pages 部署记录
- 查看项目所有部署记录
- 展示部署 ID、环境（生产/预览）、创建时间
- 支持查看部署详情、日志和重新部署

***

## **9. 🛡️ 增强：Worker 部署管理**

新增 Worker 部署记录查询 API 与数据模型，优化页面部署交互：
- Worker 版本详情新增部署信息展示模块
- Pages 部署列表的删除按钮改为重试按钮

***

## **10. 🔒 新增：Zero Trust 网关管理**

### 网关策略（Gateway Rules）
- 支持创建、编辑、删除网关策略
- 支持多种规则类型：DNS、HTTP、L4、Network
- 丰富的操作动作：Allow、Block、Override、SafeSearch、YoutubeRestricted 等
- 支持流量表达式（regex `matches` 语法）
- 规则列表支持状态筛选和统计

### 网关列表（Gateway Lists）
- 支持创建、编辑、删除网关列表
- 支持列表类型：IP、域名、邮件等
- 域名项自动去除通配符（`*`、`.*`、`.*.`）
- 快速模板与输入示例提示

### 网关位置（Gateway Locations）
- 支持创建、编辑、删除网关位置
- 展示客户端数量、默认位置标识、EDNS 支持状态
- DNS 地址一键复制（IPv4 / IPv6 / DoH）
- 默认位置防误删保护
- CIDR 网络验证（/24 ~ /32 前缀，禁止私有/保留/组播地址）
- 网络地址标准化处理

***

## **11. ✏️ 优化：Zero Trust 访问策略与分组**

### 请求参数重构
- `AccessPolicyRequest` 和 `AccessGroupRequest` 使用 `emptyList()` 替代 `null`
- 新增 `AccessRuleAdapter` 自定义 Gson 序列化器，仅序列化非空字段
- 避免 Cloudflare API 因 null 字段返回 10001 错误

### Access 组管理优化
- 组列表新增「默认组」标签标识
- 新增编辑按钮与中文规则标签（包含规则 / 排除规则 / 必须规则）
- 创建/编辑对话框新增默认组开关
- 静默加载（无 loading 动画），编辑后自动刷新

***

## **12. 🎨 重构：首页布局与导航**

- 功能卡片改为 GridLayout 两列网格布局，更紧凑
- 账号管理从首页卡片移至底部导航栏最左侧
- 首页底部新增「关于」卡片
- 移除功能卡片入场动画，加载更自然
- 简化账号信息展示与跳转逻辑

***

## **13. ✨ 升级：脚本编辑器 → CodeMirror**

将 Monaco Editor 替换为 CodeMirror 5，大幅减小包体积、提升加载速度。

**优化内容**

| 项目 | 说明 |
|------|------|
| **包体积** | 移除大量 Monaco 本地化和语言包资源，体积显著减小 |
| **启动速度** | 轻量级编辑器，加载更快 |
| **暗色主题** | Dracula 主题，适配系统明暗模式自动切换 |
| **离线可用** | 所有资源本地打包（`file:///android_asset/`） |
| **搜索功能** | 支持查找、下一个/上一个、替换 |

**闪屏修复**
- 修复页面切换/重建时的白屏闪烁问题
- WebView 背景色随主题动态调整
- 编辑器刷新与浏览器重绘周期对齐

***

## **14. 🏠 关于页面**

- 新增「本应用官网」入口：`https://cf.390202.xyz/`
- 保留 Telegram 群组、GitHub、Cloudflare API 文档、Cloudflare 官网等链接

***

## **📦 其他改进**

- Hilt 依赖版本升级至 2.54
- 新增 `MANAGE_EXTERNAL_STORAGE` 权限声明
- 隧道命令弹窗 UI 优化，令牌默认隐藏
- Access 应用更新字段合并逻辑优化
- Worker 日志页面新增全选、复制功能
- 多处协程生命周期管理优化，添加 `repeatOnLifecycle` 保护
- ViewPager2 页面 Snackbar 显示逻辑修复（仅当前页显示错误提示）
- 导航操作改用直接 fragment ID，避免跨页面跳转崩溃

***

**完整提交范围**：`381bbed7` ~ `89cd032`
