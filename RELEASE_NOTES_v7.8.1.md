### **v7.8.1 更新日志**

> **提示：** 本次更新无破坏性变更，可直接覆盖安装。

#### 优化：启用代码混淆与资源压缩

- 调整 release 构建类型，启用 minifyEnabled 和 shrinkResources，显著缩小安装包体积
- 新增 R8 兼容模式配置，避免 Retrofit / Gson 在 full 模式下因泛型擦除导致的解析失败
- 优化打包排除规则，移除无用的第三方库资源文件，进一步压缩产物大小
- 更新混淆规则，保留 Gson / Retrofit 必要的反射信息，确保运行时类型解析正常

#### 新增：项目部署日志与轮询就绪逻辑

- 添加项目部署日志字符串，方便排查新建项目后的部署流程问题
- 新增轮询就绪逻辑，解决新建项目后 upload-token 接口偶发失败的问题

#### 修复：删除网关位置解析异常

- 调整 CloudFlareApi 的 deleteGatewayLocation 接口返回类型为 Response<Void>，适配接口空 body 的情况
- 优化错误信息获取逻辑，优先取 errorBody 内容并截断长度，fallback 到 response message 和默认提示

本次更新完成代码混淆与资源压缩启用、R8 full 模式反射问题修复、项目部署轮询就绪逻辑以及删除网关位置解析异常修复
