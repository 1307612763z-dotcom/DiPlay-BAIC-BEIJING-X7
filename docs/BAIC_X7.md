# BAIC X7 仪表投屏（Pateo c3005h）

本分支为 **北汽 X7（2021 款，博泰 Pateo 车机）** 增加：

1. **CarPlay 地图投到仪表盘**（全屏 1920×720 或中间 768×720）
2. **CarPlay 内的应用麦克风可用**（微信语音、iOS 听写）
3. 本机 ADB 端口自适应（这台车机的 adbd 不在 5555）
4. 音频通道的机型默认值（媒体 4、导航 0）

上游 DiPlay 明确只支持比亚迪车型，因此这些改动维护在本分支，不改上游行为。

---

## 1. 车机信息

| 项 | 值 |
|---|---|
| 型号 | `C3005H_MX8Q` / 主板 `c3005h` / Freescale i.MX8 |
| 系统 | Android 9 (API 28)，`1.0.0-ga-rc3 dev-keys` |
| 系统属性 | `ro.product.device=c3005h`，`ro.adb.secure=0` |
| 车机软件 | `2.30.20240717112805.rc35.6.user.c3005h` |

## 2. 显示拓扑

`dumpsys display`（2026-11-21 实测）：

| displayId | 名称 | 分辨率 | 刷新 | 类型 | 备注 |
|---|---|---|---|---|---|
| 0 | 内置屏幕 | 1920×720 | 50 | BUILT_IN | 中控主屏 |
| 1 | HDMI 屏幕 | 1920×720 | 30 | HDMI | **仪表通道**，`FLAG_PRESENTATION` |
| 2 | THIRD Screen | 648×608 | 50 | vendor(6) | 第三屏 |

仪表只显示 display 1 的**一块区域**，且区域随车机自身的仪表布局变化：

| 车机仪表布局 | display 1 上的可见区域 |
|---|---|
| 全屏地图 | **整块 1920×720** |
| 默认 / 小窗 | 中间 **768×720**（x=576..1344） |

两种区域都用 `screencap -d 1` + 非黑像素包围盒测得（`tools/measure_cluster_rect.py`）。
对应代码：`PateoClusterGeometry.FULL_MAP` / `SMALL_MAP`。

## 3. 为什么不用 Presentation

原车导航 `com.pateonavi.naviapp`（uid 1000）在 display 1 上有一个全屏
`TYPE_APPLICATION_OVERLAY` 窗口（`mBaseLayer=121000`）。普通 Activity 窗口是 21000，
永远在它下面；`Presentation` 同理。

实测：force-stop 原车导航后，DiPlay 的普通 Activity 立刻在 display 1 可见 —— 说明层级是唯一障碍。

因此本分支改为**自己创建 `TYPE_APPLICATION_OVERLAY` 窗口**：

- 同类型窗口后创建的在上，DiPlay 晚于原车导航启动，天然压住它
- 需要 `android.permission.SYSTEM_ALERT_WINDOW`（用户授权「显示在其他应用上层」）
- 未授权时该功能静默关闭，不影响中控

关键日志：

```
DiPlay-Cluster: cluster stream active=true
DiPlay-Cluster: cluster: overlay 1920x720+0+0 on display 1
DiPlay-Cluster: cluster: panel surface 1920x720
```

窗口层级（`dumpsys window windows`）：

```
Window #10 DiPlay-cluster            ← 我们的
  (0,0)(1920x720) ty=APPLICATION_OVERLAY appop=SYSTEM_ALERT_WINDOW
Window #11 com.pateonavi.naviapp     ← 原车导航，在下面
```

## 4. 麦克风：为什么原来没声音

DiPlay 对 `speechrecognition` 默认使用 `VOICE_RECOGNITION`。这台车机上：

```
source=VOICE_RECOGNITION routedDeviceType=15 captureBytes=0
reads=901 zeroReads=901 peakDbfs=silent failure code=1 stage=RECORDING
```

