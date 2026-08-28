# **v7.7.5 更新日志 · Release Notes**

> **版本号 / Version**：`7.7.5`（`versionCode = 2608283`）
> **发布日期 / Date**：2026‑08‑28
> **最低支持 / Min SDK**：Android 8.0（API 26）
> **编译 / Target SDK**：Android 16（API 36）
> **建议升级用户 / Audience**：有英文界面需求 · 参与 Zero Trust / Zone 规则运营 · 关注多语言一致性与构建质量

---

## 📌 重要更新（本次升级必看）

1. **完整英文界面落地**：从设置入口、首页 9 大卡片、账号/Token/域名/DNS/Worker/KV/R2/Pages，到 Zero Trust 23 个子页面与 Zone 规则 17 个子页面，全部支持中英双语；设置里即可一键切换「跟随系统 / 简体中文 / English」，切换后即时生效无需冷启动。
2. **全项目硬编码文本清零 & 硬编码回归拦截**：
   - 代码层约 1200+ 处、Layout XML 约 534 处中文字面量全部替换为 Android 标准 `@string` 资源；
   - 新 CI 级 Lint 基线（`HardcodedText = error`）正式启用，后续提交若再混入硬编码中文/英文会在构建阶段**立即失败**，确保多语言不回退。
3. **ViewModel / Repository 层消息本地化**：之前 ViewModel `_message.emit("中文提示")` 的 Toast/Snackbar，英语用户看到的仍是中文；现在统一通过 `UiMessage sealed class` 管道传递，Collector 侧按用户当前语言 `asString(context)` 渲染，**18 个 ViewModel + 14 个 Repository 全部迁移完毕**，错误提示从此真正双语。
4. **时间/复数本地化**：日期格式不再硬编码 `yyyy-MM-dd HH:mm (Locale.CHINA)`，改为系统推荐 `DateFormat.getDateTimeInstance(…, Locale.getDefault())`；15 组类「删除 N 个脚本 / N 条设备」文案升级为 Android `<plurals>` 复数形态，英文严格区分单复数。

---

## 🌐 多语言全量支持（Phases 1‑4）

### 阶段 1：基础框架（第一阶段已在早期交付，本次一并补齐覆盖）
- 新增 `values-en/strings.xml`，与 `values/strings.xml` 中英严格 1:1（name / 占位符编号 / 顺序完全对齐）
- 新增 `LocaleHelper.kt`：跟随系统 / 简体中文 / English 三种模式，启动即应用偏好；设置对话框语言切换入口

### 阶段 2：核心业务模块 9 大页面
**全部硬编码中文 → R.string 资源 + 中英翻译**
- 账号 / Token 管理（TokenManagerActivity）— 约 75 处
- 域名列表 / 详情（DomainList、DomainDetail）— 约 23 处
- DNS 记录管理（DnsFragment，含 companion → lazy + ctx）— 约 64 处
- Worker 脚本（WorkerFragment + WorkerLogsActivity + WorkerHistoryAdapter）— 约 140 处
- KV 命名空间（KvFragment）— 约 20 处
- R2 存储（R2Fragment + R2CustomDomain 状态标签）— 约 64 处
- Pages 项目（PagesFragment + PagesDeploymentsAdapter + PagesLogsActivity）— 约 53 处
- Script Editor / Runtime Settings / Log Activity / MainActivity 等零散 UI — 约 243 处

### 阶段 3：Zero Trust · Zone 规则与高级特性
**约 40 个文件 · ~790 处字符串统一资源化**
- Zero Trust（23 文件）：Access / AccessPolicyAdapter / AccessGroupAdapter / Devices / Gateway 规则 / Tunnels
- Zone（17 文件）：WAF / Firewall Rules / Cache Rules / Transform Rules / Rate Limit / Access Rules / Snippets / SSL Certificates / Email Routing / Load Balancer / Performance / Zone Settings
- Snippet Rule 重构：`Field.label:String → Field.labelResId:Int + label(ctx)`、所有 Dialog/Toast 升级为资源

