### **v7.7.6 更新日志**

#### 新增：Worker / Pages 远程 URL 文件上传
- Worker 与 Pages 部署输入框支持直接粘贴 HTTP / HTTPS 远程 URL，保存时自动下载远程文件再进行部署
- Pages 远程文件下载前校验 Content-Length，超过 25MB 直接拒绝，下载过程中流式校验最终体积，避免内存溢出
- 远程文件扩展名严格限定为 js / zip / html / htm，不区分大小写，不符合时走本地文本部署逻辑
- 远程下载遵循 301 / 302 重定向，连接超时 15 秒，读取超时 90 秒，总调用超时 120 秒
- Worker 脚本兼容性标志同步纳入远程上传流程，与脚本内容一并提交
- 新增远程下载专用字符串资源与 RemoteFileDownloader 工具类，Repository 层签名保持不变

#### 新增：显示大小设置重构
- 显示大小由旧的自定义 Spinner 加确认弹窗改为 Material3 MaterialButtonToggleGroup 单选样式，每行三个按钮，与语言切换与主题模式 UI 保持一致
- 选择后即时生效，不再弹出二次确认对话框，交互路径与 App 语言切换对齐
- 夜间模式快照残留修复：Locale 与 DisplaySize 变更时强制重建 Activity 前清理过渡窗口背景，避免深色主题残影
- AppCompatDelegate 主题同步逻辑完善：配置变更后按当前 nightMode 重新应用 DayNight 资源，确保夜间模式在 Configuration 变化后仍正确生效

#### 优化：兼容性标志输入体验重构
- 新增内置下拉箭头 drawable 资源 ic_dropdown_arrow，替换此前对 Android 私有资源 arrow_down_float 的引用，避免厂商定制 ROM 资源缺失
- 常用兼容性标志快速添加逻辑由独立 Spinner 改为直接绑定目标 TextInputLayout OutlinedBox 风格，点击箭头展开内置选项，选中后追加到输入框并换行
- 下拉选项自动去重：输入框中已存在的标志不会重复追加，选择后清空选中态以便连续添加
- 输入框不再限制 maxLines，高度随内容自适应，长列表标志无需手动滚动浏览
- Worker 部署卡片、Pages 部署卡片、运行时设置对话框三场景兼容性标志输入框样式、占位文案、下拉箭头、快速添加逻辑完全统一
- 主题夜间模式异常修复：下拉菜单背景统一通过 bg_popup_surface 配合 DropdownInjector 双路径注入，暗色主题下不再出现白底黑字或黑底白字错位

#### 修复：网络日志敏感头泄露
- HttpLoggingInterceptor 用于 release 与 debug 网络层，对以下请求头统一脱敏显示为 ██：Authorization、X-Auth-Email、X-Auth-Key、X-Auth-Token、CF-Access-Client-Id、CF-Access-Client-Secret、Cookie、Set-Cookie
- 自定义 LogOkHttpInterceptor 应用内日志记录同步对上述八类请求与响应头做大小写不敏感脱敏，覆盖 Worker、Pages、R2 S3 等所有复用该拦截器的请求链
- 避免 Global API Key、API Token、Cloudflare Access 凭证、会话 Cookie 等写入 logcat 或本地日志文件

本次更新完成 Worker 与 Pages 远程 URL 部署链路、显示大小设置 UI 统一、兼容性标志输入体验重构以及网络日志敏感信息脱敏，建议所有使用 Global API Key 认证与远程脚本部署的用户升级。