设备选对了（15 = 内置麦），但每次 `read` 返回 0 字节 —— 该固件不打开
`VOICE_RECOGNITION` 的输入流。改用 `VOICE_COMMUNICATION` 后：

```
source=VOICE_COMMUNICATION routedDeviceType=8 captureBytes=237568
zeroReads=0 encodedFrames=123 udpSent=123 peakDbfs=-31
```

实测结果：微信语音可回放、iOS 听写出字。

实现：`network/PateoHeadUnit.kt` 按 `ro.product.device` 识别，`MicrophoneUplink` 中
与 Geely 同样归到 `VOICE_COMMUNICATION`；其他机型不受影响（Robolectric 下 `Build` 不匹配）。

## 5. 本机 ADB 端口

这台车机 `persist.adb.tcp.port=5578`（不是 Android 默认的 5555），且该属性连 root 都改不了。
`LocalAdb` 原来的默认端口写死 5555，改为 `localAdbPort()`：依次读
`service.adb.tcp.port`、`persist.adb.tcp.port`，读不到才回退 5555。

`ro.adb.secure=0` 表示 adbd 不校验密钥，本机客户端可直接连接，无需授权弹窗。

## 6. 音频通道（重要）

这台车机的**媒体通道必须用 4**。其他通道各有问题（音量不对、被裁音、或跑到别的喇叭），
导航通道用默认的 **0** 就正常。

因此本分支在 `AirPlayPersistence.loadMediaAudioChannel()` 里加了设备默认值：
未设置过时，博泰车机取 **4**，其他车机仍取 **0**（自动）。驱动者手动选过的值优先，不会被覆盖。

设置位置：DiPlay → 设置 → 高级 → 音频通道映射（`advanced_audio_channel_mapping`），
对应偏好键 `media_audio_channel` / `navigation_audio_channel`。

| 流 | 通道 | 说明 |
|---|---|---|
| 媒体 / 音乐 | **4** | 唯一干净的通道 |
| 导航 / 提示音 | **0**（默认） | 正常，无需改 |

## 7. 构建

需要 JDK 25 + Android SDK platform 37 + build-tools 37。

```bash
export DIPLAY_AUTH_ASSETS_DIR=/absolute/path/to/runtime-assets   # 见下
./gradlew :mobile:assembleStandaloneDebug
```

`runtime-assets/offline-mfi/` 需要 `identity.pk8` 与 `certificate.p7b` —— 这两份是
CarPlay 配件身份，**不在本仓库中，也不要提交**。自备来源见 `docs/BUILD.md`。

产物 `mobile/build/outputs/apk/debug/mobile-debug.apk`，debug 包名
`com.shihab.diplay.hudtest`（与正式包并存，不会覆盖已装版本）。

## 8. 已知限制

1. **通话上行**仍被车机自带蓝牙电话抢走（`setForceUse(FOR_RECORD, FORCE_BT_SCO)`），
   与第 4 节是两条独立链路。可选方案：删除车机蓝牙里的 iPhone「电话」配对，
   或启用「CarPlay 期间暂停车机蓝牙」（需要本机 ADB）。
2. 原车导航进程**重启并重建 overlay** 时可能把我们压回去，`raise()` 尚未接线。
3. 车机切到**小窗布局**时，1920 宽窗口会被裁成中间 768；布局跟随未实现。
4. 未连接 CarPlay 时，仪表会回退为**中控镜像**（车机自身行为，非本分支引起）。
5. 通话、短信、Siri 之外的电话相关功能未验证。

## 9. 实测证据（日志摘录，不含定位信息）

```
# 仪表投屏
cluster stream active=true
cluster: overlay 1920x720+0+0 on display 1
cluster: panel surface 1920x720

# 麦克风（修复前后）
Microphone: stats type=speechrecognition source=VOICE_RECOGNITION  routedDeviceType=15 captureBytes=0    zeroReads=901 peakDbfs=silent
Microphone: stats type=speechrecognition source=VOICE_COMMUNICATION routedDeviceType=8  captureBytes=237568 zeroReads=0   peakDbfs=-31
```
