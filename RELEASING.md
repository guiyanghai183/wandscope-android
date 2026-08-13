# Android Release 流程

1. 提升 `versionCode`，更新 `versionName`。
2. 使用私有 Android release keystore 构建；keystore 和密码不得进入 Git。
3. 运行：`clean test lint assembleRelease`。
4. 使用 `apksigner verify --verbose --print-certs` 验证 APK，并记录证书 SHA-256。
5. 使用 `zipalign -c -P 16 4` 验证对齐。
6. 计算 APK SHA-256，生成严格五字段 `update.json`。
7. 创建与 `versionName` 一致的 GitHub Release，上传命名后的 APK 与 `update.json`。
8. 从 GitHub 下载回所有资产，重新核对哈希、签名证书与包名。
9. 在至少一台 Android 真机上验证全新安装和从上一版本覆盖升级。

当前 GitHub 个人测试 Release 必须与此前本地 Debug APK 使用同一证书，可在构建时显式传入 `-PuseDebugSigning=true`。该参数只复用本机 Android Debug keystore，不会把私钥或密码写进仓库；发布前必须核对证书 SHA-256 连续。未来迁移到正式 Release keystore 后，旧 Debug 签名安装无法直接覆盖升级。

`update.json` 示例：

```json
{
  "versionCode": 4,
  "versionName": "1.0.3",
  "apkUrl": "https://github.com/guiyanghai183/wandscope-android/releases/download/v1.0.3/WandScope-1.0.3.apk",
  "sha256": "<64位小写SHA-256>",
  "releaseUrl": "https://github.com/guiyanghai183/wandscope-android/releases/tag/v1.0.3"
}
```

应用只负责发现更新、下载、校验并启动系统安装器，不会静默安装。覆盖升级要求 `versionCode` 递增且签名证书连续。
