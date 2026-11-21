# 北汽 X7 安装指南（车主版）

适用：**2021 款北汽 X7**，车机型号 `C3005H_MX8Q`、主板 `c3005h`（博泰 Pateo，Android 9）。
同款车机可以直接按本文操作；其他博泰车机见文末「移植到其他机型」。

整个过程约 **20–30 分钟**，需要一台电脑 + 一根数据线。

---

## 0. 先确认你的车机

在车机开发者选项里打开 USB 调试，连上电脑，执行：

```bash
adb shell getprop ro.product.device    # 期望输出 c3005h
adb shell getprop ro.build.version.release   # 期望 9
adb shell dumpsys display | grep -E 'HDMI|1920'
```

必须能看到一块 **1920×720 的 HDMI 屏（display 1）**。看不到就不要继续，说明不是同一套硬件。

---

## 1. 准备 CarPlay 身份资产（唯一无法跳过的一步）

调试包本身**不含** CarPlay 配件身份，没有它连不上 iPhone（应用会显示"无法加载 CarPlay 认证"）。

需要一个目录，结构固定：

```
runtime-assets/
└── offline-mfi/
    ├── identity.pk8
    └── certificate.p7b
```

来源二选一：

**A. 从你车机上已装的官方 DiPlay 提取**（本项目开发时用的方式）

```bash
adb shell pm path com.shihab.diplay
adb pull /data/app/com.shihab.diplay-.../base.apk official.apk
unzip -j official.apk 'assets/offline-mfi/*' -d runtime-assets/offline-mfi
```

**B. 你自己的 MFi 配件身份**（如果你有）

> ⚠️ 这些文件是配件身份与私钥：**只用于你自己的车机，绝不提交到仓库、不公开分享**。
> 本项目仓库不包含它们，也不提供下载。

---

## 2. 编译

需要 JDK 25、Android SDK platform 37、build-tools 37，以及 NDK 28.2（`shared` 模块有 native 代码）。

```bash
export DIPLAY_AUTH_ASSETS_DIR=/absolute/path/to/runtime-assets
chmod +x gradlew            # 仓库从 Windows 发布，git 丢了可执行位
./gradlew :mobile:assembleStandaloneDebug
```

产物：`mobile/build/outputs/apk/debug/mobile-debug.apk`
包名 `com.shihab.diplay.hudtest`，**与正式版并存**，不会覆盖你已装的 DiPlay。

不想自己编译？找已经编译过的人要 `mobile-debug.apk` 即可（同样需要第 1 步的资产才能用）。

---

## 3. 装包

```bash
adb install -r mobile-debug.apk
```

## 4. 一条命令完成设置

仓库带了一个脚本，做四件事：装包（可选）、授予悬浮窗权限、写入仪表/音频通道偏好、重启应用。

```powershell
# Windows
powershell -ExecutionPolicy Bypass -File tools\setup-x7.ps1 -Apk path\to\mobile-debug.apk
```

```bash
# macOS / Linux（无脚本时手动执行下面的等价命令）
adb shell appops set com.shihab.diplay.hudtest SYSTEM_ALERT_WINDOW allow
adb shell am force-stop com.shihab.diplay.hudtest
adb shell am start -n com.shihab.diplay.hudtest/com.shilapi.xcertplay.DiPlayActivity
```

脚本写入的偏好：

| 键 | 值 | 作用 |
|---|---|---|
| `cluster_map_enabled` | `true` | 打开仪表投屏 |
| `media_audio_channel` | `4` | 媒体走 4（这台车机唯一干净的通道） |
| `navigation_audio_channel` | `0` | 导航保持默认 |

> 写偏好需要 adb shell 是 root。如果 `adb shell id` 不是 `uid=0`，脚本会跳过这一步，
> 你需要在 DiPlay 设置里手动打开「CarPlay 地图投到仪表」，并把音频通道按上表设好。

## 5. 连接并验证

1. 车机打开热点（设置 → 网络 → 热点，SSID 建议 `car`）
2. 打开 DiPlay → 连接手机 → iPhone 上确认 CarPlay
3. **仪表应显示 CarPlay 地图**

验证命令：

```bash
adb logcat -d -s DiPlay-Cluster        # 期望看到 cluster: overlay 1920x720+0+0 on display 1
adb shell dumpsys window windows | grep -A3 DiPlay-cluster
```

---

## 出问题对照表

| 现象 | 原因 | 处理 |
|---|---|---|
| 应用里红字"无法加载 CarPlay 认证" | 缺身份资产，或资产没进包 | 回第 1 步；确认 `assets/offline-mfi/` 在 APK 里 |
| 仪表中间只有一条，两侧黑 | 车机处在"小窗/默认"布局 | 车机切到「全屏地图」；或把 `FULL_MAP` 改成 `(576,0,768,720)` 重编 |
| 仪表被原车导航盖住 | 原车导航重建了 overlay | 关闭 DiPlay 再打开（会重新提层）；`raise()` 自动提层尚未实现 |
| 完全没画面 | 没授权悬浮窗 | `adb shell appops get com.shihab.diplay.hudtest SYSTEM_ALERT_WINDOW` |
| 微信语音 / 听写没声音 | 麦克风 source 不对 | 看日志 `Microphone: start` 是否为 `VOICE_COMMUNICATION` |
| 媒体声音异常/被裁 | 音频通道不是 4 | 设置 → 高级 → 音频通道映射 → 媒体改 4 |
| 装包报签名冲突 | 你装的是官方包 | 本分支包名带 `.hudtest`，正常不会冲突；若冲突请卸载官方版（会丢配对） |

---

## 卸载 / 恢复

```bash
adb uninstall com.shihab.diplay.hudtest
```

只卸载自测包即可，你的正式版 DiPlay 不受影响。车机自身没有任何改动（全部为只读采集与
per-app 设置）。

---

## 移植到其他博泰机型

代码里所有机型相关的东西都集中在两个地方，改动很小：

1. `PateoClusterGeometry` —— 用 `tools/measure_cluster_rect.py` 量出你那台车仪表的可见矩形：

   ```bash
   adb shell screencap -d 1 -p /sdcard/d1.png && adb pull /sdcard/d1.png
   python tools/measure_cluster_rect.py d1.png --mode FULL_MAP
   ```

   把输出的 `ClusterRect(...)` 填进 `FULL_MAP` / `SMALL_MAP`。

2. `PateoHeadUnit` —— 把 `ro.product.device` 换成你的机型；同一个类还控制麦克风 source
   与媒体通道默认值，按实测结果调整。

其余逻辑（overlay 层级、ALT 流、ADB 端口解析）与机型无关，通常不用改。

实测数据与结论见 [BAIC_X7.md](BAIC_X7.md)。
