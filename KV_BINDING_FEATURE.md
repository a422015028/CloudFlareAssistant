# Worker KV 命名空间绑定功能

## 功能概述

此功能允许用户在上传 Worker 脚本时，直接配置 KV 命名空间绑定，无需手动编辑配置文件。

## 功能特性

- ✅ 支持在上传 Worker 时添加多个 KV 命名空间绑定
- ✅ **支持为已上传的 Worker 脚本配置 KV 绑定** ⭐新增
- ✅ 从现有的 KV 命名空间列表中选择
- ✅ 自定义绑定名称（在代码中使用的变量名）
- ✅ 可视化管理绑定列表
- ✅ 支持删除已添加的绑定
- ✅ 自动获取脚本内容并重新上传

## 使用方法

### 1. 上传新的 Worker 脚本并配置 KV 绑定

1. 在 Worker 页面填写 Worker 名称
2. 选择要上传的脚本文件
3. 点击 **"+ 添加"** 按钮添加 KV 绑定
4. 在弹出的对话框中：
   - 输入绑定名称（例如：`MY_KV`）
   - 从下拉列表中选择要绑定的 KV 命名空间
5. 点击 **"添加"** 确认
6. 可以添加多个 KV 绑定
7. 点击 **"上传脚本"** 完成上传

### 2. 为已上传的 Worker 脚本配置 KV 绑定 ⭐新增

如果你已经上传了 Worker 脚本，想要为其添加或更新 KV 绑定：

1. 在 **"已上传的脚本"** 列表中找到目标脚本
2. 点击脚本右侧的 **"KV"** 按钮
3. 在弹出的配置对话框中：
   - 查看当前脚本名称
   - 点击 **"+ 添加绑定"** 添加新的 KV 绑定
   - 可以删除不需要的绑定
4. 点击 **"应用配置"** 确认
5. 系统会自动：
   - 获取原脚本内容
   - 应用新的 KV 绑定配置
   - 重新上传脚本

⚠️ **注意**：应用配置会重新上传 Worker 脚本，请确保操作正确

### 3. 在 Worker 代码中使用 KV 绑定

上传后，可以在 Worker 代码中通过绑定名称访问 KV 命名空间：

```javascript
// 假设绑定名称为 MY_KV
export default {
  async fetch(request, env) {
    // 读取 KV 值
    const value = await env.MY_KV.get('key');
    
    // 写入 KV 值
    await env.MY_KV.put('key', 'value');
    
    // 删除 KV 值
    await env.MY_KV.delete('key');
    
    return new Response(value);
  }
}
```

### 4. 管理绑定

#### 新上传脚本时：
- **查看绑定**：添加的绑定会显示在 "KV 命名空间绑定" 卡片中
- **删除绑定**：点击绑定右侧的删除按钮即可移除
- **清空绑定**：重新选择文件或刷新页面会清空已添加的绑定

#### 已上传脚本：
- **配置绑定**：点击脚本列表中的 "KV" 按钮
- **添加绑定**：在配置对话框中点击 "+ 添加绑定"
- **删除绑定**：点击绑定右侧的删除按钮
- **应用配置**：确认后自动重新上传脚本

## 技术实现

### 架构层级

```
WorkerFragment (UI)
    ↓
WorkerViewModel (业务逻辑)
    ↓
WorkerRepository (数据层)
    ↓
CloudFlareApi (网络请求)
```

### 核心代码

#### 1. WorkerRepository

```kotlin
suspend fun uploadWorkerScriptWithKvBindings(
    account: Account,
    scriptName: String,
    scriptFile: File,
    kvBindings: List<Pair<String, String>>
): Resource<WorkerScript>
```

将 KV 绑定转换为 `WorkerBinding` 对象，并通过 `WorkerMetadata` 上传。

#### 2. WorkerViewModel

```kotlin
fun uploadWorkerScriptWithKvBindings(
    account: Account,
    scriptName: String,
    scriptFile: File,
    kvBindings: List<Pair<String, String>>
)
```

处理上传逻辑，并更新 UI 状态。

#### 3. WorkerFragment

- 管理 KV 绑定列表
- 显示添加绑定对话框
- 与 KvRepository 交互获取命名空间列表

## 绑定数据格式

绑定数据以 `Pair<String, String>` 格式存储：
- `first`: 绑定名称（在代码中使用）
- `second`: KV 命名空间 ID

示例：
```kotlin
val kvBindings = listOf(
    Pair("MY_KV", "abc123..."),
    Pair("CACHE", "def456...")
)
```

## API 元数据格式

上传时会生成如下 JSON 元数据：

```json
{
  "main_module": "worker.js",
  "compatibility_date": "2024-12-01",
  "bindings": [
    {
      "type": "kv_namespace",
      "name": "MY_KV",
      "namespace_id": "abc123..."
    }
  ]
}
```

## 注意事项

1. **需要先创建 KV 命名空间**：如果账号下没有 KV 命名空间，请先在 KV 页面创建
2. **绑定名称规范**：建议使用大写字母和下划线，如 `MY_KV`、`USER_DATA` 等
3. **命名空间 ID**：从 KV 命名空间列表中自动获取，无需手动输入
4. **多个绑定**：可以为同一个 Worker 添加多个 KV 命名空间绑定
5. **更新脚本**：重新上传同名 Worker 会覆盖原有配置，包括 KV 绑定
6. **已上传脚本配置**：为已上传的脚本配置 KV 绑定时，会自动获取原脚本内容并重新上传
7. **配置生效**：新的绑定配置在重新上传后立即生效

## 使用场景示例

### 场景 1：新上传脚本时配置 KV
适用于首次上传脚本，直接配置好所需的 KV 绑定。

### 场景 2：为已部署脚本添加 KV 功能
你有一个已经运行的 Worker，现在想要添加缓存功能：

1. 在 KV 页面创建一个名为 "cache" 的命名空间
2. 在 Worker 页面找到你的脚本
3. 点击 "KV" 按钮
4. 添加绑定：名称 `CACHE`，选择 "cache" 命名空间
5. 应用配置
6. 在代码中即可使用 `env.CACHE` 访问缓存

### 场景 3：批量配置多个 KV
为一个 Worker 配置多个 KV 命名空间，实现不同数据的分离存储：

- `USER_DATA` - 用户数据
- `SESSION` - 会话数据  
- `CACHE` - 缓存数据
- `CONFIG` - 配置数据

## 扩展功能

未来可以支持更多类型的绑定：

- **R2 Bucket**: 对象存储绑定
- **D1 Database**: 数据库绑定
- **Service Binding**: Worker 间服务绑定
- **Durable Objects**: 持久化对象绑定
- **Environment Variables**: 环境变量配置

## 参考文档

- [Cloudflare Workers KV](https://developers.cloudflare.com/workers/runtime-apis/kv/)
- [Worker Script Upload API](https://developers.cloudflare.com/api/operations/worker-script-upload-worker-module)
- [Multipart Upload Metadata](https://developers.cloudflare.com/workers/configuration/multipart-upload-metadata/)
