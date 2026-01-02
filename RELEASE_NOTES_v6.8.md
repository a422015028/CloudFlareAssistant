## 🎉 核心新功能
新增  CloudFlare Assistant 添加了强大的可观测性与数据可视化功能，让您随时随地在移动端查看 Cloudflare 服务的关键指标和健康状态。

### 📊 D1 数据库监控

全新的 D1 数据库监控功能，实时掌握 Cloudflare D1 数据库使用情况：

- **D1 已读行数** 📖: 过去 24 小时总读取行数（主要计费指标）
- **D1 已写行数** ✍️: 过去 24 小时总写入行数（主要计费指标）
- **D1 总存储** 💾: 所有数据库的存储空间占用
- **D1 数据库数量** 🗄️: 账户下的数据库总数

**技术实现**:
- 通过 GraphQL Analytics API 获取实时行数统计
- 通过 REST API 获取精确的存储和数量信息
- 支持自定义时间范围查询（1天/7天/30天）

### 🗄️ R2 存储监控

新增 Cloudflare R2 对象存储全方位监控，优化存储成本：

- **R2 A类操作** 📝: 写入/变更/列表操作（PutObject, DeleteObject, ListBuckets 等）
- **R2 B类操作** 📥: 读取操作（GetObject, HeadObject 等）
- **R2 总存储** 💽: 所有存储桶的总空间占用
- **R2 存储桶数量** 🪣: 账户下的存储桶总数

**智能分类**:
- A类操作：ListBuckets, ListObjects, PutObject, DeleteObject, CopyObject 等
- B类操作：GetObject, HeadObject, HeadBucket, GetBucket* 等
- 完全符合 Cloudflare R2 官方计费分类标准

**技术实现**:
- 使用 `r2OperationsAdaptiveGroups` 查询，按 `actionType` 智能分类
- 使用 `r2StorageAdaptiveGroups` 获取存储数据
- 统一使用 `date_geq/date_leq` 过滤器

## 🎨 界面优化

### 仪表盘增强

在主仪表盘新增 **8 个监控指标卡片**：

**D1 监控区域**:
- D1已读行 | D1已写行
- D1总存储 | D1数据库

**R2 监控区域**:
- R2 A类操作 | R2 B类操作
- R2总存储 | R2存储桶

**卡片特性**:
- Material Design 3 设计风格
- 清晰的标签与醒目的数值
- 自动数字格式化（K/M/B 单位）
- 智能字节单位转换（B/KB/MB/GB/TB）

## 🔧 技术改进

### GraphQL 查询优化

1. **统一过滤器**: D1 和 R2 统一使用 `date_geq/date_leq` 日期过滤
2. **精确字段**:
   - D1: `rowsRead`, `rowsWritten`
   - R2: `requests` (替代旧的 objectCount/uploadCount)
3. **维度分组**: R2 使用 `actionType` 维度实现 A/B 类智能分类
4. **聚合优化**: R2 存储使用 `max { payloadSize }` 获取总大小

### 数据模型扩展

新增完整的 D1 和 R2 数据模型：

```kotlin
// DashboardMetrics 新增字段
val d1ReadRows: Long = 0
val d1WriteRows: Long = 0
val d1StorageBytes: Long = 0
val d1DatabaseCount: Int = 0

val r2ClassAOperations: Long = 0
val r2ClassBOperations: Long = 0
val r2StorageBytes: Long = 0
val r2BucketCount: Int = 0
```

### 性能优化

- **并行请求**: GraphQL 和 REST API 并行加载
- **数据缓存**: ViewModel 层智能缓存
- **异步处理**: Kotlin Coroutines 异步网络请求

## 🐛 Bug 修复

- 修复编译警告：移除未使用的 `dateOnlyFormat` 和 `dateTimeFormat` 变量
- 优化代码质量：修正未使用的 lambda 参数命名
- 提升代码可维护性

## 📋 API 权限要求

新功能需要以下 Cloudflare API 权限：

✅ **Account Analytics**: Read（必需）  
✅ **D1**: Read（查看数据库列表）  
✅ **R2**: Read（查看存储桶列表）

## 💰 计费参考

### D1 免费额度
- 每天 **500 万行读取** + **10 万行写入**
- 超出按行计费
- 存储费用单独计算

### R2 计费
- A 类操作：**$4.50** / 百万次请求
- B 类操作：**$0.36** / 百万次请求
- 存储费用：**$0.015** / GB / 月
- 出站流量：**免费** ✨

## 🚀 使用指南

1. **打开应用主页**，仪表盘自动加载数据
2. **查看监控指标**，了解 D1 和 R2 使用情况
3. **点击刷新按钮** 🔄，获取最新数据
4. **切换时间范围**（1天/7天/30天），查看趋势
