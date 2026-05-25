# Release v7.3 — 更新说明 (2026-05-25)

## 概述
本次 v7.3 发布专注于在 Workers 与 Pages 列表中新增“批量管理/批量删除”功能，修复与简化了相关 UI 与编译问题，并优化了交互体验与提示。

## 主要更新
- 批量删除（Workers 脚本 & Pages 项目）
  - 在列表标题处新增“批量管理 / 全选 / 批量删除”按钮组，进入“选择”模式后可以点选多项并统一删除。
  - 按钮行为调整：默认显示为“批量管理”；未进入批量管理模式时隐藏“全选”；点击“批量管理”后按钮文字切为“取消”，并显示“全选”。
  - 删除前显示确认对话框，单条与多条删除提示更明确。
。

## 受影响/变更文件（示例）
- 布局：
  - `app/src/main/res/layout/fragment_worker.xml` — 新增批量管理工具条（批量管理/全选/批量删除 / 批量管理状态）
  - `app/src/main/res/layout/fragment_pages.xml` — 同上
- 代码：
  - `app/src/main/java/.../feature/worker/WorkerFragment.kt` — 批量管理模式切换、UI 更新、批量删除实现
  - `app/src/main/java/.../feature/pages/PagesFragment.kt` — 批量管理模式切换、UI 更新、批量删除实现