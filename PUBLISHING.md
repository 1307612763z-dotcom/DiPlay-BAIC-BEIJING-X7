# 发布说明（DiPlay · 北汽/北京 X7）

本目录是可直接推送的干净分支。推送前请确认以下清单。

## 一、绝对不要提交

| 路径 / 文件 | 原因 |
|---|---|
| `runtime-assets/offline-mfi/identity.pk8`、`certificate.p7b` | CarPlay 配件身份与私钥，泄露即等于公开密钥 |
| `*.apk`（官方包、厂商包、构建产物） | 第三方专有软件，不得再分发 |
| `local.properties` | 本机 SDK 路径 |
| `build/`、`.gradle/`、`.idea/` | 构建产物 / IDE 配置 |
| 含车牌、定位、住址的截图 | 隐私，且地图截图会暴露常去地点 |

本仓库已排除上述内容；若你自己补图，请先打码。

## 二、许可证

上游 DiPlay 为 **GPL-3.0**。本分支是衍生作品，必须：

- 保持 GPL-3.0（保留 `LICENSE` 与 `docs/THIRD_PARTY_NOTICES.md`）
- 在 README 注明基于 DiPlay 的哪个提交（当前：`615b32ce`）
- 不声称与 Apple / 比亚迪 / 博泰 / 北汽有任何关联或认证

## 三、两个仓库

| 仓库 | 类型 | 作用 |
|---|---|---|
| `DiPlay-BAIC-BEIJING-X7` | fork of `shihabal3amri/DiPlay` | 开发主仓；与上游共享 git 历史，可 `fetch upstream`、可提 PR |
| `DiPlay-BEIJING-X7` | 普通仓库（非 fork） | **搜索入口**。GitHub 仓库搜索默认排除 fork（需 `fork:true` 才含），镜像仓让默认搜索能命中；内容与 fork 的 `baic-x7` 分支一致，推成 `main` |

同步命令：

```powershell
tools\sync-mirror.ps1                       # 已提交时
tools\sync-mirror.ps1 -Message "fix: xxx"   # 顺手提交
```

等价的手工命令：

```bash
git push origin baic-x7
git push mirror baic-x7:main
```

> 镜像仓库里有同一份 `.github/workflows/android.yml`，每次同步都会跑一遍完整 CI。
> 不想浪费额度就在镜像仓库 Settings → Actions 里设为 Disabled——它只承担搜索入口的职责。

## 四、与上游同步

```bash
git fetch upstream
git merge upstream/main      # 或 rebase，按你的习惯
```

冲突高发文件（都是我们改过的）：

- `CarPlayHostActivity.kt`
- `AirPlayPersistence.kt`
- `MicrophoneUplink.kt`
- `LocalAdb.kt`
- `README.md`、`.github/workflows/android.yml`

如果冲突太难解，另一条路是"重放补丁"：重新下载上游源码，再把本分支这几个文件的改动搬过去。

## 五、仓库设置建议

- Topics：`carplay` `android-headunit` `baic` `beijingx7` `pateo` `diplay`
- Description 建议单行、中英之间用 `·` 分隔，避免粘连
- 默认分支：fork 用 `baic-x7`；镜像用 `main`（推送即默认）
- 不要开启 Actions 自动发布 APK（涉及签名与身份资产）

## 六、发布前自检

```bash
# 1. 确认没有身份资产
git ls-files | grep -Ei 'pk8|p7b|\.apk$|local\.properties' && echo "!! needs removal" || echo "clean"

# 2. 确认我们的改动在
git ls-files | grep -Ei 'PateoClusterRoute|PateoClusterGeometry|PateoHeadUnit|BAIC_X7'

# 3. 编译（需 JDK 25 + SDK 37 + NDK 28.2）
DIPLAY_AUTH_ASSETS_DIR=/path/to/runtime-assets ./gradlew :mobile:assembleStandaloneDebug

# 4. 单测（AGENTS.md 要求）
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest
```

第 4 步在本地开发机上可能因网络跑不通，但 CI（`.github/workflows/android.yml`）会执行同一组任务，
推送后看 Actions 结果即可。
