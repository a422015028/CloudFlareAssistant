# Worker 脚本上传 API 使用指南

本项目现在支持 Cloudflare 官方的所有 Worker 脚本上传方式。

## 三种上传方法

### 1. Multipart 上传（推荐）✨

这是官方推荐的上传方式，支持完整的 metadata 配置。

```kotlin
// 创建 metadata（可选）
val metadata = WorkerMetadata(
    mainModule = "worker.js",
    compatibilityDate = "2024-12-01",
    compatibilityFlags = listOf("nodejs_compat"),
    bindings = listOf(
        // KV 绑定
        WorkerBinding(
            type = "kv_namespace",
            name = "MY_KV",
            namespaceId = "your-kv-namespace-id"
        ),
        // R2 绑定
        WorkerBinding(
            type = "r2_bucket",
            name = "MY_BUCKET",
            bucketName = "my-bucket"
        ),
        // D1 绑定
        WorkerBinding(
            type = "d1",
            name = "MY_DB",
            databaseId = "your-database-id"
        )
    ),
    vars = mapOf(
        "ENV" to "production",
        "API_KEY" to "your-api-key"
    ),
    logpush = true
)

// 上传脚本
val result = workerRepository.uploadWorkerScriptMultipart(
    account = account,
    scriptName = "my-worker",
    scriptFile = File("path/to/worker.js"),
    metadata = metadata
)
```

**优点：**
- ✅ 支持完整的配置（bindings、环境变量等）
- ✅ 支持多种文件类型（.js, .mjs, .py, .wasm）
- ✅ 官方推荐的方式
- ✅ 可以一次性设置所有配置

**适用场景：**
- 新建 Worker 需要配置 KV、R2、D1 等绑定
- 需要设置环境变量
- 需要指定兼容性日期和标志
- Python Workers

### 2. Content-Only 上传（快速更新）⚡

只更新脚本内容，不修改配置和 metadata。

```kotlin
val result = workerRepository.uploadWorkerScriptContent(
    account = account,
    scriptName = "my-worker",
    scriptFile = File("path/to/worker.js")
)
```

**优点：**
- ✅ 更快的上传速度
- ✅ 不会影响现有的配置和绑定
- ✅ 适合频繁更新代码

**适用场景：**
- 只需要更新代码，保持现有配置不变
- 快速迭代开发
- CI/CD 自动部署

### 3. 简单上传（向后兼容）🔄

这是保留的原有方法，会自动尝试多种上传方式。

```kotlin
val result = workerRepository.uploadWorkerScript(
    account = account,
    scriptName = "my-worker",
    scriptFile = File("path/to/worker.js")
)
```

**工作流程：**
1. 首先尝试 multipart 上传（带默认 metadata）
2. 如果失败，尝试 content-only 上传
3. 如果还失败，尝试简单上传

**优点：**
- ✅ 向后兼容现有代码
- ✅ 自动重试机制
- ✅ 无需修改现有代码

**适用场景：**
- 保持现有代码兼容
- 不确定使用哪种方式
- 需要容错机制

## API 端点对照表

| 方法 | 端点 | Content-Type | 说明 |
|------|------|--------------|------|
| `uploadWorkerScriptMultipart` | `PUT /accounts/{account_id}/workers/scripts/{script_name}` | `multipart/form-data` | 官方推荐 |
| `uploadWorkerScriptContent` | `PUT /accounts/{account_id}/workers/scripts/{script_name}/content` | `application/javascript` | 仅更新代码 |
| `uploadWorkerScript` | `PUT /accounts/{account_id}/workers/scripts/{script_name}` | `application/javascript` | 向后兼容 |

## 支持的文件类型

- **JavaScript**: `.js`, `.mjs` → `application/javascript+module`
- **Python**: `.py` → `text/x-python`
- **WebAssembly**: `.wasm` → `application/wasm`

## WorkerMetadata 配置选项

```kotlin
data class WorkerMetadata(
    // 主模块文件名（必须与上传的文件名匹配）
    val mainModule: String? = null,
    
    // 兼容性日期（格式：YYYY-MM-DD）
    val compatibilityDate: String? = null,
    
    // 兼容性标志（如：nodejs_compat, streams_enable_constructors）
    val compatibilityFlags: List<String>? = null,
    
    // 使用模型（bundled 或 unbound）
    val usageModel: String? = null,
    
    // 绑定（KV、R2、D1、服务等）
    val bindings: List<WorkerBinding>? = null,
    
    // 环境变量
    val vars: Map<String, String>? = null,
    
    // 启用日志推送
    val logpush: Boolean? = null,
    
    // Tail 消费者
    val tailConsumers: List<TailConsumer>? = null
)
```

## WorkerBinding 类型

### KV Namespace
```kotlin
WorkerBinding(
    type = "kv_namespace",
    name = "MY_KV",
    namespaceId = "your-namespace-id"
)
```

### R2 Bucket
```kotlin
WorkerBinding(
    type = "r2_bucket",
    name = "MY_BUCKET",
    bucketName = "my-bucket"
)
```

### D1 Database
```kotlin
WorkerBinding(
    type = "d1",
    name = "MY_DB",
    databaseId = "your-database-id"
)
```

### Service Binding
```kotlin
WorkerBinding(
    type = "service",
    name = "MY_SERVICE",
    service = "other-worker",
    environment = "production"
)
```

## 使用建议

1. **新建 Worker**：使用 `uploadWorkerScriptMultipart` 并提供完整的 metadata
2. **更新代码**：使用 `uploadWorkerScriptContent` 快速更新
3. **快速开发**：使用 `uploadWorkerScript` 自动选择最佳方式
4. **生产环境**：始终指定 `compatibilityDate` 确保稳定性
5. **使用绑定**：通过 metadata 配置 KV、R2、D1 等服务

## 错误处理

所有方法都返回 `Resource<WorkerScript>`：

```kotlin
when (val result = workerRepository.uploadWorkerScriptMultipart(...)) {
    is Resource.Success -> {
        val script = result.data
        println("Upload successful: ${script.id}")
    }
    is Resource.Error -> {
        println("Upload failed: ${result.message}")
    }
    is Resource.Loading -> {
        // 不会返回这个状态
    }
}
```

## 参考文档

- [Cloudflare Workers API](https://developers.cloudflare.com/api/operations/worker-script-upload-worker-module)
- [Multipart Upload Metadata](https://developers.cloudflare.com/workers/configuration/multipart-upload-metadata/)
- [Workers Bindings](https://developers.cloudflare.com/workers/configuration/bindings/)

---

**更新日期**: 2024-12-15  
**版本**: 2.0
