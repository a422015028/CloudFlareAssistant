# CloudFlareAssistant

[English](README.md) | 简体中文

一款 Android 应用，用于在手机上管理 Cloudflare 服务 —— 部署 Workers/Pages、管理 DNS、D1、R2、KV、Zero Trust 等。

## 为什么做这个

Cloudflare 控制台是桌面优先的。这个应用把核心工作流搬到手机上：写一个 Worker、部署一个 Pages 项目、查 DNS 记录、执行 D1 查询 —— 全程不需要打开浏览器。

## 功能

### 手机端部署
- **Workers** — 上传、编辑、部署脚本，支持自定义域名和路由规则
- **Pages** — 部署 zip/JS/HTML 项目，设备端本地完成 TypeScript/JSX 编译（Sucrase）和 NPM 包打包（esbuild-wasm），无需 CI/CD
- **内置代码编辑器** — 语法高亮编辑器（CodeMirror），支持版本历史和未保存变更检测

### Cloudflare 服务
| 服务 | 能力 |
|------|------|
| **Workers** | 脚本上传（multipart/content/legacy）、设置、自定义域名、路由、构建触发器、部署历史、实时日志 |
| **Pages** | 项目列表、部署管理、自定义域名、环境变量、D1 绑定、部署日志 |
| **DNS & 域名** | Zone 列表、添加域名、DNS 记录增删改查、域名设置（缓存、SSL、WAF、速率限制、转换规则、代码片段、负载均衡、邮件路由） |
| **D1** | 数据库列表、表数据查看、SQL 执行 |
| **R2** | 存储桶浏览，通过 S3 兼容 API 上传/下载文件 |
| **KV** | 命名空间管理、键值增删改查 |
| **Zero Trust** | Access 应用/策略/组、设备管理、网关规则/列表/位置、隧道连接 |

### 便捷功能
- **多账户** — 一键切换 Cloudflare 账户
- **双认证** — 支持 API Token 和 Global API Key
- **备份** — 导出账户配置到 WebDAV 或 R2
- **仪表盘** — 账户分析数据，支持时间范围筛选
- **应用内更新** — 直接检查并安装新版本
- **离线优先编辑器** — 本地编写保存代码，随时部署

## 技术栈

Kotlin · Hilt · MVVM · Coroutines/Flow · Room · Retrofit/OkHttp · Navigation Component · Material 3 · Sucrase · esbuild-wasm

## 截图

<!-- 在此添加截图 -->

## 下载

从 [Releases](../../releases) 下载最新 APK，或访问 [cf.390202.xyz](https://cf.390202.xyz)。

## 环境要求

- Android 8.0+（API 26）
- Cloudflare 账户（API Token 或 Global API Key）
- 网络连接；首次使用 NPM 包部署 Pages 时需下载 esbuild-wasm（约 12MB）

## 开源协议

MIT
