## 🎉 主要更新

### 🆕 新增 Zero Trust 模块
全新添加 Cloudflare Zero Trust (Cloudflare One) 管理功能，支持 Access、Gateway、Devices、Tunnels 四大子模块。

#### 🔐 Access 访问控制
- **应用管理**
  - 查看所有 Access 应用列表
  - 创建/编辑/删除应用
  - 配置应用类型、域名、会话时长等
  
- **策略管理**
  - 为应用配置访问策略
  - 支持 Allow/Deny/Bypass 等策略类型
  - 配置包含/排除规则
  
- **访问组管理**
  - 创建/编辑/删除访问组
  - 配置组成员规则

#### 🛡️ Gateway 网关
- **规则管理**
  - 查看所有 Gateway 规则
  - 创建/编辑/删除规则
  - 支持 DNS/HTTP/Network 规则类型
  - 配置规则条件和动作
  
- **列表管理**
  - 创建/编辑/删除 Gateway 列表
  - 支持域名、IP、URL 等列表类型
  - 添加/删除列表项
  
- **位置管理**
  - 查看所有网络位置
  - 创建/编辑/删除位置
  - 配置位置网络

#### 🖥️ Devices 设备管理
- **设备列表**
  - 查看所有已注册设备
  - 设备详情对话框（类型、平台、用户、网络、时间信息）
  - 撤销设备访问权限

- **设备策略**
  - 创建/编辑/删除设备策略
  - 配置策略名称、描述、匹配表达式
  - 设置策略优先级
  - 启用/禁用策略

#### 🚇 Tunnels 隧道管理
- **隧道列表**
  - 查看所有 Cloudflare Tunnel
  - 创建/删除隧道
  - 显示隧道状态

- **隧道详情**
  - 查看隧道 ID、名称、状态、创建时间
  - 实时显示连接信息（Colo、客户端版本、Origin IP、连接时间）

- **隧道配置**
  - 编辑 Ingress 入口规则（主机名、路径、服务地址）
  - 添加/删除入口规则
  - 配置 WARP 路由

### 🎨 UI/UX 优化
- **主界面新增 Zero Trust 卡片**
  - 位于 D1 数据库卡片下方
  - 点击进入 Zero Trust 管理界面
  
- **Zero Trust 导航界面**
  - 四大模块卡片式入口
  - TabLayout 分页导航

### 📚 文档更新
- **API 权限说明完善**
  - 新增 Zero Trust 相关权限说明
  - 账户级别权限：Account Settings、Workers、KV、R2、Pages、D1、Analytics
  - Zero Trust 权限：Zero Trust、Access、Gateway、Cloudflare Tunnel
  - Zone 级别权限：Zone、DNS

## 📋 技术细节

### 新增文件
**Zero Trust 主模块**
- `ZeroTrustFragment.kt` - Zero Trust 主界面
- `ZeroTrustRepository.kt` - Zero Trust 数据仓库
- `fragment_zero_trust.xml` - 主界面布局

**Access 模块**
- `AccessFragment.kt` - Access 主界面（TabLayout）
- `AccessApplicationsFragment.kt` - 应用列表
- `AccessPoliciesFragment.kt` - 策略列表
- `AccessGroupsFragment.kt` - 访问组列表
- `AccessViewModel.kt` - Access 状态管理
- 相关适配器和布局文件

**Gateway 模块**
- `GatewayFragment.kt` - Gateway 主界面（TabLayout）
- `GatewayRulesFragment.kt` - 规则列表
- `GatewayListsFragment.kt` - 列表管理
- `GatewayLocationsFragment.kt` - 位置管理
- `GatewayViewModel.kt` - Gateway 状态管理
- 相关适配器和布局文件

**Devices 模块**
- `DevicesFragment.kt` - Devices 主界面（TabLayout）
- `DevicesListFragment.kt` - 设备列表
- `DevicePoliciesFragment.kt` - 设备策略
- `DevicesViewModel.kt` - Devices 状态管理
- 相关适配器和布局文件

**Tunnels 模块**
- `TunnelsFragment.kt` - 隧道列表和管理
- `TunnelsViewModel.kt` - Tunnels 状态管理
- `TunnelAdapter.kt` - 隧道列表适配器
- `TunnelConnectionAdapter.kt` - 连接列表适配器
- 相关布局文件

**数据模型**
- `AccessApplication.kt` - Access 应用模型
- `AccessPolicy.kt` - Access 策略模型
- `AccessGroup.kt` - 访问组模型
- `GatewayRule.kt` - Gateway 规则模型
- `GatewayList.kt` - Gateway 列表模型
- `GatewayLocation.kt` - Gateway 位置模型
- `Device.kt` - 设备模型
- `DeviceSettingsPolicy.kt` - 设备策略模型
- `CloudflareTunnel.kt` - 隧道模型
- `TunnelConnection.kt` - 隧道连接模型
- `TunnelConfiguration.kt` - 隧道配置模型

### API 新增
- Access 应用/策略/组 CRUD 接口
- Gateway 规则/列表/位置 CRUD 接口
- Devices 设备/策略 管理接口
- Tunnels 隧道/连接/配置 管理接口

## 🔄 升级建议
- 使用 Zero Trust 功能需要 API Token 具有相应权限
- 建议在关于页面查看完整权限列表并更新 Token
- 不同功能需要不同权限：
  - Access: 需要 Access: Apps and Policies 权限
  - Gateway: 需要 Gateway 权限
  - Devices: 需要 Zero Trust 权限
  - Tunnels: 需要 Cloudflare Tunnel 权限

## ⚠️ 已知问题
- 如遇到 403 错误，请检查 API Token 权限配置
