## 新特性
- ✨ 支持 Cloudflare 官方 Worker 脚本上传 API 的所有方式
  - **Multipart 上传**（推荐）：支持完整的 metadata 配置，包括 KV、R2、D1 等绑定
  - **Content-Only 上传**：快速更新脚本代码，不影响现有配置
  - **智能上传**：自动选择最佳上传方式，向后兼容
- 🔧 支持多种脚本文件类型：JavaScript (.js, .mjs)、Python (.py)、WebAssembly (.wasm)
- 📦 新增 Worker 配置选项：
  - 环境变量 (vars)
  - 兼容性设置 (compatibility_date, compatibility_flags)
  - 资源绑定 (KV Namespace, R2 Bucket, D1 Database, Service Binding)
  - 日志推送 (Logpush)、Tail 消费者

## 改进
- 🚀 优化上传性能和可靠性
- 📚 新增完整的 API 使用文档
- 🔄 保持向后兼容，现有代码无需修改

## 下载
下载 **app-release.apk** 安装包即可使用（已签名，可直接安装）

## 版本信息
- 版本号: 5.2
- Build: 2025121502
- 文件大小: 6.62 MB
- 最低 Android 版本: 7.0 (API 25)
- 目标 Android 版本: 14 (API 34)

## 签名信息
此版本已使用开发者证书签名，可以直接安装使用。
