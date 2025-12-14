# CloudFlare Assistant

<div align="center">

![Platform](https://img.shields.io/badge/Platform-Android-green.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.20-blue.svg)
![Material Design](https://img.shields.io/badge/Material%20Design-3-purple.svg)
![License](https://img.shields.io/badge/License-MIT-orange.svg)

**CloudFlare Assistant** 是一款专业的 Cloudflare 多账号管理 Android 应用，采用现代化架构和 Material Design 3 设计语言。

</div>

---

## ✨ 核心特性

### 🔐 多账号管理
- **完善的账号系统**：基于 Room 数据库的持久化存储
- **一键切换账号**：Material Design 3 风格的账号选择对话框
- **导入导出**：支持 WebDAV 自动备份，保障数据安全
- **加密存储**：API Token 安全存储，防止泄露

### 🚀 Workers 管理
- **脚本上传**：图形化界面选择并上传 JavaScript 脚本
- **智能命名**：自动识别文件名，支持自定义脚本名称
- **脚本列表**：查看所有已上传的 Worker 脚本
- **快速删除**：一键清理不需要的脚本

### 🌐 路由管理
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

### 📄 Pages 管理
- **项目管理**：查看、创建、删除 Pages 项目
- **部署管理**：查看部署历史和状态
- **自定义域名**：管理 Pages 项目的自定义域名
- **环境变量**：配置生产环境和预览环境变量

### 🗂️ R2 对象存储
- **Bucket 管理**：创建、删除 R2 存储桶
- **对象操作**：上传、下载、删除对象
- **自定义域名**：为 Bucket 配置自定义域名（支持验证）
- **存储统计**：查看存储使用情况和对象数量

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
├── Android SDK 25-34          # 支持 Android 7.0+
└── Gradle 8.2.0               # 构建系统

Jetpack 组件：
├── Room 2.6.1                 # 数据持久化
├── Navigation 2.7.6           # 导航管理
├── ViewModel & LiveData       # 架构组件
├── DataStore                  # 偏好设置
└── WorkManager                # 后台任务

网络层：
├── Retrofit 2.9.0             # HTTP 客户端
├── OkHttp 4.12.0              # 网络层
├── Gson 2.10.1                # JSON 解析
└── AWS SDK 2.76.0             # S3 兼容 API (R2)

依赖注入：
└── Hilt 2.48                  # 依赖注入框架

UI 设计：
├── Material Design 3          # 设计语言
└── RecyclerView               # 列表展示
```

### 项目结构

```
app/
├── src/main/
│   ├── java/com/muort/upworker/
│   │   ├── core/                    # 核心层
│   │   │   ├── database/            # Room 数据库
│   │   │   │   ├── AppDatabase.kt   # 数据库实例
│   │   │   │   ├── AccountDao.kt    # 账号 DAO
│   │   │   │   └── Migration.kt     # 数据库迁移
│   │   │   ├── network/             # 网络层
│   │   │   │   ├── CloudFlareApi.kt # API 接口
│   │   │   │   ├── R2S3Client.kt    # R2 客户端
│   │   │   │   └── WebDavClient.kt  # WebDAV 客户端
│   │   │   ├── model/               # 数据模型
│   │   │   │   └── Models.kt        # 实体类
│   │   │   ├── repository/          # 数据仓库
│   │   │   │   ├── AccountRepository.kt
│   │   │   │   ├── WorkerRepository.kt
│   │   │   │   ├── DnsRepository.kt
│   │   │   │   ├── KvRepository.kt
│   │   │   │   ├── PagesRepository.kt
│   │   │   │   └── R2Repository.kt
│   │   │   └── util/                # 工具类
│   │   │       └── Extensions.kt
│   │   ├── feature/                 # 功能模块
│   │   │   ├── account/             # 账号管理
│   │   │   │   ├── AccountViewModel.kt
│   │   │   │   └── AccountListFragment.kt
│   │   │   ├── worker/              # Workers
│   │   │   │   └── WorkerFragment.kt
│   │   │   ├── route/               # 路由
│   │   │   │   └── RouteFragment.kt
│   │   │   ├── dns/                 # DNS
│   │   │   │   └── DnsFragment.kt
│   │   │   ├── kv/                  # KV 存储
│   │   │   │   └── KvFragment.kt
│   │   │   ├── pages/               # Pages
│   │   │   │   └── PagesFragment.kt
│   │   │   ├── r2/                  # R2 存储
│   │   │   │   └── R2Fragment.kt
│   │   │   └── backup/              # 备份
│   │   │       └── BackupFragment.kt
│   │   ├── adapter/                 # 适配器
│   │   │   └── AccountSelectionAdapter.kt
│   │   ├── util/                    # 工具类
│   │   │   └── DialogUtils.kt       # 对话框工具
│   │   ├── MainActivity.kt          # 主界面
│   │   └── CloudFlareApp.kt         # Application
│   ├── res/                         # 资源文件
│   │   ├── layout/                  # 布局文件
│   │   ├── values/                  # 配置文件
│   │   └── drawable/                # 图片资源
│   └── AndroidManifest.xml          # 清单文件
└── build.gradle.kts                 # 构建配置
```

---

## 🚀 快速开始

### 环境要求
- **Android Studio**: Hedgehog | 2023.1.1 或更高版本
- **JDK**: 17
- **Android SDK**: API 25+ (Android 7.0+)
- **Gradle**: 8.0+

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
4. 选择模板或自定义权限：
   - **Workers Scripts**: Edit
   - **Workers Routes**: Edit
   - **DNS**: Edit
   - **Account Settings**: Read
   - **Workers KV Storage**: Edit
   - **Pages**: Edit
   - **R2**: Edit
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

### 3. 上传 Worker 脚本
- 切换到 **Workers** 标签
- 点击 **上传脚本** 按钮
- 选择 JavaScript 文件
- 确认脚本名称并提交

### 4. 管理 DNS 记录
- 切换到 **DNS** 标签
- 点击 **+** 添加新记录
- 选择记录类型并填写详细信息
- 保存后记录会立即生效

### 5. 配置 R2 存储
- 切换到 **R2** 标签
- 创建 Bucket 或选择现有 Bucket
- 上传文件或配置自定义域名
- 管理对象和查看存储统计

---

## 🎯 功能状态

| 功能模块 | 状态 | 说明 |
|---------|------|------|
| 多账号管理 | ✅ 已完成 | 支持添加、编辑、删除、切换账号 |
| Workers 管理 | ✅ 已完成 | 上传、列表、删除脚本 |
| 路由管理 | ✅ 已完成 | 创建、更新、删除路由 |
| DNS 管理 | ✅ 已完成 | 支持 20+ 种记录类型 |
| KV 存储 | ✅ 已完成 | 命名空间和键值对管理 |
| Pages 管理 | ✅ 已完成 | 项目和域名管理 |
| R2 对象存储 | ✅ 已完成 | Bucket 和对象管理 |
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

![应用截图](https://raw.githubusercontent.com/a422015028/wow/main/1000094309.jpg)

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
