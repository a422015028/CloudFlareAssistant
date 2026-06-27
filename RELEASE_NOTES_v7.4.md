# Release v7.4 — 更新说明 (2026-06-27)

## 概述
本次 v7.4 发布专注于新增 **Global API Key** 认证方式，提升账号管理的灵活性，同时支持自动获取 Account ID，简化配置流程。

## 主要更新

### 1. Global API Key 认证支持
- 新增 **认证类型选择**：在账号编辑页面支持选择 API Token 或 Global API Key 两种认证方式
- **凭据同时保存**：API Token 和 Global API Key 可以同时填写并保存，随时切换使用
- **认证方式切换**：通过单选按钮直接切换当前使用的认证方式，无需重新填写凭据

### 2. 自动获取 Account ID
- 在 Account ID 输入框旁新增 **"自动获取"** 按钮
- 支持通过 API Token 或 Global API Key 自动获取账号列表
- 单个账号时自动填充 Account ID 和名称，多个账号时弹出选择对话框

### 3. 认证方式说明
- **API Token**：使用 `Authorization: Bearer xxx` 认证，权限可精确控制
- **Global API Key**：使用 `X-Auth-Email` + `X-Auth-Key` 认证，拥有账号全部权限

### 4. 向后兼容性
- 现有账号默认使用 API Token 认证方式，不受影响
- 数据库自动迁移（v6 → v7），无需手动操作

## 受影响/变更文件

### 布局：
- `app/src/main/res/layout/fragment_account_edit.xml` — 新增认证类型选择器、Global API Key 输入区域、自动获取 Account ID 按钮

### 代码：
- `app/src/main/java/com/muort/upworker/core/model/Models.kt` — 新增 `AuthType` 枚举、`AccountInfo` 数据模型
- `app/src/main/java/com/muort/upworker/core/database/AppDatabase.kt` — 数据库迁移 v6 → v7，新增 email、globalApiKey、authType 字段
- `app/src/main/java/com/muort/upworker/core/network/CloudFlareApi.kt` — 新增 `listAccounts` API 接口，所有 API 支持两种认证方式
- `app/src/main/java/com/muort/upworker/core/util/AuthHelper.kt` — 新建认证辅助类，根据认证类型返回正确的 HTTP Headers
- `app/src/main/java/com/muort/upworker/core/repository/AccountRepository.kt` — 新增 `fetchAccountsFromApi` 方法
- `app/src/main/java/com/muort/upworker/feature/account/AccountEditFragment.kt` — 认证类型切换逻辑、自动获取 Account ID 功能
- `app/src/main/java/com/muort/upworker/feature/account/AccountViewModel.kt` — 新增 `fetchAccountsFromApi` 方法
- 所有 Repository 文件 — 更新为使用 AuthHelper 进行认证调用

## 使用说明

### 添加新账号
1. 在账号编辑页面选择认证方式（API Token / Global API Key）
2. 填写对应凭据（可同时填写两种）
3. 点击"自动获取"按钮自动填充 Account ID
4. 保存账号

### 切换认证方式
1. 编辑已保存的账号
2. 通过单选按钮切换认证方式
3. 保存即可生效
