### **v7.7.5 更新日志**

#### 新增：全模块英文界面与语言切换
- Zero Trust、Zone 规则、Snippets、SSL、Email Routing、Load Balancer、Performance、D1、Backup、Route 等 40+ 页面全部补齐英文
- 15 组复数文案（如"删除 N 个脚本 / N 条设备"）升级为 Android `<plurals>` 资源，英文严格区分单复数
- 设置对话框"跟随系统 / 简体中文 / English"三选项保留，切换即时生效

#### 新增：UI 提示消息多语言管道
- 新增 `UiMessage sealed class`（`ResourceString / RawString / Empty` + `asString(context)`）统一承载 Toast / Snackbar / 错误提示
- 18 个 ViewModel 的 `_message / _error` 从 `String` 升级为 `UiMessage`，Collector 侧按当前语言解析
- 14 个 Repository + WebDavClient 通过 Hilt 注入 `@ApplicationContext`，内部错误直接调用 `appContext.getString(...)` 返回本地化文本；对外签名保持不变，ViewModel 零改动

#### 新增：CI 级硬编码文本拦截（Lint 基线）
- 新增 `app/lint.xml`：`HardcodedText`、`MissingTranslation`、`StringFormatInvalid` 均设为 error 级
- `app/build.gradle.kts` 启用 `lint { abortOnError = true; warningsAsErrors = true; baseline = file("lint-baseline.xml") }`
- 生成 `lint-baseline.xml` 历史 Issue 白名单（948 errors + 607 warnings），新混入的硬编码 UI 文本将在构建阶段直接失败，避免多语言回退

#### 优化：日期格式本地化
- 全项目 7 处 `SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)` 替换为 `DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())`，尊重用户系统区域与语言

#### 优化：全项目字符串资源化收尾
- 77 个 Layout XML 共 534 处中文硬编码 → `@string/xml_*` 或复用已有资源；新增 `xml_*` 条目 390 条，144 处直接复用（`add/delete/save/cancel/worker_*/pages_*` 等）
- Kotlin UI 零散硬编码 243 处（MainActivity、Log、Route、D1、Backup、ScriptEditor、RuntimeSettings 等）→ 全部替换为 `ctx.getString(R.string.xxx)`
- ViewModel 静态提示新增 `vm_msg_*` 资源 ~324 条；Repository/Core 新增 `repo_* / model_* / helper_*` 资源 ~280 条；values 与 values-en 严格 1:1 对齐
- Snippet Rule `Field.label` 由 `String` 重构为 `labelResId:Int + label(ctx)`，彻底消除内部中文字面量

#### 修复：Kotlin 编译与 Lint 告警
- `DevicesListFragment` 中 4 处 `android.R.string.yes/no` deprecated → 新增 `status_yes / status_no`（中英）
- `KvFragment` 与 `DnsFragment` 的 ViewHolder 内直接 `getString(...)` Unresolved → 改为 `binding.root.context.getString(...)`
- 二阶段资源链接失败：补 `waf_expression_example`（中英）+ `zt_tunnel_card_title`（默认 values）
- `DnsFragment` 引用不存在的 `R.string.dns_delete_record` → 复用 `R.string.delete` 与 `R.string.dns_delete_confirm`
- `MainActivity / RouteFragment / PagesFragment` 中 Elvis `?: fallback` 永远返回左值（源字段本身非空）→ 删除无用 fallback，消除 warning
- `EmailRoutingFragment` 未使用 `ctx` 参数 → `@Suppress("UNUSED_PARAMETER")`；`SslCertsFragment` 未使用 `val ctx` → 直接删除
- Batch B 后 5 条 deprecation warning：AnalyticsRepository/Dashboard/AccountAnalyticsViewModel 中用于 Timber 日志的 `TimeRange.displayName` 加 `@Suppress("DEPRECATION")`；`DisplaySizeHelper.getSelectedIndex` 改为 `getOptions(context).indexOfFirst { ... }`，彻底消掉 OPTIONS 引用

本次更新完成全项目多语言适配、ViewModel/Repository 错误提示本地化以及 Lint 基线 CI 级回归防护，建议有英文使用需求与代码质量合规需求的用户升级。
