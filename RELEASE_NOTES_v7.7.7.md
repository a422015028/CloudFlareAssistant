### **v7.7.7 更新日志**

#### 新增：Worker 脚本功能开关

- Worker 卡片第三排新增「功能开关」按钮，支持在对话框中独立配置子域名、可观测性与 Logs 持久化三项开关
- 新增 `/script-settings` 与 `/subdomain` 两组 GET 接口封装，打开对话框时并行读取当前值并回填，未加载完成前禁用开关与保存按钮
- 任意开关修改后统一 PATCH 或 POST 回 Cloudflare；PATCH 脚本设置时保留 baseline 的 destinations / invocation\_logs / tags / tail\_consumers / logpush 字段，避免误清空其他配置
- 失败时回滚 Switch 状态并展示错误对话框，避免 UI 与服务端不一致

#### 新增：Zone 选择下拉 + 通配符自动填（域名 / 路由 五入口统一）

- Worker 卡片添加自定义域、Pages 卡片添加自定义域、RouteFragment 自定义域卡片添加、RouteFragment 路由添加、R2 存储桶管理自定义域添加，全部接入同一套 Material3 ExposedDropdownMenu Zone 选择器
- 选中 Zone 后自动填入与场景匹配的 glob 模式：添加域名类填 `*.{zone.name}`（通配子域名），路由添加类填 `{zone.name}/*`（路径通配）
- Zone 列表按 `zone.name` 字母序排序；加载中/空列表/失败三种状态均有独立文案，并以 Toast 提示加载失败原因

#### 新增：Worker 部署三阶段后流程

- Worker 脚本上传成功后串联执行三阶段：写入 Observability 设置、启用自定义子域名、将最新版本 100% 切换到生产流量
- 自动探测 Node.js 兼容标志（nodejs\_compat / nodejs\_compat\_v2），对包含 `node:` 导入的脚本自动附加标志并在重试路径保留
- 部署对话框与部署结果页新增对应国际化字符串、进度与错误提示

#### 新增：远程文件下载安全校验与基础能力增强

- RemoteFileDownloader 新增 SSRF 防护：严格校验协议（仅 http/https）、IP（禁止内网、回环、链路本地）、Host 与端口，并对最终重定向地址做二次检查
- 新增 Content-Type 白名单校验，下载前校验 Content-Length ≤ 25MB，下载中流式校验实际大小，超出立即终止
- 新增基于 BouncyCastle 的 Blake3 资产哈希计算与 SHA-256 fallback，并引入 JWT 过期自动刷新、指数退避重试器等通用工具类
- Pages/Worker 页面新增「允许本地 URL 访问」开关并持久化到本地偏好，默认关闭，按需放行 file:/// / 内网 http(s)

#### 新增：R2 文件上传与绑定复用

- R2 存储桶上传对话框支持批量选择本地文件，统一配置路径前缀、自动绑定复用、Content-Type 推断与进度提示
- 新增绑定创建复用相关多语言字符串；绑定 PATCH 路径保留旧 ES Module / metadata，避免 10021 语法错误与字段被意外清空

#### 优化：Worker 上传流程统一 & 配置保留修复

- 旧 `uploadWorkerScript` 标记废弃，所有上传入口统一走 `uploadWorkerScriptWithBindings` 链路
- KV/R2/D1/Service 绑定与兼容性标志、placement、tags 等字段在重上传 / 重部署时从 baseline 保留修复，避免 PATCH/多部分请求把 ES Module 或 D1 元数据清空
- 新增单元测试覆盖保留逻辑，验证绑定与配置未被误清空

#### 优化：对话框与列表 UI 统一 Material3 风格

- 全工程原生 `AlertDialog.Builder`、旧版 Dialog 调用统一切换到 `MaterialAlertDialogBuilder`，配合全局弹窗主题支持动态取色
- 新建代码片段、R2 添加自定义域、域名添加对话框等 UI 从「裸 EditText / FrameLayout setMessage」改为 OutlinedBox TextInputLayout + TextInputEditText / ExposedDropdownMenu 标准样式，支持悬浮 hint、错误红框、helperText 常驻提示
- 脚本卡片 Row 1 / Row 2 / Row 3 全部改为 `layout_width=0dp + layout_weight=1` 均分布局，搭配 `baselineAligned=false + gravity=center_vertical`，英文长文本或系统大字下按钮不再溢出屏幕、图标始终水平齐平
- Worker / Pages / R2 / Route 多处输入对话框统一内边距（24/16/24/8 dp），单行高度 52dp、EditText 内边距 16/14/16/14 dp

#### 优化：Lint 基线与资源清理

- 清理本轮改动引入的 3 处 UnusedResources（worker\_feature\_loading / r2\_add\_custom\_domain\_message / r2\_please\_enter\_domain）
- 修复 `dialog_domain_input.xml` 四个 AutoCompleteTextView 缺失 `LabelFor` 的无障碍错误，统一补齐与外层 TextInputLayout 相同的 hint
- 执行 `:app:lintDebug` 通过（0 error），同步更新 `lint-baseline.xml`

本次更新完成 Worker 功能开关、Zone 选择下拉五入口统一、Worker 上传后三阶段链路、R2 上传、远程下载安全校验、Material3 UI 风格统一以及 Lint 问题修复，建议所有使用自定义域、路由、R2 上传与远程脚本部署的用户升级。
