# GitHub Actions 配置说明

## 🔐 配置 GitHub Secrets

要让 GitHub Actions 自动编译和发布 APK，需要在 GitHub 仓库中配置以下 Secrets：

### 1. 访问仓库设置
1. 打开仓库：https://github.com/a422015028/CloudFlareAssistant
2. 点击 **Settings** → **Secrets and variables** → **Actions**
3. 点击 **New repository secret**

### 2. 添加以下 Secrets

#### KEYSTORE_BASE64
- **Name**: `KEYSTORE_BASE64`
- **Value**: 密钥库文件的 Base64 编码

**生成方法**（在本地 PowerShell 运行）：
```powershell
$bytes = [System.IO.File]::ReadAllBytes("E:\AI\MT.jks")
$base64 = [System.Convert]::ToBase64String($bytes)
$base64 | Set-Clipboard
Write-Host "密钥库 Base64 已复制到剪贴板，可直接粘贴到 GitHub Secrets"
```

#### KEYSTORE_PASSWORD
- **Name**: `KEYSTORE_PASSWORD`
- **Value**: `861390202`

#### KEY_ALIAS
- **Name**: `KEY_ALIAS`
- **Value**: `MT`

#### KEY_PASSWORD
- **Name**: `KEY_PASSWORD`
- **Value**: `861390202`

## 🚀 使用方法

### 方法 1：通过 Git Tag 自动触发
```bash
# 创建并推送 tag
git tag -a v5.2 -m "版本 5.2"
git push origin v5.2
```

### 方法 2：手动触发
1. 访问：https://github.com/a422015028/CloudFlareAssistant/actions
2. 选择 **Build and Release APK** workflow
3. 点击 **Run workflow**
4. 选择分支并点击 **Run workflow**

## 📋 Workflow 功能

- ✅ 自动编译 Release APK
- ✅ 使用密钥库签名
- ✅ 自动创建 GitHub Release
- ✅ 自动上传签名后的 APK
- ✅ 自动生成版本说明

## 🔄 工作流程

1. 推送 tag 或手动触发
2. GitHub Actions 自动：
   - 检出代码
   - 配置 JDK 17
   - 解码密钥库
   - 编译并签名 APK
   - 创建 Release
   - 上传 APK

## ⚠️ 注意事项

1. **密钥安全**：永远不要将密钥库文件提交到 Git
2. **Secrets 管理**：定期更新和检查 Secrets
3. **Tag 命名**：使用 `v*` 格式（如 v5.1, v5.2）
4. **本地编译**：本地仍可使用原有配置编译

## 📝 本地快速生成 Base64

运行以下命令生成密钥库的 Base64 编码：
```powershell
$bytes = [System.IO.File]::ReadAllBytes("E:\AI\MT.jks")
[System.Convert]::ToBase64String($bytes) | Out-File -FilePath "keystore-base64.txt"
Write-Host "Base64 已保存到 keystore-base64.txt"
```
