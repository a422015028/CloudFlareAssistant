# Pages 功能完整重构 - 与 Workers 界面一致

## 功能概述

Pages 界面已完全重构，现在与 Workers 管理界面完全一致：
- ✅ 支持直接上传 `.zip` 和 `.js` 文件进行部署
- ✅ 项目列表卡片显示详细操作按钮（与 Workers 脚本列表一致）
- ✅ 每个项目卡片包含所有配置和管理按钮

## 主要变更

### 1. UI 布局重构 (fragment_pages.xml)

- **上传部署区域**：包含项目名称、分支名称、文件选择和部署按钮
- **已部署项目列表**：显示所有已部署的 Pages 项目，带刷新按钮
- 采用 Material CardView 设计，与 Workers 界面风格一致

### 2. 项目列表项重构 (item_pages_project.xml)

**新设计完全匹配 Workers 脚本列表：**

#### 信息显示区
- 项目名称（粗体，16sp）
- 分支信息和创建日期（灰色，12sp）

#### 配置按钮行（第一行）
- **生产环境**：配置生产环境变量
- **预览环境**：配置预览环境变量
- **KV**：配置 KV 命名空间绑定
- **R2**：配置 R2 存储桶绑定

#### 操作按钮行（第二行）
- **部署**：查看部署历史
- **删除**：删除项目

**对比 Workers 脚本列表：**
- Workers：KV、R2、变量、机密 + 查看、删除
- Pages：生产环境、预览环境、KV、R2 + 部署、删除
- 布局样式完全一致（按钮大小、间距、图标）

### 3. ProjectAdapter 重构

**新增功能回调：**
```kotlin
ProjectAdapter(
    onProjectClick: (PagesProject) -> Unit,           // 点击卡片（暂无操作）
    onDeleteClick: (PagesProject) -> Unit,            // 删除项目
    onConfigEnvProdClick: (PagesProject) -> Unit,     // 配置生产环境
    onConfigEnvPreviewClick: (PagesProject) -> Unit,  // 配置预览环境
    onConfigKvClick: (PagesProject) -> Unit,          // 配置 KV 绑定
    onConfigR2Click: (PagesProject) -> Unit,          // 配置 R2 绑定
    onViewDeploymentsClick: (PagesProject) -> Unit    // 查看部署
)
```

**ViewHolder 改进：**
- 移除了 PopupMenu（与 Workers 一致，不使用菜单）
- 添加日期格式化显示（与 Workers 相同格式）
- 每个按钮直接绑定对应操作

**对比旧版本：**
- 旧版：只有 onProjectClick 和 onDeleteClick，使用菜单按钮
- 新版：7 个独立回调，所有操作都有专用按钮

### 4. 文件上传支持

#### 支持的文件类型
- `.zip` 文件：完整的项目构建输出
- `.js` 文件：Workers Functions 文件

#### 文件选择流程
1. 点击"选择文件"按钮或文件路径输入框
2. 系统打开文件选择器（支持 zip 和 js 文件）
3. 选择文件后自动：
   - 复制文件到应用缓存目录
   - 显示文件名
   - 自动填充项目名称（基于文件名）

### 5. 部署流程

1. **输入信息**：
   - 项目名称（必填）
   - 生产分支（默认 "main"）
   - 选择部署文件

2. **验证检查**：
   - 项目名称不能为空
   - 分支名称不能为空
   - 必须选择文件
   - 文件必须存在
   - 文件类型必须是 .zip 或 .js

3. **部署执行**：
   - 显示进度条
   - 调用 Cloudflare Pages API 创建部署
   - 上传完成后刷新项目列表

### 6. API 集成

#### CloudFlareApi.kt
```kotlin
@Multipart
@POST("accounts/{account_id}/pages/projects/{project_name}/deployments")
suspend fun createPagesDeployment(
    @Header("Authorization") token: String,
    @Path("account_id") accountId: String,
    @Path("project_name") projectName: String,
    @Part("branch") branch: RequestBody,
    @Part file: MultipartBody.Part
): Response<CloudFlareResponse<PagesDeployment>>
```

#### PagesRepository.kt
- 新增 `createDeployment()` 方法
- 支持 .zip 和 .js 文件类型检测
- 自动设置正确的 Content-Type
- 使用 multipart/form-data 上传

#### PagesViewModel.kt
- 新增 `createDeployment()` 方法
- 处理部署状态和消息
- 部署成功后自动刷新项目列表

#### PagesFragment.kt
- 添加文件选择器（ActivityResultContracts）
- 文件处理和缓存
- 部署表单验证
- 进度显示

## 使用说明

### 部署新项目

1. 打开 Pages 标签
2. 在上传区域输入：
   - 项目名称
   - 生产分支（如 main）
3. 点击"选择文件"选择 .zip 或 .js 文件
4. 点击"部署项目"按钮
5. 等待部署完成

### 管理已部署项目

**每个项目卡片包含以下操作：**

#### 配置按钮（第一行）
- **生产环境**：管理生产环境的环境变量
  - 查看当前变量
  - 添加新变量
  - 编辑/删除变量
  
- **预览环境**：管理预览环境的环境变量
  - 独立的预览环境配置
  - 与生产环境分离管理
  
