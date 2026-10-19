# 哔哩TV（Android TV 客户端）

简约液态玻璃风格的 B 站电视客户端，Kotlin + Jetpack Compose。

## 功能

- 扫码登录（手机 B 站 App 扫码，Cookie 持久化，重启自动恢复）
- 推荐 / 热门 / 排行、热搜与搜索
- 关注动态（视频）
- 我的：历史记录、收藏夹、关注列表，进入 UP 主空间
- 视频详情：分 P、相关推荐、评论、点赞 / 投币 / 收藏 / 关注
- 播放器：DASH 音视频合并、清晰度切换、倍速、滚动弹幕、进度上报（写入历史）
- 遥控器：方向键导航，确认键播放 / 暂停，左右键快退 / 快进 10 秒，返回键退出

## 构建 APK

### 方式一：GitHub Actions（推荐，无需本地环境）

1. 新建一个 GitHub 仓库，把本目录全部内容推送上去（保持 `.github` 目录）。
2. 打开仓库的 Actions 页面，等待 “Build APK” 完成（或手动 Run workflow）。
3. 在运行结果的 Artifacts 中下载 `BiliTV-apk`，得到 APK。

### 方式二：本地 Android Studio

用 Android Studio（Koala 或更新）打开本目录，等待同步后执行
Build → Build APK(s)。

## 安装到电视

用 adb：`adb connect <电视IP>:5555`，然后 `adb install app-release.apk`；
或把 APK 放到 U 盘 / 通过“当贝”等文件管理应用安装。

## 注意

- 这是第三方客户端，使用的是 B 站网页端公开接口；接口可能随时调整。
- 未登录时可观看低清晰度，登录后可用更高清晰度（4K / HDR 取决于账号权限）。
- 工程在编写时没有条件实际编译运行，首次构建如遇编译错误，请把错误日志反馈以便修正。
