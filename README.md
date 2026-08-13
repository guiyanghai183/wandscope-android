# WandScope for Android

WandScope 是一个只读的原生 Android W&B 客户端，采用 Jetpack Compose 构建。

## 功能

- API Key 使用 Android Keystore 加密，只保存在设备本地。
- 浏览 Entity、Projects、Runs、Run Summary、Config 和 System 数据。
- 两层曲线选择器：先选 `System`、`Charts` 或路径分组，再选择具体指标。
- 只显示由 W&B 明确标记为数值类型的 history/system 指标，不显示最新值。
- Project 页面最多对比最近 5 个 Run，最多选择 8 个指标；Run 页面显示单 Run 曲线。
- 首次读取只建立 Run 状态基线；此后 Run 进入 `finished`/`completed` 时通知。
- 前台当前 Project 每 60 秒刷新；后台由 WorkManager 最快约 15 分钟、由 Android 系统择机执行。
- 支持 GitHub Release `update.json` 检查、HTTPS 域名约束、APK SHA-256 校验和系统安装器确认。

## 构建

需要 JDK 17 与 Android SDK 36：

```powershell
$env:JAVA_HOME='C:\Path\To\JDK17'
.\gradlew.bat test lint assembleDebug assembleRelease
```

Release 默认不签名。正式发布时请从环境变量或私有 Gradle 配置接入独立 Android keystore，绝不要提交私钥或密码。

## 更新清单

应用固定读取：

`https://github.com/guiyanghai183/wandscope-android/releases/latest/download/update.json`

文件必须恰好包含以下字段：

```json
{
  "versionCode": 2,
  "versionName": "1.0.1",
  "apkUrl": "https://github.com/guiyanghai183/wandscope-android/releases/download/v1.0.1/WandScope-1.0.1.apk",
  "sha256": "64位小写SHA-256",
  "releaseUrl": "https://github.com/guiyanghai183/wandscope-android/releases/tag/v1.0.1"
}
```

Android 不允许普通应用静默安装。校验成功后，WandScope 只会打开系统安装器，最终安装必须由用户确认，并且新 APK 必须使用与当前版本相同的签名证书。
