# WandScope for Android

WandScope 是一个只读的原生 Android W&B 客户端，采用 Jetpack Compose 构建。

## 功能

- API Key 在连接时立即使用 Android Keystore 加密并保存在设备本地；关闭应用或断开当前会话后可以自动恢复，不需要重复输入。
- 浏览 Entity、Projects、Runs、Run Config 和曲线数据。
- 两层曲线选择器：根据当前 Run 实际记录的命名空间动态显示 `System`、`Charts`、`Train`、`Validation` 等分类；进入分类后可实时搜索具体指标。
- 只显示由 W&B 明确标记为数值类型的 history/system 指标，不显示最新值。
- Project 页面最多对比最近 5 个 Run，最多选择 8 个指标；Run 页面显示单 Run 曲线、完整首尾范围以及横纵双轴刻度。
- 已显示的曲线卡片可向右拖动，拖动时实时露出红色“移除曲线”；超过约 28% 宽度后松手才移除，未达到阈值会回弹。该操作只取消本地显示和选择，不会删除 W&B 指标或 Run。
- 首次读取只建立 Run 状态基线；此后 Run 进入 `finished`/`completed` 时通知。
- 前台当前 Project 每 60 秒刷新；后台由 WorkManager 最快约 15 分钟、由 Android 系统择机执行。
- 支持 GitHub Release `update.json` 检查、HTTPS 域名约束、APK SHA-256 校验和系统安装器确认。
- 更新对话框在下载 APK 时显示实时进度条和百分比，并分别提示下载、SHA-256 校验和打开系统安装器阶段。
- 仓库尚未发布 Release 或缺少 `update.json` 时，更新检查会显示“当前没有可用的在线更新”，不会把 GitHub 404 显示为服务器故障。

## 构建

需要 JDK 17 与 Android SDK 36：

```powershell
$env:JAVA_HOME='C:\Path\To\JDK17'
.\gradlew.bat test lint assembleDebug assembleRelease
```

Release 默认不签名。正式发布时请从环境变量或私有 Gradle 配置接入独立 Android keystore，绝不要提交私钥或密码。为了与当前本地测试安装保持签名连续，可显式使用 `-PuseDebugSigning=true` 生成由本机 Debug 证书签名的个人测试 Release；该模式不适合应用商店发布。

## 更新清单

应用固定读取：

`https://github.com/guiyanghai183/wandscope-android/releases/latest/download/update.json`

文件必须恰好包含以下字段：

```json
{
  "versionCode": 4,
  "versionName": "1.0.3",
  "apkUrl": "https://github.com/guiyanghai183/wandscope-android/releases/download/v1.0.3/WandScope-1.0.3.apk",
  "sha256": "64位小写SHA-256",
  "releaseUrl": "https://github.com/guiyanghai183/wandscope-android/releases/tag/v1.0.3"
}
```

Android 不允许普通应用静默安装。校验成功后，WandScope 只会打开系统安装器，最终安装必须由用户确认，并且新 APK 必须使用与当前版本相同的签名证书。
