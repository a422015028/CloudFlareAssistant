# KV 绑定功能调试指南

## 问题诊断步骤

### 1. 查看日志输出

重新编译并安装APK后，执行以下操作：

#### 使用 ADB 查看实时日志：
```bash
adb logcat | findstr "WorkerRepository\|WorkerViewModel\|WorkerFragment"
```

或者更详细的：
```bash
adb logcat -s "WorkerRepository:D" "WorkerViewModel:D" "WorkerFragment:D"
```

### 2. 测试步骤

#### 测试1：上传新脚本并添加KV绑定
1. 打开 Worker 页面
2. 输入 Worker 名称（例如：`test-worker`）
3. 选择一个 .js 文件
4. 点击 **"+ 添加"** 按钮
5. 输入绑定名称（例如：`MY_KV`）
6. 从下拉列表选择一个 KV 命名空间
7. 点击 **"添加"**
8. 确认绑定出现在列表中
9. 点击 **"上传脚本"**
10. 观察日志和Toast消息

**期望日志输出：**
```
D/WorkerRepository: Uploading worker with 1 KV bindings
D/WorkerRepository: Adding KV binding: MY_KV -> [namespace-id]
D/WorkerRepository: Metadata: {"main_module":"test.js","compatibility_date":"2024-12-01","bindings":[{"type":"kv_namespace","name":"MY_KV","namespace_id":"[id]"}]}
D/WorkerRepository: Upload metadata JSON: [json内容]
D/WorkerRepository: Upload successful: test-worker
```

#### 测试2：为已有脚本配置KV绑定
1. 在已上传脚本列表中找到一个脚本
2. 点击脚本右侧的 **"KV"** 按钮
3. 在弹出对话框中点击 **"+ 添加绑定"**
4. 配置绑定并添加
5. 点击 **"应用配置"**
6. 观察日志和Toast消息

### 3. 常见问题排查

#### 问题1：没有KV命名空间可选
**症状：** 点击"添加"按钮后提示"暂无 KV 命名空间，请先创建"

**解决方法：**
1. 切换到 KV 页面
2. 点击 **"+"** 按钮创建一个新的 KV 命名空间
3. 返回 Worker 页面重试

#### 问题2：上传失败
**症状：** 显示"Upload failed: [错误信息]"

**检查项：**
1. **API Token 权限**
   - 登录 Cloudflare Dashboard
   - 检查 API Token 是否有 "Workers Scripts: Edit" 权限
   - 检查是否有 "Workers KV Storage: Edit" 权限

2. **脚本内容**
   - 确保脚本文件是有效的 JavaScript
   - 检查文件大小（Worker 脚本有大小限制）

3. **命名空间ID**
   - 在日志中查看 namespace_id 是否正确
   - 在 KV 页面确认该命名空间确实存在

#### 问题3：绑定未生效
**症状：** 上传成功，但在 Worker 代码中访问 `env.MY_KV` 报错

**检查项：**
1. **查看 Cloudflare Dashboard**
   - 登录 Cloudflare Dashboard
   - 进入 Workers & Pages → 你的 Worker
   - 查看 Settings → Variables 
   - 确认 KV Namespace Bindings 中有你配置的绑定

2. **检查日志中的 metadata**
   - 确认 JSON 中包含正确的 bindings 数组
   - 确认 namespace_id 正确

3. **测试代码**
   ```javascript
   export default {
     async fetch(request, env) {
       // 尝试访问 KV
       try {
         const value = await env.MY_KV.get('test-key');
         return new Response(`KV Value: ${value || 'null'}`);
       } catch (e) {
         return new Response(`Error: ${e.message}`, { status: 500 });
       }
     }
   }
   ```

### 4. 调试检查清单

- [ ] 已创建至少一个 KV 命名空间
- [ ] API Token 有正确的权限
- [ ] 能够成功上传不带绑定的 Worker
- [ ] 绑定名称使用了正确的格式（大写字母+下划线）
- [ ] 能看到日志输出 "Uploading worker with X KV bindings"
- [ ] 能看到日志输出 "Upload successful"
- [ ] Cloudflare Dashboard 中能看到绑定配置

### 5. 获取详细错误信息

如果问题持续，请提供以下信息：

1. **日志输出**（使用 adb logcat）
2. **Toast 消息内容**
3. **是否在 Cloudflare Dashboard 中能手动配置 KV 绑定**
4. **账号类型**（免费版/付费版）

### 6. 手动验证 API

你可以使用 curl 手动测试 Cloudflare API：

```bash
curl -X PUT "https://api.cloudflare.com/client/v4/accounts/{account_id}/workers/scripts/{script_name}" \
  -H "Authorization: Bearer {api_token}" \
  -H "Content-Type: multipart/form-data" \
  -F 'metadata={"main_module":"worker.js","bindings":[{"type":"kv_namespace","name":"MY_KV","namespace_id":"{namespace_id}"}]}' \
  -F 'worker.js=@worker.js;type=application/javascript+module'
```

替换：
- `{account_id}` - 你的账号ID
- `{script_name}` - Worker 名称
- `{api_token}` - API Token
- `{namespace_id}` - KV 命名空间ID

### 7. 已知限制

1. **免费账号限制**
   - 某些账号可能有 Worker 数量限制
   - 某些账号可能有 KV 使用限制

2. **命名空间限制**
   - 一个 Worker 最多可以绑定 100 个 KV 命名空间

3. **命名规范**
   - 绑定名称只能包含字母、数字和下划线
   - 建议使用大写字母（如 `MY_KV`）

## 下一步

如果按照以上步骤仍无法解决问题，请提供：
1. 完整的 logcat 日志输出
2. Cloudflare Dashboard 的截图（去掉敏感信息）
3. 具体的错误消息

这将帮助进一步诊断问题。
