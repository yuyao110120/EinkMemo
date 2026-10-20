# 构建说明（ElinkMemo v0.1）

## 工具链（已安装在本仓库 tools/ 下）

| 组件 | 路径 / 版本 |
|---|---|
| JDK | `tools/jdk17/Contents/Home`（Temurin 17.0.20.1） |
| Gradle | `tools/gradle-8.7/bin/gradle` |
| Android SDK | `tools/android-sdk`（platforms;android-34、build-tools;34.0.0、platform-tools） |
| SDK 的 ANDROID_USER_HOME | `tools/android-user-home`（避免写 ~/.android） |
| Gradle 缓存 | `tools/gradle-home` |
| 本地 SDK 声明 | `android/local.properties` 指向 `tools/android-sdk` |

## 构建 debug APK

优先用仓库内的 Gradle wrapper（CI 也走这条路径，需 JDK 17、联网下载 Gradle 8.7）：

    cd android
    ./gradlew assembleDebug

本机离线工具链（已装在 tools/，无需下载）：

    cd android
    JAVA_HOME=../tools/jdk17/Contents/Home \
    ANDROID_HOME=../tools/android-sdk \
    ANDROID_USER_HOME=../tools/android-user-home \
    GRADLE_USER_HOME=../tools/gradle-home \
    ./gradlew --no-daemon --console=plain assembleDebug

产物：`android/app/build/outputs/apk/debug/app-debug.apk`

## 发布（GitHub Releases，APK 不入库）

### 自动（推荐）：推 tag 触发 GitHub Actions

    git tag -a v0.7 -m "ElinkMemo v0.7"
    git push origin v0.7

工作流 `.github/workflows/release.yml` 会：JDK 17 + Android SDK → `./gradlew assembleDebug` → 生成 Release 并上传 `ElinkMemo-<版本>-debug.apk`。Release 说明优先读取 `docs/releases/v0.7.md`，不存在时自动生成。

### 签名说明

CI 通过仓库 Secret `ANDROID_DEBUG_KEYSTORE_BASE64` 恢复本机 `~/.android/debug.keystore`（默认 debug 证书），并以 `ELINKMEMO_DEBUG_KEYSTORE` 传给 Gradle，因此 **Release APK 与本地 `./gradlew assembleDebug` 同签名，可覆盖安装、数据不丢**。若更换 keystore，已安装版本需要先卸载再装。

### 手动（本地构建后发布）

    mkdir -p dist
    cp android/app/build/outputs/apk/debug/app-debug.apk dist/ElinkMemo-0.7-debug.apk
    gh release create v0.7 dist/ElinkMemo-0.7-debug.apk \
      --title "ElinkMemo v0.7" --notes-file docs/releases/v0.7.md

`dist/` 已在 .gitignore 中忽略；Release 资产由 `gh release create` 上传。

## 版本参数

- applicationId `com.elinkmemo.app`，versionName `0.7`，versionCode 7
- compileSdk 34 / targetSdk 30 / minSdk 26（Leaf2 是 Android 11 = API 30）
- 依赖：Compose BOM 2024.06.00、Kotlin 1.9.24、AGP 8.5.2、OkHttp 4.12.0

## 安装

- `adb install -r app-debug.apk`（需设备开 USB 调试）
- 或把 APK 拷到设备上点击安装（允许「安装未知应用」）

## 与规格的偏差（v0.1）

- 凭据未用 Keystore（v0.2）
- 明文 HTTP 全局放行（内网自用；v0.2 白名单）
- 未接 Onyx SDK，保底刷新靠系统
