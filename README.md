# CloudFlare Assistant

<div align="center">

![Platform](https://img.shields.io/badge/Platform-Android-green.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.20-blue.svg)
![Material Design](https://img.shields.io/badge/Material%20Design-3-purple.svg)
![License](https://img.shields.io/badge/License-MIT-orange.svg)

**CloudFlare Assistant** 是一款功能全面的 Cloudflare 服务管理 Android 应用，采用现代化架构和 Material Design 3 设计语言，提供完整的 Cloudflare 生态系统管理能力。

</div>

---

## ✨ 核心特性

### 🔐 多账号管理
- **完善的账号系统**：基于 Room 数据库的持久化存储
- **一键切换账号**：Material Design 3 风格的账号选择对话框
- **导入导出**：支持 WebDAV 自动备份，保障数据安全
- **加密存储**：API Token 安全存储，防止泄露

### �️ D1 数据库管理
- **数据库操作**：创建、删除 D1 数据库实例
- **SQL 执行**：支持直接执行 SQL 查询语句
- **数据浏览**：查看数据库表结构和数据内容
- **查询历史**：保存和重用常用查询

### 🚀 Workers 管理
- **脚本上传**：图形化界面选择并上传 JavaScript 脚本
- **智能命名**：自动识别文件名，支持自定义脚本名称
- **脚本列表**：查看所有已上传的 Worker 脚本
- **快速删除**：一键清理不需要的脚本
- **绑定配置**：支持 KV、D1、R2 等资源绑定

### 📄 Pages 管理
- **项目管理**：查看、创建、删除 Pages 项目
- **部署管理**：查看部署历史和状态
- **自定义域名**：管理 Pages 项目的自定义域名

### 🌐 路由和域管理
- **路由绑定**：将 Worker 脚本绑定到指定域名路径
- **域名管理**：支持自定义域名模式（/*.example.com/*）
- **灵活配置**：支持完整的路由创建、更新、删除操作
- **批量操作**：高效管理多个路由规则

### 📡 DNS 管理
- **全类型支持**：支持 20+ 种 DNS 记录类型（A、AAAA、CNAME、TXT、MX、SRV、CAA 等）
- **完整操作**：添加、编辑、删除 DNS 记录
- **代理控制**：灵活设置 Proxied 状态（橙色云朵）
- **TTL 配置**：支持自定义 TTL 值或使用 Auto

### 💾 KV 存储管理
- **命名空间管理**：创建、删除 KV 命名空间
- **键值操作**：完整的 CRUD 功能（创建、读取、更新、删除）
- **批量管理**：支持批量键值对操作
- **元数据查看**：查看存储配额和使用情况

### 🗂️ R2 对象存储
- **Bucket 管理**：创建、删除 R2 存储桶
- **对象操作**：上传、下载、删除对象
- **自定义域名**：为 Bucket 配置自定义域名（支持验证）
- **存储统计**：查看存储使用情况和对象数量

### 📋 日志系统
- **实时日志**：实时显示应用运行日志
- **语法高亮**：支持日志内容语法高亮显示
- **日志过滤**：按级别过滤日志内容
- **日志导出**：支持导出日志到文件

### 💼 备份与恢复
- **WebDAV 支持**：自动备份账号数据到 WebDAV 服务器
- **手动备份**：随时导出账号配置到本地文件
- **一键恢复**：从备份文件快速恢复账号数据
- **数据加密**：备份文件支持密码保护

---

## 🏗️ 技术架构

### 架构模式
- **MVVM 架构**：ViewModel + LiveData/Flow + Repository 分层设计
- **依赖注入**：Hilt 管理应用依赖，提高可测试性
- **响应式编程**：Kotlin Coroutines + Flow 处理异步操作
- **模块化设计**：清晰的代码组织和关注点分离

### 技术栈

```
核心框架：
├── Kotlin 1.9.20              # 主开发语言
├── Android SDK 26-36          # 支持 Android 8.0+ (minSdk=26, targetSdk=36)
└── Gradle 8.13                # 构建系统

Jetpack 组件：
├── Room 2.6.1                 # 数据持久化
├── Navigation 2.7.6           # 导航管理
├── ViewModel & LiveData 2.7.0 # 架构组件
├── DataStore 1.0.0            # 偏好设置
└── WorkManager 2.9.0          # 后台任务

网络层：
├── Retrofit 2.9.0             # HTTP 客户端
├── OkHttp 4.12.0             # 网络层 + 自签名 S3 API (R2)
└── Gson 2.10.1               # JSON 解析

依赖注入：
└── Hilt 2.48                 # 依赖注入框架

UI 设计：
├── Material Design 1.11.0     # 设计语言
└── RecyclerView              # 列表展示 (通过其他依赖)

其他工具：
├── Coroutines 1.7.3          # 协程支持
└── Timber 5.0.1              # 日志框架
```

### 项目结构

```
app/
├── src/main/
│   ├── java/com/muort/upworker/
│   │   ├── AccountSelectionAdapter.kt    # 账号选择适配器
│   │   ├── CloudFlareApp.kt              # Application 类
│   │   ├── MainActivity.kt               # 主 Activity
│   │   ├── core/                         # 核心层
│   │   │   ├── database/                 # 数据库层
│   │   │   │   ├── AccountDao.kt         # 账号数据访问对象
│   │   │   │   ├── AppDatabase.kt        # 数据库实例
│   │   │   │   ├── DatabaseModule.kt     # 数据库依赖注入模块
│   │   │   │   ├── WebDavConfigDao.kt    # WebDAV 配置 DAO
│   │   │   │   └── ZoneDao.kt            # Zone 数据访问对象
│   │   │   ├── log/                      # 日志相关
│   │   │   ├── model/                    # 数据模型
│   │   │   ├── network/                  # 网络层
│   │   │   │   ├── AppModule.kt          # 应用依赖注入模块
│   │   │   │   ├── CloudFlareApi.kt      # Cloudflare API 接口
│   │   │   │   ├── LogOkHttpInterceptor.kt # 日志拦截器
│   │   │   │   ├── NetworkModule.kt      # 网络依赖注入模块
│   │   │   │   └── R2S3Client.kt         # R2 S3 客户端
│   │   │   ├── repository/               # 数据仓库层
│   │   │   │   ├── AccountRepository.kt  # 账号仓库
│   │   │   │   ├── BackupRepository.kt   # 备份仓库
│   │   │   │   ├── D1Repository.kt       # D1 数据库仓库
│   │   │   │   ├── DnsRepository.kt      # DNS 仓库
│   │   │   │   ├── KvRepository.kt       # KV 仓库
│   │   │   │   ├── PagesRepository.kt    # Pages 仓库
│   │   │   │   ├── R2Repository.kt       # R2 仓库
│   │   │   │   ├── WorkerRepository.kt   # Worker 仓库
│   │   │   │   └── ZoneRepository.kt     # Zone 仓库
│   │   │   ├── util/                     # 工具类
│   │   │   └── webdav/                   # WebDAV 相关
│   │   └── feature/                      # 功能模块层
│   │       ├── account/                  # 账号管理模块
│   │       │   ├── AccountEditFragment.kt    # 账号编辑界面
│   │       │   ├── AccountListFragment.kt    # 账号列表界面
│   │       │   └── AccountViewModel.kt       # 账号 ViewModel
│   │       ├── backup/                   # 备份模块
│   │       │   ├── BackupFilesAdapter.kt     # 备份文件适配器
│   │       │   ├── BackupFragment.kt         # 备份界面
│   │       │   └── BackupViewModel.kt        # 备份 ViewModel
│   │       ├── d1/                       # D1 数据库模块
│   │       │   ├── D1DataAdapter.kt          # D1 数据适配器
│   │       │   ├── D1DataViewerFragment.kt   # D1 数据查看界面
│   │       │   ├── D1ManagerFragment.kt      # D1 管理界面
│   │       │   └── D1ViewModel.kt            # D1 ViewModel
│   │       ├── dns/                      # DNS 模块
│   │       │   ├── DnsFragment.kt            # DNS 界面
│   │       │   └── DnsViewModel.kt           # DNS ViewModel
│   │       ├── home/                     # 主界面模块
│   │       │   └── HomeFragment.kt           # 主界面
│   │       ├── kv/                       # KV 存储模块
│   │       │   ├── KvFragment.kt             # KV 界面
│   │       │   └── KvViewModel.kt            # KV ViewModel
│   │       ├── log/                      # 日志模块
│   │       │   └── LogActivity.kt            # 日志 Activity
│   │       ├── pages/                    # Pages 模块
│   │       │   ├── PagesFragment.kt          # Pages 界面
│   │       │   └── PagesViewModel.kt         # Pages ViewModel
│   │       ├── r2/                       # R2 存储模块
│   │       │   ├── ObjectAdapter.kt          # 对象适配器
│   │       │   ├── R2Fragment.kt             # R2 界面
│   │       │   └── R2ViewModel.kt            # R2 ViewModel
│   │       ├── route/                    # 路由模块
│   │       │   └── RouteFragment.kt          # 路由界面
│   │       └── worker/                   # Worker 模块
│   │           ├── WorkerFragment.kt         # Worker 界面
│   │           └── WorkerViewModel.kt        # Worker ViewModel
│   ├── res/                              # 资源文件
│   │   ├── layout/                       # 布局文件
│   │   ├── values/                       # 值文件
│   │   └── drawable/                     # 图片资源
│   └── AndroidManifest.xml               # 应用清单
└── build.gradle.kts                      # 应用构建配置
```

---

## 🚀 快速开始

### 环境要求
- **Android Studio**: Hedgehog | 2023.1.1 或更高版本
- **JDK**: 17
- **Android SDK**: API 26+ (Android 8.0+)
- **Gradle**: 8.13

### 编译步骤

```bash
# 1. 克隆仓库
git clone https://github.com/a422015028/CloudFlareAssistant.git

# 2. 进入项目目录
cd CloudFlareAssistant

# 3. 清理项目（可选）
./gradlew clean

# 4. 编译 Debug 版本
./gradlew assembleDebug

# 5. 编译 Release 版本
./gradlew assembleRelease

# 6. 安装到连接的设备
./gradlew installDebug
```

### 获取 Cloudflare API Token

1. 登录 [Cloudflare Dashboard](https://dash.cloudflare.com/)
2. 进入 **My Profile** → **API Tokens**
3. 点击 **Create Token**
4. 选择模板或自定义权限（根据应用功能需要以下权限）：
   - **Workers Scripts**: Edit（上传、删除、获取Worker脚本）
   - **Workers Routes**: Edit（创建、更新、删除路由规则）
   - **Workers Custom Domains**: Edit（管理Worker自定义域名）
   - **DNS**: Edit（DNS记录的增删改查）
   - **Workers KV Storage**: Edit（KV命名空间和键值对管理）
   - **Pages**: Edit（Pages项目、部署和域名管理）
   - **R2**: Edit（R2存储桶和自定义域名管理）
   - **D1**: Edit（D1数据库创建、删除和SQL执行）
   - **Account Settings**: Read（获取账号基本信息）
   - **Zone Settings**: Read（获取Zone列表和配置）
5. 复制生成的 Token 并妥善保存

---

## 📱 使用说明

### 1. 添加账号
- 打开应用，点击 **账号管理**
- 点击 **+** 按钮添加新账号
- 输入账号名称、Account ID 和 API Token
- 点击 **保存** 完成添加

### 2. 切换账号
- 在主界面点击当前账号名称
- 选择要切换的账号
- 应用会自动加载该账号的数据

### 3. 管理 D1 数据库
- 切换到 **D1** 标签
- 创建新的数据库实例
- 执行 SQL 查询查看和修改数据
- 浏览数据库表结构和内容

### 4. 上传 Worker 脚本
- 切换到 **Workers** 标签
- 点击 **上传脚本** 按钮
- 选择 JavaScript 文件
- 确认脚本名称并提交

### 5. 管理 DNS 记录
- 切换到 **DNS** 标签
- 点击 **+** 添加新记录
- 选择记录类型并填写详细信息
- 保存后记录会立即生效

### 6. 配置 R2 存储
- 切换到 **R2** 标签
- 创建 Bucket 或选择现有 Bucket
- 上传文件或配置自定义域名
- 管理对象和查看存储统计

### 7. 查看日志
- 切换到 **日志** 标签
- 实时查看应用运行日志
- 使用语法高亮和过滤功能
- 导出日志文件进行分析

---

## 🎯 功能状态

| 功能模块 | 状态 | 说明 |
|---------|------|------|
| 多账号管理 | ✅ 已完成 | 支持添加、编辑、删除、切换账号 |
| D1 数据库管理 | ✅ 已完成 | 数据库创建、SQL 执行、数据浏览 |
| Workers 管理 | ✅ 已完成 | 上传、列表、删除脚本，支持绑定配置 |
| 路由管理 | ✅ 已完成 | 创建、更新、删除路由 |
| DNS 管理 | ✅ 已完成 | 支持 20+ 种记录类型 |
| KV 存储 | ✅ 已完成 | 命名空间和键值对管理 |
| Pages 管理 | ✅ 已完成 | 项目和域名管理 |
| R2 对象存储 | ✅ 已完成 | Bucket 和对象管理 |
| 日志系统 | ✅ 已完成 | 实时日志显示和语法高亮 |
| WebDAV 备份 | ✅ 已完成 | 自动备份和恢复 |
| Material Design 3 | ✅ 已完成 | 统一的界面风格 |

---

## 🔧 开发指南

### 添加新功能

1. **定义 API 接口**  
   在 [CloudFlareApi.kt](app/src/main/java/com/muort/upworker/core/network/CloudFlareApi.kt) 添加接口方法

2. **创建数据模型**  
   在 [Models.kt](app/src/main/java/com/muort/upworker/core/model/Models.kt) 定义数据类

3. **实现 Repository**  
   在 `core/repository/` 创建仓库类处理数据逻辑

4. **创建 ViewModel**  
   在 `feature/` 对应模块创建 ViewModel

5. **构建 UI**  
   创建 Fragment 和对应的 XML 布局

### 代码规范
- 遵循 Kotlin 官方编码规范
- 使用 Material Design 3 组件
- 所有对话框使用 `MaterialAlertDialogBuilder`
- 异步操作使用 Coroutines + Flow
- 添加必要的注释和文档

### 测试
```bash
# 运行单元测试
./gradlew test

# 运行 UI 测试
./gradlew connectedAndroidTest
```

---

## 📸 预览截图

![应用截图](https://raw.githubusercontent.com/a422015028/wow/main/1000094593.jpg)

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

### 贡献指南
1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提交 Pull Request

---

## 📄 许可证

```
MIT License

Copyright (c) 2024 CloudFlare Assistant

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## 🙏 致谢

感谢所有贡献者和使用者的支持！

---

## 🔗 相关链接

- **项目地址**: [GitHub Repository](https://github.com/a422015028/CloudFlareAssistant)
- **问题反馈**: [Issues](https://github.com/a422015028/CloudFlareAssistant/issues)
- **Cloudflare 文档**: [Cloudflare Docs](https://developers.cloudflare.com/)
- **Cloudflare API**: [API Reference](https://developers.cloudflare.com/api/)

---

<div align="center">

**⭐ 如果这个项目对你有帮助，请给一个 Star！⭐**

Made with ❤️ by CloudFlare Assistant Team

</div>
