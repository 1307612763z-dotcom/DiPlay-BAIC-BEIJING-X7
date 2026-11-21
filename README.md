# DiPlay — BAIC X7 branch

English | [简体中文](README.zh-CN.md)

Fork of [DiPlay](https://github.com/shihabal3amri/DiPlay) (GPL-3.0) that adds support for the
**BAIC X7 (2021, Pateo `c3005h` head unit)**:

- CarPlay on the **instrument panel** — full panel 1920×720, or the centre 768×720 strip
- **App microphone works** through CarPlay: WeChat voice messages and iOS dictation
- Local ADB uses the port the head unit actually listens on (this unit: 5578, not 5555)

**Keywords:** BAIC X7 · BEIJING X7 · Beijing X7 · X7 head unit · instrument cluster CarPlay ·
WeChat voice messages · iOS dictation · Pateo c3005h · DiPlay fork — 北汽X7、北京X7、车机、
仪表盘投屏、微信语音、语音转文字.

Upstream DiPlay targets BYD cars only, so this work lives here instead of in the main repository.

Based on upstream commit `615b32ce` (2026-10-28, the tip of DiPlay `main` at the time of writing).
This repository has no shared git history with upstream; the diff against that commit is listed
under [Changes against upstream](#changes-against-upstream).

**Read [docs/BAIC_X7.md](docs/BAIC_X7.md)** for the measurements, the overlay approach, the
microphone root cause, and the known limits.

**New here? Start with [docs/INSTALL-X7.md](docs/INSTALL-X7.md)** — the owner-facing steps: identity
assets, build, `tools/setup-x7.ps1`, and a troubleshooting table. Problems go to the
[BAIC X7 issue form](.github/ISSUE_TEMPLATE/x7-report.yml).

## Status

| Feature | State |
|---|---|
| CarPlay map on the 1920×720 panel | verified on the car |
| App microphone (WeChat voice, dictation) | verified on the car |
| Local ADB on port 5578 | implemented, connection itself not yet exercised |
| Phone calls | not fixed — the head unit's own Bluetooth phone seizes the mic |
| Small-window panel layout | geometry known, follow-the-layout not implemented |
| Re-raising the panel window after the stock map restarts | not wired |

## Build

JDK 25, Android SDK platform 37, build-tools 37.

```bash
export DIPLAY_AUTH_ASSETS_DIR=/absolute/path/to/runtime-assets   # see docs/BUILD.md
chmod +x gradlew                  # the repo was published from Windows, where git dropped the bit
./gradlew :mobile:assembleStandaloneDebug
```

The runtime CarPlay identity is **not** part of this repository and must not be committed.
The debug APK installs as `com.shihab.diplay.hudtest`, alongside a release install.

## Changes against upstream

| File | Change |
|---|---|
| `common/.../PateoClusterRoute.kt` | new: overlay window on the panel display, feeds the alt stream |
| `common/.../PateoClusterGeometry.kt` | new: panel rectangles for the two layouts |
| `common/.../CarPlayHostActivity.kt` | 3 hooks: cluster route, stream size, dismiss |
| `common/.../AirPlayPersistence.kt` | media audio channel defaults to 4 on this unit |
| `shared/.../network/PateoHeadUnit.kt` | new: device detection |
| `shared/.../media/MicrophoneUplink.kt` | speech recognition uses `VOICE_COMMUNICATION` on this unit |
| `shared/.../adb/LocalAdb.kt` | default port resolved from the system properties |

## Tools

- `tools/measure_cluster_rect.py` — measure the panel's visible rectangle from a
  `screencap -d 1` capture (`--mode FULL_MAP` prints the Kotlin snippet).

## Upstream

DiPlay's own README (BYD scope, build and release procedure) is archived at
[`docs/DIPLAY-README.md`](docs/DIPLAY-README.md); the older xcertplay readme that upstream keeps in
`docs/` is unchanged.

## Credits

The BAIC X7 support in this branch was developed with AI assistance: **DeepSeek Harness
(`deepseek-flash`)** wrote the code changes and ran the on-car measurements and debugging over ADB,
at the vehicle owner's direction. The owner confirmed every fix on the car — the panel picture,
WeChat voice messages, iOS dictation and the audio channel behaviour.

DiPlay itself is by [shihabal3amri](https://github.com/shihabal3amri) and its contributors.

## Licence

GPL-3.0, inherited from DiPlay. CarPlay is a trademark of Apple Inc.; no affiliation is implied.