- **KV**：绑定 KV 命名空间
  - 选择已有的 KV 命名空间
  - 设置绑定名称
  - 管理多个 KV 绑定
  
- **R2**：绑定 R2 存储桶
  - 选择已有的 R2 存储桶
  - 设置绑定名称
  - 管理多个 R2 绑定

#### 操作按钮（第二行）
- **部署**：查看项目的所有部署历史
  - 部署列表
  - 部署详情
  - 重新部署
  - 删除部署
  
- **删除**：删除整个项目（需确认）

### 其他功能
- 点击"刷新"按钮更新项目列表
- 所有操作都有即时反馈提示

## 技术细节

### 文件类型映射

| 文件扩展名 | Content-Type | API 参数名 |
|-----------|--------------|-----------|
| .zip | application/zip | pages_build_output_dir |
| .js | application/javascript | _worker.js |

### 缓存管理

- 选择的文件会复制到 `context.cacheDir`
- 文件名保持不变
- 部署后文件保留在缓存中

### 错误处理

- 文件选择失败：显示错误提示
- 表单验证失败：显示具体缺失项
- API 调用失败：显示错误信息
- 不支持的文件类型：拒绝并提示

## 界面对比

### Workers 脚本列表
```
┌─────────────────────────────────────────┐
│ my-worker                               │
│ 创建于 2025-12-19 14:30                 │
├─────────────────────────────────────────┤
│ [KV] [R2] [变量] [机密]                 │
│ [查看] [删除]                           │
└─────────────────────────────────────────┘
```

### Pages 项目列表（新版）
```
┌─────────────────────────────────────────┐
│ my-pages-project                        │
│ main 分支 • 2025-12-19 14:30           │
├─────────────────────────────────────────┤
│ [生产环境] [预览环境] [KV] [R2]         │
│ [部署] [删除]                           │
└─────────────────────────────────────────┘
```

**核心改进：**
- ✅ 布局结构完全一致
- ✅ 按钮样式和大小统一
- ✅ 信息展示格式相同
- ✅ 操作直观，无需菜单
- ✅ 符合 Material Design 3 规范

## 弹窗对话框对比

### KV 绑定配置弹窗

**Workers（参考）：**
- 使用 `dialog_script_kv_bindings.xml`
- 显示脚本名称
- RecyclerView 展示绑定列表
- 每个绑定有删除按钮
- 底部警告提示
- "应用配置" 和 "取消" 按钮

**Pages（新版 - 完全一致）：**
- 使用 `dialog_pages_kv_bindings.xml`
- 显示项目名称和环境
- RecyclerView 展示绑定列表
- 每个绑定有删除按钮
- 底部警告提示
- "应用配置" 和 "取消" 按钮

### R2 绑定配置弹窗

**Workers（参考）：**
- 使用 `dialog_script_r2_bindings.xml`
- RecyclerView 展示绑定列表
- 添加绑定按钮
- 应用配置按钮

**Pages（新版 - 完全一致）：**
- 使用 `dialog_pages_r2_bindings.xml`
- RecyclerView 展示绑定列表
- 添加绑定按钮
- 应用配置按钮

### 关键改进点

1. **统一的视觉风格**
   - 相同的布局结构
   - 相同的组件样式
   - 相同的交互方式

2. **功能完整性**
   - 支持查看现有绑定
   - 支持添加新绑定
   - 支持删除绑定
   - 实时更新列表

3. **用户体验**
   - 加载状态提示
   - 操作成功反馈
   - 错误信息提示
   - 防止重复操作

## API 文档参考

- [Cloudflare Pages API - Create Deployment](https://developers.cloudflare.com/api/operations/pages-deployment-create-deployment)
- [Pages Direct Upload Documentation](https://developers.cloudflare.com/pages/platform/direct-upload/)

## 文件变更清单

### 修改的文件
1. **fragment_pages.xml** - 主布局（上传区 + 列表区）
2. **item_pages_project.xml** - 列表项布局（完全重构）
3. **PagesFragment.kt** - Fragment 逻辑（完整重构）
   - Adapter 重构（7个回调）
   - KV/R2 绑定对话框重构（与 Workers 一致）
   - 新增适配器类：PagesKvBindingsAdapter、PagesR2BindingsAdapter
4. **CloudFlareApi.kt** - 添加部署上传 API
5. **PagesRepository.kt** - 添加部署上传方法
6. **PagesViewModel.kt** - 添加部署状态管理

### 新增的文件
1. **dialog_pages_kv_bindings.xml** - KV 绑定配置弹窗布局
2. **dialog_pages_r2_bindings.xml** - R2 绑定配置弹窗布局

### 新增的功能
- 文件选择器集成
- 文件类型验证（.zip 和 .js）
- 部署上传逻辑
- 直接操作按钮（7个独立按钮）
- RecyclerView 绑定列表（与 Workers 一致）
- 完整的绑定管理（添加/删除）

## 编译状态

✅ **BUILD SUCCESSFUL** - 无错误，无警告

最后编译时间：2025-12-19
编译输出：44 actionable tasks: 8 executed, 36 up-to-date

## 后续优化建议

1. 添加上传进度百分比显示
2. 支持大文件分片上传
3. 添加部署日志实时查看
4. 支持从 Git 仓库部署
5. 添加部署预览功能
