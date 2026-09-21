## **v7.8.5 更新日志**

> **提示：** 本次更新无破坏性变更，可直接覆盖安装。

#### 新增：应用内日志功能

- 新增 InAppLogTree 日志树，将 Timber 日志转发到应用内存储，与 HTTP 日志一同展示
- 应用内日志支持按级别过滤（应用日志/HTTP 日志/错误日志/全部），便于快速定位问题
- 新增日志导出功能，可将日志保存为文件用于问题反馈
- 调整 ProGuard 规则，保留 Timber.d/i/w 调用，确保 Release 包中应用内日志开关仍可正常捕获日志

#### 新增：S3 客户端凭据支持

- 实现令牌 SHA-256 哈希生成，支持基于 API Token 派生 S3 客户端访问密钥
- 更新令牌管理弹窗，新增 S3 凭据（Access Key / Secret Key）展示与一键复制

#### 优化：API 错误处理统一化

- 新增 resolveApiError 工具函数，统一提取与格式化 API 错误信息
- 移除多处冗余的错误信息解析代码，替换为统一调用，减少重复逻辑
- 统一 API 错误处理流程并添加调试日志，便于排查接口异常

#### 优化：调试日志增强

- 在多处业务逻辑中添加详细的调试日志（开始/成功/失败三态），覆盖 D1、备份、令牌、DNS、R2、KV、Workers、Pages、Access、Gateway 等核心模块
- 日志记录关键标识符（账号 ID、Zone ID、资源名称等），不记录 Token、密钥等敏感数据

#### 修复：账号编辑返回逻辑

- 调整账号编辑页面的返回逻辑，等待操作结果返回后再导航返回，避免保存未完成时退出导致数据丢失

#### 修复：资源列表分页缺失

- 修复 Pages 项目列表仅显示 10 条的问题：Pages Projects 接口 `per_page` 上限为 10，实现翻页循环拉取全部项目
- 修复 Pages 部署列表分页：Pages Deployments 接口 `per_page` 上限为 25，实现翻页循环拉取全部部署
- 为 Workers Scripts、DNS Records、KV、D1、Access、Gateway、Devices、Tokens、Email、SSL、Snippets 等列表接口补充 `per_page` 参数（值经 Cloudflare OpenAPI 与实际请求双重验证）
- 为 `CloudFlareResponse` 新增 `result_info` 分页字段，支持判断总页数并循环翻页

本次更新包含应用内日志捕获与导出、S3 客户端凭据支持、API 错误处理统一化、全模块调试日志增强、账号编辑返回逻辑修复及资源列表分页修复
