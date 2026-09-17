## **v7.8.2 更新日志**

> **提示：** 本次更新无破坏性变更，可直接覆盖安装。

#### 修复：Worker Cron 触发器时区不准

- 修复按中国时间（UTC+8）设置的 Cron 表达式与实际运行时间不符的问题
- 提交 Cloudflare 前自动将 UTC+8 转换为 UTC，展示时再转回 UTC+8，保证按中国时间运行
- 修正 Cloudflare 周字段编号约定差异（Cloudflare 使用 1-7，1=周日；标准 cron 使用 0-6，0=周日）
- 支持小时字段的列表、范围、步长等语法，跨午夜时自动回退日期

#### 优化：Lint 问题大幅清理

- 修复 1500+ 条 Lint 问题（硬编码文本、国际化拼接、废弃 API、过度绘制、嵌套权重等）
- 仅剩 14 条有意保留（UseCompoundDrawables、IconLocation、IconLauncherShape）

本次更新包含 Worker Cron 时区修复、模板商店大字体适配、Zero Trust 交互优化及 CI / Lint 流程清理
