# CloudFlare Assistant v5.5 版本更新

## 🎉 新增功能

### Worker 脚本配置增强

#### 1. R2 绑定配置
- **R2 存储桶绑定**：为 Worker 脚本添加 R2 存储桶绑定
- **绑定管理**：支持查看、添加、删除 R2 绑定
- **位置显示**：R2 存储桶位置智能显示（auto → "自动选择"）
- **与 KV 并列**：R2 绑定与 KV 绑定同级管理，互不影响

#### 2. 环境变量配置
- **变量类型支持**：
  - 文本类型 (plain_text)：普通字符串变量
  - JSON 类型 (json)：结构化数据，带格式验证
- **完整管理**：支持添加、编辑、删除环境变量
- **类型标识**：列表显示变量名、值和类型标识 [文本]/[JSON]
- **格式验证**：JSON 类型变量添加时自动验证格式

#### 3. 机密配置
- **加密存储**：支持配置加密机密 (secret_text)
- **安全管理**：机密值加密存储，支持编辑和删除
- **独立配置**：机密配置独立于变量和其他绑定

#### 4. R2 对象 URL 显示
- **URL 展示**：R2 存储桶对象列表显示访问 URL
- **双 URL 支持**：
  - 默认 R2 公开 URL
  - 自定义域 URL（如已配置）
- **一键复制**：点击快速复制 URL 到剪贴板
- **详细信息**：显示文件大小和访问方式说明

## 🎨 界面优化

### Worker 脚本列表布局改进
- **两行布局**：按钮重新组织为两行，提升可读性
  - 第一行：KV、R2、变量、机密（配置按钮）
  - 第二行：查看、删除（操作按钮）
- **空间优化**：避免按钮挤压，提供更好的点击体验

### R2 对象详情对话框
- **信息展示**：文件名、大小、URL 清晰展示
- **快速操作**：
  - 快速复制按钮（复制最常用的 URL）
  - 更多操作菜单（查看所有选项）
- **自定义域集成**：自动加载和显示自定义域 URL

## 🔧 修复与优化

### 关键修复

#### 1. 绑定配置互不干扰
- **问题**：配置一种绑定类型时会覆盖其他类型
- **修复**：所有绑定更新方法增加保留机制
  - `updateWorkerKvBindings`：保留非 kv_namespace 类型
  - `updateWorkerR2Bindings`：保留非 r2_bucket 类型
  - `updateWorkerVariables`：保留非 plain_text/json 类型
  - `updateWorkerSecrets`：保留非 secret_text 类型
- **效果**：KV、R2、变量、机密可以同时配置使用

#### 2. JSON 变量存储修复
- **问题**：JSON 类型变量值显示为 null
- **原因**：Cloudflare API 对 JSON 类型使用 `json` 字段而非 `text` 字段
- **修复**：
  - 添加 `json` 字段到 `WorkerBinding` 模型
  - 发送时根据类型选择正确字段
  - 读取时使用 `getValue()` 方法统一处理
- **效果**：JSON 变量正确存储和读取

## 📋 技术细节

### 代码变更
- **新增布局文件**（9个）：
  - `dialog_script_r2_bindings.xml` - R2 绑定配置对话框
  - `dialog_r2_binding.xml` - 添加 R2 绑定对话框
  - `item_r2_binding.xml` - R2 绑定列表项
  - `dialog_script_variables.xml` - 变量配置对话框
  - `dialog_add_variable.xml` - 添加/编辑变量对话框
  - `item_variable.xml` - 变量列表项（含类型标识）
  - `dialog_script_secrets.xml` - 机密配置对话框
  - `dialog_add_secret.xml` - 添加/编辑机密对话框
  - `item_secret.xml` - 机密列表项

- **修改核心文件**：
  - `Models.kt`：添加 json 字段和 getValue() 方法
  - `WorkerRepository.kt`：修复所有绑定更新方法
  - `WorkerFragment.kt`：新增 R2/变量/机密配置功能
  - `R2Fragment.kt`：新增对象 URL 显示和复制功能
  - `item_worker_script.xml`：优化按钮布局

### API 集成
- 正确处理 Cloudflare Workers API 的不同绑定类型
- JSON 变量使用 JsonParser 验证格式
- 自动加载 R2 自定义域配置

## 📝 版本信息

- **版本号**：5.5
- **构建代码**：2025121801
- **发布日期**：2025年12月18日

## 🔗 相关链接

- Telegram 群组：https://t.me/CFmuort
- GitHub 仓库：https://github.com/a422015028/CloudFlareAssistant
- 项目 Wiki：https://github.com/a422015028/CloudFlareAssistant/wiki

## 📦 安装说明

1. 下载 `CloudFlareAssistant-v5.5.apk`
2. 如已安装旧版本，可直接覆盖安装
3. 首次使用请在设置中添加 Cloudflare 账号 Token

## ⚠️ 注意事项

1. **首次配置**：建议先单独配置一种绑定类型测试
2. **JSON 格式**：JSON 类型变量需要填写有效的 JSON 格式
3. **R2 公开访问**：R2 URL 需要为存储桶配置公开访问才能使用
4. **版本兼容**：建议及时更新到最新版本以获得完整功能

## 🙏 感谢

感谢所有用户的反馈和建议！如遇到问题请在 GitHub Issues 或 Telegram 群组反馈。