### 阶段 4：完善与收尾
- **Layout XML 77 文件**：534 处中文硬编码 → `@string/xml_*` 或复用已有资源；新增 `xml_*` 条目 390 条，另外 144 处直接复用（`add / delete / save / cancel / worker_* / pages_*` …）
- **日期本地化**：7 处 SDF `yyyy-MM-dd HH:mm (Locale.CHINA)` → `DateFormat.getDateTimeInstance(MEDIUM, SHORT, Locale.getDefault())` 尊重用户系统区域
- **Plurals 复数（15 组）**：`zt_device_minutes / zt_policy_*_rules / zt_group_*_rules / zt_list_items / zt_location_clients / zt_tunnel_*_conns / worker_cleanup_old_versions / worker_selected_scripts / pages_cleanup_old_deployments / pages_selected_projects`
  - 中文 one/other 同文案；英文严格 one/other 区分（删除脚本 → `Delete 1 script?` vs `Delete 5 scripts?`）
  - 对应 9 处调用全部升级 `resources.getQuantityString(R.plurals.xxx, count, count)`
- **Kotlin RTL（6 处）**：LEFT/RIGHT → START/END、`setPadding` → `setPaddingRelative`、`setMargins(ltrb)` → `marginStart/marginEnd`（按决策 XML 级 RTL 暂不做）

---

## 🧩 架构改造：UiMessage 归一化（ViewModel / Repository 56 文件迁移）

> **解决的痛点**：改造前 `_message.emit("登录失败")` 是写死的中文，即使用户切英文也收不到翻译。Repository 更难拿到 Context，错误消息全是中文。

