## **v7.8.3 更新日志**

> **提示：** 本次更新无破坏性变更，可直接覆盖安装。

#### 新增：Worker / Pages 折叠式功能按钮布局

- 页面项目和 Worker 脚本的功能按钮改为 4 列网格布局，图标在上文字在下，解决按钮过多导致文字截断的问题
- 新增"展开全部功能 / 收起"按钮，默认显示前 8 个按钮，点击展开显示全部
- Pages 环境同步按钮纳入网格布局，新增"环境同步"短标签字符串资源

#### 优化：RecyclerView 列表更新性能

- 新增 notifyListChanged 扩展方法，根据列表新旧大小自动选择 notifyItemRangeChanged / Inserted / Removed
- 替代全局 notifyDataSetChanged 或固定范围更新，提升列表更新性能并避免缩容时的 IndexOutOfBoundsException 崩溃

#### 修复：配置变更重建 Activity 异常及主页布局适配

- 将直接调用 recreate() 改为通过 decorView.post 延迟执行，避免 MIUI 等 ROM 在 onResume 中触发 ClassCastException
- 调整主页纵向布局最小高度阈值为 95dp，优化卡片内容显示
- 修复主页卡片内布局高度未匹配父容器导致内容无法垂直居中的问题

本次更新包含 Worker / Pages 折叠式功能按钮布局、RecyclerView 列表更新优化、配置变更重建 Activity 异常修复及主页布局适配优化
