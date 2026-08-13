# WandScope Android 隐私说明

WandScope 是一个只读的第三方 W&B 客户端，不隶属于 Weights & Biases。

## 本地保存的数据

- W&B API Key：使用 Android Keystore 的 AES-GCM 密钥加密后保存在应用私有目录。
- 最近选择的 Entity 与曲线指标。
- 最多 8 个曾打开 Project 的 `entity/project` 标识，用于 Run 完成检查。
- 最近 Run 的 ID、状态，以及已通知的 Run ID，用于首次静默基线和通知去重。
- 上述普通偏好不会包含 API Key、曲线采样点、Config 或完整 W&B 响应。

## 网络访问

- W&B GraphQL：`https://api.wandb.ai/graphql`，用于读取用户授权可见的 Projects、Runs、指标和曲线。
- GitHub Releases：仅访问 `guiyanghai183/wandscope-android` 及 GitHub 官方资产域，用于检查和下载更新。

## 通知与后台任务

- 用户登录后，Android 13 及以上会请求通知权限一次；拒绝不会影响浏览功能。
- 首次读取每个 Project 只建立基线，不会为已有完成 Run 发送通知。
- 前台打开 Project 时约每 60 秒刷新一次；后台使用 WorkManager，最短周期约 15 分钟，实际执行时间由 Android 系统决定，不保证即时。

## 备份与退出

- 应用禁用云备份和设备迁移备份，避免 API Key 与监控状态离开当前设备。
- 断开当前会话只会取消当前网络会话和后台任务，不会删除已加密保存的 API Key；下次打开应用时可以自动连接。
- 登录页提供“删除已保存的 API Key”操作；只有明确使用该操作时，应用才会删除密文、Android Keystore 密钥、普通偏好和监控状态。
