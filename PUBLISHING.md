# 鍙戝竷璇存槑锛圖iPlay-BAIC-X7锛?
鏈洰褰曟槸鍙洿鎺?`git init` 骞舵帹閫佸埌 GitHub 鐨勫共鍑€鍒嗘敮銆傛帹閫佸墠璇风‘璁や互涓嬫竻鍗曘€?
## 涓€銆佺粷瀵逛笉瑕佹彁浜?
| 璺緞 / 鏂囦欢 | 鍘熷洜 |
|---|---|
| `runtime-assets/offline-mfi/identity.pk8`銆乣certificate.p7b` | CarPlay 閰嶄欢韬唤涓庣閽ワ紝娉勯湶鍗崇瓑浜庡叕寮€瀵嗛挜 |
| `*.apk`锛堝畼鏂瑰寘銆佸巶鍟嗗寘銆佹瀯寤轰骇鐗╋級 | 绗笁鏂逛笓鏈夎蒋浠讹紝涓嶅緱鍐嶅垎鍙?|
| `local.properties` | 鏈満 SDK 璺緞 |
| `build/`銆乣.gradle/`銆乣.idea/` | 鏋勫缓浜х墿/IDE 閰嶇疆 |
| 鍚溅鐗屻€佸畾浣嶃€佷綇鍧€鐨勬埅鍥?| 闅愮锛屼笖鍦板浘鎴浘浼氭毚闇插父鍘诲湴鐐?|

鏈洰褰曞凡鎺掗櫎涓婅堪鍐呭锛涜嫢浣犺嚜宸辫ˉ鍥撅紝璇峰厛鎵撶爜銆?
## 浜屻€佽鍙瘉

涓婃父 DiPlay 涓?**GPL-3.0**銆傛湰鍒嗘敮鏄鐢熶綔鍝侊紝蹇呴』锛?
- 淇濇寔 GPL-3.0锛堜繚鐣?`LICENSE` 涓?`docs/THIRD_PARTY_NOTICES.md`锛?- 鍦?README 娉ㄦ槑鍩轰簬 DiPlay 鐨勫摢涓彁浜?- 涓嶅０绉颁笌 Apple / 姣斾簹杩?/ 鍗氭嘲 / 鍖楁苯鏈変换浣曞叧鑱旀垨璁よ瘉

## 涓夈€佹帹閫佸懡浠?
```bash
cd DiPlay-BAIC-X7
git init
git branch -M baic-x7
git add .
git commit -m "BAIC X7: CarPlay on the instrument panel, app microphone, local ADB port"

# 鍏堝湪 GitHub 涓婂垱寤轰竴涓┖浠撳簱锛堜笉瑕佸嬀閫夊垵濮嬪寲 README锛?git remote add origin git@github.com:<浣犵殑璐﹀彿>/DiPlay-BAIC-X7.git
git push -u origin baic-x7
```

寤鸿鍚屾椂鎵撲竴涓?tag锛歚git tag v0.2.22-baic-x7.1 && git push --tags`銆?
## 鍥涖€佷粨搴撹缃缓璁?
- 鎻忚堪锛歚DiPlay fork for the BAIC X7 (Pateo c3005h): CarPlay on the instrument panel`
- 鍦?README 椤堕儴鍔犱竴鍙ワ紝鎸囧悜 `docs/BAIC_X7.md`锛屽苟娉ㄦ槑涓婃父鍙敮鎸佹瘮浜氳开
- 寮€鍚?Issues锛屾ā鏉挎部鐢ㄤ笂娓?- 涓嶈寮€鍚?Actions 鑷姩鍙戝竷 APK锛堜細娑夊強绛惧悕涓庤韩浠借祫浜э級

## 浜斻€佷笌涓婃父鍚屾

```bash
git remote add upstream https://github.com/shihabal3amri/DiPlay.git
git fetch upstream
git merge upstream/main   # 鎴?rebase锛屾寜浣犵殑涔犳儻
```

鍐茬獊楂樺彂鏂囦欢锛歚CarPlayHostActivity.kt`銆乣MicrophoneUplink.kt`銆乣LocalAdb.kt`銆?
## 鍏€佸彂甯冨墠鑷

```bash
# 1. 纭娌℃湁韬唤璧勪骇
git ls-files | grep -Ei 'pk8|p7b|\.apk$|local\.properties' && echo "!! 闇€瑕佺Щ闄? || echo "clean"

# 2. 纭鎴戜滑鐨勬敼鍔ㄥ湪
git ls-files | grep -Ei 'PateoClusterRoute|PateoClusterGeometry|PateoHeadUnit|BAIC_X7'

# 3. 缂栬瘧锛堥渶 JDK 25 + SDK 37锛?DIPLAY_AUTH_ASSETS_DIR=/path/to/runtime-assets ./gradlew :mobile:assembleStandaloneDebug

# 4. 鍗曟祴锛圓GENTS.md 瑕佹眰锛?./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest
```

绗?4 姝ュ湪鏈満鏃犳硶鎵ц锛圡aven Central 鍩熷悕瑙ｆ瀽澶辫触锛夛紝璇峰湪鏈湴鎴?CI 涓婅窇閫氬悗鍐嶅彂甯冦€?