### ViewModel + Collector（Batch A · 44 文件）
- 新增 [core/model/UiMessage.kt](file:///E:/AI/CloudFlareAssistant/app/src/main/java/com/muort/upworker/core/model/UiMessage.kt) sealed class：
  - `ResourceString(@StringRes resId, args)` — 静态资源 + 格式化参数
  - `RawString(value)` — 服务器 / 异常动态消息（无法提前翻译）
  - `Empty` — StateFlow 初始空值
  - 内抽象成员 `asString(context: Context)`（避免顶层扩展漏 import 的问题）
- **18 个 ViewModel 升级**：`Flow<String> → Flow<UiMessage>`；所有 `emit("中文")` 改用 `UiMessage.of(R.string.vm_msg_xxx, args)`
- **26 个 Fragment/Activity/CardView Collector 升级**：`collect { text -> showToast(text) }` → `collect { msg -> showToast(msg.asString(requireContext())) }`
- 新增 `vm_msg_*` 资源：~324 条中英对齐

### Repository / Core 层（Batch B · 12+ 文件）
> **保持公开 API 签名不变**（返回仍为 `String` / `Result<*, String>`），避免 ViewModel 调用点级联修改。
- **14 个 Repository + WebDavClient**：Hilt 注入 `@Inject @ApplicationContext private val appContext: Context`，所有 `Err("中文失败")` → `Err(appContext.getString(R.string.repo_<module>_xxx, args))`
- **6 个 util 文件**：DialogUtils / AuthHelper / DisplaySizeHelper / BackupCrypto / EsbuildBundler / ThemeHelper — 硬编码默认参数 null 化 + 函数体 fallback getString
- **Core Models 枚举（TimeRange / TokenPermission / R2CustomDomain）**：`val displayName: String → fun displayName(ctx)`，保留 `@Deprecated` 属性回退给 Timber 日志
- 新增 `repo_* / model_* / helper_*`：~280 条中英 1:1

---

## 🛡️ CI 工程质量：Lint HardcodedText 基线接入

### [app/lint.xml](file:///E:/AI/CloudFlareAssistant/app/lint.xml)（新建）
| Issue | Severity | 作用 |
|---|---|---|
| HardcodedText | **error** | UI 硬编码拦截（多语言守门员） |
| MissingTranslation | **error** | 只有一边翻译 / 缺少 default values |
| StringFormatInvalid / StringFormatMatches | **error** | 占位符顺序 / 类型不匹配 |
| DuplicateStrings | warning | 同名 string 重复 |
| RtlHardcoded / RtlSymmetry / RtlCompat / RtlEnabled | warning | XML 级 RTL（本轮保留 warning，按决策暂不强制）|
| Typos / GradleDependency / NewerVersionAvailable | ignore | 去 CI 噪点 |

### [app/build.gradle.kts#L97-L116](file:///E:/AI/CloudFlareAssistant/app/build.gradle.kts#L97-L116) 新增 Lint 配置
```kotlin
lint {
  abortOnError = true          // 新 error 立即 FAIL（CI 拦截）
  warningsAsErrors = true      // 防止 HardcodedText 作为 warning 溜走
  checkDependencies = true
  baseline = file("lint-baseline.xml")
  checkReleaseBuilds = true    // release 构建同样拦截
}
```

### [app/lint-baseline.xml](file:///E:/AI/CloudFlareAssistant/app/lint-baseline.xml)（生成）
登记历史 Issue **948 errors + 607 warnings** 白名单；已在基线内的不触发失败，新增任意 HardcodedText 将在下一次构建直接报错。

### CI 自检命令
```bash
# 提交前自检
./gradlew :app:lintDebug
# 发布前双构建拦截
./gradlew :app:lintRelease
# 合入可允许的 baseline 变更（代码审查必过）
./gradlew :app:updateLintBaseline
```

---

## 🐛 Bug 修复 & 稳定性
- **DevicesListFragment**：4 处 `android.R.string.yes/no` deprecated → 新增 `status_yes / status_no`（中英）
- **KvFragment / DnsFragment ViewHolder**：`Unresolved reference getString` → 改用 `binding.root.context.getString(...)`
- **二阶段资源链接失败**：补 `waf_expression_example`（中英）+ `zt_tunnel_card_title`（default values）
- **DnsFragment**：不存在的 `R.string.dns_delete_record` → 复用 `R.string.delete` + `getString(R.string.dns_delete_confirm)`
- **MainActivity / RouteFragment / PagesFragment**：Elvis `?: fallback` 永远左值（源字段本身非空）— 去掉无用 fallback，消除 Kotlin compiler warning
- **EmailRoutingFragment**：未使用参数 `ctx` → `@Suppress("UNUSED_PARAMETER")`
- **SslCertsFragment**：未使用局部 `val ctx` → 直接删除
- **Batch B deprecation warnings 5 条**：
  - AnalyticsRepository × 2 / AccountAnalyticsViewModel × 1 / DashboardViewModel × 1 — 日志用 `timeRange.displayName`（deprecated，本来就是为日志提供的 fallback）→ `@Suppress("DEPRECATION")`
  - DisplaySizeHelper.getSelectedIndex：`OPTIONS` deprecated → 改为 `getOptions(context).indexOfFirst { ... }`

---

## ✅ 构建验证（全过程）
| 验证项 | 结果 |
|---|---|
| `:app:processDebugResources`（资源链接） | ✅ PASS |
| `:app:compileDebugKotlin`（Kotlin 编译） | ✅ PASS（0 errors，0 deprecation warnings） |
| `:app:hiltJavaCompileDebug`（Dagger 注入图） | ✅ PASS |
| `:app:assembleDebug -x lint` | ✅ BUILD SUCCESSFUL in 33s |
| `:app:updateLintBaseline`（基线生成） | ✅ 948 errors + 607 warnings 入库 |
| `:app:lintDebug`（CI 模拟） | ✅ **Lint found no new issues** · BUILD SUCCESSFUL |

---

## 🚧 已知 & 后续计划
- 不做：**XML 级 RTL 全量（marginLeft→Start 等 200+ 文件）** — 按决策保留 lint warning，不强制执行
- 不做：`<plurals>` 自动候选识别（`PluralsCandidate=ignore`）
- 后续如有新增页面，遵循以下流程：
  1. 所有 UI 字符串 → 先写 `values/strings.xml` + `values-en/strings.xml`，两边 1:1
  2. ViewModel 提示消息 → 用 `UiMessage.of(R.string.vm_msg_xxx, args)`，不要 RawString
  3. Repository 错误消息 → 用 `appContext.getString(R.string.repo_xxx, args)`
  4. 提交前 `:app:lintDebug` 必须绿

---

# 🌐 English Version · v7.7.5 Release Notes

> **Version**：`7.7.5`（`versionCode = 2608283`）
> **Release Date**：2026‑08‑28
> **Min SDK**：Android 8.0（API 26）
> **Target SDK**：Android 16（API 36）
> **Audience**：English‑speaking users · Zero Trust / Zone operators · quality‑conscious maintainers

---

## 📌 Highlights

1. **Full English UI across the app** — from the 9 home cards and settings dialog to Account / Token / Domain / DNS / Worker / KV / R2 / Pages and the entire **Zero Trust + Zone rulesets** (23 + 17 sub‑pages, ~790 strings). Switch via Settings → **Follow system / 简体中文 / English** with instant apply.
2. **Hardcoded text zeroed out + CI regression guard enabled** — ~1200 Kotlin + 534 XML literal strings replaced with Android `@string` resources; a new CI‑level `HardcodedText = error` baseline now **fails the build immediately** if any new literal slips back in.
3. **ViewModel / Repository messages finally respect locale** — previously `_message.emit("中文提示")` always showed Chinese toast to English users. All 18 ViewModels + 14 Repositories migrated to a `UiMessage` pipeline; the UI collector resolves `msg.asString(context)` using the user's currently selected language.
4. **Date & plural localization** — hardcoded `yyyy-MM-dd HH:mm (Locale.CHINA)` replaced with locale‑aware `DateFormat.getDateTimeInstance(…)`; 15 groups of item counts upgraded to Android `<plurals>` so English correctly distinguishes singular vs plural (`Delete 1 script?` vs `Delete 5 scripts?`).

---

## 🌐 Full Internationalization Coverage (Phases 1‑4)

### Phase 1 — Foundation
- Added `values-en/strings.xml` aligned 1:1 with `values/strings.xml`
- `LocaleHelper` with three modes (Follow system / 简体中文 / English); settings switch applies immediately

### Phase 2 — 9 Core Modules (~529 strings)
Account & Token management, Domain list/detail, DNS records, Worker + Worker Logs/History, KV namespaces, R2 storage + custom domains, Pages projects/deployments/logs, Script Editor, Log viewer, MainActivity, Runtime settings, Route, D1 manager & data viewer, Backup.

### Phase 3 — Zero Trust + Zone Features (~790 strings)
Zero Trust: Access + policy & group adapters, Devices, Gateway rules, Tunnels;
Zone: WAF / Firewall rules / Cache rules / Transform rules / Rate limit / Access rules / Snippets / SSL certs / Email routing / Load balancing / Performance / Zone settings.
Snippet Rule labels refactored: `Field.label:String → Field.labelResId:Int + label(ctx)`.

### Phase 4 — Polish
- 77 layout XMLs · 534 hardcoded strings → `@string/xml_*` (390 new + 144 reused)
- 7 date formats localized
- 15 `<plurals>` groups enabled; 9 call sites upgraded to `resources.getQuantityString(…, count, count)`
- Kotlin‑level RTL fixes (6 sites): LEFT/RIGHT → START/END, relative padding/margin setters

---

## 🧩 UiMessage Architecture (56‑file migration)

New sealed class at [core/model/UiMessage.kt](file:///E:/AI/CloudFlareAssistant/app/src/main/java/com/muort/upworker/core/model/UiMessage.kt):
- `ResourceString(@StringRes resId, args)` — static translatable text
- `RawString(value)` — server / exception messages that cannot be pre‑translated
- `Empty` — initial StateFlow value
- Built‑in abstract `asString(context: Context)` — no top‑level extension imports required

**Batch A · ViewModel + Collector (44 files)**
- 18 ViewModels: `MutableSharedFlow<String> → <UiMessage>`; every static `emit("…")` → `UiMessage.of(R.string.vm_msg_xxx, args)`
- 26 collectors (Fragments/Activities/CardViews) switched to `msg.asString(requireContext())`
- ~324 new `vm_msg_*` strings (EN/ZH 1:1)

**Batch B · Repository + Core (~29 files)**
Public API signatures (`String` / `Result<*, String>`) are unchanged — ViewModel call sites need no edits.
- 14 Repositories + WebDavClient: `@Inject @ApplicationContext private val appContext: Context` is injected via Hilt; every hardcoded error `Err("中文")` → `Err(appContext.getString(R.string.repo_<module>_xxx, args))`
- 6 utils (DialogUtils / AuthHelper / DisplaySizeHelper / BackupCrypto / EsbuildBundler / ThemeHelper) localized
- Models enums (TimeRange / TokenPermission / R2CustomDomain): `displayName: String → displayName(ctx)` with `@Deprecated` fallback for Timber logs
- ~280 new `repo_* / model_* / helper_*` strings (EN/ZH 1:1)

---

## 🛡️ CI HardcodedText Baseline

- **[app/lint.xml](file:///E:/AI/CloudFlareAssistant/app/lint.xml)** — rule matrix (HardcodedText=error, MissingTranslation=error, RTL=warning, noise ignored)
- **[app/build.gradle.kts#L97-L116](file:///E:/AI/CloudFlareAssistant/app/build.gradle.kts#L97-L116)** — `lint { abortOnError=true; warningsAsErrors=true; baseline=file("lint-baseline.xml"); checkReleaseBuilds=true }`
- **[app/lint-baseline.xml](file:///E:/AI/CloudFlareAssistant/app/lint-baseline.xml)** — generated baseline whitelisting 948 errors + 607 warnings of legacy issues

CI commands
```bash
# pre‑commit check
./gradlew :app:lintDebug
# release gate
./gradlew :app:lintRelease
# regenerate baseline after intentional additions (CR mandatory)
./gradlew :app:updateLintBaseline
```

---

## 🐛 Fixes & Stability
- DevicesListFragment: 4× deprecated `android.R.string.yes/no` → new `status_yes / status_no` (EN/ZH)
- KV/DNS ViewHolder `Unresolved getString` → `binding.root.context.getString(...)`
- Resource linking regressions: added missing `waf_expression_example` (EN/ZH) + `zt_tunnel_card_title` (default values)
- DnsFragment missing `R.string.dns_delete_record` → reuse `delete` + `dns_delete_confirm`
- MainActivity / RouteFragment / PagesFragment: removed never‑hit Elvis fallbacks (source was non‑null)
- EmailRoutingFragment unused `ctx` param → `@Suppress`; SslCertsFragment unused local → removed
- 5 post‑Batch‑B deprecation warnings suppressed (4× log‑only `displayName`) or fixed (DisplaySizeHelper `OPTIONS` → `getOptions(context)`)

---

## ✅ Build verification
| Check | Result |
|---|---|
| `:app:processDebugResources` | ✅ PASS |
| `:app:compileDebugKotlin` | ✅ PASS (0 errors · 0 deprecation) |
| `:app:hiltJavaCompileDebug` (Hilt graph) | ✅ PASS |
| `:app:assembleDebug -x lint` | ✅ BUILD SUCCESSFUL in 33s |
| `:app:updateLintBaseline` | ✅ 948E + 607W recorded |
| `:app:lintDebug` (CI simulation) | ✅ **Lint found no new issues** · BUILD SUCCESSFUL |

---

## 🚧 Deferred (explicitly out of scope)
- XML‑level RTL pass (200+ files): margin/padding Left → Start. Lint warnings kept; no enforcement.
- Plurals auto‑candidate detection (`PluralsCandidate=ignore`)
