# DiPlay —— 北汽 X7 分支

[English](README.md) | 简体中文

本项目是 [DiPlay](https://github.com/shihabal3amri/DiPlay)（GPL-3.0）的分支，为 **北汽 X7（2021 款，博泰 Pateo `c3005h` 车机）** 增加支持：

- **CarPlay 地图投到仪表盘** —— 整块 1920×720，或中间 768×720 一条
- **CarPlay 里的应用麦克风可用** —— 微信语音消息、iOS 听写
- 音频通道按机型取默认值 —— 媒体走 4，导航走 0
- 本机 ADB 使用车机实际监听的端口（这台是 5578，不是 5555）

上游 DiPlay 只支持比亚迪车型，所以这些改动放在这里，不进入主仓库。

基于上游提交 `615b32ce`（2026-10-28，即撰写时的 DiPlay `main` 顶端）。
本仓库与上游**没有共享 git 历史**；与那份提交的差异列在下方「相对上游的改动」。
上游 DiPlay 自带的 README 已归档到 [`docs/DIPLAY-README.md`](docs/DIPLAY-README.md)。

完整的技术细节——显示拓扑、为什么不用 `Presentation`、麦克风根因、实测日志——见
**[docs/BAIC_X7.md](docs/BAIC_X7.md)**。

## 状态

| 功能 | 状态 |
|---|---|
| 仪表盘 1920×720 显示 CarPlay 地图 | 已在实车验证 |
| 应用麦克风（微信语音、听写） | 已在实车验证 |
| 媒体通道默认 4 | 已实现，未单独实车验证 |
| 本机 ADB（端口 5578） | 已实现，连接本身尚未验证 |
| 电话通话 | 未解决 —— 车机自带蓝牙电话会抢走麦克风 |
| 仪表小窗布局 | 几何已知，跟随未实现 |
| 原车导航重启后重新提层 | 未接线 |

## 构建

需要 JDK 25、Android SDK platform 37、build-tools 37。

```bash
export DIPLAY_AUTH_ASSETS_DIR=/absolute/path/to/runtime-assets   # 见 docs/BUILD.md
chmod +x gradlew                  # 仓库从 Windows 发布，git 丢了可执行位
./gradlew :mobile:assembleStandaloneDebug
```

运行时 CarPlay 身份文件**不在本仓库中，也请勿提交**。
调试包安装为 `com.shihab.diplay.hudtest`，可与正式版并存。

## 相对上游的改动

| 文件 | 改动 |
|---|---|
| `common/.../PateoClusterRoute.kt` | 新增：在仪表屏的 display 上创建 overlay 窗口，接管 ALT 流 |
| `common/.../PateoClusterGeometry.kt` | 新增：两种仪表布局的矩形参数 |
| `common/.../CarPlayHostActivity.kt` | 3 处接线：仪表路线、流尺寸、关闭清理 |
| `common/.../AirPlayPersistence.kt` | 本机媒体音频通道默认 4 |
| `shared/.../network/PateoHeadUnit.kt` | 新增：车机识别 |
| `shared/.../media/MicrophoneUplink.kt` | 语音识别在本机改用 `VOICE_COMMUNICATION` |
| `shared/.../adb/LocalAdb.kt` | 默认端口改为从系统属性解析 |

## 工具

- `tools/measure_cluster_rect.py` —— 从 `screencap -d 1` 截图量出仪表可见矩形
  （`--mode FULL_MAP` 会直接打印 Kotlin 片段）。

## 许可

GPL-3.0，继承自 DiPlay。CarPlay 是 Apple Inc. 的商标，本项目与 Apple、比亚迪、博泰、北汽均无隶属关系。
